package io.mehdieidi.varka.backend.admin;

import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read models for the administration workspace. */
@Service
public class AdminQueryService {

  private final JdbcTemplate jdbc;

  public AdminQueryService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Overview overview() {
    return new Overview(
        scalar("SELECT count(*) FROM users"),
        scalar("SELECT count(*) FROM disabled_users"),
        scalar("SELECT count(*) FROM auth_sessions WHERE expires_at > now()"),
        scalar("SELECT count(*) FROM projects"),
        scalar("SELECT count(*) FROM models"),
        scalar("SELECT count(*) FROM artifacts"),
        scalar("SELECT count(*) FROM mde_jobs WHERE status IN ('QUEUED','RUNNING')"),
        scalar("SELECT count(*) FROM mde_jobs WHERE status = 'FAILED'"),
        scalar("SELECT count(*) FROM assistant_turns WHERE state IN ('QUEUED','RUNNING')"),
        scalar("SELECT count(*) FROM assistant_turns WHERE state = 'FAILED'"));
  }

  public List<UserSummary> users() {
    return jdbc.query(
        """
SELECT u.id, u.email, u.display_name, u.created_at, u.updated_at,
  d.disabled_at,
  COALESCE(s.active_sessions, 0) AS active_sessions,
  COALESCE(p.project_count, 0) AS project_count,
  COALESCE(ar.roles, '') AS roles
FROM users u
LEFT JOIN disabled_users d ON d.user_id = u.id
LEFT JOIN (
  SELECT user_id, count(*) active_sessions FROM auth_sessions
  WHERE expires_at > now() GROUP BY user_id
) s ON s.user_id = u.id
LEFT JOIN (
  SELECT user_id, count(*) project_count FROM project_members GROUP BY user_id
) p ON p.user_id = u.id
LEFT JOIN (
  SELECT user_id, string_agg(role, ',' ORDER BY role) roles FROM admin_roles GROUP BY user_id
) ar ON ar.user_id = u.id
ORDER BY u.created_at DESC
""",
        (rs, row) -> userSummary(rs));
  }

  public UserDetail user(String id) {
    UserSummary user =
        users().stream()
            .filter(item -> item.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new PlatformException(404, "User not found."));
    return new UserDetail(
        user,
        jdbc.query(
            """
            SELECT p.id, p.name, pm.role, p.updated_at
            FROM project_members pm JOIN projects p ON p.id = pm.project_id
            WHERE pm.user_id = ? ORDER BY p.updated_at DESC
            """,
            (rs, row) ->
                new UserProject(
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("role"),
                    instant(rs, "updated_at")),
            id),
        jdbc.query(
            "SELECT token, created_at, expires_at FROM auth_sessions WHERE user_id = ? ORDER BY"
                + " created_at DESC",
            (rs, row) ->
                new SessionSummary(
                    mask(rs.getString("token")),
                    instant(rs, "created_at"),
                    instant(rs, "expires_at")),
            id));
  }

  public List<ProjectSummary> projects() {
    return jdbc.query(
        """
SELECT p.id, p.name, p.description, p.owner_user_id, u.email owner_email,
  p.created_at, p.updated_at,
  COALESCE(pm.member_count, 0) member_count,
  COALESCE(m.model_count, 0) model_count,
  COALESCE(a.artifact_count, 0) artifact_count,
  COALESCE(j.job_count, 0) job_count
FROM projects p
JOIN users u ON u.id = p.owner_user_id
LEFT JOIN (SELECT project_id, count(*) member_count FROM project_members GROUP BY project_id) pm
  ON pm.project_id = p.id
LEFT JOIN (SELECT project_id, count(*) model_count FROM models GROUP BY project_id) m
  ON m.project_id = p.id
LEFT JOIN (SELECT project_id, count(*) artifact_count FROM artifacts GROUP BY project_id) a
  ON a.project_id = p.id
LEFT JOIN (SELECT project_id, count(*) job_count FROM mde_jobs GROUP BY project_id) j
  ON j.project_id = p.id
ORDER BY p.updated_at DESC
""",
        (rs, row) ->
            new ProjectSummary(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("owner_user_id"),
                rs.getString("owner_email"),
                rs.getLong("member_count"),
                rs.getLong("model_count"),
                rs.getLong("artifact_count"),
                rs.getLong("job_count"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")));
  }

  public List<ModelSummary> models() {
    return jdbc.query(
        """
        SELECT m.id, m.project_id, p.name project_name, m.level, m.name, m.revision,
          m.metamodel_version, m.migration_state, m.created_at, m.updated_at
        FROM models m JOIN projects p ON p.id = m.project_id
        ORDER BY m.updated_at DESC LIMIT 500
        """,
        (rs, row) ->
            new ModelSummary(
                rs.getString("id"),
                rs.getString("project_id"),
                rs.getString("project_name"),
                rs.getString("level"),
                rs.getString("name"),
                rs.getLong("revision"),
                rs.getString("metamodel_version"),
                rs.getString("migration_state"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")));
  }

  public List<JobSummary> jobs() {
    return jdbc.query(
        """
        SELECT j.id, j.project_id, p.name project_name, j.user_id, u.email user_email,
          j.operation, j.status, j.progress_percent, j.source_level, j.created_at, j.started_at,
          j.finished_at
        FROM mde_jobs j
        JOIN projects p ON p.id = j.project_id
        JOIN users u ON u.id = j.user_id
        ORDER BY j.created_at DESC LIMIT 500
        """,
        (rs, row) ->
            new JobSummary(
                rs.getString("id"),
                rs.getString("project_id"),
                rs.getString("project_name"),
                rs.getString("user_id"),
                rs.getString("user_email"),
                rs.getString("operation"),
                rs.getString("status"),
                rs.getInt("progress_percent"),
                rs.getString("source_level"),
                instant(rs, "created_at"),
                instant(rs, "started_at"),
                instant(rs, "finished_at")));
  }

  public List<AssistantTurnSummary> assistantTurns() {
    return jdbc.query(
        """
        SELECT t.id, t.thread_id, t.project_id, p.name project_name, t.user_id, u.email user_email,
          t.level, t.model_id, t.state, t.provider_calls, t.prompt_tokens, t.completion_tokens,
          t.accepted_at, t.started_at, t.completed_at
        FROM assistant_turns t
        JOIN projects p ON p.id = t.project_id
        JOIN users u ON u.id = t.user_id
        ORDER BY t.accepted_at DESC LIMIT 500
        """,
        (rs, row) ->
            new AssistantTurnSummary(
                rs.getString("id"),
                rs.getString("thread_id"),
                rs.getString("project_id"),
                rs.getString("project_name"),
                rs.getString("user_id"),
                rs.getString("user_email"),
                rs.getString("level"),
                rs.getString("model_id"),
                rs.getString("state"),
                rs.getLong("provider_calls"),
                rs.getLong("prompt_tokens"),
                rs.getLong("completion_tokens"),
                instant(rs, "accepted_at"),
                instant(rs, "started_at"),
                instant(rs, "completed_at")));
  }

  public List<AuditEvent> auditEvents() {
    return jdbc.query(
        """
        SELECT e.id, e.actor_id, u.email actor_email, e.action, e.target_type, e.target_id,
          e.reason, e.details::text, e.request_id, e.created_at
        FROM admin_audit_events e
        LEFT JOIN users u ON u.id = e.actor_id
        ORDER BY e.created_at DESC LIMIT 500
        """,
        (rs, row) ->
            new AuditEvent(
                rs.getString("id"),
                rs.getString("actor_id"),
                rs.getString("actor_email"),
                rs.getString("action"),
                rs.getString("target_type"),
                rs.getString("target_id"),
                rs.getString("reason"),
                rs.getString("details"),
                rs.getString("request_id"),
                instant(rs, "created_at")));
  }

  public List<UserLoginEvent> userLoginEvents() {
    return jdbc.query(
        """
        SELECT e.id, e.user_id, e.email_snapshot, u.display_name, e.ip_address, e.country, e.os,
          e.browser, e.device, e.user_agent, e.request_id, e.occurred_at
        FROM user_login_events e
        LEFT JOIN users u ON u.id = e.user_id
        ORDER BY e.occurred_at DESC LIMIT 500
        """,
        (rs, row) ->
            new UserLoginEvent(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("email_snapshot"),
                rs.getString("display_name"),
                rs.getString("ip_address"),
                rs.getString("country"),
                rs.getString("os"),
                rs.getString("browser"),
                rs.getString("device"),
                rs.getString("user_agent"),
                rs.getString("request_id"),
                instant(rs, "occurred_at")));
  }

  public List<LandingPageVisit> landingPageVisits() {
    return jdbc.query(
        """
        SELECT id, ip_address, country, os, browser, device, user_agent, path, referrer,
          request_id, occurred_at
        FROM landing_page_visits
        WHERE occurred_at >= now() - interval '3 days'
        ORDER BY occurred_at DESC LIMIT 500
        """,
        (rs, row) ->
            new LandingPageVisit(
                rs.getString("id"),
                rs.getString("ip_address"),
                rs.getString("country"),
                rs.getString("os"),
                rs.getString("browser"),
                rs.getString("device"),
                rs.getString("user_agent"),
                rs.getString("path"),
                rs.getString("referrer"),
                rs.getString("request_id"),
                instant(rs, "occurred_at")));
  }

  public Map<String, Long> jobStatusCounts() {
    return jdbc
        .query(
            "SELECT status, count(*) count FROM mde_jobs GROUP BY status ORDER BY status",
            (rs, row) -> Map.entry(rs.getString("status"), rs.getLong("count")))
        .stream()
        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private long scalar(String sql) {
    Long value = jdbc.queryForObject(sql, Long.class);
    return value == null ? 0L : value;
  }

  private UserSummary userSummary(ResultSet rs) throws SQLException {
    String roles = rs.getString("roles");
    return new UserSummary(
        rs.getString("id"),
        rs.getString("email"),
        rs.getString("display_name"),
        rs.getLong("active_sessions"),
        rs.getLong("project_count"),
        roles == null || roles.isBlank() ? List.of() : List.of(roles.split(",")),
        instant(rs, "disabled_at") != null,
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private String mask(String token) {
    if (token == null || token.length() <= 12) {
      return "session";
    }
    return token.substring(0, 8) + "...";
  }

  private Instant instant(ResultSet rs, String column) throws SQLException {
    java.sql.Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  public record Overview(
      long users,
      long disabledUsers,
      long activeSessions,
      long projects,
      long models,
      long artifacts,
      long activeJobs,
      long failedJobs,
      long activeAssistantTurns,
      long failedAssistantTurns) {}

  public record UserSummary(
      String id,
      String email,
      String displayName,
      long activeSessions,
      long projectCount,
      List<String> adminRoles,
      boolean disabled,
      Instant createdAt,
      Instant updatedAt) {}

  public record UserDetail(
      UserSummary user, List<UserProject> projects, List<SessionSummary> sessions) {}

  public record UserProject(String id, String name, String role, Instant updatedAt) {}

  public record SessionSummary(String tokenPreview, Instant createdAt, Instant expiresAt) {}

  public record ProjectSummary(
      String id,
      String name,
      String description,
      String ownerUserId,
      String ownerEmail,
      long memberCount,
      long modelCount,
      long artifactCount,
      long jobCount,
      Instant createdAt,
      Instant updatedAt) {}

  public record ModelSummary(
      String id,
      String projectId,
      String projectName,
      String level,
      String name,
      long revision,
      String metamodelVersion,
      String migrationState,
      Instant createdAt,
      Instant updatedAt) {}

  public record JobSummary(
      String id,
      String projectId,
      String projectName,
      String userId,
      String userEmail,
      String operation,
      String status,
      int progressPercent,
      String sourceLevel,
      Instant createdAt,
      Instant startedAt,
      Instant finishedAt) {}

  public record AssistantTurnSummary(
      String id,
      String threadId,
      String projectId,
      String projectName,
      String userId,
      String userEmail,
      String level,
      String modelId,
      String state,
      long providerCalls,
      long promptTokens,
      long completionTokens,
      Instant acceptedAt,
      Instant startedAt,
      Instant completedAt) {}

  public record AuditEvent(
      String id,
      String actorId,
      String actorEmail,
      String action,
      String targetType,
      String targetId,
      String reason,
      String details,
      String requestId,
      Instant createdAt) {}

  public record UserLoginEvent(
      String id,
      String userId,
      String email,
      String displayName,
      String ipAddress,
      String country,
      String os,
      String browser,
      String device,
      String userAgent,
      String requestId,
      Instant occurredAt) {}

  public record LandingPageVisit(
      String id,
      String ipAddress,
      String country,
      String os,
      String browser,
      String device,
      String userAgent,
      String path,
      String referrer,
      String requestId,
      Instant occurredAt) {}
}
