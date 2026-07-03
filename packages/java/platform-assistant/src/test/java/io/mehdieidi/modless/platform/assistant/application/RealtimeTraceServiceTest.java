package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.assistant.domain.AssistantWorkflowState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RealtimeTraceServiceTest {

  private final List<Event> events = new ArrayList<>();
  private final RealtimeTraceService service =
      new RealtimeTraceService(
          (sessionId, type, payload) -> events.add(new Event(sessionId, type, payload)));

  @Test
  void publishesStartedProgressPreviewAssistantAndTerminalEvents() {
    service.started("session", "turn-1", "thread-1", "pim", "2026-07-03T12:00:00Z");
    service.progress("session", "turn-1", "PLANNING", "Drafting");
    service.modelPreview("session", Map.of("operationCount", 2));
    service.assistantMessage("session", "done");
    service.terminal("session", AssistantWorkflowState.APPLIED, "model-1", 3L, null);

    assertEquals(
        List.of(
            "assistant.trace.started",
            "assistant.progress",
            "assistant.trace.step",
            "assistant.model.preview",
            "chat.assistant",
            "assistant.turn.completed"),
        events.stream().map(Event::type).toList());
    assertTrue(payload(0).containsKey("deadlineAt"));
    assertEquals("PLANNING", payload(1).get("stage"));
    assertEquals("turn-1", payload(2).get("turnId"));
    assertEquals(2, payload(3).get("operationCount"));
    assertEquals("APPLIED", payload(5).get("workflowState"));
    assertEquals(3L, payload(5).get("revision"));
  }

  @Test
  void failedTerminalUsesFailedEventTypeAndMessage() {
    service.terminal("session", AssistantWorkflowState.FAILED, "", null, "Provider timed out");

    assertEquals("assistant.turn.failed", events.get(0).type());
    assertEquals("FAILED", payload(0).get("workflowState"));
    assertEquals("Provider timed out", payload(0).get("message"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> payload(int index) {
    return (Map<String, Object>) events.get(index).payload();
  }

  private record Event(String sessionId, String type, Object payload) {}
}
