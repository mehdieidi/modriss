package io.mehdieidi.modriss.backend.analytics;

import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Captures admin-visible request analytics for logins and browser page visits. */
@Service
public class VisitorAnalyticsService {

  private static final int MAX_VALUE_LENGTH = 300;
  private static final List<String> IP_HEADERS =
      List.of("CF-Connecting-IP", "True-Client-IP", "X-Real-IP", "X-Forwarded-For", "Forwarded");
  private static final List<String> COUNTRY_HEADERS =
      List.of(
          "CF-IPCountry",
          "X-Vercel-IP-Country",
          "X-Appengine-Country",
          "CloudFront-Viewer-Country",
          "X-Country-Code",
          "X-Country");
  private static final Set<String> INFRASTRUCTURE_APPS =
      Set.of("grafana", "prometheus", "dozzle", "loki", "localstack", "floci");

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final String countryLookupUrl;
  private final HttpClient countryLookupClient;
  private final ExecutorService countryLookupExecutor = Executors.newFixedThreadPool(2);
  private final ConcurrentMap<String, CompletableFuture<String>> countryLookups =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, String> countryCache = new ConcurrentHashMap<>();
  private final java.nio.file.Path caddyLogRoot;
  private volatile long caddyLogPosition;

  public VisitorAnalyticsService(
      JdbcTemplate jdbc,
      ObjectMapper objectMapper,
      @Value("${modriss.analytics.country-lookup-url:https://ipapi.co/{ip}/country/}")
          String countryLookupUrl,
      @Value("${modriss.analytics.country-lookup-timeout-ms:2000}") long countryLookupTimeoutMs,
      @Value("${modriss.analytics.caddy-log-root:}") String caddyLogRoot) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.countryLookupUrl = countryLookupUrl;
    this.countryLookupClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(countryLookupTimeoutMs)).build();
    this.caddyLogRoot =
        caddyLogRoot == null || caddyLogRoot.isBlank() ? null : java.nio.file.Path.of(caddyLogRoot);
  }

  public void recordLogin(UserRecord user, HttpServletRequest request, String reportedPublicIp) {
    ClientInfo info = clientInfo(request, reportedPublicIp, "");
    String id = UUID.randomUUID().toString();
    jdbc.update(
        """
        INSERT INTO user_login_events (
          id, user_id, email_snapshot, ip_address, country, os, browser, device, user_agent,
          request_id, occurred_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        user.id(),
        user.email(),
        info.ipAddress(),
        info.country(),
        info.os(),
        info.browser(),
        info.device(),
        info.userAgent(),
        truncate(request.getHeader("X-Request-Id")),
        Timestamp.from(Instant.now()));
    enrichCountryAsync("user_login_events", id, info.ipAddress(), info.country());
  }

  public void recordLandingVisit(
      HttpServletRequest request, String path, String referrer, String reportedPublicIp) {
    recordPageVisit(request, "landing", path, referrer, reportedPublicIp, "");
  }

  public void recordPageVisit(
      HttpServletRequest request,
      String app,
      String path,
      String referrer,
      String reportedPublicIp,
      String reportedCountry) {
    ClientInfo info = clientInfo(request, reportedPublicIp, reportedCountry);
    String id = UUID.randomUUID().toString();
    jdbc.update(
        """
        INSERT INTO landing_page_visits (
          id, app, ip_address, country, os, browser, device, user_agent, path, referrer,
          request_id, occurred_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        truncate(app),
        info.ipAddress(),
        info.country(),
        info.os(),
        info.browser(),
        info.device(),
        info.userAgent(),
        truncate(path),
        truncate(referrer),
        truncate(request.getHeader("X-Request-Id")),
        Timestamp.from(Instant.now()));
    enrichCountryAsync("landing_page_visits", id, info.ipAddress(), info.country());
  }

  @Scheduled(fixedDelayString = "${modriss.analytics.landing-cleanup-interval:PT1H}")
  public void cleanupLandingVisits() {
    jdbc.update("DELETE FROM landing_page_visits WHERE occurred_at < now() - interval '3 days'");
  }

  @Scheduled(fixedDelayString = "${modriss.analytics.country-enrichment-interval:PT30S}")
  public void enrichMissingCountries() {
    enrichMissingCountries("landing_page_visits", "occurred_at >= now() - interval '3 days'");
    enrichMissingCountries("user_login_events", "occurred_at >= now() - interval '30 days'");
  }

  private void enrichMissingCountries(String table, String retentionCondition) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT id, ip_address FROM "
                + table
                + " WHERE country = '' AND ip_address <> '' AND "
                + retentionCondition
                + " ORDER BY occurred_at DESC LIMIT 100");
    for (Map<String, Object> row : rows) {
      enrichCountryAsync(
          table, String.valueOf(row.get("id")), String.valueOf(row.get("ip_address")), "");
    }
  }

  /** Imports visits to infrastructure sites that cannot load the MODRISS browser telemetry code. */
  @Scheduled(fixedDelayString = "${modriss.analytics.caddy-log-interval:PT15S}")
  public void importCaddyInfrastructureVisits() {
    if (caddyLogRoot == null) {
      return;
    }
    java.nio.file.Path accessLog = caddyLogRoot.resolve("access.log");
    if (!java.nio.file.Files.isRegularFile(accessLog)) {
      return;
    }
    try (RandomAccessFile file = new RandomAccessFile(accessLog.toFile(), "r")) {
      if (caddyLogPosition > file.length()) {
        caddyLogPosition = 0;
      }
      file.seek(caddyLogPosition);
      String line;
      while ((line = file.readLine()) != null) {
        caddyLogPosition = file.getFilePointer();
        importCaddyLine(
            new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
      }
    } catch (IOException ex) {
      // Access-log ingestion is best effort and must never affect browser/API requests.
    }
  }

  private void importCaddyLine(String line) {
    try {
      JsonNode root = objectMapper.readTree(line);
      JsonNode request = root.path("request");
      String app = infrastructureApp(request.path("host").asText());
      String method = request.path("method").asText("");
      int status = root.path("status").asInt(0);
      String uri = request.path("uri").asText("");
      String path = uri.split("\\?", 2)[0];
      if (app.isBlank()
          || !("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method))
          || status < 200
          || status >= 400
          || !isPagePath(path)) {
        return;
      }
      String ip = firstText(request, "client_ip", "remote_ip");
      String userAgent = header(request.path("headers"), "User-Agent");
      String referrer = header(request.path("headers"), "Referer");
      Instant occurredAt =
          root.path("ts").isNumber()
              ? Instant.ofEpochMilli((long) (root.path("ts").asDouble() * 1000))
              : Instant.now();
      String id =
          UUID.nameUUIDFromBytes(("caddy:" + line).getBytes(StandardCharsets.UTF_8)).toString();
      recordCaddyPageVisit(id, app, path, referrer, ip, userAgent, occurredAt);
    } catch (RuntimeException ex) {
      // A malformed or non-Caddy line should not stop later access-log entries from importing.
    }
  }

  private void recordCaddyPageVisit(
      String id,
      String app,
      String path,
      String referrer,
      String ip,
      String userAgent,
      Instant occurredAt) {
    String normalizedIp = normalizeEdgeIp(ip);
    String safeUserAgent = truncate(userAgent);
    int inserted =
        jdbc.update(
            """
            INSERT INTO landing_page_visits (
              id, app, ip_address, country, os, browser, device, user_agent, path, referrer,
              request_id, occurred_at
            ) VALUES (?, ?, ?, '', ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """,
            id,
            app,
            normalizedIp,
            truncate(os(safeUserAgent)),
            truncate(browser(safeUserAgent)),
            truncate(device(safeUserAgent)),
            safeUserAgent,
            truncate(path),
            truncate(referrer),
            "caddy:" + id,
            Timestamp.from(occurredAt));
    if (inserted > 0) {
      enrichCountryAsync("landing_page_visits", id, normalizedIp, "");
    }
  }

  private String infrastructureApp(String host) {
    String hostname = host == null ? "" : host.toLowerCase(Locale.ROOT).split(":", 2)[0];
    String app = hostname.split("\\.", 2)[0];
    return INFRASTRUCTURE_APPS.contains(app) ? app : "";
  }

  private boolean isPagePath(String path) {
    if (path == null || path.isBlank() || path.length() > MAX_VALUE_LENGTH) {
      return false;
    }
    if ("/".equals(path) || path.endsWith("/")) {
      return true;
    }
    String lastSegment = path.substring(path.lastIndexOf('/') + 1);
    return !lastSegment.contains(".")
        && !path.startsWith("/api/")
        && !path.startsWith("/api")
        && !path.startsWith("/actuator/")
        && !path.startsWith("/public/")
        && !path.startsWith("/static/")
        && !path.endsWith("favicon");
  }

  private String firstText(JsonNode node, String... fields) {
    for (String field : fields) {
      String value = node.path(field).asText("");
      if (!value.isBlank()) {
        return value;
      }
    }
    return "";
  }

  private String header(JsonNode headers, String name) {
    JsonNode value = headers.path(name);
    if (value.isArray() && !value.isEmpty()) {
      return value.get(0).asText("");
    }
    return value.asText("");
  }

  private String normalizeEdgeIp(String value) {
    if (value == null) {
      return "";
    }
    String ip = value.trim();
    if (ip.startsWith("[") && ip.contains("]")) {
      ip = ip.substring(1, ip.indexOf(']'));
    } else if (ip.indexOf(':') == ip.lastIndexOf(':')
        && ip.lastIndexOf(':') > ip.lastIndexOf('.')) {
      ip = ip.substring(0, ip.lastIndexOf(':'));
    }
    return ip;
  }

  private void enrichCountryAsync(String table, String id, String ip, String existingCountry) {
    if (!existingCountry.isBlank()) {
      return;
    }
    String normalizedIp = normalizePublicIp(ip);
    if (normalizedIp.isBlank()) {
      return;
    }
    String cachedCountry = countryCache.get(normalizedIp);
    if (cachedCountry != null) {
      updateCountry(table, id, cachedCountry);
      return;
    }
    CompletableFuture<String> lookup =
        countryLookups.computeIfAbsent(
            normalizedIp,
            key ->
                CompletableFuture.supplyAsync(() -> lookupCountry(key), countryLookupExecutor)
                    .whenComplete(
                        (country, error) -> {
                          countryCache.put(key, country == null ? "" : country);
                          countryLookups.remove(key);
                        }));
    lookup.thenAccept(country -> updateCountry(table, id, country));
  }

  private void updateCountry(String table, String id, String country) {
    if (country == null || country.isBlank()) {
      return;
    }
    jdbc.update("UPDATE " + table + " SET country = ? WHERE id = ? AND country = ''", country, id);
  }

  private String lookupCountry(String ip) {
    try {
      String url = countryLookupUrl.replace("{ip}", ip);
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(2))
              .header("Accept", "text/plain")
              .GET()
              .build();
      HttpResponse<String> response =
          countryLookupClient.send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() >= 200 && response.statusCode() < 300
          ? normalizeCountry(response.body())
          : "";
    } catch (IOException | InterruptedException | RuntimeException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return "";
    }
  }

  @PreDestroy
  public void shutdownCountryLookup() {
    countryLookupExecutor.shutdownNow();
  }

  private ClientInfo clientInfo(
      HttpServletRequest request, String reportedPublicIp, String reportedCountry) {
    String userAgent = truncate(request.getHeader("User-Agent"));
    return new ClientInfo(
        truncate(clientIp(request, reportedPublicIp)),
        truncate(country(request, reportedCountry)),
        truncate(os(userAgent)),
        truncate(browser(userAgent)),
        truncate(device(userAgent)),
        userAgent);
  }

  private String clientIp(HttpServletRequest request, String reportedPublicIp) {
    String normalizedReportedIp = normalizePublicIp(reportedPublicIp);
    if (!normalizedReportedIp.isBlank()) {
      return normalizedReportedIp;
    }
    String headerIp = "";
    boolean foundHeader = false;
    for (int index = 0; index < IP_HEADERS.size() && !foundHeader; index++) {
      String header = IP_HEADERS.get(index);
      String value = request.getHeader(header);
      if (value == null || value.isBlank()) {
        continue;
      }
      if ("Forwarded".equalsIgnoreCase(header)) {
        String forwarded = forwardedFor(value);
        if (!forwarded.isBlank()) {
          headerIp = forwarded;
        } else {
          headerIp = value.split(",")[0].trim();
        }
      } else {
        headerIp = value.split(",")[0].trim();
      }
      foundHeader = true;
    }
    if (!headerIp.isBlank()) {
      return headerIp;
    }
    return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
  }

  private String normalizePublicIp(String value) {
    if (value == null) {
      return "";
    }
    String ip = value.trim();
    if (ip.length() > 45
        || !ip.matches("[0-9a-fA-F:.]+")
        || !(ip.contains(".") || ip.contains(":"))) {
      return "";
    }
    return isPrivateIp(ip) ? "" : ip;
  }

  private boolean isPrivateIp(String ip) {
    String lower = ip.toLowerCase(Locale.ROOT);
    if (lower.equals("::1")
        || lower.startsWith("fe80:")
        || lower.startsWith("fc")
        || lower.startsWith("fd")) {
      return true;
    }
    String[] parts = ip.split("\\.");
    if (parts.length != 4) {
      return false;
    }
    try {
      int first = Integer.parseInt(parts[0]);
      int second = Integer.parseInt(parts[1]);
      if (first == 10 || first == 127 || first == 0 || first == 169 && second == 254) {
        return true;
      }
      if (first == 172 && second >= 16 && second <= 31) {
        return true;
      }
      if (first == 192 && second == 168) {
        return true;
      }
      return first == 100 && second >= 64 && second <= 127;
    } catch (NumberFormatException ex) {
      return true;
    }
  }

  private String forwardedFor(String value) {
    for (String part : value.split(";")) {
      String trimmed = part.trim();
      if (trimmed.toLowerCase(Locale.ROOT).startsWith("for=")) {
        return trimmed.substring(4).replace("\"", "").trim();
      }
    }
    return "";
  }

  private String country(HttpServletRequest request, String reportedCountry) {
    for (String header : COUNTRY_HEADERS) {
      String value = request.getHeader(header);
      if (value != null && !value.isBlank() && !"XX".equalsIgnoreCase(value.trim())) {
        return normalizeCountry(value);
      }
    }
    return normalizeCountry(reportedCountry);
  }

  private String normalizeCountry(String value) {
    if (value == null) {
      return "";
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    return normalized.matches("[A-Z]{2}") && !"XX".equals(normalized) ? normalized : "";
  }

  private String os(String userAgent) {
    String ua = userAgent.toLowerCase(Locale.ROOT);
    if (ua.contains("windows")) {
      return "Windows";
    }
    if (ua.contains("android")) {
      return "Android";
    }
    if (ua.contains("iphone") || ua.contains("ipad")) {
      return "iOS";
    }
    if (ua.contains("mac os") || ua.contains("macintosh")) {
      return "macOS";
    }
    if (ua.contains("linux")) {
      return "Linux";
    }
    return "";
  }

  private String browser(String userAgent) {
    String ua = userAgent.toLowerCase(Locale.ROOT);
    if (ua.contains("edg/")) {
      return "Edge";
    }
    if (ua.contains("opr/") || ua.contains("opera")) {
      return "Opera";
    }
    if (ua.contains("chrome/") || ua.contains("crios/")) {
      return "Chrome";
    }
    if (ua.contains("firefox/") || ua.contains("fxios/")) {
      return "Firefox";
    }
    if (ua.contains("safari/")) {
      return "Safari";
    }
    return "";
  }

  private String device(String userAgent) {
    String ua = userAgent.toLowerCase(Locale.ROOT);
    if (ua.contains("mobile") || ua.contains("iphone") || ua.contains("android")) {
      return "Mobile";
    }
    if (ua.contains("ipad") || ua.contains("tablet")) {
      return "Tablet";
    }
    return "Desktop";
  }

  private String truncate(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.length() <= MAX_VALUE_LENGTH ? trimmed : trimmed.substring(0, MAX_VALUE_LENGTH);
  }

  private record ClientInfo(
      String ipAddress,
      String country,
      String os,
      String browser,
      String device,
      String userAgent) {}
}
