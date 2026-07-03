package io.mehdieidi.modless.platform.assistant.application;

import io.mehdieidi.modless.platform.assistant.application.AssistantOrchestrator.AssistantTurnResponse;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks active assistant turns, duplicate submit keys, deadlines, and cancellation requests. */
public class CancellationRegistry {

  private final Map<String, ActiveTurn> activeBySession = new ConcurrentHashMap<>();
  private final Map<String, AssistantTurnResponse> terminalByIdempotencyKey =
      new ConcurrentHashMap<>();

  /** Returns a previously completed response for a duplicate idempotency key. */
  public Optional<AssistantTurnResponse> terminalResponse(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(terminalByIdempotencyKey.get(idempotencyKey.trim()));
  }

  /** Registers an active turn for cancellation/deadline checks. */
  public void start(String sessionId, String turnId, String idempotencyKey, Instant deadlineAt) {
    if (sessionId == null || sessionId.isBlank() || turnId == null || turnId.isBlank()) {
      return;
    }
    activeBySession.put(
        sessionId,
        new ActiveTurn(
            turnId, idempotencyKey == null ? "" : idempotencyKey.trim(), deadlineAt, false));
  }

  /** Requests cancellation for the currently active turn in a session. */
  public Optional<String> cancel(String sessionId) {
    ActiveTurn current = activeBySession.get(sessionId);
    if (current == null) {
      return Optional.empty();
    }
    activeBySession.put(
        sessionId,
        new ActiveTurn(current.turnId(), current.idempotencyKey(), current.deadlineAt(), true));
    return Optional.of(current.turnId());
  }

  /** Throws when the turn is canceled, timed out, or no longer active for the session. */
  public void check(String sessionId, String turnId) {
    ActiveTurn current = activeBySession.get(sessionId);
    if (current == null || !current.turnId().equals(turnId)) {
      throw new PlatformException(499, "Assistant turn is no longer active.");
    }
    if (current.cancelRequested()) {
      throw new PlatformException(499, "Assistant turn was canceled. Your model is unchanged.");
    }
    if (current.deadlineAt() != null && Instant.now().isAfter(current.deadlineAt())) {
      throw new PlatformException(504, "Assistant turn exceeded its configured deadline.");
    }
  }

  /** Stores the terminal response and clears the active turn. */
  public void complete(String sessionId, String turnId, AssistantTurnResponse response) {
    ActiveTurn current = activeBySession.get(sessionId);
    if (current != null && current.turnId().equals(turnId)) {
      activeBySession.remove(sessionId);
      if (!current.idempotencyKey().isBlank() && response != null) {
        terminalByIdempotencyKey.putIfAbsent(current.idempotencyKey(), response);
      }
    }
  }

  /** Clears an active turn without caching a response. */
  public void failOpen(String sessionId, String turnId) {
    ActiveTurn current = activeBySession.get(sessionId);
    if (current != null && current.turnId().equals(turnId)) {
      activeBySession.remove(sessionId);
    }
  }

  private record ActiveTurn(
      String turnId, String idempotencyKey, Instant deadlineAt, boolean cancelRequested) {}
}
