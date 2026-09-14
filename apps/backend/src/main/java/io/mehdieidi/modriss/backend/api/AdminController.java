package io.mehdieidi.modriss.backend.api;

import io.mehdieidi.modriss.backend.admin.AdminAccessService;
import io.mehdieidi.modriss.backend.admin.AdminAccessService.AdminPrincipal;
import io.mehdieidi.modriss.backend.admin.AdminNotationService;
import io.mehdieidi.modriss.backend.admin.AdminQueryService;
import io.mehdieidi.modriss.backend.admin.AdminThemeService;
import io.mehdieidi.modriss.backend.admin.AdminThemeService.ThemeProfileRequest;
import io.mehdieidi.modriss.backend.admin.AdminThemeService.ThemeState;
import io.mehdieidi.modriss.platform.assistant.turn.AssistantTurnStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Protected administration and observability API. */
@SuppressWarnings("unused")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final AuthSupport auth;
  private final AdminAccessService access;
  private final AdminNotationService notation;
  private final AdminQueryService queries;
  private final AdminThemeService themes;
  private final JdbcTemplate jdbc;
  private final AssistantTurnStore assistantTurns;

  public AdminController(
      AuthSupport auth,
      AdminAccessService access,
      AdminNotationService notation,
      AdminQueryService queries,
      AdminThemeService themes,
      JdbcTemplate jdbc,
      AssistantTurnStore assistantTurns) {
    this.auth = auth;
    this.access = access;
    this.notation = notation;
    this.queries = queries;
    this.themes = themes;
    this.jdbc = jdbc;
    this.assistantTurns = assistantTurns;
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

  @DeleteMapping("/users/{id}")
  ResponseEntity<Void> deleteGuest(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String id,
      @RequestBody(required = false) ReasonRequest request) {
    access.deleteGuest(
        access.requireOwner(auth.user(token)), id, request == null ? "" : request.reason());
    return ResponseEntity.noContent().build();
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

  @GetMapping("/notation/{level}")
  Map<String, Object> notation(
      @RequestHeader("X-Auth-Token") String token, @PathVariable String level) {
    access.requireAdmin(auth.user(token));
    return notation.load(level);
  }

  @PostMapping("/notation/{level}")
  AdminNotationService.ActivationResult saveNotation(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String level,
      @Valid @RequestBody NotationSaveRequest request) {
    AdminPrincipal principal = access.requireOwner(auth.user(token));
    AdminNotationService.ActivationResult activation = notation.save(level, request.document());
    access.audit(
        principal,
        "CVS_NOTATION_UPDATED",
        "CVS_NOTATION",
        level,
        request.reason(),
        Map.of("level", level));
    return activation;
  }

  @GetMapping("/theme-profiles")
  List<ThemeState> themeProfiles(@RequestHeader("X-Auth-Token") String token) {
    access.requireAdmin(auth.user(token));
    return themes.profiles();
  }

  @PostMapping("/theme-profiles")
  ThemeState createThemeProfile(
      @RequestHeader("X-Auth-Token") String token, @RequestBody ThemeProfileRequest request) {
    AdminPrincipal principal = access.requireOwner(auth.user(token));
    ThemeState profile = themes.create(request);
    access.audit(
        principal,
        "THEME_PROFILE_CREATED",
        "THEME_PROFILE",
        profile.id(),
        "",
        Map.of("name", profile.name()));
    return profile;
  }

  @PostMapping("/theme-profiles/{id}")
  ThemeState updateThemeProfile(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String id,
      @RequestBody ThemeProfileRequest request) {
    AdminPrincipal principal = access.requireOwner(auth.user(token));
    ThemeState profile = themes.update(id, request);
    access.audit(
        principal,
        "THEME_PROFILE_UPDATED",
        "THEME_PROFILE",
        profile.id(),
        "",
        Map.of("name", profile.name()));
    return profile;
  }

  @PostMapping("/theme-profiles/{id}/delete")
  ResponseEntity<Void> deleteThemeProfile(
      @RequestHeader("X-Auth-Token") String token, @PathVariable String id) {
    AdminPrincipal principal = access.requireOwner(auth.user(token));
    themes.delete(id);
    access.audit(principal, "THEME_PROFILE_DELETED", "THEME_PROFILE", id, "", Map.of());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/theme-profiles/{id}/activate")
  ThemeState activateThemeProfile(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String id,
      @RequestParam(defaultValue = "dark") String scheme) {
    AdminPrincipal principal = access.requireOwner(auth.user(token));
    ThemeState profile = themes.activate(id, scheme);
    access.audit(
        principal,
        "THEME_PROFILE_ACTIVATED",
        "THEME_PROFILE",
        profile.id(),
        "",
        Map.of("name", profile.name(), "scheme", scheme));
    return profile;
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

  @GetMapping("/assistant/turns/{id}/provider-calls")
  List<AdminQueryService.AssistantProviderCallPrompt> assistantProviderCallPrompts(
      @RequestHeader("X-Auth-Token") String token, @PathVariable String id) {
    access.requireAdmin(auth.user(token));
    return queries.assistantProviderCallPrompts(id);
  }

  @PostMapping("/assistant/turns/{id}/cancel")
  ResponseEntity<Void> cancelAssistantTurn(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String id,
      @RequestBody(required = false) ReasonRequest request) {
    AdminPrincipal principal = access.requireOperator(auth.user(token));
    assistantTurns.requestCancellation(id);
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

  @GetMapping("/user-login-events")
  List<AdminQueryService.UserLoginEvent> userLoginEvents(
      @RequestHeader("X-Auth-Token") String token) {
    access.requireAdmin(auth.user(token));
    return queries.userLoginEvents();
  }

  @GetMapping("/landing-page-visits")
  List<AdminQueryService.LandingPageVisit> landingPageVisits(
      @RequestHeader("X-Auth-Token") String token) {
    access.requireAdmin(auth.user(token));
    return queries.landingPageVisits();
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

  public record NotationSaveRequest(Map<String, Object> document, String reason) {}
}
