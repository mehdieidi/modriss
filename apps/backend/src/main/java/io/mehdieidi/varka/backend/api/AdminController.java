package io.mehdieidi.varka.backend.api;

import io.mehdieidi.varka.backend.admin.AdminAccessService;
import io.mehdieidi.varka.backend.admin.AdminAccessService.AdminPrincipal;
import io.mehdieidi.varka.backend.admin.AdminQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Protected administration and observability API. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final AuthSupport auth;
  private final AdminAccessService access;
  private final AdminQueryService queries;
  private final JdbcTemplate jdbc;

  public AdminController(
      AuthSupport auth, AdminAccessService access, AdminQueryService queries, JdbcTemplate jdbc) {
    this.auth = auth;
    this.access = access;
    this.queries = queries;
    this.jdbc = jdbc;
  }

  @GetMapping("/me")
  AdminMe me(@RequestHeader("X-Auth-Token") String token) {
    AdminPrincipal principal = access.requireAdmin(auth.user(token));
    return new AdminMe(
        principal.user().id(),
        principal.user().email(),
        principal.user().displayName(),
        principal.roles());
  }

  @PostMapping("/bootstrap")
  ResponseEntity<Void> bootstrap(
      @RequestHeader("X-Auth-Token") String token, @Valid @RequestBody BootstrapRequest request) {
    access.bootstrapFirstAdmin(auth.user(token), request.bootstrapToken());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/overview")
  AdminQueryService.Overview overview(@RequestHeader("X-Auth-Token") String token) {
    access.requireAdmin(auth.user(token));
    return queries.overview();
  }

  @GetMapping("/users")
  List<AdminQueryService.UserSummary> users(@RequestHeader("X-Auth-Token") String token) {
    access.requireAdmin(auth.user(token));
    return queries.users();
  }

  @GetMapping("/users/{id}")
  AdminQueryService.UserDetail user(
      @RequestHeader("X-Auth-Token") String token, @PathVariable String id) {
    access.requireAdmin(auth.user(token));
    return queries.user(id);
  }

  @PostMapping("/users/{id}/roles")
  ResponseEntity<Void> grantRole(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String id,
      @Valid @RequestBody RoleRequest request) {
    access.grantRole(access.requireOwner(auth.user(token)), id, request.role(), request.reason());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/users/{id}/roles/revoke")
  ResponseEntity<Void> revokeRole(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String id,
      @Valid @RequestBody RoleRequest request) {
    access.revokeRole(access.requireOwner(auth.user(token)), id, request.role(), request.reason());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/users/{id}/disable")
  ResponseEntity<Void> disableUser(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String id,
      @RequestBody(required = false) ReasonRequest request) {
    access.disableUser(
        access.requireOwner(auth.user(token)), id, request == null ? "" : request.reason());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/users/{id}/enable")
  ResponseEntity<Void> enableUser(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String id,
      @RequestBody(required = false) ReasonRequest request) {
    access.enableUser(
        access.requireOwner(auth.user(token)), id, request == null ? "" : request.reason());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/users/{id}/sessions/revoke")
  ResponseEntity<Void> revokeSessions(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String id,
      @RequestBody(required = false) ReasonRequest request) {
    access.revokeSessions(
        access.requireOperator(auth.user(token)), id, request == null ? "" : request.reason());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/projects")
  List<AdminQueryService.ProjectSummary> projects(@RequestHeader("X-Auth-Token") String token) {
    access.requireAdmin(auth.user(token));
    return queries.projects();
  }

  @GetMapping("/models")
  List<AdminQueryService.ModelSummary> models(@RequestHeader("X-Auth-Token") String token) {
    access.requireAdmin(auth.user(token));
    return queries.models();
  }

  @GetMapping("/jobs")
  List<AdminQueryService.JobSummary> jobs(@RequestHeader("X-Auth-Token") String token) {
    access.requireAdmin(auth.user(token));
    return queries.jobs();
  }

  @PostMapping("/jobs/{id}/cancel")
  ResponseEntity<Void> cancelJob(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String id,
      @RequestBody(required = false) ReasonRequest request) {
    AdminPrincipal principal = access.requireOperator(auth.user(token));
    jdbc.update(
        """
        UPDATE mde_jobs SET status = 'CANCELLED', finished_at = COALESCE(finished_at, now())
        WHERE id = ? AND status IN ('QUEUED', 'RUNNING')
        """,
        id);
    access.audit(
        principal,
        "MDE_JOB_CANCELLED",
        "MDE_JOB",
        id,
        request == null ? "" : request.reason(),
        Map.of());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/assistant/turns")
  List<AdminQueryService.AssistantTurnSummary> assistantTurns(
      @RequestHeader("X-Auth-Token") String token) {
    access.requireAdmin(auth.user(token));
    return queries.assistantTurns();
  }

  @PostMapping("/assistant/turns/{id}/cancel")
  ResponseEntity<Void> cancelAssistantTurn(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String id,
      @RequestBody(required = false) ReasonRequest request) {
    AdminPrincipal principal = access.requireOperator(auth.user(token));
    jdbc.update(
        """
        UPDATE assistant_turns SET cancellation_requested = true
        WHERE id = ? AND state IN ('QUEUED', 'RUNNING')
        """,
        id);
    access.audit(
        principal,
        "ASSISTANT_TURN_CANCEL_REQUESTED",
        "ASSISTANT_TURN",
        id,
        request == null ? "" : request.reason(),
        Map.of());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/audit-events")
  List<AdminQueryService.AuditEvent> auditEvents(@RequestHeader("X-Auth-Token") String token) {
    access.requireAdmin(auth.user(token));
    return queries.auditEvents();
  }

  @GetMapping("/metrics-summary")
  Map<String, ?> metricsSummary(@RequestHeader("X-Auth-Token") String token) {
    access.requireAdmin(auth.user(token));
    return Map.of("jobStatusCounts", queries.jobStatusCounts(), "overview", queries.overview());
  }

  public record AdminMe(String id, String email, String displayName, List<String> roles) {}

  public record BootstrapRequest(@NotBlank String bootstrapToken) {}

  public record RoleRequest(@NotBlank String role, String reason) {}

  public record ReasonRequest(String reason) {}
}
