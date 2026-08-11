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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Claims queued assistant turns and executes them outside the request thread. */
@Component
public final class DurableAssistantTurnWorker {
  private static final Logger log = LoggerFactory.getLogger(DurableAssistantTurnWorker.class);

  /** Server-written marker used only by the confirmation endpoint, never shown to providers. */
  public static final String CONFIRMED_DESTRUCTION_PREFIX = "[durable-confirmed-destruction] ";

  private final AssistantTurnStore turns;
  private final AgenticAssistantFacade assistant;
  private final PlatformStore store;
  private final VarkaMetrics metrics;
  private final TransactionTemplate transactions;
  private final boolean workflowEngineV2;
  private final AssistantMode assistantMode;
  private final int maxSourceChunksPerTurn;
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
      TransactionTemplate transactions,
      @Value("${varka.ai.workflow-engine-v2:true}") boolean workflowEngineV2,
      @Value("${varka.ai.mode:unified}") String assistantMode,
      @Value("${varka.ai.max-source-chunks-per-turn:24}") int maxSourceChunksPerTurn) {
    this.turns = turns;
    this.assistant = assistant;
    this.store = store;
    this.metrics = metrics;
    this.transactions = transactions;
    this.workflowEngineV2 = workflowEngineV2;
    this.assistantMode = AssistantMode.parse(assistantMode);
    this.maxSourceChunksPerTurn = Math.max(1, maxSourceChunksPerTurn);
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
    org.slf4j.MDC.put("assistantTurnId", turn.id());
    org.slf4j.MDC.put("assistantSessionId", turn.threadId());
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
        units = turnScopedSourceUnits(turn.id(), sourceUnits.split(turn.sourceText()));
        turns.saveSourceUnits(turn.id(), units);
        if (turns.sourceFacts(turn.id()).isEmpty())
          turns.saveSourceFacts(turn.id(), SourceFactGraph.fromSpans(units));
        java.util.List<AssistantTurnStore.SourceUnit> sourceUnitsForTurn = units;
        var blueprint = turns.sourceBlueprint(turn.id());
        java.util.List<AssistantTurnStore.SourceUnit> selected =
            blueprint
                .map(item -> selectBlueprintSourceUnits(turn.id(), sourceUnitsForTurn, item))
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
        var analysis = sourceAnalysis(turn.id());
        if (analysis.isPresent()) {
          sourceForAgent += "\n<source-analysis>\n" + analysis.get() + "\n</source-analysis>\n";
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
        Long savedRevision =
            turns
                .latestCheckpoint(turn.id())
                .map(AssistantTurnStore.Checkpoint::revision)
                .orElse(turn.revision());
        complete(
            turn,
            AssistantTurn.State.CANCELLED,
            cancellationMessage(turn, savedRevision),
            savedRevision,
            turn.remainingWork());
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
      AgentTurnLoop.WorkflowMode route = route(turn, message);
      if (assistantMode == AssistantMode.CONCEPTUAL_TEST && !destructiveConfirmed) {
        route = AgentTurnLoop.WorkflowMode.CONCEPTUAL_INSTANCE_GENERATION;
      } else if (assistantMode == AssistantMode.UNIFIED
          && !destructiveConfirmed
          && turn.selectedElementIds().isEmpty()
          && (route == AgentTurnLoop.WorkflowMode.AUTO
              || route == AgentTurnLoop.WorkflowMode.SOURCE_TO_MODEL)) {
        route = AgentTurnLoop.WorkflowMode.ADAPTIVE;
      }
      if (!turn.selectedElementIds().isEmpty()) {
        message +=
            "\n\nSelected canvas elements are focus context only: "
                + String.join(", ", turn.selectedElementIds())
                + ". Inspect these ids first, then inspect related ownership and references before"
                + " editing.";
      }
      message = appendPersistedWorkflowContext(turn.id(), message);
      String checkpointKey = turn.id() + "-checkpoint-" + (turn.checkpointCount() + 1);
      boolean modelCheckpointExpected = !route.readOnly();
      if (modelCheckpointExpected) {
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
      }
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
                      : null,
              route);
      boolean checkpointSaved = !result.inversePatch().isEmpty();
      preflightSourceEvidence(turn, units, result.commandBatch());
      final long[] committedRevision = {result.revision()};
      final java.util.List<AssistantTurnStore.SourceUnit> sourceUnitsForCommit =
          java.util.List.copyOf(units);
      var existingModelingWorkflow = turns.workflow(turn.id());
      boolean persistedModelingPlanPresent =
          existingModelingWorkflow
              .filter(workflow -> "MODELING_PLAN".equals(workflow.workflowKind()))
              .map(AssistantTurnStore.Workflow::plan)
              .filter(this::durablePlan)
              .isPresent();
      if (result.modelingPlan() != null
          && result.modelingPlan().isObject()
          && !persistedModelingPlanPresent) {
        var planItems = modelingPlanWorkItems(turn.id(), result.modelingPlan(), !checkpointSaved);
        turns.saveWorkItems(turn.id(), planItems);
        turns.saveWorkflow(
            new AssistantTurnStore.Workflow(
                turn.id(),
                "MODELING_PLAN",
                "EXECUTING",
                planItems.isEmpty() ? null : planItems.get(0).id(),
                result.modelingPlan()));
        turns.appendEvent(
            turn.id(),
            "turn.plan.ready",
            java.util.Map.of("workflowKind", "MODELING_PLAN", "workItems", planItems.size()));
      }
      if (checkpointSaved) {
        transactions.executeWithoutResult(
            status -> {
              var committed = assistant.commitDurableDraft(user, turn.level(), result);
              committedRevision[0] = committed.revision();
              turns.recordValidationAttempt(
                  new AssistantTurnStore.ValidationAttempt(
                      turn.id(), null, 1, true, null, Instant.now()));
              turns.appendEvent(
                  turn.id(), "turn.validation.completed", java.util.Map.of("valid", true));
              turns.finalizeCheckpoint(
                  turn.id(),
                  checkpointKey,
                  committed.revision(),
                  result.inversePatch(),
                  java.util.Map.of("valid", true));
              saveCommittedProvenance(turn, sourceUnitsForCommit, result.commandBatch());
              metrics.recordAssistantCheckpoint("saved");
              int sliceCount =
                  result.modelingPlan() == null ? 0 : result.modelingPlan().path("slices").size();
              turns.appendEvent(
                  turn.id(),
                  "model.checkpoint.committed",
                  java.util.Map.of(
                      "modelId",
                      committed.id(),
                      "revision",
                      committed.revision(),
                      "checkpointOrdinal",
                      turn.checkpointCount() + 1,
                      "sliceCount",
                      Math.max(sliceCount, turn.checkpointCount() + 1)));
              turns.audit(
                  turn.id(),
                  "CHECKPOINT_SAVED",
                  java.util.Map.of("modelId", committed.id(), "revision", committed.revision()));
              if (persistedModelingPlanPresent) {
                advanceModelingPlanWorkItems(turn.id(), turn.checkpointCount() + 1);
              }
            });
      }
      if (result.sourceAnalysis() != null) {
        saveSourceAnalysis(turn.id(), result.sourceAnalysis());
        turns.saveWorkflow(
            new AssistantTurnStore.Workflow(
                turn.id(), "DOCUMENT_TO_CIM", "ANALYZED", null, result.sourceAnalysis()));
        turns.appendEvent(
            turn.id(),
            "turn.source_analysis.ready",
            java.util.Map.of(
                "sourceUnits", result.sourceAnalysis().path("sourceUnits").size(),
                "concepts", result.sourceAnalysis().path("concepts").size()));
        remainingWork = "Source analysis is ready; resume this turn to plan the CIM blueprint.";
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
      turns.setSavedElementCount(
          turn.id(), Math.max(0, turn.savedElementCount()) + result.affectedElementIds().size());
      turns.setProviderCallCount(
          turn.id(), Math.max(0, turn.providerCalls()) + result.providerCalls());
      turns.setTokenUsage(
          turn.id(),
          Math.max(0L, turn.promptTokens()) + result.promptTokens(),
          Math.max(0L, turn.completionTokens()) + result.completionTokens());
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
      if (state == AssistantTurn.State.SUCCEEDED
          && (result.sourceBlueprint() != null || result.sourceAnalysis() != null)) {
        state = AssistantTurn.State.PARTIAL;
      }
      if (turn.sourceText() != null && !turn.sourceText().isBlank()) {
        java.util.Set<String> knownUnits =
            units.stream()
                .map(AssistantTurnStore.SourceUnit::id)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<String, String> sourceAliases = sourceUnitAliases(units);
        java.util.Set<String> accounted = new java.util.HashSet<>();
        var batch = result.commandBatch();
        if (batch != null) {
          for (var evidence : batch.evidence()) {
            String kind = normalizedEvidenceKind(evidence.kind());
            String sourceUnitId =
                evidence.sourceUnitId() == null ? "" : evidence.sourceUnitId().trim();
            sourceUnitId = sourceAliases.getOrDefault(sourceUnitId, sourceUnitId);
            if ("SOURCE_GROUNDED".equals(kind) && !knownUnits.contains(sourceUnitId))
              throw new io.mehdieidi.varka.platform.kernel.PlatformException(
                  422, "Evidence references an unknown source unit.");
            if (!"SOURCE_GROUNDED".equals(kind)) sourceUnitId = null;
            String requirementId =
                evidence.requirementId() == null || evidence.requirementId().isBlank()
                    ? null
                    : evidence.requirementId().trim();
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
          // Coverage is durable across resumed checkpoints.  Counting only the evidence produced
          // by this increment made every later slice appear to lose the spans modeled by earlier
          // slices, so a two-checkpoint source workflow could remain PARTIAL forever.
          turns.provenance(turn.id()).stream()
              .map(AssistantTurnStore.Provenance::sourceUnitId)
              .filter(spanId -> spanId != null && !spanId.isBlank())
              .map(spanId -> sourceAliases.getOrDefault(spanId.trim(), spanId.trim()))
              .forEach(accounted::add);
          int coverage = units.isEmpty() ? 100 : (accounted.size() * 100 / units.size());
          remainingWork =
              accounted.size() == units.size()
                  ? null
                  : "Source spans still need explicit modeling evidence: "
                      + (units.size() - accounted.size())
                      + ".";
          updateConceptCoverageAndQuality(turn.id(), accounted, result.commandBatch());
          // Evidence tracks source coverage only. It must never override the modeling workflow's
          // explicit completion state: several source ids can be attached to one shallow element
          // while planned concepts and relationships still remain to be modeled.
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
      }
      var sourceBlueprint = turns.sourceBlueprint(turn.id());
      if (sourceBlueprint.isPresent()
          && remainingWork != null
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
              ? cancellationMessage(turn, committedRevision[0])
              : result.message(),
          committedRevision[0],
          remainingWork);
      // PARTIAL is deliberately durable state on this same turn. Resume reclaims it from the
      // checkpoint; no child turn or string-encoded continuation is created.
    } catch (io.mehdieidi.varka.platform.kernel.PlatformException ex) {
      if (ex instanceof AgentTurnLoop.TurnExecutionException turnFailure) {
        turns.setProviderCallCount(
            turn.id(), Math.max(0, turn.providerCalls()) + turnFailure.providerCalls());
        turns.setTokenUsage(
            turn.id(),
            Math.max(0L, turn.promptTokens()) + turnFailure.promptTokens(),
            Math.max(0L, turn.completionTokens()) + turnFailure.completionTokens());
        turns.recordProviderCalls(turn.id(), turnFailure.providerCallDetails());
      }
      // Bounded in-turn repair is exhausted. Preserve the validated checkpoint, expose the
      // exact remaining work, and let the user explicitly resume the same durable run.
      var latestCheckpoint = turns.latestCheckpoint(turn.id());
      if ((ex.status() == 422 || transientProviderFailure(ex.status()))
          && (turn.checkpointCount() > 0 || latestCheckpoint.isPresent())
          && !turns.cancellationRequested(turn.id())) {
        complete(
            turn,
            AssistantTurn.State.PARTIAL,
            "This work item could not be applied. Saved checkpoints remain available; resume this"
                + " turn when ready.",
            latestCheckpoint.map(AssistantTurnStore.Checkpoint::revision).orElse(turn.revision()),
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
      complete(
          turn,
          state,
          state == AssistantTurn.State.CANCELLED
              ? cancellationMessage(
                  turn,
                  latestCheckpoint
                      .map(AssistantTurnStore.Checkpoint::revision)
                      .orElse(turn.revision()))
              : ex.getMessage(),
          state == AssistantTurn.State.CANCELLED
              ? latestCheckpoint
                  .map(AssistantTurnStore.Checkpoint::revision)
                  .orElse(turn.revision())
              : null,
          null);
    } catch (RuntimeException ex) {
      log.error("Assistant durable turn failed unexpectedly turnId={}", turn.id(), ex);
      var latestCheckpoint = turns.latestCheckpoint(turn.id());
      Long savedRevision =
          latestCheckpoint.map(AssistantTurnStore.Checkpoint::revision).orElse(turn.revision());
      complete(
          turn,
          turns.cancellationRequested(turn.id())
              ? AssistantTurn.State.CANCELLED
              : AssistantTurn.State.FAILED,
          turns.cancellationRequested(turn.id())
              ? cancellationMessage(turn, savedRevision)
              : "Assistant processing failed.",
          turns.cancellationRequested(turn.id()) ? savedRevision : null,
          null);
    } finally {
      heartbeat.cancel(false);
      org.slf4j.MDC.remove("assistantTurnId");
      org.slf4j.MDC.remove("assistantSessionId");
    }
  }

  private java.util.List<AssistantTurnStore.SourceUnit> turnScopedSourceUnits(
      String turnId, java.util.List<AssistantTurnStore.SourceUnit> units) {
    if (units == null || units.isEmpty()) return java.util.List.of();
    return units;
  }

  private void preflightSourceEvidence(
      AssistantTurn turn,
      java.util.List<AssistantTurnStore.SourceUnit> units,
      io.mehdieidi.varka.platform.assistant.domain.ModelCommandBatch batch) {
    if (batch == null || turn.sourceText() == null || turn.sourceText().isBlank()) return;
    java.util.Set<String> knownUnits =
        units.stream()
            .map(AssistantTurnStore.SourceUnit::id)
            .collect(java.util.stream.Collectors.toSet());
    java.util.Map<String, String> sourceAliases = sourceUnitAliases(units);
    for (var evidence : batch.evidence()) {
      if (!"SOURCE_GROUNDED".equals(normalizedEvidenceKind(evidence.kind()))) continue;
      String sourceUnitId = evidence.sourceUnitId() == null ? "" : evidence.sourceUnitId().trim();
      sourceUnitId = sourceAliases.getOrDefault(sourceUnitId, sourceUnitId);
      if (!knownUnits.contains(sourceUnitId)) {
        throw new io.mehdieidi.varka.platform.kernel.PlatformException(
            422, "Evidence references an unknown source unit.");
      }
    }
  }

  private void saveCommittedProvenance(
      AssistantTurn turn,
      java.util.List<AssistantTurnStore.SourceUnit> units,
      io.mehdieidi.varka.platform.assistant.domain.ModelCommandBatch batch) {
    if (batch == null) return;
    java.util.Set<String> knownUnits =
        units.stream()
            .map(AssistantTurnStore.SourceUnit::id)
            .collect(java.util.stream.Collectors.toSet());
    java.util.Map<String, String> sourceAliases = sourceUnitAliases(units);
    for (var evidence : batch.evidence()) {
      String kind = normalizedEvidenceKind(evidence.kind());
      String sourceUnitId = evidence.sourceUnitId() == null ? "" : evidence.sourceUnitId().trim();
      sourceUnitId = sourceAliases.getOrDefault(sourceUnitId, sourceUnitId);
      if ("SOURCE_GROUNDED".equals(kind) && !knownUnits.contains(sourceUnitId)) {
        throw new io.mehdieidi.varka.platform.kernel.PlatformException(
            422, "Evidence references an unknown source unit.");
      }
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

  private java.util.List<AssistantTurnStore.WorkItem> modelingPlanWorkItems(
      String turnId, tools.jackson.databind.JsonNode plan, boolean noCheckpointSaved) {
    java.util.List<AssistantTurnStore.WorkItem> items = new java.util.ArrayList<>();
    int ordinal = 1;
    for (tools.jackson.databind.JsonNode slice : plan.path("slices")) {
      String id = turnId + ":slice:" + ordinal;
      String status =
          ordinal == 1 && !noCheckpointSaved
              ? "committed"
              : ordinal == 1 ? "running" : slice.path("status").asText("pending");
      items.add(
          new AssistantTurnStore.WorkItem(
              id,
              ordinal,
              slice.path("label").asText("Checkpoint " + ordinal),
              status,
              "modeling-plan-slice-" + ordinal,
              slice));
      ordinal++;
    }
    return items;
  }

  private String normalizedEvidenceKind(String kind) {
    if (kind == null || kind.isBlank()) return "INFERRED";
    String normalized =
        kind.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    if (normalized.equals("SOURCE_GROUNDING")
        || normalized.startsWith("SOURCE_GROUNDED")
        || normalized.contains("USER_STORY")
        || normalized.contains("ACCEPTANCE")) {
      return "SOURCE_GROUNDED";
    }
    return normalized;
  }

  private void advanceModelingPlanWorkItems(String turnId, int completedOrdinal) {
    var workflow = turns.workflow(turnId);
    if (workflow.isEmpty()
        || !"MODELING_PLAN".equals(workflow.get().workflowKind())
        || workflow.get().plan() == null) {
      return;
    }
    var existing = turns.workItems(turnId);
    if (existing.isEmpty()) return;
    java.util.List<AssistantTurnStore.WorkItem> updated = new java.util.ArrayList<>();
    String nextWorkItemId = null;
    for (AssistantTurnStore.WorkItem item : existing) {
      String status;
      if (item.ordinal() <= completedOrdinal) {
        status = "committed";
      } else if (item.ordinal() == completedOrdinal + 1) {
        status = "running";
        nextWorkItemId = item.id();
      } else {
        status = "pending";
      }
      updated.add(
          new AssistantTurnStore.WorkItem(
              item.id(),
              item.ordinal(),
              item.label(),
              status,
              item.idempotencyKey(),
              item.payload()));
    }
    turns.saveWorkItems(turnId, updated);
    var saved = workflow.get();
    var advancedPlan = saved.plan().deepCopy();
    if (advancedPlan.isObject()) {
      ((tools.jackson.databind.node.ObjectNode) advancedPlan)
          .put("currentSliceIndex", Math.max(0, completedOrdinal));
    }
    turns.saveWorkflow(
        new AssistantTurnStore.Workflow(
            saved.turnId(), saved.workflowKind(), "EXECUTING", nextWorkItemId, advancedPlan));
  }

  private AgentTurnLoop.WorkflowMode route(AssistantTurn turn, String message) {
    boolean sourceBacked = turn.sourceText() != null && !turn.sourceText().isBlank();
    var existingWorkflow = turns.workflow(turn.id());
    boolean durablePlanPresent =
        existingWorkflow
            .map(AssistantTurnStore.Workflow::plan)
            .filter(this::durablePlan)
            .isPresent();
    boolean hasDurableWorkflow =
        turn.checkpointCount() > 0
            || durablePlanPresent
            || turns.sourceBlueprint(turn.id()).isPresent()
            || sourceAnalysis(turn.id()).isPresent();
    AgentTurnLoop.WorkflowMode mode;
    if (hasDurableWorkflow) {
      mode = AgentTurnLoop.WorkflowMode.RESUME_REPAIR;
    } else if (sourceBacked) {
      mode = AgentTurnLoop.WorkflowMode.SOURCE_TO_MODEL;
    } else {
      // The constrained agent selects intent. Keyword routing made normal explanations and
      // model mutations brittle, and could bypass the ordinary planning/execution workflow.
      mode = AgentTurnLoop.WorkflowMode.AUTO;
    }
    if (existingWorkflow.isEmpty() || !durablePlanPresent) {
      turns.saveWorkflow(
          new AssistantTurnStore.Workflow(
              turn.id(), mode.name(), mode.readOnly() ? "READING" : "ROUTED", null, null));
    }
    turns.appendEvent(
        turn.id(),
        "turn.workflow.routed",
        java.util.Map.of("workflowKind", mode.name(), "readOnly", mode.readOnly()));
    return mode;
  }

  /** One production mode plus explicit, non-production acceptance-test overrides. */
  enum AssistantMode {
    UNIFIED,
    AGENT_TEST,
    CONCEPTUAL_TEST;

    static AssistantMode parse(String configured) {
      String value =
          configured == null ? "unified" : configured.trim().toLowerCase(java.util.Locale.ROOT);
      return switch (value) {
        case "unified" -> UNIFIED;
        case "agent-test" -> AGENT_TEST;
        case "conceptual-test" -> CONCEPTUAL_TEST;
        default ->
            throw new IllegalArgumentException(
                "Invalid varka.ai.mode '"
                    + value
                    + "'. Expected unified, agent-test, or conceptual-test.");
      };
    }
  }

  private String appendPersistedWorkflowContext(String turnId, String message) {
    var workflow = turns.workflow(turnId);
    if (workflow.isEmpty() || !durablePlan(workflow.get().plan())) return message;
    var workItems = turns.workItems(turnId);
    StringBuilder result = new StringBuilder(message == null ? "" : message);
    result
        .append("\n\nPersisted workflow state (authoritative; continue, do not replan):\n")
        .append("workflowKind=")
        .append(workflow.get().workflowKind())
        .append(", phase=")
        .append(workflow.get().phase() == null ? "" : workflow.get().phase())
        .append(", currentWorkItemId=")
        .append(
            workflow.get().currentWorkItemId() == null ? "" : workflow.get().currentWorkItemId())
        .append("\nPlan:\n")
        .append(workflow.get().plan());
    if (!workItems.isEmpty()) {
      result.append("\nWork items:");
      workItems.forEach(
          item ->
              result
                  .append("\n- ")
                  .append(item.id())
                  .append(" #")
                  .append(item.ordinal())
                  .append(" ")
                  .append(item.status())
                  .append(" ")
                  .append(item.label()));
    }
    return result.toString();
  }

  private boolean durablePlan(tools.jackson.databind.JsonNode plan) {
    if (plan == null || plan.isNull() || plan.isMissingNode() || !plan.isObject()) return false;
    if (plan.path("slices").isArray() && !plan.path("slices").isEmpty()) return true;
    return plan.path("sourceUnitIds").isArray() && !plan.path("sourceUnitIds").isEmpty();
  }

  private boolean transientProviderFailure(int status) {
    return status == 500 || status == 502 || status == 503 || status == 504;
  }

  private java.util.Optional<tools.jackson.databind.JsonNode> sourceAnalysis(String turnId) {
    return turns.sourceFacts(turnId).stream()
        .filter(fact -> "SOURCE_ANALYSIS".equals(fact.kind()))
        .map(AssistantTurnStore.SourceFact::payload)
        .findFirst();
  }

  private void saveSourceAnalysis(String turnId, tools.jackson.databind.JsonNode analysis) {
    turns.saveSourceFacts(
        turnId,
        java.util.List.of(
            new AssistantTurnStore.SourceFact(
                turnId + ":source-analysis", "SOURCE_ANALYSIS", "ACCEPTED", analysis, null)));
    java.util.List<AssistantTurnStore.SourceFact> conceptFacts = new java.util.ArrayList<>();
    int ordinal = 1;
    for (tools.jackson.databind.JsonNode concept : analysis.path("concepts")) {
      String key = concept.path("key").asText("").trim();
      if (key.isBlank()) key = "concept-" + ordinal;
      conceptFacts.add(
          new AssistantTurnStore.SourceFact(
              turnId + ":concept:" + key, "SOURCE_CONCEPT", "PLANNED", concept, null));
      ordinal++;
    }
    if (!conceptFacts.isEmpty()) turns.saveSourceFacts(turnId, conceptFacts);
  }

  private void updateConceptCoverageAndQuality(
      String turnId,
      java.util.Set<String> modeledSourceUnitIds,
      io.mehdieidi.varka.platform.assistant.domain.ModelCommandBatch batch) {
    java.util.List<AssistantTurnStore.SourceFact> updated = new java.util.ArrayList<>();
    int total = 0;
    int modeled = 0;
    for (AssistantTurnStore.SourceFact fact : turns.sourceFacts(turnId)) {
      if (!"SOURCE_CONCEPT".equals(fact.kind())) continue;
      total++;
      java.util.Set<String> conceptSourceIds = new java.util.LinkedHashSet<>();
      fact.payload()
          .path("sourceUnitIds")
          .forEach(id -> conceptSourceIds.add(id.asText("").trim()));
      boolean covered =
          !conceptSourceIds.isEmpty()
              && conceptSourceIds.stream().anyMatch(modeledSourceUnitIds::contains);
      if (covered) modeled++;
      updated.add(
          new AssistantTurnStore.SourceFact(
              fact.id(),
              fact.kind(),
              covered ? "MODELED" : fact.status(),
              fact.payload(),
              fact.assumption()));
    }
    if (!updated.isEmpty()) turns.saveSourceFacts(turnId, updated);
    int relationshipCount = batch == null ? 0 : batch.connections().size();
    int richCreates =
        batch == null
            ? 0
            : (int)
                batch.creates().stream()
                    .map(create -> create.eClass() == null ? "" : create.eClass())
                    .filter(
                        eClass ->
                            java.util.Set.of(
                                    "Command",
                                    "Query",
                                    "BusinessEvent",
                                    "DomainEntity",
                                    "InformationItem",
                                    "Policy",
                                    "DecisionModel",
                                    "DecisionRule")
                                .contains(eClass))
                    .count();
    int score =
        total == 0
            ? 0
            : Math.min(
                100,
                (modeled * 70 / total)
                    + Math.min(20, relationshipCount * 5)
                    + Math.min(10, richCreates * 2));
    turns.appendEvent(
        turnId,
        "turn.quality.updated",
        java.util.Map.of(
            "qualityScore",
            score,
            "conceptsModeled",
            modeled,
            "conceptsTotal",
            total,
            "relationshipsInLastBatch",
            relationshipCount,
            "richCimCreatesInLastBatch",
            richCreates));
  }

  private java.util.Map<String, String> sourceUnitAliases(
      java.util.List<AssistantTurnStore.SourceUnit> units) {
    java.util.Map<String, String> aliases = new java.util.LinkedHashMap<>();
    for (var unit : units) {
      String id = unit.id();
      aliases.put(id, id);
      int scope = id.indexOf(':');
      if (scope >= 0 && scope + 1 < id.length()) {
        aliases.putIfAbsent(id.substring(scope + 1), id);
      }
      String ordinal = Integer.toString(unit.ordinal());
      aliases.putIfAbsent(ordinal, id);
      aliases.putIfAbsent("source-" + ordinal, id);
      aliases.putIfAbsent("src-" + ordinal, id);
    }
    return aliases;
  }

  private String cancellationMessage(AssistantTurn turn, Long revision) {
    var latest = turns.latestCheckpoint(turn.id());
    if (latest.isPresent()) {
      long savedRevision = revision == null ? latest.get().revision() : revision;
      return "Stopped after checkpoint "
          + latest.get().ordinal()
          + ". Revision "
          + savedRevision
          + " is saved. Remaining work can be continued.";
    }
    return "Assistant turn was cancelled before a model checkpoint was saved.";
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
    if (!remaining.isEmpty()) {
      int sourceWindow = Math.min(maxSourceChunksPerTurn, remaining.size());
      return remaining.stream().limit(sourceWindow).toList();
    }
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
      String turnId,
      java.util.List<AssistantTurnStore.SourceUnit> units,
      AssistantTurnStore.SourceBlueprint blueprint) {
    var slices = blueprint.blueprint().path("slices");
    if (!slices.isArray() || blueprint.nextSlice() >= slices.size()) return java.util.List.of();
    java.util.Set<String> alreadyModeled =
        turns.provenance(turnId).stream()
            .map(AssistantTurnStore.Provenance::sourceUnitId)
            .filter(id -> id != null && !id.isBlank())
            .collect(java.util.stream.Collectors.toSet());
    for (int index = blueprint.nextSlice(); index < slices.size(); index++) {
      java.util.Set<String> ids = new java.util.LinkedHashSet<>();
      slices.get(index).path("sourceUnitIds").forEach(item -> ids.add(item.asText()));
      java.util.List<AssistantTurnStore.SourceUnit> selected =
          units.stream()
              .filter(unit -> ids.contains(unit.id()))
              .filter(unit -> !alreadyModeled.contains(unit.id()))
              .toList();
      if (!selected.isEmpty()) return selected;
    }
    return java.util.List.of();
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
