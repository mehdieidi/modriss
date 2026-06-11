package io.mehdieidi.modless.backend.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesIncomingRequestIdAndCleansUpMdc() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        request.addHeader("X-Request-Id", "req-123");
        request.addHeader("User-Agent", "modless-test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdSeenInChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            requestIdSeenInChain.set(MDC.get("requestId"));
            ((MockHttpServletResponse) res).setStatus(201);
        };

        filter.doFilter(request, response, chain);

        assertEquals("req-123", response.getHeader("X-Request-Id"));
        assertEquals("req-123", requestIdSeenInChain.get());
        assertNull(MDC.get("requestId"));
    }

    @Test
    void skipsHealthChecks() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> chainInvoked = new AtomicReference<>(false);

        FilterChain chain = (req, res) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertTrue(chainInvoked.get());
        assertNull(response.getHeader("X-Request-Id"));
        assertNull(MDC.get("requestId"));
    }
}
