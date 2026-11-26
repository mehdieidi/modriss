package io.mehdieidi.modriss.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class OpenApiControllerTest {

  @TempDir Path tempDirectory;

  @Test
  void servesLocalSpecWithAssistantRoutes() throws IOException {
    Path spec = tempDirectory.resolve("openapi.yaml");
    Files.writeString(
        spec,
        """
        openapi: 3.0.3
        info:
          title: Test API
          version: '1.0'
        paths:
          /api/chatbot/sessions: {}
          /api/chatbot/sessions/{sessionId}/messages: {}
          /api/chatbot/sessions/{sessionId}/events: {}
          /api/chatbot/turns/{turnId}: {}
          /api/chatbot/turns/{turnId}/events: {}
        """);
    Map<String, Object> document = new OpenApiController(() -> spec).docs();
    Map<?, ?> paths = (Map<?, ?>) document.get("paths");

    assertEquals(
        Set.of(
            "/api/chatbot/sessions",
            "/api/chatbot/sessions/{sessionId}/messages",
            "/api/chatbot/sessions/{sessionId}/events",
            "/api/chatbot/turns/{turnId}",
            "/api/chatbot/turns/{turnId}/events"),
        paths.keySet());
  }

  @Test
  void rejectsDocsAndSwaggerUiWhenLocalSpecIsMissing() {
    Path spec = tempDirectory.resolve("openapi.yaml");
    OpenApiController controller = new OpenApiController(() -> spec);

    assertEquals(
        HttpStatus.NOT_FOUND,
        assertThrows(ResponseStatusException.class, controller::docs).getStatusCode());
    assertEquals(
        HttpStatus.NOT_FOUND,
        assertThrows(ResponseStatusException.class, controller::swaggerUi).getStatusCode());
  }
}
