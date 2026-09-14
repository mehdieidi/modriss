package io.mehdieidi.modriss.platform.assistant.turn;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/** Persistence port for durable assistant turns and replayable events. */
public interface AssistantTurnStore {
  AssistantTurn create(AssistantTurn turn);

  /** Links a continuation to its durable parent after both rows have been created. */
  void linkContinuation(String parentTurnId, String childTurnId);

  /** Returns direct durable continuations in acceptance order. */
  List<AssistantTurn> continuations(String parentTurnId);

  void saveContextCache(String turnId, ContextCache cache);

  Optional<ContextCache> contextCache(String turnId);

  /** Durable, validated source-to-CIM plan used to resume incremental model application. */
  void saveSourceBlueprint(String turnId, JsonNode blueprint, int nextSlice);

  Optional<SourceBlueprint> sourceBlueprint(String turnId);

  Optional<AssistantTurn> find(String turnId);

  Optional<AssistantTurn> findByIdempotency(String threadId, String key);

  /** Returns the current queued or running turn for a thread, if durable work is still active. */
  Optional<AssistantTurn> activeForThread(String threadId);

  Optional<AssistantTurn> claim(String workerId, Instant now, Duration lease);

  boolean heartbeat(String turnId, String workerId, Instant now, Duration lease);

  boolean cancellationRequested(String turnId);

  void requestCancellation(String turnId);

  /** Marks accepted work that missed its absolute deadline terminal without claiming it. */
  void expireTimedOut(Instant now);

  void complete(
      String turnId,
      AssistantTurn.State state,
      String message,
      Long revision,
      String remainingWork);

  /** Requeues a successful progressive phase without exposing a terminal user-resume state. */
  void continueProgressively(String turnId, Long revision, String remainingWork);

  /** Requeues the same durable request from its last committed checkpoint. */
  default void resume(String turnId, Long expectedRevision) {
    resume(turnId, expectedRevision, null);
  }

  /** Requeues the same durable request and optionally refreshes its absolute deadline. */
  void resume(String turnId, Long expectedRevision, Instant deadlineAt);

  /** Records a user-approved rebase before resuming a conflicted request. */
  void rebase(String turnId, long expectedRevision);

  default void saveWorkflow(Workflow workflow) {}

  default Optional<Workflow> workflow(String turnId) {
    return Optional.empty();
  }

  default void saveWorkItems(String turnId, List<WorkItem> items) {}

  default List<WorkItem> workItems(String turnId) {
    return List.of();
  }

  default void saveSourceFacts(String turnId, List<SourceFact> facts) {}

  default List<SourceFact> sourceFacts(String turnId) {
    return List.of();
  }

  default void recordValidationAttempt(ValidationAttempt attempt) {}

  /**
   * Returns durable structural-validation attempts in creation order.
   *
   * <p>The live-evaluation gate uses these records to report repair passes without inferring them
   * from provider-call totals.
   */
  default List<ValidationAttempt> validationAttempts(String turnId) {
    return List.of();
  }

  AssistantTurn.Event appendEvent(String turnId, String type, Map<String, Object> payload);

  List<AssistantTurn.Event> events(String turnId, long afterEventId);

  void saveSourceUnits(String turnId, List<SourceUnit> units);

  void markSourceUnits(String turnId, String status, String reason);

  void markSourceUnit(String turnId, String sourceUnitId, String status, String reason);

  void setSourceCoverage(String turnId, int coveragePercent, String remainingWork);

  void setSavedElementCount(String turnId, int savedElementCount);

  void setProviderCallCount(String turnId, int providerCalls);

  /** Persists aggregate provider-reported token usage for the durable turn. */
  void setTokenUsage(String turnId, long promptTokens, long completionTokens);

  void recordProviderCalls(String turnId, List<ProviderCall> calls);

  /** Persists one call immediately; keyed calls are safe to record again at turn completion. */
  default void recordProviderCall(String turnId, ProviderCall call) {
    recordProviderCalls(turnId, List.of(call));
  }

  void saveCheckpoint(String turnId, String modelId, long revision, Object inversePatch);

  /** Creates or returns a durable checkpoint intent before mutating the model. */
  default Checkpoint beginCheckpoint(
      String turnId,
      String modelId,
      long baseRevision,
      String idempotencyKey,
      String label,
      String candidateHash) {
    throw new UnsupportedOperationException("Checkpoint intents are unavailable");
  }

  /** Atomically makes a validated checkpoint visible. Safe to repeat after a crash. */
  default Checkpoint finalizeCheckpoint(
      String turnId,
      String idempotencyKey,
      long revision,
      Object inversePatch,
      Object validationSummary) {
    throw new UnsupportedOperationException("Checkpoint finalization is unavailable");
  }

  Optional<Checkpoint> latestCheckpoint(String turnId);

  List<Checkpoint> checkpoints(String turnId);

  void saveProvenance(
      String turnId,
      String elementId,
      String sourceUnitId,
      String requirementId,
      String kind,
      String assumption);

  /**
   * Evidence labels displayed with a durable turn; source content itself is never returned here.
   */
  List<Provenance> provenance(String turnId);

  void audit(String turnId, String action, Map<String, Object> details);

  /** Locally extracted, stable source span. */
  record SourceUnit(String id, int ordinal, int startOffset, int endOffset, String content) {}

  record Checkpoint(
      long id,
      String modelId,
      long revision,
      JsonNode inversePatch,
      String idempotencyKey,
      int ordinal,
      String label,
      Long baseRevision,
      String candidateHash,
      String status,
      JsonNode validationSummary) {}

  record Workflow(
      String turnId, String workflowKind, String phase, String currentWorkItemId, JsonNode plan) {}

  record WorkItem(
      String id,
      int ordinal,
      String label,
      String status,
      String idempotencyKey,
      JsonNode payload) {}

  record SourceFact(String id, String kind, String status, JsonNode payload, String assumption) {}

  record ValidationAttempt(
      String turnId,
      String workItemId,
      int attempt,
      boolean valid,
      JsonNode diagnostics,
      Instant createdAt) {}

  record Provenance(
      String elementId,
      String sourceUnitId,
      String requirementId,
      String kind,
      String assumption) {}

  /** Provider-call telemetry, including the redacted prompts actually sent to the provider. */
  record ProviderCall(
      String provider,
      String model,
      long latencyMillis,
      long promptTokens,
      long completionTokens,
      boolean usageReported,
      String systemPrompt,
      String userPrompt,
      String finishReason,
      String error,
      String callKey) {
    public ProviderCall(
        String provider,
        String model,
        long latencyMillis,
        long promptTokens,
        long completionTokens,
        boolean usageReported,
        String systemPrompt,
        String userPrompt,
        String finishReason,
        String error) {
      this(
          provider,
          model,
          latencyMillis,
          promptTokens,
          completionTokens,
          usageReported,
          systemPrompt,
          userPrompt,
          finishReason,
          error,
          null);
    }

    public ProviderCall(
        String provider,
        String model,
        long latencyMillis,
        long promptTokens,
        long completionTokens,
        boolean usageReported,
        String systemPrompt,
        String userPrompt) {
      this(
          provider,
          model,
          latencyMillis,
          promptTokens,
          completionTokens,
          usageReported,
          systemPrompt,
          userPrompt,
          "COMPLETED",
          null,
          null);
    }

    public ProviderCall(
        String provider,
        String model,
        long latencyMillis,
        long promptTokens,
        long completionTokens,
        boolean usageReported) {
      this(
          provider,
          model,
          latencyMillis,
          promptTokens,
          completionTokens,
          usageReported,
          null,
          null,
          "COMPLETED",
          null,
          null);
    }
  }

  record ContextCache(List<String> selectedSourceUnitIds, List<String> contractClosures) {}

  record SourceBlueprint(JsonNode blueprint, int nextSlice) {}
}
