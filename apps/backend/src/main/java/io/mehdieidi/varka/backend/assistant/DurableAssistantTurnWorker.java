package io.mehdieidi.varka.backend.assistant;

import io.mehdieidi.varka.backend.observability.VarkaMetrics;
import io.mehdieidi.varka.platform.assistant.agent.AgentTurnLoop;
import io.mehdieidi.varka.platform.assistant.application.AgenticAssistantFacade;
import io.mehdieidi.varka.platform.assistant.source.SourceUnitSplitter;
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
      VarkaMetrics metrics) {
    this.turns = turns;
    this.assistant = assistant;
    this.store = store;
    this.metrics = metrics;
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
        sourceForAgent = annotatedSourceUnits(units);
        turns.appendEvent(turn.id(), "turn.stage", java.util.Map.of("stage", "SOURCE_UNITS_READY"));
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
          assistant.message(
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
      turns.recordProviderCalls(
          turn.id(), result.provider(), result.model(), result.providerCalls());
      AssistantTurn.State state =
          turns.cancellationRequested(turn.id())
              ? AssistantTurn.State.CANCELLED
              : AssistantTurn.State.SUCCEEDED;
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
            turns.saveProvenance(
                turn.id(), evidence.elementRef(), sourceUnitId, kind, evidence.assumption());
            if (sourceUnitId != null) {
              accounted.add(sourceUnitId);
              turns.markSourceUnit(
                  turn.id(),
                  sourceUnitId,
                  "MODELED",
                  "Referenced by committed element " + evidence.elementRef() + ".");
            }
          }
        }
        int coverage = units.isEmpty() ? 100 : (accounted.size() * 100 / units.size());
        remainingWork =
            accounted.size() == units.size()
                ? null
                : "Source units still need explicit modeling evidence: "
                    + (units.size() - accounted.size())
                    + ".";
        turns.setSourceCoverage(turn.id(), coverage, remainingWork);
        if (state == AssistantTurn.State.SUCCEEDED && accounted.size() != units.size())
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
    } catch (io.mehdieidi.varka.platform.kernel.PlatformException ex) {
      if (ex instanceof AgentTurnLoop.TurnExecutionException turnFailure) {
        turns.setProviderCallCount(turn.id(), turnFailure.providerCalls());
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
    metrics.recordAssistantTurnOutcome("durable", state.name());
    metrics.recordAssistantPhaseDuration(
        "durable_turn",
        Math.max(0L, java.time.Duration.between(turn.acceptedAt(), Instant.now()).toMillis()));
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

  @PreDestroy
  void shutdown() {
    heartbeats.shutdownNow();
    workers.shutdownNow();
  }
}
