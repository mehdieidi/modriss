package io.mehdieidi.modless.platform.assistant.persistence.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.application.AssistantOrchestrator;
import io.mehdieidi.modless.platform.assistant.domain.AssistantChoice;
import io.mehdieidi.modless.platform.assistant.domain.AssistantProposal;
import io.mehdieidi.modless.platform.assistant.domain.AssistantValidationSummary;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.ConversationSummary;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.MessageRecord;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.PendingInteractionRecord;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.ProposalRecord;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.ThreadRecord;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMemoryStore;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Durable assistant conversation, proposal, and audit persistence. */
public class JdbcAssistantMemoryStore implements AssistantMemoryStore {

  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private static final TypeReference<List<AssistantChoice>> CHOICE_LIST = new TypeReference<>() {};
  private static final TypeReference<
          List<io.mehdieidi.modless.platform.model.application.ModelService.ModelPatchOperation>>
      PATCH_LIST = new TypeReference<>() {};

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  /**
   * Creates the repository.
   *
   * @param jdbc JDBC access
   * @param mapper JSON mapper
   */
  public JdbcAssistantMemoryStore(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  /**
   * Ensures a thread exists and returns its record.
   *
   * @param user owner user
   * @param projectId project scope
   * @param level model level
   * @param title display title
   * @param modelId active model ID
   * @param revision active model revision
   * @return thread record
   */
  public ThreadRecord ensureThread(
      UserRecord user,
      String projectId,
      ModelLevel level,
      String title,
      String modelId,
      Long revision) {
    Instant now = Instant.now();
    String id = user.id() + ":" + projectId + ":" + level.name();
    jdbc.update(
        """
        INSERT INTO assistant_threads(id, user_id, project_id, level, active_model_id,
          active_revision, title, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET active_model_id = EXCLUDED.active_model_id,
          active_revision = EXCLUDED.active_revision, title = EXCLUDED.title,
          updated_at = EXCLUDED.updated_at
        """,
        id,
        user.id(),
        projectId,
        level.name(),
        modelId,
        revision,
        title,
        sqlTimestamp(now),
        sqlTimestamp(now));
    return requireThread(id);
  }

  /**
   * Builds a unique thread ID for a new conversation.
   *
   * @param userId owner user ID
   * @param projectId project scope
   * @param level model level
   * @return unique thread ID
   */
  public String newThreadId(String userId, String projectId, ModelLevel level) {
    return userId + ":" + projectId + ":" + level.name() + ":" + UUID.randomUUID();
  }

  /**
   * Creates a new durable conversation thread.
   *
   * @param threadId thread ID
   * @param user owner user
   * @param projectId project scope
   * @param level model level
   * @param title display title
   * @param modelId active model ID
   * @param revision active model revision
   * @return created thread record
   */
  public ThreadRecord createThread(
      String threadId,
      UserRecord user,
      String projectId,
      ModelLevel level,
      String title,
      String modelId,
      Long revision) {
    Instant now = Instant.now();
    jdbc.update(
        """
        INSERT INTO assistant_threads(id, user_id, project_id, level, active_model_id,
          active_revision, title, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET active_model_id = EXCLUDED.active_model_id,
          active_revision = EXCLUDED.active_revision, title = EXCLUDED.title,
          updated_at = EXCLUDED.updated_at
        """,
        threadId,
        user.id(),
        projectId,
        level.name(),
        modelId,
        revision,
        title,
        sqlTimestamp(now),
        sqlTimestamp(now));
    return requireThread(threadId);
  }

  /**
   * Returns the most recently updated thread for a scope.
   *
   * @param userId owner user ID
   * @param projectId project scope
   * @param level model level
   * @param since earliest updated timestamp
   * @return latest thread, if any
   */
  public Optional<ThreadRecord> findMostRecentThread(
      String userId, String projectId, ModelLevel level, Instant since) {
    return jdbc.query(
        """
        SELECT id, user_id, project_id, level, active_model_id, active_revision, title,
          created_at, updated_at
        FROM assistant_threads
        WHERE user_id = ? AND project_id = ? AND level = ? AND updated_at >= ?
        ORDER BY updated_at DESC LIMIT 1
        """,
        rs ->
            rs.next()
                ? Optional.of(
                    new ThreadRecord(
                        rs.getString("id"),
                        rs.getString("user_id"),
                        rs.getString("project_id"),
                        ModelLevel.valueOf(rs.getString("level")),
                        rs.getString("title"),
                        rs.getString("active_model_id"),
                        rs.getObject("active_revision", Long.class),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()))
                : Optional.empty(),
        userId,
        projectId,
        level.name(),
        sqlTimestamp(since));
  }

  /**
   * Lists recent conversations for history browsing.
   *
   * @param userId owner user ID
   * @param projectId project scope
   * @param level model level
   * @param since earliest updated timestamp
   * @param limit maximum rows
   * @return recent conversation summaries
   */
  public List<ConversationSummary> listRecentConversations(
      String userId, String projectId, ModelLevel level, Instant since, int limit) {
    return jdbc.query(
        """
        SELECT t.id, t.title, t.updated_at,
          (
            SELECT m.content
            FROM assistant_messages m
            WHERE m.thread_id = t.id AND m.role = 'USER'
            ORDER BY m.created_at ASC LIMIT 1
          ) AS preview,
          (
            SELECT COUNT(*)::int
            FROM assistant_messages m
            WHERE m.thread_id = t.id
          ) AS message_count
        FROM assistant_threads t
        WHERE t.user_id = ? AND t.project_id = ? AND t.level = ? AND t.updated_at >= ?
          AND EXISTS (
            SELECT 1 FROM assistant_messages m WHERE m.thread_id = t.id
          )
        ORDER BY t.updated_at DESC
        LIMIT ?
        """,
        (rs, row) ->
            new ConversationSummary(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("preview"),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getInt("message_count")),
        userId,
        projectId,
        level.name(),
        sqlTimestamp(since),
        limit);
  }

  /**
   * Updates the active model binding for a thread.
   *
   * @param threadId thread ID
   * @param modelId active model ID
   * @param revision active model revision
   */
  public void updateThreadModel(String threadId, String modelId, Long revision) {
    jdbc.update(
        """
        UPDATE assistant_threads
        SET active_model_id = ?, active_revision = ?, updated_at = ?
        WHERE id = ?
        """,
        modelId,
        revision,
        sqlTimestamp(Instant.now()),
        threadId);
  }

  /**
   * Updates a thread title when the first user message arrives.
   *
   * @param threadId thread ID
   * @param title new title
   */
  public void updateThreadTitle(String threadId, String title) {
    jdbc.update(
        "UPDATE assistant_threads SET title = ?, updated_at = ? WHERE id = ?",
        title,
        sqlTimestamp(Instant.now()),
        threadId);
  }

  /** Marks a thread as recently active. */
  public void touchThread(String threadId) {
    jdbc.update(
        "UPDATE assistant_threads SET updated_at = ? WHERE id = ?",
        sqlTimestamp(Instant.now()),
        threadId);
  }

  /**
   * Counts user messages in a thread.
   *
   * @param threadId thread ID
   * @return user message count
   */
  public int userMessageCount(String threadId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM assistant_messages WHERE thread_id = ? AND role = 'USER'",
            Integer.class,
            threadId);
    return count == null ? 0 : count;
  }

  /**
   * Loads a thread by ID.
   *
   * @param threadId thread ID
   * @return thread record
   */
  public ThreadRecord requireThread(String threadId) {
    return jdbc.query(
        """
        SELECT id, user_id, project_id, level, active_model_id, active_revision, title,
          created_at, updated_at
        FROM assistant_threads WHERE id = ?
        """,
        rs ->
            rs.next()
                ? new ThreadRecord(
                    rs.getString("id"),
                    rs.getString("user_id"),
                    rs.getString("project_id"),
                    ModelLevel.valueOf(rs.getString("level")),
                    rs.getString("title"),
                    rs.getString("active_model_id"),
                    rs.getObject("active_revision", Long.class),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant())
                : null,
        threadId);
  }

  /**
   * Loads a thread when it belongs to a user.
   *
   * @param threadId thread ID
   * @param userId owner user ID
   * @return thread, if visible to the user
   */
  public Optional<ThreadRecord> findThread(String threadId, String userId) {
    ThreadRecord thread = requireThread(threadId);
    if (thread == null || !thread.userId().equals(userId)) {
      return Optional.empty();
    }
    return Optional.of(thread);
  }

  /**
   * Appends a chat message to durable history.
   *
   * @param threadId thread ID
   * @param role message role
   * @param content message content
   * @param metadata metadata
   */
  public void appendMessage(
      String threadId, String role, String content, Map<String, Object> metadata) {
    jdbc.update(
        """
        INSERT INTO assistant_messages(id, thread_id, role, content, metadata, created_at)
        VALUES (?, ?, ?, ?, ?::jsonb, ?)
        """,
        java.util.UUID.randomUUID().toString(),
        threadId,
        role,
        content,
        json(metadata == null ? Map.of() : metadata),
        sqlTimestamp(Instant.now()));
    touchThread(threadId);
  }

  /** Stores the clarification required to resume a turn across processes and restarts. */
  public void savePendingInteraction(
      String threadId,
      AssistantOrchestrator.AssistantTurnRequest request,
      List<AssistantChoice> questions) {
    Instant now = Instant.now();
    jdbc.update(
        """
        INSERT INTO assistant_pending_interactions(
          thread_id, request_payload, questions, created_at, updated_at)
        VALUES (?, ?::jsonb, ?::jsonb, ?, ?)
        ON CONFLICT (thread_id) DO UPDATE SET request_payload = EXCLUDED.request_payload,
          questions = EXCLUDED.questions, updated_at = EXCLUDED.updated_at
        """,
        threadId,
        json(request),
        json(questions),
        sqlTimestamp(now),
        sqlTimestamp(now));
  }

  /** Loads a pending clarification for a thread. */
  public Optional<PendingInteractionRecord> pendingInteraction(String threadId) {
    return jdbc.query(
        """
        SELECT thread_id, request_payload, questions, created_at
        FROM assistant_pending_interactions WHERE thread_id = ?
        """,
        rs -> {
          if (!rs.next()) {
            return Optional.empty();
          }
          try {
            return Optional.of(
                new PendingInteractionRecord(
                    rs.getString("thread_id"),
                    mapper.readValue(
                        rs.getString("request_payload"),
                        AssistantOrchestrator.AssistantTurnRequest.class),
                    mapper.readValue(rs.getString("questions"), CHOICE_LIST),
                    rs.getTimestamp("created_at").toInstant()));
          } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Could not read pending assistant interaction.", ex);
          }
        },
        threadId);
  }

  /** Removes a completed or superseded clarification. */
  public void clearPendingInteraction(String threadId) {
    jdbc.update("DELETE FROM assistant_pending_interactions WHERE thread_id = ?", threadId);
  }

  /**
   * Returns the most recent messages for a thread.
   *
   * @param threadId thread ID
   * @param limit message limit
   * @return recent messages
   */
  public List<MessageRecord> recentMessages(String threadId, int limit) {
    return jdbc.query(
        """
        SELECT id, thread_id, role, content, metadata, created_at
        FROM assistant_messages WHERE thread_id = ?
        ORDER BY created_at DESC LIMIT ?
        """,
        (rs, row) ->
            new MessageRecord(
                rs.getString("id"),
                rs.getString("thread_id"),
                rs.getString("role"),
                rs.getString("content"),
                map(rs.getString("metadata")),
                rs.getTimestamp("created_at").toInstant()),
        threadId,
        limit);
  }

  /**
   * Returns the rolling summary, if present.
   *
   * @param threadId thread ID
   * @return summary text
   */
  public Optional<String> summary(String threadId) {
    return jdbc.query(
        """
        SELECT summary FROM assistant_thread_summaries WHERE thread_id = ?
        """,
        rs -> rs.next() ? Optional.of(rs.getString("summary")) : Optional.empty(),
        threadId);
  }

  /**
   * Updates the thread summary.
   *
   * @param threadId thread ID
   * @param summary summary text
   * @param messageId summary source message ID
   */
  public void updateSummary(String threadId, String summary, String messageId) {
    jdbc.update(
        """
        INSERT INTO assistant_thread_summaries(thread_id, summary, message_id, updated_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (thread_id) DO UPDATE SET summary = EXCLUDED.summary,
          message_id = EXCLUDED.message_id, updated_at = EXCLUDED.updated_at
        """,
        threadId,
        summary,
        messageId,
        sqlTimestamp(Instant.now()));
  }

  /**
   * Saves a proposal record.
   *
   * @param threadId thread ID
   * @param projectId project ID
   * @param modelId model ID
   * @param modelRevision model revision
   * @param proposal proposal payload
   * @param status proposal status
   */
  public void saveProposal(
      String threadId,
      String projectId,
      String modelId,
      long modelRevision,
      AssistantProposal proposal,
      String status) {
    jdbc.update(
        """
                INSERT INTO assistant_proposals
                  (id, thread_id, project_id, model_id, model_revision, risk_level,
                   semantic_patch, inverse_patch, validation_summary,
                   citations, status, created_at, decided_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status,
                  decided_at = EXCLUDED.decided_at
        """,
        proposal.id(),
        threadId,
        projectId,
        modelId,
        modelRevision,
        proposal.riskLevel().name(),
        json(proposal.patch()),
        json(proposal.inversePatch()),
        json(proposal.validation()),
        json(proposal.citations()),
        status,
        sqlTimestamp(proposal.createdAt()),
        sqlTimestamp(Instant.now()));
  }

  /**
   * Loads a proposal by ID.
   *
   * @param proposalId proposal ID
   * @return proposal record
   */
  public Optional<ProposalRecord> findProposal(String proposalId) {
    return jdbc.query(
        """
        SELECT id, thread_id, project_id, model_id, model_revision, semantic_patch,
          validation_summary, citations, status, decided_at, risk_level, inverse_patch, created_at
        FROM assistant_proposals WHERE id = ?
        """,
        rs ->
            rs.next()
                ? Optional.of(
                    new ProposalRecord(
                        rs.getString("id"),
                        rs.getString("thread_id"),
                        rs.getString("project_id"),
                        rs.getString("model_id"),
                        rs.getLong("model_revision"),
                        deserializeProposal(rs),
                        rs.getString("status"),
                        rs.getTimestamp("decided_at") == null
                            ? null
                            : rs.getTimestamp("decided_at").toInstant()))
                : Optional.empty(),
        proposalId);
  }

  /**
   * Loads the most recent proposal in a workflow state for a thread.
   *
   * @param threadId thread ID
   * @param status proposal workflow status
   * @return latest matching proposal, if present
   */
  public Optional<ProposalRecord> findLatestProposal(String threadId, String status) {
    return jdbc.query(
        """
        SELECT id, thread_id, project_id, model_id, model_revision, semantic_patch,
          validation_summary, citations, status, decided_at, risk_level, inverse_patch, created_at
        FROM assistant_proposals
        WHERE thread_id = ? AND status = ?
        ORDER BY created_at DESC LIMIT 1
        """,
        rs ->
            rs.next()
                ? Optional.of(
                    new ProposalRecord(
                        rs.getString("id"),
                        rs.getString("thread_id"),
                        rs.getString("project_id"),
                        rs.getString("model_id"),
                        rs.getLong("model_revision"),
                        deserializeProposal(rs),
                        rs.getString("status"),
                        rs.getTimestamp("decided_at") == null
                            ? null
                            : rs.getTimestamp("decided_at").toInstant()))
                : Optional.empty(),
        threadId,
        status);
  }

  /**
   * Updates proposal status.
   *
   * @param proposalId proposal ID
   * @param status status
   */
  public void updateProposalStatus(String proposalId, String status) {
    jdbc.update(
        "UPDATE assistant_proposals SET status = ?, decided_at = ? WHERE id = ?",
        status,
        sqlTimestamp(Instant.now()),
        proposalId);
  }

  /** Marks a proposal applied and binds bootstrap proposals to the model they created. */
  public void markProposalApplied(String proposalId, String modelId, long modelRevision) {
    jdbc.update(
        """
        UPDATE assistant_proposals
        SET status = 'APPLIED', model_id = ?, model_revision = ?, decided_at = ?
        WHERE id = ?
        """,
        modelId,
        modelRevision,
        sqlTimestamp(Instant.now()),
        proposalId);
  }

  /**
   * Appends an audit row.
   *
   * @param proposalId proposal ID
   * @param projectId project ID
   * @param actorId actor user ID
   * @param action action name
   * @param details details
   */
  public void appendAudit(
      String proposalId,
      String projectId,
      String actorId,
      String action,
      Map<String, Object> details) {
    jdbc.update(
        """
        INSERT INTO assistant_action_audits(id, proposal_id, project_id, actor_id, action,
          details, created_at)
        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
        """,
        java.util.UUID.randomUUID().toString(),
        proposalId,
        projectId,
        actorId,
        action,
        json(details == null ? Map.of() : details),
        sqlTimestamp(Instant.now()));
  }

  /**
   * Clears a thread and its durable data.
   *
   * @param threadId thread ID
   */
  public void clearThread(String threadId) {
    jdbc.update("DELETE FROM assistant_messages WHERE thread_id = ?", threadId);
    jdbc.update("DELETE FROM assistant_thread_summaries WHERE thread_id = ?", threadId);
    jdbc.update(
        "DELETE FROM assistant_action_audits WHERE proposal_id IN (SELECT id FROM"
            + " assistant_proposals WHERE thread_id = ?)",
        threadId);
    jdbc.update("DELETE FROM assistant_proposals WHERE thread_id = ?", threadId);
    jdbc.update("DELETE FROM assistant_threads WHERE id = ?", threadId);
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("Could not serialize assistant data.", ex);
    }
  }

  private Timestamp sqlTimestamp(Instant instant) {
    return Timestamp.from(instant == null ? Instant.now() : instant);
  }

  private Map<String, Object> map(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return mapper.readValue(json, new TypeReference<>() {});
    } catch (Exception ex) {
      return Map.of();
    }
  }

  private AssistantProposal deserializeProposal(java.sql.ResultSet rs) {
    try {
      SemanticModelPatch patch =
          mapper.readValue(rs.getString("semantic_patch"), SemanticModelPatch.class);
      List<io.mehdieidi.modless.platform.model.application.ModelService.ModelPatchOperation>
          inversePatch = mapper.readValue(rs.getString("inverse_patch"), PATCH_LIST);
      AssistantValidationSummary validation =
          mapper.readValue(rs.getString("validation_summary"), AssistantValidationSummary.class);
      List<String> citations = mapper.readValue(rs.getString("citations"), STRING_LIST);
      List<String> affected =
          patch.operations().stream()
              .map(operation -> Optional.ofNullable(operation.targetElementId()).orElse(""))
              .filter(value -> !value.isBlank())
              .distinct()
              .toList();
      return new AssistantProposal(
          rs.getString("id"),
          affected,
          patch,
          inversePatch,
          validation,
          AssistantProposal.RiskLevel.valueOf(rs.getString("risk_level")),
          citations,
          rs.getTimestamp("created_at").toInstant());
    } catch (Exception ex) {
      throw new IllegalStateException("Could not load assistant proposal.", ex);
    }
  }
}
