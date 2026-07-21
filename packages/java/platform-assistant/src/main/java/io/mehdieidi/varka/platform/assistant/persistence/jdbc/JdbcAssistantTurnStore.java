package io.mehdieidi.varka.platform.assistant.persistence.jdbc;

import io.mehdieidi.varka.platform.assistant.turn.AssistantTurn;
import io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** PostgreSQL implementation using leases and {@code SKIP LOCKED} claims. */
public final class JdbcAssistantTurnStore implements AssistantTurnStore {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public JdbcAssistantTurnStore(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  public AssistantTurn create(AssistantTurn turn) {
    try {
      jdbc.update(
          """
          INSERT INTO assistant_turns(id, thread_id, user_id, project_id, level, model_id,
            expected_revision, idempotency_key, message, source_text, selected_element_ids, state,
            accepted_at, deadline_at)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
          """,
          turn.id(),
          turn.threadId(),
          turn.userId(),
          turn.projectId(),
          turn.level().name(),
          turn.modelId(),
          turn.expectedRevision(),
          turn.idempotencyKey(),
          turn.message(),
          turn.sourceText(),
          json(turn.selectedElementIds()),
          turn.state().name(),
          timestamp(turn.acceptedAt()),
          timestamp(turn.deadlineAt()));
    } catch (DuplicateKeyException activeTurn) {
      throw new PlatformException(409, "An assistant turn is already active for this model.");
    }
    appendEvent(turn.id(), "turn.accepted", Map.of("state", turn.state().name()));
    return turn;
  }

  @Override
  public void linkContinuation(String parentTurnId, String childTurnId) {
    jdbc.update(
        "UPDATE assistant_turns SET parent_turn_id = ? WHERE id = ?", parentTurnId, childTurnId);
  }

  @Override
  public List<AssistantTurn> continuations(String parentTurnId) {
    return jdbc.query(
        """
        WITH RECURSIVE chain AS (
          SELECT id, 1 AS depth FROM assistant_turns WHERE parent_turn_id = ?
          UNION ALL
          SELECT child.id, chain.depth + 1
          FROM assistant_turns child JOIN chain ON child.parent_turn_id = chain.id
        )
        SELECT turns.* FROM assistant_turns turns JOIN chain ON chain.id = turns.id
        ORDER BY chain.depth, turns.accepted_at
        """,
        this::turn,
        parentTurnId);
  }

  @Override
  public void saveContextCache(String turnId, AssistantTurnStore.ContextCache cache) {
    jdbc.update(
        "INSERT INTO assistant_turn_context_cache(turn_id, selected_source_ids, contract_closures,"
            + " created_at) VALUES (?, ?::jsonb, ?::jsonb, ?) ON CONFLICT (turn_id) DO UPDATE SET"
            + " selected_source_ids = EXCLUDED.selected_source_ids, contract_closures ="
            + " EXCLUDED.contract_closures",
        turnId,
        json(cache == null ? List.of() : cache.selectedSourceUnitIds()),
        json(cache == null ? List.of() : cache.contractClosures()),
        timestamp(Instant.now()));
  }

  @Override
  public Optional<AssistantTurnStore.ContextCache> contextCache(String turnId) {
    return jdbc
        .query(
            "SELECT selected_source_ids, contract_closures FROM assistant_turn_context_cache WHERE"
                + " turn_id = ?",
            (rs, row) ->
                new AssistantTurnStore.ContextCache(
                    list(rs.getString("selected_source_ids")),
                    list(rs.getString("contract_closures"))),
            turnId)
        .stream()
        .findFirst();
  }

  @Override
  public void saveSourceBlueprint(
      String turnId, tools.jackson.databind.JsonNode blueprint, int nextSlice) {
    Instant now = Instant.now();
    jdbc.update(
        "INSERT INTO assistant_source_blueprints(turn_id, blueprint, next_slice, created_at,"
            + " updated_at) VALUES (?, ?::jsonb, ?, ?, ?) ON CONFLICT (turn_id) DO UPDATE SET"
            + " blueprint = EXCLUDED.blueprint, next_slice = EXCLUDED.next_slice, updated_at ="
            + " EXCLUDED.updated_at",
        turnId,
        blueprint == null ? "{}" : blueprint.toString(),
        Math.max(0, nextSlice),
        timestamp(now),
        timestamp(now));
  }

  @Override
  public Optional<AssistantTurnStore.SourceBlueprint> sourceBlueprint(String turnId) {
    return jdbc
        .query(
            "SELECT blueprint, next_slice FROM assistant_source_blueprints WHERE turn_id = ?",
            (rs, row) ->
                new AssistantTurnStore.SourceBlueprint(
                    tree(rs.getString("blueprint")), rs.getInt("next_slice")),
            turnId)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<AssistantTurn> find(String turnId) {
    return one("SELECT * FROM assistant_turns WHERE id = ?", turnId);
  }

  @Override
  public Optional<AssistantTurn> findByIdempotency(String threadId, String key) {
    return one(
        "SELECT * FROM assistant_turns WHERE thread_id = ? AND idempotency_key = ?", threadId, key);
  }

  @Override
  public Optional<AssistantTurn> activeForThread(String threadId) {
    return one(
        "SELECT * FROM assistant_turns WHERE thread_id = ? AND state IN ('QUEUED', 'RUNNING') "
            + "ORDER BY accepted_at DESC LIMIT 1",
        threadId);
  }

  @Override
  public Optional<AssistantTurn> claim(String workerId, Instant now, Duration lease) {
    List<AssistantTurn> claimed =
        jdbc.query(
            """
WITH candidate AS (
  SELECT id FROM assistant_turns
  WHERE (state = 'QUEUED' OR (state = 'RUNNING' AND lease_until < ?))
    AND deadline_at > ? AND cancellation_requested = false
  ORDER BY accepted_at FOR UPDATE SKIP LOCKED LIMIT 1
)
UPDATE assistant_turns t SET state = 'RUNNING', worker_id = ?, lease_until = ?, started_at = COALESCE(started_at, ?)
FROM candidate WHERE t.id = candidate.id RETURNING t.*
""",
            this::turn,
            timestamp(now),
            timestamp(now),
            workerId,
            timestamp(now.plus(lease)),
            timestamp(now));
    if (claimed.isEmpty()) return Optional.empty();
    appendEvent(claimed.get(0).id(), "turn.stage", Map.of("stage", "RUNNING"));
    return Optional.of(claimed.get(0));
  }

  @Override
  public boolean heartbeat(String turnId, String workerId, Instant now, Duration lease) {
    return jdbc.update(
            "UPDATE assistant_turns SET lease_until = ? WHERE id = ? AND worker_id = ? AND state ="
                + " 'RUNNING'",
            timestamp(now.plus(lease)),
            turnId,
            workerId)
        == 1;
  }

  @Override
  public boolean cancellationRequested(String turnId) {
    Boolean value =
        jdbc.queryForObject(
            "SELECT cancellation_requested FROM assistant_turns WHERE id = ?",
            Boolean.class,
            turnId);
    return Boolean.TRUE.equals(value);
  }

  @Override
  public void requestCancellation(String turnId) {
    List<Cancellation> cancelled =
        jdbc.query(
            """
            WITH RECURSIVE turn_tree AS (
              SELECT id FROM assistant_turns WHERE id = ?
              UNION ALL
              SELECT child.id FROM assistant_turns child
              JOIN turn_tree parent ON child.parent_turn_id = parent.id
            )
            UPDATE assistant_turns
            SET cancellation_requested = true,
                state = CASE WHEN state = 'QUEUED' THEN 'CANCELLED' ELSE state END,
                lease_until = CASE WHEN state = 'QUEUED' THEN NULL ELSE lease_until END,
                completed_at = CASE WHEN state = 'QUEUED' THEN ? ELSE completed_at END,
                final_message = CASE WHEN state = 'QUEUED'
                  THEN 'Assistant turn was cancelled.' ELSE final_message END
            WHERE id IN (SELECT id FROM turn_tree) AND state IN ('QUEUED', 'RUNNING')
            RETURNING id, state
            """,
            (rs, row) -> new Cancellation(rs.getString("id"), rs.getString("state")),
            turnId,
            timestamp(Instant.now()));
    for (Cancellation item : cancelled) {
      if ("CANCELLED".equals(item.state())) {
        appendEvent(
            item.id(),
            "turn.completed",
            Map.of("state", "CANCELLED", "message", "Assistant turn was cancelled."));
      } else {
        appendEvent(item.id(), "turn.stage", Map.of("stage", "CANCELLING"));
      }
    }
  }

  @Override
  public void expireTimedOut(Instant now) {
    List<String> expired =
        jdbc.query(
            "UPDATE assistant_turns SET state = 'TIMED_OUT', lease_until = NULL, completed_at = ?,"
                + " final_message = COALESCE(final_message, 'Assistant turn exceeded its configured"
                + " deadline.') WHERE state = 'QUEUED' AND deadline_at <= ? RETURNING"
                + " id",
            (rs, row) -> rs.getString(1),
            timestamp(now),
            timestamp(now));
    for (String id : expired)
      appendEvent(
          id,
          "turn.completed",
          Map.of(
              "state", "TIMED_OUT", "message", "Assistant turn exceeded its configured deadline."));
  }

  @Override
  public void complete(
      String id, AssistantTurn.State state, String message, Long revision, String remaining) {
    jdbc.update(
        "UPDATE assistant_turns SET state = ?, final_message = ?, revision = ?, remaining_work = ?,"
            + " lease_until = NULL, completed_at = ? WHERE id = ?",
        state.name(),
        message,
        revision,
        remaining,
        timestamp(Instant.now()),
        id);
    appendEvent(
        id,
        "turn.completed",
        Map.of("state", state.name(), "message", message == null ? "" : message));
  }

  @Override
  public AssistantTurn.Event appendEvent(String turnId, String type, Map<String, Object> payload) {
    DuplicateKeyException duplicate = null;
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        return appendEventOnce(turnId, type, payload);
      } catch (DuplicateKeyException ex) {
        duplicate = ex;
      }
    }
    throw duplicate;
  }

  private AssistantTurn.Event appendEventOnce(
      String turnId, String type, Map<String, Object> payload) {
    return jdbc
        .query(
            """
WITH locked_turn AS (SELECT id FROM assistant_turns WHERE id = ? FOR UPDATE)
INSERT INTO assistant_turn_events(turn_id, sequence, type, occurred_at, payload)
SELECT ?, COALESCE((SELECT MAX(sequence) FROM assistant_turn_events WHERE turn_id = ?), 0) + 1,
  ?, ?, ?::jsonb
FROM locked_turn
RETURNING *
""",
            this::event,
            turnId,
            turnId,
            turnId,
            type,
            timestamp(Instant.now()),
            json(payload))
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException("Assistant turn disappeared while appending an event."));
  }

  @Override
  public List<AssistantTurn.Event> events(String turnId, long after) {
    return jdbc.query(
        "SELECT * FROM assistant_turn_events WHERE turn_id = ? AND id > ? ORDER BY id",
        this::event,
        turnId,
        after);
  }

  @Override
  public void saveSourceUnits(String turnId, List<AssistantTurnStore.SourceUnit> units) {
    for (AssistantTurnStore.SourceUnit unit : units) {
      jdbc.update(
          "INSERT INTO assistant_source_units(id, turn_id, ordinal, start_offset, end_offset,"
              + " content, status) VALUES (?, ?, ?, ?, ?, ?, 'DEFERRED') ON CONFLICT (id) DO"
              + " NOTHING",
          unit.id(),
          turnId,
          unit.ordinal(),
          unit.startOffset(),
          unit.endOffset(),
          unit.content());
    }
  }

  @Override
  public void markSourceUnits(String turnId, String status, String reason) {
    jdbc.update(
        "UPDATE assistant_source_units SET status = ?, reason = ? WHERE turn_id = ?",
        status,
        reason,
        turnId);
  }

  @Override
  public void markSourceUnit(String turnId, String sourceUnitId, String status, String reason) {
    jdbc.update(
        "UPDATE assistant_source_units SET status = ?, reason = ? WHERE turn_id = ? AND id = ?",
        status,
        reason,
        turnId,
        sourceUnitId);
  }

  @Override
  public void setSourceCoverage(String turnId, int coveragePercent, String remainingWork) {
    jdbc.update(
        "UPDATE assistant_turns SET coverage_percent = ?, remaining_work = ? WHERE id = ?",
        Math.max(0, Math.min(100, coveragePercent)),
        remainingWork,
        turnId);
  }

  @Override
  public void setSavedElementCount(String turnId, int savedElementCount) {
    jdbc.update(
        "UPDATE assistant_turns SET saved_element_count = ? WHERE id = ?",
        Math.max(0, savedElementCount),
        turnId);
  }

  @Override
  public void setProviderCallCount(String turnId, int providerCalls) {
    jdbc.update(
        "UPDATE assistant_turns SET provider_calls = ? WHERE id = ?",
        Math.max(0, providerCalls),
        turnId);
  }

  @Override
  public void setTokenUsage(String turnId, long promptTokens, long completionTokens) {
    jdbc.update(
        "UPDATE assistant_turns SET prompt_tokens = ?, completion_tokens = ? WHERE id = ?",
        Math.max(0L, promptTokens),
        Math.max(0L, completionTokens),
        turnId);
  }

  @Override
  public void recordProviderCalls(String turnId, List<AssistantTurnStore.ProviderCall> calls) {
    for (AssistantTurnStore.ProviderCall call :
        calls == null ? List.<AssistantTurnStore.ProviderCall>of() : calls) {
      jdbc.update(
          "INSERT INTO assistant_provider_calls(turn_id, provider, model, started_at, latency_ms,"
              + " prompt_tokens, completion_tokens, finish_reason, system_prompt, user_prompt)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
          turnId,
          call.provider(),
          call.model(),
          timestamp(Instant.now()),
          Math.max(0L, call.latencyMillis()),
          call.usageReported() ? Math.max(0L, call.promptTokens()) : null,
          call.usageReported() ? Math.max(0L, call.completionTokens()) : null,
          "COMPLETED",
          call.systemPrompt(),
          call.userPrompt());
    }
  }

  @Override
  public void saveCheckpoint(String turnId, String modelId, long revision, Object inversePatch) {
    jdbc.update(
        "INSERT INTO assistant_checkpoints(turn_id, model_id, revision, inverse_patch, created_at)"
            + " VALUES (?, ?, ?, ?::jsonb, ?)",
        turnId,
        modelId,
        revision,
        json(inversePatch == null ? Map.of() : inversePatch),
        timestamp(Instant.now()));
    jdbc.update(
        "UPDATE assistant_turns SET checkpoint_count = checkpoint_count + 1, revision = ? WHERE id"
            + " = ?",
        revision,
        turnId);
  }

  @Override
  public Optional<AssistantTurnStore.Checkpoint> latestCheckpoint(String turnId) {
    return jdbc
        .query(
            "SELECT model_id, revision, inverse_patch FROM assistant_checkpoints WHERE turn_id = ?"
                + " ORDER BY id DESC LIMIT 1",
            (rs, row) ->
                new AssistantTurnStore.Checkpoint(
                    rs.getString("model_id"),
                    rs.getLong("revision"),
                    tree(rs.getString("inverse_patch"))),
            turnId)
        .stream()
        .findFirst();
  }

  @Override
  public List<AssistantTurnStore.Checkpoint> checkpoints(String turnId) {
    return jdbc.query(
        "SELECT model_id, revision, inverse_patch FROM assistant_checkpoints WHERE turn_id = ?"
            + " ORDER BY id",
        (rs, row) ->
            new AssistantTurnStore.Checkpoint(
                rs.getString("model_id"),
                rs.getLong("revision"),
                tree(rs.getString("inverse_patch"))),
        turnId);
  }

  @Override
  public void saveProvenance(
      String turnId,
      String elementId,
      String sourceUnitId,
      String requirementId,
      String kind,
      String assumption) {
    jdbc.update(
        "INSERT INTO assistant_element_provenance(turn_id, element_id, source_unit_id,"
            + " requirement_id, kind, assumption) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
        turnId,
        elementId,
        sourceUnitId,
        requirementId,
        kind,
        assumption);
  }

  @Override
  public List<AssistantTurnStore.Provenance> provenance(String turnId) {
    return jdbc.query(
        "SELECT element_id, source_unit_id, requirement_id, kind, assumption FROM"
            + " assistant_element_provenance WHERE turn_id = ? ORDER BY id",
        (rs, row) ->
            new AssistantTurnStore.Provenance(
                rs.getString("element_id"),
                rs.getString("source_unit_id"),
                rs.getString("requirement_id"),
                rs.getString("kind"),
                rs.getString("assumption")),
        turnId);
  }

  @Override
  public void audit(String turnId, String action, Map<String, Object> details) {
    jdbc.update(
        """
INSERT INTO assistant_action_audits(id, turn_id, project_id, actor_id, action, details, created_at)
SELECT ?, ?, project_id, user_id, ?, ?::jsonb, ? FROM assistant_turns WHERE id = ?
""",
        java.util.UUID.randomUUID().toString(),
        turnId,
        action,
        json(details),
        timestamp(Instant.now()),
        turnId);
  }

  private Optional<AssistantTurn> one(String sql, Object... args) {
    return jdbc.query(sql, this::turn, args).stream().findFirst();
  }

  private AssistantTurn turn(ResultSet rs, int ignored) throws java.sql.SQLException {
    return new AssistantTurn(
        rs.getString("id"),
        rs.getString("thread_id"),
        rs.getString("user_id"),
        rs.getString("project_id"),
        ModelLevel.valueOf(rs.getString("level")),
        rs.getString("model_id"),
        (Long) rs.getObject("expected_revision"),
        rs.getString("idempotency_key"),
        rs.getString("message"),
        rs.getString("source_text"),
        list(rs.getString("selected_element_ids")),
        AssistantTurn.State.valueOf(rs.getString("state")),
        instant(rs, "accepted_at"),
        instant(rs, "deadline_at"),
        instant(rs, "lease_until"),
        rs.getString("worker_id"),
        rs.getBoolean("cancellation_requested"),
        (Long) rs.getObject("revision"),
        rs.getInt("checkpoint_count"),
        rs.getInt("saved_element_count"),
        (Integer) rs.getObject("coverage_percent"),
        rs.getString("remaining_work"),
        rs.getString("final_message"),
        rs.getInt("provider_calls"),
        rs.getLong("prompt_tokens"),
        rs.getLong("completion_tokens"));
  }

  private AssistantTurn.Event event(ResultSet rs, int ignored) throws java.sql.SQLException {
    return new AssistantTurn.Event(
        rs.getLong("id"),
        rs.getString("turn_id"),
        rs.getLong("sequence"),
        rs.getString("type"),
        instant(rs, "occurred_at"),
        map(rs.getString("payload")));
  }

  private Instant instant(ResultSet rs, String name) throws java.sql.SQLException {
    Timestamp value = rs.getTimestamp(name);
    return value == null ? null : value.toInstant();
  }

  private Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("Could not encode assistant state", ex);
    }
  }

  private List<String> list(String value) {
    try {
      return value == null
          ? List.of()
          : mapper.readValue(value, new TypeReference<List<String>>() {});
    } catch (Exception ex) {
      return List.of();
    }
  }

  private Map<String, Object> map(String value) {
    try {
      return value == null
          ? Map.of()
          : mapper.readValue(value, new TypeReference<Map<String, Object>>() {});
    } catch (Exception ex) {
      return Map.of();
    }
  }

  private tools.jackson.databind.JsonNode tree(String value) {
    try {
      return mapper.readTree(value);
    } catch (Exception ex) {
      return mapper.createObjectNode();
    }
  }

  private record Cancellation(String id, String state) {}
}
