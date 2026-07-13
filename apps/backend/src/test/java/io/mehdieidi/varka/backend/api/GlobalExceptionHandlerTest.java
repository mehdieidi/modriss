package io.mehdieidi.varka.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.varka.platform.kernel.PlatformException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void missingStaticResourceMapsToNotFound() {
    MDC.put("requestId", "req-not-found");

    var response =
        handler.missingResource(
            new NoResourceFoundException(
                HttpMethod.POST, "api/chatbot/sessions/messages", "No matching resource"));

    assertEquals(404, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals("Resource not found.", response.getBody().message());
    assertEquals("req-not-found", response.getBody().errorId());
  }

  @Test
  void platformServerErrorHidesInternalDetails() {
    MDC.put("requestId", "req-platform-500");

    var response =
        handler.platform(
            new PlatformException(
                500, "CIM-to-PIM ETL failed: internal classpath:/secret/path details"));

    assertEquals(500, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals("An unexpected server error occurred.", response.getBody().message());
    assertEquals("req-platform-500", response.getBody().errorId());
    assertTrue(response.getBody().issues().isEmpty());
  }

  @Test
  void platformClientErrorKeepsActionableMessage() {
    MDC.put("requestId", "req-platform-400");

    var response = handler.platform(new PlatformException(400, "Project id is required."));

    assertEquals(400, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals("Project id is required.", response.getBody().message());
    assertEquals("req-platform-400", response.getBody().errorId());
  }

  @Test
  void unexpectedExceptionReturnsGenericMessageAndErrorId() {
    MDC.put("requestId", "req-unexpected");

    var response = handler.unexpected(new IllegalStateException("database password leaked"));

    assertEquals(500, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals("An unexpected server error occurred.", response.getBody().message());
    assertEquals("req-unexpected", response.getBody().errorId());
    assertFalse(response.getBody().message().contains("password"));
  }
}
