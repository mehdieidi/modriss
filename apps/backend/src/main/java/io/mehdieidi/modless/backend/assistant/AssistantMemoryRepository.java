package io.mehdieidi.modless.backend.assistant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Durable assistant conversation, proposal, and audit persistence.
 */
@Repository
public class AssistantMemoryRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<io.mehdieidi.modless.platform.core.service.ModelService.ModelPatchOperation>> PATCH_LIST =
            new TypeReference<>() {
            };

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    /**
     * Creates the repository.
     *
     * @param jdbc   JDBC access
     * @param mapper JSON mapper
     */
    public AssistantMemoryRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * Ensures a thread exists and returns its record.
     *
     * @param user      owner user
     * @param projectId project scope
     * @param level     model level
     * @param title     display title
     * @param modelId   active model ID
     * @param revision  active model revision
     * @return thread record
     */
    public ThreadRecord ensureThread(UserRecord user, String projectId, ModelLevel level,
            String title, String modelId, Long revision) {
        Instant now = Instant.now();
        String id = user.id() + ":" + projectId + ":" + level.name();
        jdbc.update("""
                INSERT INTO assistant_threads(id, user_id, project_id, level, active_model_id,
                  active_revision, title, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET active_model_id = EXCLUDED.active_model_id,
                  active_revision = EXCLUDED.active_revision, title = EXCLUDED.title,
                  updated_at = EXCLUDED.updated_at
                """, id, user.id(), projectId, level.name(), modelId, revision, title,
                sqlTimestamp(now), sqlTimestamp(now));
        return requireThread(id);
    }

    /**
     * Loads a thread by ID.
     *
     * @param threadId thread ID
     * @return thread record
     */
    public ThreadRecord requireThread(String threadId) {
        return jdbc.query("""
                SELECT id, user_id, project_id, level, active_model_id, active_revision, title,
                  created_at, updated_at
                FROM assistant_threads WHERE id = ?
                """, rs -> rs.next()
                ? new ThreadRecord(rs.getString("id"),
                rs.getString("user_id"), rs.getString("project_id"),
                ModelLevel.valueOf(rs.getString("level")), rs.getString("title"),
                rs.getString("active_model_id"),
                rs.getObject("active_revision", Long.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant())
                : null, threadId);
    }

    /**
     * Loads a thread when it belongs to a user.
     *
     * @param threadId thread ID
     * @param userId   owner user ID
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
     * @param role     message role
     * @param content  message content
     * @param metadata metadata
     */
    public void appendMessage(String threadId, String role, String content,
            Map<String, Object> metadata) {
                jdbc.update("""
                        INSERT INTO assistant_messages(id, thread_id, role, content, metadata, created_at)
                        VALUES (?, ?, ?, ?, ?::jsonb, ?)
                        """, java.util.UUID.randomUUID().toString(), threadId, role, content,
                json(metadata == null ? Map.of() : metadata), sqlTimestamp(Instant.now()));
    }

    /**
     * Returns the most recent messages for a thread.
     *
     * @param threadId thread ID
     * @param limit    message limit
     * @return recent messages
     */
    public List<MessageRecord> recentMessages(String threadId, int limit) {
        return jdbc.query("""
                SELECT id, thread_id, role, content, metadata, created_at
                FROM assistant_messages WHERE thread_id = ?
                ORDER BY created_at DESC LIMIT ?
                """, (rs, row) -> new MessageRecord(rs.getString("id"), rs.getString("thread_id"),
                rs.getString("role"), rs.getString("content"), map(rs.getString("metadata")),
                rs.getTimestamp("created_at").toInstant()), threadId, limit);
    }

    /**
     * Returns the rolling summary, if present.
     *
     * @param threadId thread ID
     * @return summary text
     */
    public Optional<String> summary(String threadId) {
        return jdbc.query("""
                SELECT summary FROM assistant_thread_summaries WHERE thread_id = ?
                """, rs -> rs.next() ? Optional.of(rs.getString("summary")) : Optional.empty(),
                threadId);
    }

    /**
     * Updates the thread summary.
     *
     * @param threadId  thread ID
     * @param summary   summary text
     * @param messageId summary source message ID
     */
    public void updateSummary(String threadId, String summary, String messageId) {
        jdbc.update("""
                INSERT INTO assistant_thread_summaries(thread_id, summary, message_id, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (thread_id) DO UPDATE SET summary = EXCLUDED.summary,
                  message_id = EXCLUDED.message_id, updated_at = EXCLUDED.updated_at
                """, threadId, summary, messageId, sqlTimestamp(Instant.now()));
    }

    /**
     * Saves a proposal record.
     *
     * @param threadId      thread ID
     * @param projectId     project ID
     * @param modelId       model ID
     * @param modelRevision model revision
     * @param proposal      proposal payload
     * @param status        proposal status
     */
    public void saveProposal(String threadId, String projectId, String modelId,
            long modelRevision, AssistantProposal proposal, String status) {
        jdbc.update("""
                        INSERT INTO assistant_proposals
                          (id, thread_id, project_id, model_id, model_revision, risk_level,
                           approval_required, semantic_patch, inverse_patch, validation_summary,
                           citations, status, created_at, decided_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?)
                        ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status,
                          decided_at = EXCLUDED.decided_at
                """, proposal.id(), threadId, projectId, modelId, modelRevision,
                proposal.riskLevel().name(), proposal.approvalRequired(),
                json(proposal.patch()), json(proposal.inversePatch()), json(proposal.validation()),
                json(proposal.citations()), status, sqlTimestamp(proposal.createdAt()),
                sqlTimestamp(Instant.now()));
    }

    /**
     * Loads a proposal by ID.
     *
     * @param proposalId proposal ID
     * @return proposal record
     */
    public Optional<ProposalRecord> findProposal(String proposalId) {
        return jdbc.query("""
                        SELECT id, thread_id, project_id, model_id, model_revision, semantic_patch,
                          validation_summary, citations, status, decided_at, risk_level, approval_required,
                          inverse_patch, created_at
                        FROM assistant_proposals WHERE id = ?
                        """, rs -> rs.next() ? Optional.of(new ProposalRecord(
                        rs.getString("id"),
                        rs.getString("thread_id"),
                        rs.getString("project_id"),
                        rs.getString("model_id"),
                        rs.getLong("model_revision"),
                        deserializeProposal(rs),
                        rs.getString("status"),
                        rs.getTimestamp("decided_at") == null ? null
                                : rs.getTimestamp("decided_at").toInstant())) : Optional.empty(),
                proposalId);
    }

    /**
     * Updates proposal status.
     *
     * @param proposalId proposal ID
     * @param status     status
     */
    public void updateProposalStatus(String proposalId, String status) {
        jdbc.update("UPDATE assistant_proposals SET status = ?, decided_at = ? WHERE id = ?",
                status, sqlTimestamp(Instant.now()), proposalId);
    }

    /**
     * Appends an audit row.
     *
     * @param proposalId proposal ID
     * @param projectId  project ID
     * @param actorId    actor user ID
     * @param action     action name
     * @param details    details
     */
    public void appendAudit(String proposalId, String projectId, String actorId, String action,
            Map<String, Object> details) {
        jdbc.update("""
                        INSERT INTO assistant_action_audits(id, proposal_id, project_id, actor_id, action,
                          details, created_at)
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                        """, java.util.UUID.randomUUID().toString(), proposalId, projectId, actorId, action,
                json(details == null ? Map.of() : details), sqlTimestamp(Instant.now()));
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
                "DELETE FROM assistant_action_audits WHERE proposal_id IN (SELECT id FROM assistant_proposals WHERE thread_id = ?)",
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
            return mapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private AssistantProposal deserializeProposal(java.sql.ResultSet rs) {
        try {
            SemanticModelPatch patch = mapper.readValue(rs.getString("semantic_patch"),
                    SemanticModelPatch.class);
            List<io.mehdieidi.modless.platform.core.service.ModelService.ModelPatchOperation> inversePatch =
                    mapper.readValue(rs.getString("inverse_patch"), PATCH_LIST);
            AssistantValidationSummary validation = mapper.readValue(
                    rs.getString("validation_summary"), AssistantValidationSummary.class);
            List<String> citations = mapper.readValue(rs.getString("citations"), STRING_LIST);
            List<String> affected = patch.operations().stream()
                    .map(operation -> Optional.ofNullable(operation.targetElementId())
                            .orElse(""))
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
            return new AssistantProposal(rs.getString("id"), affected, patch, inversePatch,
                    validation,
                    AssistantProposal.RiskLevel.valueOf(rs.getString("risk_level")),
                    rs.getBoolean("approval_required"), citations,
                    rs.getTimestamp("created_at").toInstant());
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load assistant proposal.", ex);
        }
    }

    /**
     * Assistant thread metadata.
     *
     * @param id             thread ID
     * @param userId         owner user ID
     * @param projectId      project ID
     * @param level          model level
     * @param title          thread title
     * @param activeModelId  active model ID
     * @param activeRevision active model revision
     * @param createdAt      creation timestamp
     * @param updatedAt      update timestamp
     */
    public record ThreadRecord(String id, String userId, String projectId, ModelLevel level,
                               String title, String activeModelId, Long activeRevision,
                               Instant createdAt, Instant updatedAt) {

    }

    /**
     * Assistant message record.
     *
     * @param id        message ID
     * @param threadId  owning thread ID
     * @param role      message role
     * @param content   message content
     * @param metadata  message metadata
     * @param createdAt creation time
     */
    public record MessageRecord(String id, String threadId, String role, String content,
                                Map<String, Object> metadata, Instant createdAt) {

    }

    /**
     * Stored proposal record.
     *
     * @param id            proposal ID
     * @param threadId      thread ID
     * @param projectId     project ID
     * @param modelId       model ID
     * @param modelRevision model revision
     * @param proposal      proposal payload
     * @param status        proposal status
     * @param decidedAt     decision time
     */
    public record ProposalRecord(String id, String threadId, String projectId, String modelId,
                                 long modelRevision, AssistantProposal proposal, String status,
                                 Instant decidedAt) {

    }
}
