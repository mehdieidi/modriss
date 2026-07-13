package io.mehdieidi.varka.platform.assistant.turn;

import io.mehdieidi.varka.platform.kernel.ModelLevel;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Durable, restartable unit of assistant work. */
public record AssistantTurn(
    String id,
    String threadId,
    String userId,
    String projectId,
    ModelLevel level,
    String modelId,
    Long expectedRevision,
    String idempotencyKey,
    String message,
    String sourceText,
    List<String> selectedElementIds,
    State state,
    Instant acceptedAt,
    Instant deadlineAt,
    Instant leaseUntil,
    String workerId,
    boolean cancellationRequested,
    Long revision,
    int checkpointCount,
    int savedElementCount,
    Integer coveragePercent,
    String remainingWork,
    String finalMessage,
    int providerCalls,
    long promptTokens,
    long completionTokens) {

  public AssistantTurn {
    selectedElementIds = selectedElementIds == null ? List.of() : List.copyOf(selectedElementIds);
    state = state == null ? State.QUEUED : state;
  }

  public boolean terminal() {
    return state.terminal();
  }

  /** Publicly visible lifecycle states. */
  public enum State {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    PARTIAL,
    NEEDS_INPUT,
    NEEDS_CONFIRMATION,
    CONFLICTED,
    CANCELLED,
    TIMED_OUT,
    FAILED;

    public boolean terminal() {
      return this != QUEUED && this != RUNNING;
    }
  }

  /** Replayable durable event. */
  public record Event(
      long eventId,
      String turnId,
      long sequence,
      String type,
      Instant occurredAt,
      Map<String, Object> payload) {}
}
