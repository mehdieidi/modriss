package io.mehdieidi.modless.backend.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs the method, URI, status, and elapsed time of every backend request.
 *
 * <p>Log severity follows the response status so failed requests remain easy to identify.</p>
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

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
        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            int status = response.getStatus();
            if (status >= 500) {
                log.error("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
                        status, durationMs);
            } else if (status >= 400) {
                log.warn("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
                        status, durationMs);
            } else {
                log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
                        status, durationMs);
            }
        }
    }
}
