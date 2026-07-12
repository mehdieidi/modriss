package io.mehdieidi.varka.backend.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
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
    request.addHeader("User-Agent", "varka-test");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> requestIdSeenInChain = new AtomicReference<>();

    FilterChain chain =
        (req, res) -> {
          requestIdSeenInChain.set(MDC.get("requestId"));
          ((MockHttpServletResponse) res).setStatus(201);
        };

    filter.doFilter(request, response, chain);

    assertEquals("req-123", response.getHeader("X-Request-Id"));
    assertEquals("req-123", requestIdSeenInChain.get());
    assertNull(MDC.get("requestId"));
  }

  @Test
  void requestSummaryKeepsRequestIdInMdc() throws ServletException, IOException {
    var logger =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(RequestLoggingFilter.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    try {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
      request.addHeader("X-Request-Id", "req-summary");

      filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
    } finally {
      logger.detachAppender(appender);
    }

    assertEquals(1, appender.list.size());
    assertEquals("req-summary", appender.list.get(0).getMDCPropertyMap().get("requestId"));
    assertNull(MDC.get("requestId"));
  }

  @Test
  void generatesRequestIdWhenHeaderIsMissing() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> requestIdSeenInChain = new AtomicReference<>();

    FilterChain chain = (req, res) -> requestIdSeenInChain.set(MDC.get("requestId"));

    filter.doFilter(request, response, chain);

    String requestId = response.getHeader("X-Request-Id");
    assertEquals(requestId, requestIdSeenInChain.get());
    assertEquals(requestId, UUID.fromString(requestId).toString());
    assertNull(MDC.get("requestId"));
  }

  @Test
  void generatesRequestIdWhenHeaderIsBlank() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
    request.addHeader("X-Request-Id", "  ");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    String requestId = response.getHeader("X-Request-Id");
    assertEquals(requestId, UUID.fromString(requestId).toString());
    assertNull(MDC.get("requestId"));
  }

  @Test
  void cleansUpMdcWhenRequestFails() {
    var logger =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(RequestLoggingFilter.class);
    boolean wasAdditive = logger.isAdditive();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.setAdditive(false);
    logger.addAppender(appender);

    try {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
      request.addHeader("X-Request-Id", "req-failure");
      MockHttpServletResponse response = new MockHttpServletResponse();

      assertThrows(
          ServletException.class,
          () ->
              filter.doFilter(
                  request,
                  response,
                  (req, res) -> {
                    throw new ServletException("failed");
                  }));

      assertEquals("req-failure", response.getHeader("X-Request-Id"));
      assertNull(MDC.get("requestId"));
      assertEquals(1, appender.list.size());
      assertEquals(Level.ERROR, appender.list.get(0).getLevel());
      assertNull(appender.list.get(0).getThrowableProxy());
    } finally {
      logger.detachAppender(appender);
      logger.setAdditive(wasAdditive);
    }
  }
}
