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

  Optional<AssistantTurn> find(String turnId);

  Optional<AssistantTurn> findByIdempotency(String threadId, String key);

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

  void recordProviderCalls(String turnId, String provider, String model, int providerCalls);

  void saveCheckpoint(String turnId, String modelId, long revision, Object inversePatch);

  Optional<Checkpoint> latestCheckpoint(String turnId);

  List<Checkpoint> checkpoints(String turnId);

  void saveProvenance(
      String turnId, String elementId, String sourceUnitId, String kind, String assumption);

  /**
   * Evidence labels displayed with a durable turn; source content itself is never returned here.
   */
  List<Provenance> provenance(String turnId);

  void audit(String turnId, String action, Map<String, Object> details);

  /** Locally extracted, stable source span. */
  record SourceUnit(String id, int ordinal, int startOffset, int endOffset, String content) {}

  record Checkpoint(String modelId, long revision, JsonNode inversePatch) {}

  record Provenance(String elementId, String sourceUnitId, String kind, String assumption) {}
}
