package io.mehdieidi.modriss.backend.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds a request correlation id and logs access information for backend requests. */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
  private static final String REQUEST_ID_HEADER = "X-Request-Id";
  private static final String REQUEST_ID_MDC_KEY = "requestId";

  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return true;
  }

  /**
   * Executes the request and records its final response status and duration.
   *
   * @param request current HTTP request
   * @param response current HTTP response
   * @param filterChain remaining servlet filter chain
   * @throws ServletException if request processing fails in the servlet layer
   * @throws IOException if request processing fails during I/O
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = requestId(request);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    applySecurityHeaders(response);
    MDC.put(REQUEST_ID_MDC_KEY, requestId);

    long started = System.nanoTime();
    boolean failed = false;
    try {
      filterChain.doFilter(request, response);
    } catch (ServletException | IOException | RuntimeException ex) {
      failed = true;
      throw ex;
    } finally {
      long durationMs = (System.nanoTime() - started) / 1_000_000;
      int status = failed ? 500 : response.getStatus();
      String userAgent = sanitize(request.getHeader("User-Agent"));
      try {
        requestLog(status)
            .addKeyValue("method", request.getMethod())
            .addKeyValue("path", request.getRequestURI())
            .addKeyValue("status", status)
            .addKeyValue("durationMs", durationMs)
            .addKeyValue("remoteAddr", request.getRemoteAddr())
            .addKeyValue("userAgent", userAgent)
            .log("request completed");
      } finally {
        MDC.clear();
      }
    }
  }

  private LoggingEventBuilder requestLog(int status) {
    if (status >= 500) {
      return log.atError();
    }
    if (status >= 400) {
      return log.atWarn();
    }
    return log.atInfo();
  }

  private String requestId(HttpServletRequest request) {
    String requestId = request.getHeader(REQUEST_ID_HEADER);
    return StringUtils.hasText(requestId) ? requestId : UUID.randomUUID().toString();
  }

  private void applySecurityHeaders(HttpServletResponse response) {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
    response.setHeader(
        "Content-Security-Policy",
        "default-src 'self'; "
            + "script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; "
            + "font-src 'self'; "
            + "connect-src 'self'; "
            + "base-uri 'self'; "
            + "frame-ancestors 'none'; "
            + "object-src 'none'");
  }

  private String sanitize(String value) {
    if (!StringUtils.hasText(value)) {
      return "-";
    }
    return value.length() <= 120 ? value : value.substring(0, 117) + "...";
  }
}
