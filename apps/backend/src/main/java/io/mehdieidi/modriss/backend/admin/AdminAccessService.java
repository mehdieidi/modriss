package io.mehdieidi.modriss.backend.admin;

import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;

/** Authorizes and audits administrative access. */
@Service
public class AdminAccessService {

  private final JdbcTemplate jdbc;
  private final AdminProperties properties;

  public AdminAccessService(JdbcTemplate jdbc, AdminProperties properties) {
    this.jdbc = jdbc;
    this.properties = properties;
  }

  /** Rejects disabled users before normal endpoints handle the request. */
  public void requireEnabled(UserRecord user) {
    if (isDisabled(user.id())) {
      throw new PlatformException(403, "This account has been disabled.");
    }
  }

  /** Requires any admin workspace role. */
  public AdminPrincipal requireAdmin(UserRecord user) {
    requireEnabled(user);
    List<String> roles = roles(user);
    if (roles.isEmpty()) {
      throw new PlatformException(403, "Administrator access is required.");
    }
    return new AdminPrincipal(user, roles);
  }

  /** Requires a role capable of making changes. */
  public AdminPrincipal requireOperator(UserRecord user) {
    AdminPrincipal principal = requireAdmin(user);
    if (!principal.hasAny("ADMIN", "OPERATOR")) {
      throw new PlatformException(403, "Operator access is required.");
    }
    return principal;
  }

  /** Requires the highest-privilege administrator role. */
  public AdminPrincipal requireOwner(UserRecord user) {
    AdminPrincipal principal = requireAdmin(user);
    if (!principal.hasAny("ADMIN")) {
      throw new PlatformException(403, "Administrator owner access is required.");
    }
    return principal;
  }

  public List<String> roles(UserRecord user) {
    return jdbc.queryForList(
        "SELECT role FROM admin_roles WHERE user_id = ? ORDER BY role", String.class, user.id());
  }

  public void bootstrapFirstAdmin(UserRecord user, String suppliedToken) {
    requireEnabled(user);
    if (!properties.bootstrapEnabled()) {
      throw new PlatformException(403, "Administrator bootstrap is disabled.");
    }
    if (properties.bootstrapToken().isBlank()) {
      throw new PlatformException(403, "Administrator bootstrap token is not configured.");
    }
    if (!constantTimeEquals(properties.bootstrapToken(), safe(suppliedToken))) {
      throw new PlatformException(403, "Administrator bootstrap token is invalid.");
    }
    boolean configuredEmail =
        properties.bootstrapEmails().stream()
            .map(AdminAccessService::normalizeEmail)
            .anyMatch(email -> email.equals(normalizeEmail(user.email())));
    if (!configuredEmail) {
      throw new PlatformException(403, "This account is not allowed to bootstrap administration.");
    }
    Integer adminRoleCount = jdbc.queryForObject("SELECT count(*) FROM admin_roles", Integer.class);
    if (adminRoleCount != null && adminRoleCount > 0) {
      throw new PlatformException(409, "Administrator bootstrap has already been completed.");
    }
    jdbc.update(
        "INSERT INTO admin_roles(user_id, role, granted_by, granted_at) VALUES (?, 'ADMIN', NULL,"
            + " ?)",
        user.id(),
        Timestamp.from(Instant.now()));
    jdbc.update(
        """
        INSERT INTO admin_audit_events(id, actor_id, action, target_type, target_id, reason,
          details, request_id, created_at)
        VALUES (?, ?, 'ADMIN_BOOTSTRAPPED', 'USER', ?, ?, ?::jsonb, ?, ?)
        """,
        UUID.randomUUID().toString(),
        user.id(),
        user.id(),
        "One-time bootstrap administrator grant.",
        json(Map.of("role", "ADMIN", "bootstrapEnabled", true)),
        MDC.get("requestId"),
        Timestamp.from(Instant.now()));
  }

  public boolean isDisabled(String userId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM disabled_users WHERE user_id = ?", Integer.class, userId);
    return count != null && count > 0;
  }

  public void grantRole(AdminPrincipal actor, String userId, String role, String reason) {
    requireOwner(actor.user());
    rejectGuestAdministration(userId);
    String normalized = normalizeRole(role);
    jdbc.update(
        """
        INSERT INTO admin_roles(user_id, role, granted_by, granted_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (user_id, role) DO UPDATE SET granted_by = EXCLUDED.granted_by,
          granted_at = EXCLUDED.granted_at
        """,
        userId,
        normalized,
        actor.user().id(),
        Timestamp.from(Instant.now()));
    audit(actor, "ADMIN_ROLE_GRANTED", "USER", userId, reason, Map.of("role", normalized));
  }

  public void revokeRole(AdminPrincipal actor, String userId, String role, String reason) {
    requireOwner(actor.user());
    String normalized = normalizeRole(role);
    if (actor.user().id().equals(userId) && "ADMIN".equals(normalized)) {
      throw new PlatformException(409, "Administrators cannot revoke their own ADMIN role.");
    }
    jdbc.update("DELETE FROM admin_roles WHERE user_id = ? AND role = ?", userId, normalized);
    audit(actor, "ADMIN_ROLE_REVOKED", "USER", userId, reason, Map.of("role", normalized));
  }

  public void disableUser(AdminPrincipal actor, String userId, String reason) {
    requireOwner(actor.user());
    if (actor.user().id().equals(userId)) {
      throw new PlatformException(409, "Administrators cannot disable their own account.");
    }
    jdbc.update(
        """
        INSERT INTO disabled_users(user_id, disabled_by, reason, disabled_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (user_id) DO UPDATE SET disabled_by = EXCLUDED.disabled_by,
          reason = EXCLUDED.reason, disabled_at = EXCLUDED.disabled_at
        """,
        userId,
        actor.user().id(),
        safe(reason),
        Timestamp.from(Instant.now()));
    jdbc.update("DELETE FROM auth_sessions WHERE user_id = ?", userId);
    audit(actor, "USER_DISABLED", "USER", userId, reason, Map.of());
  }

  public void enableUser(AdminPrincipal actor, String userId, String reason) {
    requireOwner(actor.user());
    jdbc.update("DELETE FROM disabled_users WHERE user_id = ?", userId);
    audit(actor, "USER_ENABLED", "USER", userId, reason, Map.of());
  }

  public void revokeSessions(AdminPrincipal actor, String userId, String reason) {
    requireOperator(actor.user());
    int count = jdbc.update("DELETE FROM auth_sessions WHERE user_id = ?", userId);
    audit(actor, "USER_SESSIONS_REVOKED", "USER", userId, reason, Map.of("count", count));
  }

  /** Permanently removes a guest account and the projects it owns. */
  @Transactional
  public void deleteGuest(AdminPrincipal actor, String userId, String reason) {
    requireOwner(actor.user());
    if (actor.user().id().equals(userId)) {
      throw new PlatformException(409, "Administrators cannot delete their own account.");
    }
    Integer guest =
        jdbc.queryForObject(
            "SELECT count(*) FROM guest_accounts WHERE user_id = ?", Integer.class, userId);
    if (guest == null || guest == 0) {
      throw new PlatformException(400, "Only guest accounts can be deleted from this endpoint.");
    }
    jdbc.update("DELETE FROM projects WHERE owner_user_id = ?", userId);
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
    audit(actor, "GUEST_DELETED", "GUEST", userId, reason, Map.of("ownedProjectsDeleted", true));
  }

  public void audit(
      AdminPrincipal actor,
      String action,
      String targetType,
      String targetId,
      String reason,
      Map<String, ?> details) {
    jdbc.update(
        """
        INSERT INTO admin_audit_events(id, actor_id, action, target_type, target_id, reason,
          details, request_id, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
        """,
        UUID.randomUUID().toString(),
        actor.user().id(),
        action,
        targetType,
        targetId,
        safe(reason),
        json(details),
        MDC.get("requestId"),
        Timestamp.from(Instant.now()));
  }

  private String normalizeRole(String role) {
    String normalized = safe(role).toUpperCase(Locale.ROOT);
    if (!List.of("ADMIN", "OPERATOR", "VIEWER").contains(normalized)) {
      throw new PlatformException(400, "Unsupported admin role.");
    }
    return normalized;
  }

  private void rejectGuestAdministration(String userId) {
    Integer guest =
        jdbc.queryForObject(
            "SELECT count(*) FROM guest_accounts WHERE user_id = ?", Integer.class, userId);
    if (guest != null && guest > 0) {
      throw new PlatformException(400, "Guest accounts cannot receive administrator roles.");
    }
  }

  private static String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private static boolean constantTimeEquals(String expected, String supplied) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
  }

  private String json(Map<String, ?> value) {
    try {
      return new tools.jackson.databind.ObjectMapper()
          .writeValueAsString(value == null ? Map.of() : value);
    } catch (JacksonException ex) {
      throw new PlatformException(500, "Could not serialize audit event details.");
    }
  }

  /** Authenticated admin user and granted roles. */
  public static final class AdminPrincipal {
    private final UserRecord user;
    private final String[] roles;

    public AdminPrincipal(UserRecord user, List<String> roles) {
      this.user = user;
      this.roles = roles == null ? new String[0] : roles.toArray(String[]::new);
    }

    public UserRecord user() {
      return user;
    }

    public List<String> roles() {
      return List.of(roles);
    }

    public boolean hasAny(String... required) {
      for (String role : required) {
        for (String granted : roles) {
          if (granted.equals(role)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
