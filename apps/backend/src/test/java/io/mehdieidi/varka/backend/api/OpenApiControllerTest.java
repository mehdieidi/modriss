package io.mehdieidi.varka.backend.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenApiControllerTest {

  @Test
  @SuppressWarnings("unchecked")
  void includesAssistantRoutes() {
    Map<String, Object> document = new OpenApiController().docs();
    Map<String, Object> paths = (Map<String, Object>) document.get("paths");

    assertTrue(paths.containsKey("/api/chatbot/sessions"));
    assertTrue(paths.containsKey("/api/chatbot/sessions/{sessionId}/messages"));
    assertTrue(paths.containsKey("/api/chatbot/sessions/{sessionId}/events"));
    assertTrue(paths.containsKey("/api/chatbot/turns/{turnId}"));
    assertTrue(paths.containsKey("/api/chatbot/turns/{turnId}/events"));
  }
}
