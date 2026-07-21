package io.mehdieidi.varka.platform.assistant.turn;

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

  void saveCheckpoint(String turnId, String modelId, long revision, Object inversePatch);

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

  record Checkpoint(String modelId, long revision, JsonNode inversePatch) {}

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
      String userPrompt) {
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
          null);
    }
  }

  record ContextCache(List<String> selectedSourceUnitIds, List<String> contractClosures) {}

  record SourceBlueprint(JsonNode blueprint, int nextSlice) {}
}
