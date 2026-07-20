package io.mehdieidi.varka.backend.assistant;

import io.mehdieidi.varka.backend.observability.VarkaMetrics;
import io.mehdieidi.varka.platform.assistant.agent.AgentTurnLoop;
import io.mehdieidi.varka.platform.assistant.application.AgenticAssistantFacade;
import io.mehdieidi.varka.platform.assistant.source.RequirementCoverageEvaluator;
import io.mehdieidi.varka.platform.assistant.source.SourceUnitSplitter;
import io.mehdieidi.varka.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.varka.platform.assistant.turn.AssistantTurn;
import io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.storage.api.PlatformStore;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Claims queued assistant turns and executes them outside the request thread. */
@Component
public final class DurableAssistantTurnWorker {
  /** Server-written marker used only by the confirmation endpoint, never shown to providers. */
  public static final String CONFIRMED_DESTRUCTION_PREFIX = "[durable-confirmed-destruction] ";

  private static final String AUTOMATIC_SLICE_MARKER = "[automatic-slice:";
  private static final Pattern REQUIREMENT_ID = Pattern.compile("(?i)\\bR[-_ ]?(\\d+)\\b");

  private final AssistantTurnStore turns;
  private final AgenticAssistantFacade assistant;
  private final PlatformStore store;
  private final VarkaMetrics metrics;
  private final AssistantSettings settings;
  private final String workerId = "assistant-" + UUID.randomUUID();
  private final SourceUnitSplitter sourceUnits = new SourceUnitSplitter(12000);
  private final java.util.concurrent.ScheduledExecutorService heartbeats =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "assistant-turn-heartbeat");
            thread.setDaemon(true);
            return thread;
          });

  /**
   * Executes claimed turns independently of Spring's scheduler thread. A zero-capacity queue is
   * deliberate: turns remain QUEUED in PostgreSQL until a worker is actually available rather than
   * being marked RUNNING while waiting in an in-memory queue.
   */
  private final ThreadPoolExecutor workers =
      new ThreadPoolExecutor(
          2,
          2,
          0L,
          TimeUnit.MILLISECONDS,
          new SynchronousQueue<>(),
          runnable -> {
            Thread thread = new Thread(runnable, "assistant-turn-worker");
            thread.setDaemon(true);
            return thread;
          });

  private final Semaphore workerCapacity = new Semaphore(2);

  public DurableAssistantTurnWorker(
      AssistantTurnStore turns,
      AgenticAssistantFacade assistant,
      PlatformStore store,
      VarkaMetrics metrics,
      AssistantSettings settings) {
    this.turns = turns;
    this.assistant = assistant;
    this.store = store;
    this.metrics = metrics;
    this.settings = settings;
  }

  @Scheduled(fixedDelayString = "${varka.ai.worker-poll-interval:PT0.5S}")
  public void runOne() {
    try {
      turns.expireTimedOut(Instant.now());
      if (!workerCapacity.tryAcquire()) return;
      var claimed = turns.claim(workerId, Instant.now(), Duration.ofSeconds(30));
      if (claimed.isEmpty()) {
        workerCapacity.release();
        return;
      }
      try {
        workers.execute(
            () -> {
              try {
                execute(claimed.get());
              } finally {
                workerCapacity.release();
              }
            });
      } catch (java.util.concurrent.RejectedExecutionException ignored) {
        workerCapacity.release();
        // The lease expires quickly and another poller can safely resume the turn.
      }
    } catch (org.springframework.jdbc.BadSqlGrammarException missingAssistantSchema) {
      // Some focused web-contract tests initialize only the platform schema. Production Flyway
      // always installs the assistant schema before this scheduled worker is enabled.
    }
  }

  private void execute(AssistantTurn turn) {
    // Queue time is distinct from end-to-end turn duration; it reveals saturation before users
    // experience deadline failures.
    metrics.recordAssistantPhaseDuration(
        "queue", Math.max(0L, Duration.between(turn.acceptedAt(), Instant.now()).toMillis()));
    ScheduledFuture<?> heartbeat =
        heartbeats.scheduleAtFixedRate(
            () -> turns.heartbeat(turn.id(), workerId, Instant.now(), Duration.ofSeconds(30)),
            10,
            10,
            java.util.concurrent.TimeUnit.SECONDS);
    try {
      String remainingWork = null;
      String sourceForAgent = turn.sourceText();
      java.util.List<io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore.SourceUnit>
          units = java.util.List.of();
      if (turn.sourceText() != null && !turn.sourceText().isBlank()) {
        units = sourceUnits.split(turn.sourceText());
        turns.saveSourceUnits(turn.id(), units);
        java.util.List<AssistantTurnStore.SourceUnit> selected =
            selectCachedSourceUnits(turn, units);
        turns.saveContextCache(
            turn.id(),
            new AssistantTurnStore.ContextCache(
                selected.stream().map(AssistantTurnStore.SourceUnit::id).toList(),
                java.util.List.of()));
        sourceForAgent = annotatedSourceUnits(selected);
        turns.appendEvent(
            turn.id(),
            "turn.stage",
            java.util.Map.of(
                "stage", "SOURCE_UNITS_READY",
                "sourceUnits", units.size(),
                "selectedSourceUnits", selected.size()));
      }
      if (turns.cancellationRequested(turn.id())) {
        complete(turn, AssistantTurn.State.CANCELLED, "Assistant turn was cancelled.", null, null);
        return;
      }
      UserRecord user =
          store.require(
              Path.of("users", turn.userId() + ".json"), UserRecord.class, "User not found.");
      boolean destructiveConfirmed = turn.message().startsWith(CONFIRMED_DESTRUCTION_PREFIX);
      String message =
          destructiveConfirmed
              ? turn.message().substring(CONFIRMED_DESTRUCTION_PREFIX.length())
              : turn.message();
      var result =
          assistant.durableMessage(
              user,
              turn.threadId(),
              turn.modelId(),
              turn.expectedRevision(),
              message,
              sourceForAgent,
              destructiveConfirmed,
              () -> turns.cancellationRequested(turn.id()),
              () ->
                  Instant.now().isAfter(turn.deadlineAt())
                      ? new io.mehdieidi.varka.platform.kernel.PlatformException(
                          504, "Assistant turn exceeded its configured deadline.")
                      : null);
      if (!result.inversePatch().isEmpty()) {
        turns.saveCheckpoint(turn.id(), result.modelId(), result.revision(), result.inversePatch());
        metrics.recordAssistantCheckpoint("saved");
        turns.appendEvent(
            turn.id(),
            "model.checkpoint",
            java.util.Map.of("modelId", result.modelId(), "revision", result.revision()));
        turns.audit(
            turn.id(),
            "CHECKPOINT_SAVED",
            java.util.Map.of("modelId", result.modelId(), "revision", result.revision()));
      }
      turns.setSavedElementCount(turn.id(), result.affectedElementIds().size());
      turns.setProviderCallCount(turn.id(), result.providerCalls());
      turns.setTokenUsage(turn.id(), result.promptTokens(), result.completionTokens());
      turns.recordProviderCalls(turn.id(), result.providerCallDetails());
      AssistantTurn.State state =
          turns.cancellationRequested(turn.id())
              ? AssistantTurn.State.CANCELLED
              : AssistantTurn.State.SUCCEEDED;
      // A terminal model action is a validated, atomic slice. The provider can deliberately
      // leave turnComplete=false for large/source-backed work; the persisted checkpoint then
      // becomes the durable resume point exposed by /turns/{id}/continue.
      if (state == AssistantTurn.State.SUCCEEDED
          && result.commandBatch() != null
          && !result.commandBatch().turnComplete()) {
        state = AssistantTurn.State.PARTIAL;
        remainingWork =
            result.commandBatch().planSummary().isBlank()
                ? "A validated model slice was saved. Continue the turn to model the remaining"
                    + " work."
                : result.commandBatch().planSummary();
        turns.appendEvent(
            turn.id(),
            "model.slice",
            java.util.Map.of("complete", false, "remainingWork", remainingWork));
      }
      if (turn.sourceText() != null && !turn.sourceText().isBlank()) {
        java.util.Set<String> knownUnits =
            units.stream()
                .map(AssistantTurnStore.SourceUnit::id)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> accounted = new java.util.HashSet<>();
        java.util.Set<String> expectedRequirements = requirementIds(turn.sourceText());
        java.util.Set<String> representedRequirements = new java.util.HashSet<>();
        var batch = result.commandBatch();
        if (batch != null) {
          for (var evidence : batch.evidence()) {
            String kind =
                evidence.kind() == null
                    ? "INFERRED"
                    : evidence.kind().trim().toUpperCase(java.util.Locale.ROOT);
            String sourceUnitId = evidence.sourceUnitId();
            if ("SOURCE_GROUNDED".equals(kind) && !knownUnits.contains(sourceUnitId))
              throw new io.mehdieidi.varka.platform.kernel.PlatformException(
                  422, "Evidence references an unknown source unit.");
            if (!"SOURCE_GROUNDED".equals(kind)) sourceUnitId = null;
            String requirementId = normalizeRequirementId(evidence.requirementId());
            if (requirementId != null
                && !expectedRequirements.isEmpty()
                && !expectedRequirements.contains(requirementId)) {
              throw new io.mehdieidi.varka.platform.kernel.PlatformException(
                  422, "Evidence references an unknown labelled requirement: " + requirementId);
            }
            turns.saveProvenance(
                turn.id(),
                evidence.elementRef(),
                sourceUnitId,
                requirementId,
                kind,
                evidence.assumption());
            if (sourceUnitId != null) {
              accounted.add(sourceUnitId);
              turns.markSourceUnit(
                  turn.id(),
                  sourceUnitId,
                  "MODELED",
                  "Referenced by committed element " + evidence.elementRef() + ".");
            }
            if (requirementId != null) representedRequirements.add(requirementId);
          }
        }
        var requirementScore =
            RequirementCoverageEvaluator.score(expectedRequirements, representedRequirements);
        int coverage =
            expectedRequirements.isEmpty()
                ? (units.isEmpty() ? 100 : (accounted.size() * 100 / units.size()))
                : (int) Math.round(requirementScore.recall() * 100d);
        remainingWork =
            !expectedRequirements.isEmpty()
                ? (requirementScore.recall() >= 1d
                    ? null
                    : "Labelled requirements still need model evidence: "
                        + requirementScore.falseNegatives()
                        + ".")
                : accounted.size() == units.size()
                    ? null
                    : "Source units still need explicit modeling evidence: "
                        + (units.size() - accounted.size())
                        + ".";
        turns.setSourceCoverage(turn.id(), coverage, remainingWork);
        if (state == AssistantTurn.State.SUCCEEDED && remainingWork != null)
          state = AssistantTurn.State.PARTIAL;
        if (state != AssistantTurn.State.SUCCEEDED && state != AssistantTurn.State.PARTIAL)
          turns.markSourceUnits(turn.id(), "DEFERRED", "Turn did not complete.");
      }
      complete(
          turn,
          state,
          state == AssistantTurn.State.CANCELLED
              ? "Assistant turn was cancelled."
              : result.message(),
          result.revision(),
          remainingWork);
      if (state == AssistantTurn.State.PARTIAL
          && result.commandBatch() != null
          && !turns.cancellationRequested(turn.id())) {
        enqueueNextSlice(turn, result, remainingWork);
      }
    } catch (io.mehdieidi.varka.platform.kernel.PlatformException ex) {
      if (ex instanceof AgentTurnLoop.TurnExecutionException turnFailure) {
        turns.setProviderCallCount(turn.id(), turnFailure.providerCalls());
      }
      if ((ex.status() == 502 || ex.status() == 504)
          && !turns.cancellationRequested(turn.id())
          && enqueueRecoveryTurn(turn, ex.getMessage())) {
        complete(
            turn,
            AssistantTurn.State.PARTIAL,
            ex.status() == 504
                ? "This slice reached its time limit; continuing automatically from the durable"
                    + " checkpoint."
                : "The provider did not finish this slice; retrying automatically from the durable"
                    + " checkpoint.",
            turn.revision(),
            ex.getMessage());
        return;
      }
      AssistantTurn.State state =
          switch (ex.status()) {
            case 499 -> AssistantTurn.State.CANCELLED;
            case 504 -> AssistantTurn.State.TIMED_OUT;
            case 409 ->
                ex.getMessage() != null
                        && ex.getMessage()
                            .toLowerCase(java.util.Locale.ROOT)
                            .contains("confirmation")
                    ? AssistantTurn.State.NEEDS_CONFIRMATION
                    : AssistantTurn.State.CONFLICTED;
            case 422 -> AssistantTurn.State.PARTIAL;
            default -> AssistantTurn.State.FAILED;
          };
      complete(turn, state, ex.getMessage(), null, null);
    } catch (RuntimeException ex) {
      complete(turn, AssistantTurn.State.FAILED, "Assistant processing failed.", null, null);
    } finally {
      heartbeat.cancel(false);
    }
  }

  private void complete(
      AssistantTurn turn,
      AssistantTurn.State state,
      String message,
      Long revision,
      String remainingWork) {
    turns.complete(turn.id(), state, message, revision, remainingWork);
    assistant.recordAssistantMessage(turn.threadId(), message);
    metrics.recordAssistantTurnOutcome("durable", state.name());
    metrics.recordAssistantPhaseDuration(
        "durable_turn",
        Math.max(0L, java.time.Duration.between(turn.acceptedAt(), Instant.now()).toMillis()));
  }

  /**
   * Continues deliberately partial model generation without requiring a browser round-trip. Each
   * slice is a new durable turn/checkpoint with its own deadline and provider-call budget. The
   * marker is only a runaway guard for a provider that repeatedly returns incomplete batches.
   */
  private void enqueueNextSlice(
      AssistantTurn previous,
      io.mehdieidi.varka.platform.assistant.application.AgenticTurnService.Result result,
      String remainingWork) {
    int nextSlice = automaticSliceCount(previous.message()) + 1;
    int maxSlices = Math.max(1, settings.maxAutomaticSlices());
    if (nextSlice >= maxSlices) {
      turns.appendEvent(
          previous.id(),
          "model.slice.limit_reached",
          java.util.Map.of("maxSlices", maxSlices, "remainingWork", safeRemaining(remainingWork)));
      return;
    }
    Instant acceptedAt = Instant.now();
    String message =
        previous.message()
            + "\n\n"
            + AUTOMATIC_SLICE_MARKER
            + nextSlice
            + "] Continue the same requested generation from the persisted checkpoint. "
            + "Preserve valid work, model the next coherent slice, and set turnComplete=true only "
            + "when the complete request is represented.";
    AssistantTurn next =
        new AssistantTurn(
            UUID.randomUUID().toString(),
            previous.threadId(),
            previous.userId(),
            previous.projectId(),
            previous.level(),
            result.modelId(),
            result.revision(),
            "automatic-slice-" + previous.id() + "-" + nextSlice,
            message,
            previous.sourceText(),
            previous.selectedElementIds(),
            AssistantTurn.State.QUEUED,
            acceptedAt,
            acceptedAt.plus(iterationTimeout(previous)),
            null,
            null,
            false,
            result.revision(),
            0,
            0,
            previous.coveragePercent(),
            remainingWork,
            null,
            0,
            0,
            0);
    turns.create(next);
    turns.contextCache(previous.id()).ifPresent(cache -> turns.saveContextCache(next.id(), cache));
    turns.linkContinuation(previous.id(), next.id());
    turns.appendEvent(
        previous.id(),
        "model.slice.continued",
        java.util.Map.of(
            "nextTurnId", next.id(),
            "slice", nextSlice,
            "deadlineAt", next.deadlineAt().toString()));
  }

  /**
   * Retries an exhausted/malformed provider turn as a fresh bounded turn. A new provider context
   * often recovers from intermittent compatible-provider output failures or a completed slice
   * deadline. Like a normal slice, the retry has a fresh deadline and provider-call budget.
   */
  private boolean enqueueRecoveryTurn(AssistantTurn previous, String reason) {
    int nextSlice = automaticSliceCount(previous.message()) + 1;
    int maxSlices = Math.max(1, settings.maxAutomaticSlices());
    if (nextSlice >= maxSlices) return false;
    Instant acceptedAt = Instant.now();
    AssistantTurn retry =
        new AssistantTurn(
            UUID.randomUUID().toString(),
            previous.threadId(),
            previous.userId(),
            previous.projectId(),
            previous.level(),
            previous.modelId(),
            previous.revision() == null ? previous.expectedRevision() : previous.revision(),
            "automatic-retry-" + previous.id() + "-" + nextSlice,
            previous.message()
                + "\n\n"
                + AUTOMATIC_SLICE_MARKER
                + nextSlice
                + "] Continue the same requested generation from the persisted checkpoint. The"
                + " prior slice ended before it could finish. Preserve valid work and return one"
                + " valid terminal action. Failure summary: "
                + safeRemaining(reason),
            previous.sourceText(),
            previous.selectedElementIds(),
            AssistantTurn.State.QUEUED,
            acceptedAt,
            acceptedAt.plus(iterationTimeout(previous)),
            null,
            null,
            false,
            previous.revision(),
            0,
            0,
            previous.coveragePercent(),
            reason,
            null,
            0,
            0,
            0);
    turns.create(retry);
    turns.contextCache(previous.id()).ifPresent(cache -> turns.saveContextCache(retry.id(), cache));
    turns.linkContinuation(previous.id(), retry.id());
    turns.appendEvent(
        previous.id(),
        "turn.recovery.queued",
        java.util.Map.of(
            "nextTurnId", retry.id(),
            "reason", safeRemaining(reason),
            "deadlineAt", retry.deadlineAt().toString()));
    return true;
  }

  private java.time.Duration iterationTimeout(AssistantTurn turn) {
    return turn.sourceText() == null || turn.sourceText().isBlank()
        ? settings.turnTimeout()
        : settings.sourceTurnTimeout();
  }

  private int automaticSliceCount(String message) {
    if (message == null || message.isBlank()) return 0;
    int count = 0;
    int from = 0;
    while ((from = message.indexOf(AUTOMATIC_SLICE_MARKER, from)) >= 0) {
      count++;
      from += AUTOMATIC_SLICE_MARKER.length();
    }
    return count;
  }

  private java.util.List<AssistantTurnStore.SourceUnit> selectCachedSourceUnits(
      AssistantTurn turn, java.util.List<AssistantTurnStore.SourceUnit> units) {
    java.util.Set<String> cached =
        turns
            .contextCache(turn.id())
            .map(AssistantTurnStore.ContextCache::selectedSourceUnitIds)
            .map(java.util.HashSet::new)
            .orElseGet(java.util.HashSet::new);
    if (cached.isEmpty()) return selectSourceUnits(units, turn.message());
    java.util.List<AssistantTurnStore.SourceUnit> selected =
        units.stream().filter(unit -> cached.contains(unit.id())).toList();
    return selected.isEmpty() ? selectSourceUnits(units, turn.message()) : selected;
  }

  private String safeRemaining(String remainingWork) {
    return remainingWork == null ? "" : remainingWork;
  }

  private String annotatedSourceUnits(
      java.util.List<io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore.SourceUnit>
          units) {
    StringBuilder result = new StringBuilder();
    for (var unit : units) {
      result
          .append("<source-unit id=\"")
          .append(unit.id())
          .append("\" ordinal=\"")
          .append(unit.ordinal())
          .append("\">\n")
          .append(unit.content())
          .append("\n</source-unit>\n");
    }
    return result.toString();
  }

  /**
   * Bounds source context before the first provider request. Selection is deterministic so an
   * evidence id in the model always remains traceable to the persisted source unit. The first unit
   * is retained for document-level context; the remaining slots favour request vocabulary.
   */
  private java.util.List<AssistantTurnStore.SourceUnit> selectSourceUnits(
      java.util.List<AssistantTurnStore.SourceUnit> units, String request) {
    if (units.size() <= 6) return units;
    Set<String> terms = new LinkedHashSet<>();
    for (String token :
        (request == null ? "" : request).toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
      if (token.length() >= 4) terms.add(token);
    }
    ArrayList<AssistantTurnStore.SourceUnit> ranked = new ArrayList<>(units);
    ranked.sort(
        Comparator.comparingInt((AssistantTurnStore.SourceUnit unit) -> sourceScore(unit, terms))
            .reversed()
            .thenComparingInt(AssistantTurnStore.SourceUnit::ordinal));
    LinkedHashSet<AssistantTurnStore.SourceUnit> selected = new LinkedHashSet<>();
    selected.add(units.get(0));
    for (AssistantTurnStore.SourceUnit unit : ranked) {
      if (selected.size() >= 6) break;
      selected.add(unit);
    }
    return selected.stream()
        .sorted(Comparator.comparingInt(AssistantTurnStore.SourceUnit::ordinal))
        .toList();
  }

  private int sourceScore(AssistantTurnStore.SourceUnit unit, Set<String> terms) {
    String content = unit.content().toLowerCase(Locale.ROOT);
    int score = 0;
    for (String term : terms) {
      if (content.contains(term)) score++;
    }
    return score;
  }

  private Set<String> requirementIds(String source) {
    Set<String> ids = new LinkedHashSet<>();
    if (source == null) return ids;
    Matcher matcher = REQUIREMENT_ID.matcher(source);
    while (matcher.find()) ids.add("R" + matcher.group(1));
    return ids;
  }

  private String normalizeRequirementId(String value) {
    if (value == null || value.isBlank()) return null;
    Matcher matcher = REQUIREMENT_ID.matcher(value.trim());
    return matcher.matches() ? "R" + matcher.group(1) : value.trim();
  }

  @PreDestroy
  void shutdown() {
    heartbeats.shutdownNow();
    workers.shutdownNow();
  }
}
