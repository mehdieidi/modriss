package io.mehdieidi.modriss.backend.admin;

import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Administration service for frontend theme profiles. */
@Service
public class AdminThemeService {

  private static final Pattern TOKEN_NAME = Pattern.compile("^--[a-z0-9][a-z0-9-]*$");

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public AdminThemeService(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public ThemeState currentTheme(String scheme) {
    try {
      return jdbc.queryForObject(
          """
SELECT p.id, p.name, p.description, p.tokens::text, p.built_in, p.created_at, p.updated_at,
  p.id = s.active_light_profile_id AS active_light,
  p.id = s.active_dark_profile_id AS active_dark,
  true AS active
FROM theme_settings s
JOIN theme_profiles p ON p.id =
  CASE WHEN ? = 'light' THEN s.active_light_profile_id ELSE s.active_dark_profile_id END
WHERE s.id = true
""",
          this::profileRow,
          normalizeScheme(scheme));
    } catch (EmptyResultDataAccessException ex) {
      throw new PlatformException(404, "No active theme profile is configured.");
    }
  }

  public List<ThemeState> profiles() {
    ActiveProfiles active = activeProfiles();
    return jdbc.query(
        """
        SELECT id, name, description, tokens::text, built_in, created_at, updated_at,
          id = ? AS active_light,
          id = ? AS active_dark,
          (id = ? OR id = ?) AS active
        FROM theme_profiles
        ORDER BY built_in DESC, name
        """,
        this::profileRow,
        active.lightId(),
        active.darkId(),
        active.lightId(),
        active.darkId());
  }

  public ThemeState create(ThemeProfileRequest request) {
    Map<String, String> tokens = validateTokens(request.tokens());
    String id = UUID.randomUUID().toString();
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        """
        INSERT INTO theme_profiles(id, name, description, tokens, built_in, created_at, updated_at)
        VALUES (?, ?, ?, ?::jsonb, false, ?, ?)
        """,
        id,
        requireName(request.name()),
        safe(request.description()),
        json(tokens),
        now,
        now);
    return profile(id);
  }

  public ThemeState update(String id, ThemeProfileRequest request) {
    ThemeState current = profile(id);
    if (current.builtIn()) {
      throw new PlatformException(409, "Built-in theme profiles cannot be edited.");
    }
    Map<String, String> tokens = validateTokens(request.tokens());
    jdbc.update(
        """
        UPDATE theme_profiles
        SET name = ?, description = ?, tokens = ?::jsonb, updated_at = ?
        WHERE id = ?
        """,
        requireName(request.name()),
        safe(request.description()),
        json(tokens),
        Timestamp.from(Instant.now()),
        id);
    return profile(id);
  }

  public void delete(String id) {
    ThemeState current = profile(id);
    if (current.builtIn()) {
      throw new PlatformException(409, "Built-in theme profiles cannot be deleted.");
    }
    if (current.activeLight() || current.activeDark()) {
      throw new PlatformException(409, "The active theme profile cannot be deleted.");
    }
    jdbc.update("DELETE FROM theme_profiles WHERE id = ?", id);
  }

  public ThemeState activate(String id, String scheme) {
    profile(id);
    String normalizedScheme = normalizeScheme(scheme);
    String column =
        "light".equals(normalizedScheme) ? "active_light_profile_id" : "active_dark_profile_id";
    jdbc.update(
        "UPDATE theme_settings SET " + column + " = ?, updated_at = ? WHERE id = true",
        id,
        Timestamp.from(Instant.now()));
    return profile(id);
  }

  private ActiveProfiles activeProfiles() {
    try {
      return jdbc.queryForObject(
          """
          SELECT active_light_profile_id, active_dark_profile_id
          FROM theme_settings
          WHERE id = true
          """,
          (rs, rowNum) ->
              new ActiveProfiles(
                  rs.getString("active_light_profile_id"), rs.getString("active_dark_profile_id")));
    } catch (EmptyResultDataAccessException ex) {
      return new ActiveProfiles("", "");
    }
  }

  private ThemeState profile(String id) {
    ActiveProfiles active = activeProfiles();
    try {
      return jdbc.queryForObject(
          """
          SELECT id, name, description, tokens::text, built_in, created_at, updated_at,
            id = ? AS active_light,
            id = ? AS active_dark,
            (id = ? OR id = ?) AS active
          FROM theme_profiles
          WHERE id = ?
          """,
          this::profileRow,
          active.lightId(),
          active.darkId(),
          active.lightId(),
          active.darkId(),
          id);
    } catch (EmptyResultDataAccessException ex) {
      throw new PlatformException(404, "Theme profile was not found.");
    }
  }

  private ThemeState profileRow(ResultSet rs, int rowNum) throws SQLException {
    return new ThemeState(
        rs.getString("id"),
        rs.getString("name"),
        rs.getString("description"),
        tokens(rs.getString("tokens")),
        rs.getBoolean("built_in"),
        rs.getBoolean("active"),
        rs.getBoolean("active_light"),
        rs.getBoolean("active_dark"),
        rs.getTimestamp("created_at").toInstant().toString(),
        rs.getTimestamp("updated_at").toInstant().toString());
  }

  private Map<String, String> validateTokens(Map<String, String> tokens) {
    if (tokens == null || tokens.isEmpty()) {
      throw new PlatformException(400, "Theme profile must define at least one color token.");
    }
    Map<String, String> normalized = new LinkedHashMap<>();
    tokens.forEach(
        (name, value) -> {
          String key = safe(name).toLowerCase(Locale.ROOT);
          String color = safe(value);
          if (!TOKEN_NAME.matcher(key).matches()) {
            throw new PlatformException(400, "Invalid theme token name: " + name);
          }
          if (color.isBlank() || color.length() > 160 || hasUnsafeCssCharacters(color)) {
            throw new PlatformException(400, "Invalid value for theme token: " + key);
          }
          normalized.put(key, color);
        });
    return normalized;
  }

  private boolean hasUnsafeCssCharacters(String value) {
    return value.indexOf(';') >= 0
        || value.indexOf('{') >= 0
        || value.indexOf('}') >= 0
        || value.indexOf('<') >= 0
        || value.indexOf('>') >= 0;
  }

  private String requireName(String value) {
    String name = safe(value);
    if (name.isBlank()) {
      throw new PlatformException(400, "Theme profile name is required.");
    }
    if (name.length() > 80) {
      throw new PlatformException(400, "Theme profile name is too long.");
    }
    return name;
  }

  private Map<String, String> tokens(String json) {
    try {
      return mapper.readValue(json, new TypeReference<>() {});
    } catch (JacksonException ex) {
      throw new PlatformException(500, "Could not read theme profile tokens.");
    }
  }

  private String json(Map<String, String> value) {
    try {
      return mapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (JacksonException ex) {
      throw new PlatformException(500, "Could not serialize theme profile tokens.");
    }
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private static String normalizeScheme(String value) {
    return "light".equals(safe(value).toLowerCase(Locale.ROOT)) ? "light" : "dark";
  }

  public record ThemeState(
      String id,
      String name,
      String description,
      Map<String, String> tokens,
      boolean builtIn,
      boolean active,
      boolean activeLight,
      boolean activeDark,
      String createdAt,
      String updatedAt) {}

  public record ThemeProfileRequest(String name, String description, Map<String, String> tokens) {}

  private record ActiveProfiles(String lightId, String darkId) {}
}
