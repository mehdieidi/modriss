package io.mehdieidi.modless.platform.assistant.persistence.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.application.AssistantOrchestrator.AssistantTurnResponse;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnDiagnostics;
import io.mehdieidi.modless.platform.assistant.domain.AssistantWorkflowState;
import io.mehdieidi.modless.platform.assistant.spi.AssistantTurnExecutionStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC implementation for assistant turn execution and diagnostics persistence. */
public class JdbcAssistantTurnExecutionStore implements AssistantTurnExecutionStore {

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public JdbcAssistantTurnExecutionStore(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  public Optional<AssistantTurnResponse> terminalResponse(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return Optional.empty();
    }
    return jdbc.query(
        """
        SELECT terminal_response_json
        FROM assistant_turn_executions
        WHERE idempotency_key = ? AND terminal_response_json IS NOT NULL
        LIMIT 1
        """,
        rs -> {
          if (!rs.next()) {
            return Optional.empty();
          }
          try {
            return Optional.of(
                mapper.readValue(
                    rs.getString("terminal_response_json"), AssistantTurnResponse.class));
          } catch (Exception ex) {
            return Optional.empty();
          }
        },
        idempotencyKey.trim());
  }

  @Override
  public Optional<String> activeTurnId(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return Optional.empty();
    }
    return jdbc.query(
        """
        SELECT turn_id
        FROM assistant_turn_executions
        WHERE idempotency_key = ? AND status = 'RUNNING'
        LIMIT 1
        """,
        rs -> rs.next() ? Optional.of(rs.getString("turn_id")) : Optional.empty(),
        idempotencyKey.trim());
  }

  @Override
  public void start(TurnExecution execution) {
    if (execution == null || execution.turnId().isBlank() || execution.sessionId().isBlank()) {
      return;
    }
    Instant now = Instant.now();
    try {
      jdbc.update(
          """
          INSERT INTO assistant_turn_executions(turn_id, idempotency_key, session_id, model_id,
            expected_revision, status, phase, deadline_at, created_at, updated_at)
          VALUES (?, ?, ?, ?, ?, 'RUNNING', ?, ?, ?, ?)
          ON CONFLICT (turn_id) DO UPDATE SET phase = EXCLUDED.phase,
            deadline_at = EXCLUDED.deadline_at, updated_at = EXCLUDED.updated_at
          """,
          execution.turnId(),
          blank(execution.idempotencyKey()) ? null : execution.idempotencyKey(),
          execution.sessionId(),
          blank(execution.modelId()) ? null : execution.modelId(),
          execution.expectedRevision(),
          execution.phase(),
          sqlTimestamp(execution.deadlineAt()),
          sqlTimestamp(now),
          sqlTimestamp(now));
    } catch (DuplicateKeyException duplicate) {
      updatePhase(execution.turnId(), execution.phase());
    }
  }

  @Override
  public void updatePhase(String turnId, String phase) {
    if (blank(turnId) || blank(phase)) {
      return;
    }
    jdbc.update(
        """
        UPDATE assistant_turn_executions
        SET phase = ?, updated_at = ?
        WHERE turn_id = ? AND status = 'RUNNING'
        """,
        phase.trim(),
        sqlTimestamp(Instant.now()),
        turnId.trim());
  }

  @Override
  public void requestCancel(String turnId) {
    if (blank(turnId)) {
      return;
    }
    Instant now = Instant.now();
    jdbc.update(
        """
        UPDATE assistant_turn_executions
        SET cancel_requested_at = ?, phase = 'CANCELING', updated_at = ?
        WHERE turn_id = ? AND status = 'RUNNING'
        """,
        sqlTimestamp(now),
        sqlTimestamp(now),
        turnId.trim());
  }

  @Override
  public void complete(String turnId, AssistantTurnResponse response) {
    if (blank(turnId) || response == null) {
      return;
    }
    try {
      jdbc.update(
          """
          UPDATE assistant_turn_executions
          SET status = ?, phase = ?, terminal_response_json = ?::jsonb, updated_at = ?
          WHERE turn_id = ?
          """,
          status(response),
          response.workflowState().name(),
          mapper.writeValueAsString(response),
          sqlTimestamp(Instant.now()),
          turnId.trim());
    } catch (Exception ex) {
      fail(turnId, 500, "Failed to serialize assistant terminal response.");
    }
  }

  @Override
  public void fail(String turnId, int status, String message) {
    if (blank(turnId)) {
      return;
    }
    try {
      jdbc.update(
          """
          UPDATE assistant_turn_executions
          SET status = ?, phase = 'FAILED', error_json = ?::jsonb, updated_at = ?
          WHERE turn_id = ?
          """,
          status == 499 ? "CANCELED" : status == 409 ? "STALE_REVISION" : "FAILED",
          mapper.writeValueAsString(
              Map.of("status", status, "message", message == null ? "" : message)),
          sqlTimestamp(Instant.now()),
          turnId.trim());
    } catch (Exception ignored) {
      // Diagnostics persistence must never mask the original assistant failure.
    }
  }

  @Override
  public void recordDiagnostics(
      String turnId,
      String sessionId,
      AssistantTurnDiagnostics diagnostics,
      Map<String, Long> phaseTimings,
      Object retrievalDiagnostics,
      Object validationFeedback) {
    if (blank(turnId) || blank(sessionId) || diagnostics == null) {
      return;
    }
    try {
      jdbc.update(
          """
          INSERT INTO assistant_turn_diagnostics(turn_id, session_id, provider_calls, tool_calls,
            repair_attempts, phase_timings_json, retrieval_diagnostics_json,
            validation_feedback_json, created_at)
          VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?)
          ON CONFLICT (turn_id) DO UPDATE SET provider_calls = EXCLUDED.provider_calls,
            tool_calls = EXCLUDED.tool_calls, repair_attempts = EXCLUDED.repair_attempts,
            phase_timings_json = EXCLUDED.phase_timings_json,
            retrieval_diagnostics_json = EXCLUDED.retrieval_diagnostics_json,
            validation_feedback_json = EXCLUDED.validation_feedback_json
          """,
          turnId.trim(),
          sessionId.trim(),
          Math.max(0, diagnostics.providerCalls()),
          Math.max(0, diagnostics.toolCalls()),
          Math.max(0, diagnostics.repairAttempts()),
          mapper.writeValueAsString(phaseTimings == null ? Map.of() : phaseTimings),
          mapper.writeValueAsString(retrievalDiagnostics == null ? Map.of() : retrievalDiagnostics),
          mapper.writeValueAsString(
              validationFeedback == null ? java.util.List.of() : validationFeedback),
          sqlTimestamp(Instant.now()));
    } catch (DataAccessException | com.fasterxml.jackson.core.JsonProcessingException ignored) {
      // Diagnostics persistence must never alter turn behavior.
    }
  }

  private String status(AssistantTurnResponse response) {
    AssistantWorkflowState state = response.workflowState();
    return switch (state) {
      case APPLIED -> "APPLIED";
      case WAITING_FOR_CHOICE -> "WAITING_FOR_CHOICE";
      case FAILED -> "FAILED";
      case UNDONE -> "ANSWERED";
      case EXPLAINED -> "ANSWERED";
    };
  }

  private static Timestamp sqlTimestamp(Instant instant) {
    return Timestamp.from(instant == null ? Instant.now() : instant);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
