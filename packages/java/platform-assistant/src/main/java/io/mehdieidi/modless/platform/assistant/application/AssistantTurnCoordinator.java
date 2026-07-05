package io.mehdieidi.modless.platform.assistant.application;

import io.mehdieidi.modless.platform.assistant.application.AssistantOrchestrator.AssistantTurnResponse;
import io.mehdieidi.modless.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantTurnExecutionStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Coordinates assistant turn lifecycle setup, idempotency lookup, deadlines, and cancellation. */
public class AssistantTurnCoordinator {

  private final CancellationRegistry cancellations;
  private final AssistantTurnExecutionStore executions;

  public AssistantTurnCoordinator(
      CancellationRegistry cancellations, AssistantTurnExecutionStore executions) {
    this.cancellations = cancellations == null ? new CancellationRegistry() : cancellations;
    this.executions = executions == null ? AssistantTurnExecutionStore.noop() : executions;
  }

  public Optional<AssistantTurnResponse> terminalResponse(String idempotencyKey) {
    return executions
        .terminalResponse(idempotencyKey)
        .or(() -> cancellations.terminalResponse(idempotencyKey));
  }

  public Optional<String> activeTurnId(String idempotencyKey) {
    return executions.activeTurnId(idempotencyKey);
  }

  public StartedTurn start(
      AssistantSessionStore.AssistantSession session,
      String modelId,
      Long expectedRevision,
      String idempotencyKey,
      Duration turnTimeout) {
    String turnId = UUID.randomUUID().toString();
    Instant deadlineAt =
        Instant.now().plus(turnTimeout == null ? Duration.ofMinutes(5) : turnTimeout);
    cancellations.start(session.id(), turnId, idempotencyKey, deadlineAt);
    executions.start(
        new AssistantTurnExecutionStore.TurnExecution(
            turnId,
            idempotencyKey,
            session.id(),
            modelId,
            expectedRevision,
            "STARTED",
            deadlineAt));
    return new StartedTurn(turnId, deadlineAt);
  }

  public void extendDeadline(
      AssistantSessionStore.AssistantSession session, String turnId, Instant newDeadlineAt) {
    if (session == null || turnId == null || turnId.isBlank() || newDeadlineAt == null) {
      return;
    }
    cancellations.extendDeadline(session.id(), turnId, newDeadlineAt);
    executions.extendDeadline(turnId, newDeadlineAt);
  }

  public Optional<String> cancel(String sessionId) {
    Optional<String> turnId = cancellations.cancel(sessionId);
    turnId.ifPresent(executions::requestCancel);
    return turnId;
  }

  /** One newly started assistant turn. */
  public record StartedTurn(String turnId, Instant deadlineAt) {}
}
