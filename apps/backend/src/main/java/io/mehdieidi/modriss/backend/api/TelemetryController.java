package io.mehdieidi.modriss.backend.api;

import io.mehdieidi.modriss.backend.analytics.VisitorAnalyticsService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Accepts low-volume browser telemetry for frontend/admin/landing observability. */
@SuppressWarnings("unused")
@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

  private static final Logger log = LoggerFactory.getLogger(TelemetryController.class);

  private final MeterRegistry meters;
  private final VisitorAnalyticsService analytics;

  public TelemetryController(MeterRegistry meters, VisitorAnalyticsService analytics) {
    this.meters = meters;
    this.analytics = analytics;
  }

  @PostMapping("/frontend")
  ResponseEntity<Void> frontend(
      @Valid @RequestBody FrontendTelemetryEvent event, HttpServletRequest request) {
    String app = safeTag(event.app());
    String kind = safeTag(event.kind());
    meters.counter("modriss.frontend.telemetry.events", "app", app, "kind", kind).increment();
    log.warn(
        "frontendTelemetry app={} kind={} message={} url={} durationMs={} userAgent={}",
        app,
        kind,
        event.message(),
        event.url(),
        event.durationMs(),
        truncate(request.getHeader("User-Agent"), 180));
    if ("landing".equals(app) && "page_view".equals(kind)) {
      Map<String, String> attributes = event.attributes();
      analytics.recordLandingVisit(
          request,
          event.url(),
          attributes == null ? "" : attributes.getOrDefault("referrer", ""),
          attributes == null ? "" : attributes.getOrDefault("publicIp", ""));
    }
    return ResponseEntity.accepted().build();
  }

  private static String safeTag(String value) {
    String normalized = value == null ? "unknown" : value.trim().toLowerCase();
    if (!normalized.matches("[a-z0-9_.-]{1,40}")) {
      return "unknown";
    }
    return normalized;
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return "";
    }
    return value.length() <= max ? value : value.substring(0, max);
  }

  public record FrontendTelemetryEvent(
      @NotBlank @Size(max = 40) String app,
      @NotBlank @Size(max = 40) String kind,
      @Size(max = 300) String message,
      @Size(max = 300) String url,
      Long durationMs,
      @Size(max = 20) Map<@Size(max = 40) String, @Size(max = 120) String> attributes,
      Instant occurredAt) {}
}
