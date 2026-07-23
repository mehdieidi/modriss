package io.mehdieidi.varka.backend.assistant;

import io.mehdieidi.varka.backend.observability.VarkaMetrics;
import io.mehdieidi.varka.platform.assistant.agent.AgentTurnLoop;
import io.mehdieidi.varka.platform.assistant.application.AgenticAssistantFacade;
import io.mehdieidi.varka.platform.assistant.source.SourceFactGraph;
import io.mehdieidi.varka.platform.assistant.source.SourceUnitSplitter;
import io.mehdieidi.varka.platform.assistant.source.SourceWorkPlan;
import io.mehdieidi.varka.platform.assistant.turn.AssistantTurn;
import io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.storage.api.PlatformStore;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Claims queued assistant turns and executes them outside the request thread. */
@Component
public final class DurableAssistantTurnWorker {
  /** Server-written marker used only by the confirmation endpoint, never shown to providers. */
  public static final String CONFIRMED_DESTRUCTION_PREFIX = "[durable-confirmed-destruction] ";

  private final AssistantTurnStore turns;
  private final AgenticAssistantFacade assistant;
  private final PlatformStore store;
  private final VarkaMetrics metrics;
  private final boolean workflowEngineV2;
  private final String workerId = "assistant-" + UUID.randomUUID();
  // Documents up to 24k characters remain whole. Larger documents are segmented only at semantic
  // boundaries; each incremental slice also receives a map of the complete source below.
  private final SourceUnitSplitter sourceUnits = new SourceUnitSplitter(6000);
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
      @Value("${varka.ai.workflow-engine-v2:true}") boolean workflowEngineV2) {
    this.turns = turns;
    this.assistant = assistant;
    this.store = store;
    this.metrics = metrics;
    this.workflowEngineV2 = workflowEngineV2;
  }

  @Scheduled(fixedDelayString = "${varka.ai.worker-poll-interval:PT0.5S}")
  public void runOne() {
    // The rollout switch deliberately prevents claiming new work while V2 is disabled. The
    // automatic-slice executor has been removed, so falling back would be unsafe and misleading.
    if (!workflowEngineV2) return;
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
    turns
        .workflow(turn.id())
        .ifPresent(
            workflow -> {
              turns.saveWorkflow(
                  new AssistantTurnStore.Workflow(
                      workflow.turnId(),
                      workflow.workflowKind(),
                      "EXECUTING",
                      workflow.currentWorkItemId(),
                      workflow.plan()));
              turns.appendEvent(
                  turn.id(), "turn.work_item.started", java.util.Map.of("phase", "EXECUTING"));
            });
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
      int selectedSourceUnitCount = 0;
      java.util.List<io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore.SourceUnit>
          units = java.util.List.of();
      if (turn.sourceText() != null && !turn.sourceText().isBlank()) {
        units = sourceUnits.split(turn.sourceText());
        turns.saveSourceUnits(turn.id(), units);
        if (turns.sourceFacts(turn.id()).isEmpty())
          turns.saveSourceFacts(turn.id(), SourceFactGraph.fromSpans(units));
        java.util.List<AssistantTurnStore.SourceUnit> sourceUnitsForTurn = units;
        var blueprint = turns.sourceBlueprint(turn.id());
        java.util.List<AssistantTurnStore.SourceUnit> selected =
            blueprint
                .map(item -> selectBlueprintSourceUnits(sourceUnitsForTurn, item))
                .filter(items -> !items.isEmpty())
                .orElseGet(() -> selectCachedSourceUnits(turn, sourceUnitsForTurn));
        selectedSourceUnitCount = selected.size();
        turns.saveContextCache(
            turn.id(),
            new AssistantTurnStore.ContextCache(
                selected.stream().map(AssistantTurnStore.SourceUnit::id).toList(),
                java.util.List.of()));
        sourceForAgent = annotatedSourceUnits(selected, units);
        if (blueprint.isPresent()) {
          sourceForAgent +=
              "\n<source-blueprint next-slice=\""
                  + blueprint.get().nextSlice()
                  + "\">\n"
                  + blueprint.get().blueprint()
                  + "\n</source-blueprint>\n";
        }
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
      if (!turn.selectedElementIds().isEmpty()) {
        message +=
            "\n\nSelected canvas elements are focus context only: "
                + String.join(", ", turn.selectedElementIds())
                + ". Inspect these ids first, then inspect related ownership and references before"
                + " editing.";
      }
      String checkpointKey = turn.id() + "-checkpoint-" + (turn.checkpointCount() + 1);
      // Persist the intent before invoking code which can mutate the model. A restarted worker
      // finalizes the same key, never creates a second canvas change.
      turns.beginCheckpoint(
          turn.id(),
          turn.modelId(),
          turn.revision() != null
              ? turn.revision()
              : turn.expectedRevision() == null ? 0L : turn.expectedRevision(),
          checkpointKey,
          "Checkpoint " + (turn.checkpointCount() + 1),
          null);
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
      turns.recordValidationAttempt(
          new AssistantTurnStore.ValidationAttempt(turn.id(), null, 1, true, null, Instant.now()));
      turns.appendEvent(turn.id(), "turn.validation.completed", java.util.Map.of("valid", true));
      if (!result.inversePatch().isEmpty()) {
        turns.finalizeCheckpoint(
            turn.id(),
            checkpointKey,
            result.revision(),
            result.inversePatch(),
            java.util.Map.of("valid", true));
        metrics.recordAssistantCheckpoint("saved");
        turns.appendEvent(
            turn.id(),
            "model.checkpoint.committed",
            java.util.Map.of("modelId", result.modelId(), "revision", result.revision()));
        turns.audit(
            turn.id(),
            "CHECKPOINT_SAVED",
            java.util.Map.of("modelId", result.modelId(), "revision", result.revision()));
      }
      if (result.sourceBlueprint() != null) {
        turns.saveSourceBlueprint(turn.id(), result.sourceBlueprint(), 0);
        var plan = SourceWorkPlan.fromBlueprint(turn.id(), result.sourceBlueprint());
        turns.saveWorkItems(turn.id(), plan);
        turns.saveWorkflow(
            new AssistantTurnStore.Workflow(
                turn.id(),
                "DOCUMENT_TO_CIM",
                "PLANNED",
                plan.isEmpty() ? null : plan.get(0).id(),
                result.sourceBlueprint()));
        turns.appendEvent(
            turn.id(),
            "turn.plan.ready",
            java.util.Map.of("workItems", plan.size(), "sourceSpans", units.size()));
        // Planning is a durable increment, not completion of a source-backed request.  Mark it
        // resumable so the client/gate advances this same turn to the first planned slice.
        remainingWork =
            plan.isEmpty()
                ? "The source plan is ready; resume this turn to model the source."
                : "The source plan is ready; resume this turn with " + plan.get(0).label() + ".";
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
      if (state == AssistantTurn.State.SUCCEEDED && result.sourceBlueprint() != null) {
        state = AssistantTurn.State.PARTIAL;
      }
      if (turn.sourceText() != null && !turn.sourceText().isBlank()) {
        java.util.Set<String> knownUnits =
            units.stream()
                .map(AssistantTurnStore.SourceUnit::id)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> accounted = new java.util.HashSet<>();
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
            String requirementId =
                evidence.requirementId() == null || evidence.requirementId().isBlank()
                    ? null
                    : evidence.requirementId().trim();
            turns.saveProvenance(
                turn.id(),
                evidence.elementRef(),
                sourceUnitId,
                requirementId,
                kind,
                evidence.assumption());
            if (sourceUnitId != null) {
              String groundedSpanId = sourceUnitId;
              accounted.add(sourceUnitId);
              turns.markSourceUnit(
                  turn.id(),
                  sourceUnitId,
                  "MODELED",
                  "Referenced by committed element " + evidence.elementRef() + ".");
              turns.sourceFacts(turn.id()).stream()
                  .filter(
                      fact -> groundedSpanId.equals(fact.payload().path("sourceSpanId").asText()))
                  .findFirst()
                  .ifPresent(
                      fact ->
                          turns.saveSourceFacts(
                              turn.id(),
                              java.util.List.of(
                                  new AssistantTurnStore.SourceFact(
                                      fact.id(),
                                      fact.kind(),
                                      "MODELED",
                                      fact.payload(),
                                      fact.assumption()))));
            }
          }
        }
        // Coverage is durable across resumed checkpoints.  Counting only the evidence produced
        // by this increment made every later slice appear to lose the spans modeled by earlier
        // slices, so a two-checkpoint source workflow could remain PARTIAL forever.
        turns.provenance(turn.id()).stream()
            .map(AssistantTurnStore.Provenance::sourceUnitId)
            .filter(spanId -> spanId != null && !spanId.isBlank())
            .forEach(accounted::add);
        int coverage = units.isEmpty() ? 100 : (accounted.size() * 100 / units.size());
        remainingWork =
            accounted.size() == units.size()
                ? null
                : "Source spans still need explicit modeling evidence: "
                    + (units.size() - accounted.size())
                    + ".";
        // For an unplanned source-backed increment, structural validity plus evidence for every
        // source unit is the durable completion contract.  Do not keep resuming merely because
        // the provider conservatively returned turnComplete=false after it already covered the
        // entire supplied document.
        if (accounted.size() == units.size() && turns.sourceBlueprint(turn.id()).isEmpty()) {
          state = AssistantTurn.State.SUCCEEDED;
        }
        int completedSourceWindow = Math.min(units.size(), selectedSourceUnitCount);
        if (completedSourceWindow < units.size()) {
          String nextWindow =
              "Source units still queued for the next model slice: "
                  + (units.size() - completedSourceWindow)
                  + ".";
          remainingWork = remainingWork == null ? nextWindow : remainingWork + " " + nextWindow;
          if (state == AssistantTurn.State.SUCCEEDED) state = AssistantTurn.State.PARTIAL;
        }
        turns.setSourceCoverage(turn.id(), coverage, remainingWork);
        turns.appendEvent(
            turn.id(),
            "turn.coverage.updated",
            java.util.Map.of(
                "coveragePercent", coverage, "unresolvedSpans", units.size() - accounted.size()));
        if (state == AssistantTurn.State.SUCCEEDED && remainingWork != null)
          state = AssistantTurn.State.PARTIAL;
        if (state != AssistantTurn.State.SUCCEEDED && state != AssistantTurn.State.PARTIAL)
          turns.markSourceUnits(turn.id(), "DEFERRED", "Turn did not complete.");
      }
      var sourceBlueprint = turns.sourceBlueprint(turn.id());
      if (sourceBlueprint.isPresent()
          && result.sourceBlueprint() == null
          && sourceBlueprint.get().nextSlice() + 1
              < sourceBlueprint.get().blueprint().path("slices").size()) {
        int completedSlice = sourceBlueprint.get().nextSlice();
        int nextSlice = completedSlice + 1;
        turns.saveSourceBlueprint(turn.id(), sourceBlueprint.get().blueprint(), nextSlice);
        var workItems = turns.workItems(turn.id());
        if (!workItems.isEmpty()) {
          turns.saveWorkItems(
              turn.id(),
              workItems.stream()
                  .map(
                      item ->
                          new AssistantTurnStore.WorkItem(
                              item.id(),
                              item.ordinal(),
                              item.label(),
                              item.ordinal() == completedSlice + 1 ? "COMPLETED" : item.status(),
                              item.idempotencyKey(),
                              item.payload()))
                  .toList());
          String nextWorkItem = workItems.size() > nextSlice ? workItems.get(nextSlice).id() : null;
          turns
              .workflow(turn.id())
              .ifPresent(
                  workflow ->
                      turns.saveWorkflow(
                          new AssistantTurnStore.Workflow(
                              workflow.turnId(),
                              workflow.workflowKind(),
                              "EXECUTING",
                              nextWorkItem,
                              workflow.plan())));
        }
        var next = sourceBlueprint.get().blueprint().path("slices").get(nextSlice);
        String nextFocus = next.path("focus").asText("the next planned model slice");
        remainingWork =
            remainingWork == null
                ? "Next planned model slice: " + nextFocus + "."
                : remainingWork + " Next planned model slice: " + nextFocus + ".";
        if (state == AssistantTurn.State.SUCCEEDED) state = AssistantTurn.State.PARTIAL;
      }
      complete(
          turn,
          state,
          state == AssistantTurn.State.CANCELLED
              ? "Assistant turn was cancelled."
              : result.message(),
          result.revision(),
          remainingWork);
      // PARTIAL is deliberately durable state on this same turn. Resume reclaims it from the
      // checkpoint; no child turn or string-encoded continuation is created.
    } catch (io.mehdieidi.varka.platform.kernel.PlatformException ex) {
      if (ex instanceof AgentTurnLoop.TurnExecutionException turnFailure) {
        turns.setProviderCallCount(turn.id(), turnFailure.providerCalls());
        turns.setTokenUsage(turn.id(), turnFailure.promptTokens(), turnFailure.completionTokens());
        turns.recordProviderCalls(turn.id(), turnFailure.providerCallDetails());
      }
      // Bounded in-turn repair is exhausted. Preserve the validated checkpoint, expose the
      // exact remaining work, and let the user explicitly resume the same durable run.
      if ((ex.status() == 422 || ex.status() == 502 || ex.status() == 504)
          && turn.checkpointCount() > 0
          && !turns.cancellationRequested(turn.id())) {
        complete(
            turn,
            AssistantTurn.State.PARTIAL,
            "This work item could not be applied. Saved checkpoints remain available; resume this"
                + " turn when ready.",
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
      complete(
          turn,
          turns.cancellationRequested(turn.id())
              ? AssistantTurn.State.CANCELLED
              : AssistantTurn.State.FAILED,
          turns.cancellationRequested(turn.id())
              ? "Assistant turn was cancelled."
              : "Assistant processing failed.",
          null,
          null);
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
    turns
        .workflow(turn.id())
        .ifPresent(
            workflow ->
                turns.saveWorkflow(
                    new AssistantTurnStore.Workflow(
                        workflow.turnId(),
                        workflow.workflowKind(),
                        state.terminal() ? state.name() : "EXECUTING",
                        workflow.currentWorkItemId(),
                        workflow.plan())));
    assistant.recordAssistantMessage(turn.threadId(), message);
    metrics.recordAssistantTurnOutcome("durable", state.name());
    metrics.recordAssistantPhaseDuration(
        "durable_turn",
        Math.max(0L, java.time.Duration.between(turn.acceptedAt(), Instant.now()).toMillis()));
  }

  private java.util.List<AssistantTurnStore.SourceUnit> selectCachedSourceUnits(
      AssistantTurn turn, java.util.List<AssistantTurnStore.SourceUnit> units) {
    java.util.Set<String> alreadyModeled =
        turns.provenance(turn.id()).stream()
            .map(AssistantTurnStore.Provenance::sourceUnitId)
            .filter(id -> id != null && !id.isBlank())
            .collect(java.util.stream.Collectors.toSet());
    java.util.List<AssistantTurnStore.SourceUnit> remaining =
        units.stream().filter(unit -> !alreadyModeled.contains(unit.id())).toList();
    if (!remaining.isEmpty()) return remaining;
    java.util.Set<String> cached =
        turns
            .contextCache(turn.id())
            .map(AssistantTurnStore.ContextCache::selectedSourceUnitIds)
            .map(java.util.HashSet::new)
            .orElseGet(java.util.HashSet::new);
    // The analyst always receives the complete supplied document.  Subsequent executor calls use
    // only its explicit dependency-aware work item selection.
    if (cached.isEmpty()) return units;
    java.util.List<AssistantTurnStore.SourceUnit> selected =
        units.stream().filter(unit -> cached.contains(unit.id())).toList();
    return selected.isEmpty() ? units : selected;
  }

  /** Uses the LLM's validated source-unit plan instead of a positional window when available. */
  private java.util.List<AssistantTurnStore.SourceUnit> selectBlueprintSourceUnits(
      java.util.List<AssistantTurnStore.SourceUnit> units,
      AssistantTurnStore.SourceBlueprint blueprint) {
    var slices = blueprint.blueprint().path("slices");
    if (!slices.isArray() || blueprint.nextSlice() >= slices.size()) return java.util.List.of();
    java.util.Set<String> ids = new java.util.LinkedHashSet<>();
    slices.get(blueprint.nextSlice()).path("sourceUnitIds").forEach(item -> ids.add(item.asText()));
    return units.stream().filter(unit -> ids.contains(unit.id())).toList();
  }

  private String annotatedSourceUnits(
      java.util.List<io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore.SourceUnit>
          units,
      java.util.List<io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore.SourceUnit>
          documentUnits) {
    StringBuilder result = new StringBuilder();
    if (documentUnits.size() > units.size()) {
      result.append("<source-document-map>\n");
      for (var unit : documentUnits) {
        result
            .append("<source-section id=\"")
            .append(unit.id())
            .append("\">")
            .append(sourceSummary(unit.content()))
            .append("</source-section>\n");
      }
      result.append("</source-document-map>\n");
    }
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

  /** A bounded, deterministic global map keeps cross-section concepts available in every slice. */
  private String sourceSummary(String content) {
    String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
    return normalized.length() <= 240 ? normalized : normalized.substring(0, 237) + "...";
  }

  @PreDestroy
  @SuppressWarnings("unused")
  void shutdown() {
    heartbeats.shutdownNow();
    workers.shutdownNow();
  }
}
