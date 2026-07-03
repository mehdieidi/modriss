package io.mehdieidi.modless.platform.assistant.spi;

import io.mehdieidi.modless.platform.assistant.application.AssistantOrchestrator.AssistantTurnResponse;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnDiagnostics;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Durable execution envelope for assistant turn idempotency, cancellation, and diagnostics. */
public interface AssistantTurnExecutionStore {

  /** Returns a terminal response for a duplicate idempotency key, when one exists. */
  default Optional<AssistantTurnResponse> terminalResponse(String idempotencyKey) {
    return Optional.empty();
  }

  /**
   * Returns an active turn ID for a duplicate idempotency key, when the original is still running.
   */
  default Optional<String> activeTurnId(String idempotencyKey) {
    return Optional.empty();
  }

  /** Records the start of a turn. */
  default void start(TurnExecution execution) {}

  /** Records the latest visible phase for a running turn. */
  default void updatePhase(String turnId, String phase) {}

  /** Marks the turn as cancel-requested. */
  default void requestCancel(String turnId) {}

  /** Stores a terminal response for replay and marks the turn complete. */
  default void complete(String turnId, AssistantTurnResponse response) {}

  /** Stores terminal failure details when a turn exits exceptionally before a response exists. */
  default void fail(String turnId, int status, String message) {}

  /** Stores phase/tool/repair diagnostics for the turn. */
  default void recordDiagnostics(
      String turnId,
      String sessionId,
      AssistantTurnDiagnostics diagnostics,
      Map<String, Long> phaseTimings,
      Object retrievalDiagnostics,
      Object validationFeedback) {}

  /** No-op implementation for tests and non-JDBC deployments. */
  static AssistantTurnExecutionStore noop() {
    return new AssistantTurnExecutionStore() {};
  }

  /** One active turn execution row. */
  record TurnExecution(
      String turnId,
      String idempotencyKey,
      String sessionId,
      String modelId,
      Long expectedRevision,
      String phase,
      Instant deadlineAt) {
    public TurnExecution {
      turnId = turnId == null ? "" : turnId.trim();
      idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
      sessionId = sessionId == null ? "" : sessionId.trim();
      modelId = modelId == null ? "" : modelId.trim();
      phase = phase == null || phase.isBlank() ? "STARTED" : phase.trim();
      deadlineAt = deadlineAt == null ? Instant.now() : deadlineAt;
    }
  }
}
