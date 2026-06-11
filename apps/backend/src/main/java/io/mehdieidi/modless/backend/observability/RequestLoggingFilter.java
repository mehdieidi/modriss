package io.mehdieidi.modless.backend.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds a request correlation id and logs access information for backend requests.
 *
 * <p>Health-check endpoints are skipped to keep routine probes out of the application log.</p>
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_MDC_KEY = "requestId";
    private static final Duration SLOW_REQUEST_THRESHOLD = Duration.ofSeconds(1);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && (uri.startsWith("/actuator/health") || "/api/health".equals(uri));
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    /**
     * Executes the request and records its final response status and duration.
     *
     * @param request     current HTTP request
     * @param response    current HTTP response
     * @param filterChain remaining servlet filter chain
     * @throws ServletException if request processing fails in the servlet layer
     * @throws IOException      if request processing fails during I/O
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = requestId(request);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);

        long started = System.nanoTime();
        Exception failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            failure = ex;
            throw ex;
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            int status = failure == null ? response.getStatus() : 500;
            String userAgent = sanitize(request.getHeader("User-Agent"));
            String message = "method={} path={} status={} durationMs={} remoteAddr={} userAgent={}";
            if (failure != null || status >= 500) {
                log.error(message, request.getMethod(), request.getRequestURI(), status,
                        durationMs, request.getRemoteAddr(), userAgent, failure);
            } else if (status >= 400 || durationMs >= SLOW_REQUEST_THRESHOLD.toMillis()) {
                log.warn(message, request.getMethod(), request.getRequestURI(), status,
                        durationMs, request.getRemoteAddr(), userAgent);
            } else {
                log.info(message, request.getMethod(), request.getRequestURI(), status,
                        durationMs, request.getRemoteAddr(), userAgent);
            }
        }
    }

    private String requestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        return StringUtils.hasText(requestId) ? requestId : UUID.randomUUID().toString();
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        return value.length() <= 120 ? value : value.substring(0, 117) + "...";
    }
}
