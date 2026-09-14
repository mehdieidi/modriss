package io.mehdieidi.modriss.backend.analytics;

import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Captures admin-visible request analytics for logins and public landing visits. */
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
          "CloudFront-Viewer-Country");

  private final JdbcTemplate jdbc;

  public VisitorAnalyticsService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void recordLogin(UserRecord user, HttpServletRequest request, String reportedPublicIp) {
    ClientInfo info = clientInfo(request, reportedPublicIp);
    jdbc.update(
        """
        INSERT INTO user_login_events (
          id, user_id, email_snapshot, ip_address, country, os, browser, device, user_agent,
          request_id, occurred_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID().toString(),
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
  }

  public void recordLandingVisit(
      HttpServletRequest request, String path, String referrer, String reportedPublicIp) {
    ClientInfo info = clientInfo(request, reportedPublicIp);
    jdbc.update(
        """
        INSERT INTO landing_page_visits (
          id, ip_address, country, os, browser, device, user_agent, path, referrer, request_id,
          occurred_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID().toString(),
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
  }

  @Scheduled(fixedDelayString = "${modriss.analytics.landing-cleanup-interval:PT1H}")
  public void cleanupLandingVisits() {
    jdbc.update("DELETE FROM landing_page_visits WHERE occurred_at < now() - interval '3 days'");
  }

  private ClientInfo clientInfo(HttpServletRequest request, String reportedPublicIp) {
    String userAgent = truncate(request.getHeader("User-Agent"));
    return new ClientInfo(
        truncate(clientIp(request, reportedPublicIp)),
        truncate(country(request)),
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

  private String country(HttpServletRequest request) {
    for (String header : COUNTRY_HEADERS) {
      String value = request.getHeader(header);
      if (value != null && !value.isBlank() && !"XX".equalsIgnoreCase(value.trim())) {
        return value.trim().toUpperCase(Locale.ROOT);
      }
    }
    return "";
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
