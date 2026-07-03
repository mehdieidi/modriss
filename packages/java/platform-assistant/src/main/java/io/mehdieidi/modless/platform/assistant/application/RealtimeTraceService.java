package io.mehdieidi.modless.platform.assistant.application;

import io.mehdieidi.modless.platform.assistant.domain.AssistantWorkflowState;
import io.mehdieidi.modless.platform.assistant.spi.AssistantRealtimePublisher;
import java.util.LinkedHashMap;
import java.util.Map;

/** Publishes user-visible assistant trace events without exposing private reasoning. */
public class RealtimeTraceService {

  private final AssistantRealtimePublisher realtime;

  public RealtimeTraceService(AssistantRealtimePublisher realtime) {
    this.realtime = realtime;
  }

  public void started(
      String sessionId, String turnId, String traceSessionId, String level, String deadlineAt) {
    publish(
        sessionId,
        "assistant.trace.started",
        Map.of(
            "turnId",
            safe(turnId),
            "sessionId",
            safe(traceSessionId),
            "level",
            safe(level),
            "deadlineAt",
            safe(deadlineAt)));
  }

  public void progress(String sessionId, String turnId, String stage, String message) {
    publish(
        sessionId, "assistant.progress", Map.of("stage", safe(stage), "message", safe(message)));
    publish(
        sessionId,
        "assistant.trace.step",
        Map.of("stage", safe(stage), "message", safe(message), "turnId", safe(turnId)));
  }

  public void modelPreview(String sessionId, Map<String, Object> payload) {
    publish(sessionId, "assistant.model.preview", payload == null ? Map.of() : payload);
  }

  public void assistantMessage(String sessionId, Object response) {
    publish(sessionId, "chat.assistant", response);
  }

  public void terminal(
      String sessionId,
      AssistantWorkflowState workflowState,
      String modelId,
      Long revision,
      String message) {
    AssistantWorkflowState state =
        workflowState == null ? AssistantWorkflowState.FAILED : workflowState;
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("workflowState", state.name());
    payload.put("modelId", safe(modelId));
    payload.put("revision", revision == null ? "" : revision);
    if (message != null && !message.isBlank()) {
      payload.put("message", message);
    }
    publish(
        sessionId,
        state == AssistantWorkflowState.FAILED
            ? "assistant.turn.failed"
            : "assistant.turn.completed",
        payload);
  }

  private void publish(String sessionId, String type, Object payload) {
    if (realtime != null) {
      realtime.publish(sessionId, type, payload);
    }
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}
