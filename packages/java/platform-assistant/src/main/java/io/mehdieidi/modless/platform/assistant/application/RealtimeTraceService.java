package io.mehdieidi.modless.platform.assistant.application;

import io.mehdieidi.modless.platform.assistant.domain.AssistantWorkflowState;
import io.mehdieidi.modless.platform.assistant.spi.AssistantRealtimePublisher;
import java.util.LinkedHashMap;
import java.util.Map;

/** Publishes user-visible assistant trace events without exposing private reasoning. */
public class RealtimeTraceService {

  private static final int PREVIEW_THROTTLE_EVERY = 5;

  private final AssistantRealtimePublisher realtime;
  private final java.util.Map<String, Integer> previewCounters =
      new java.util.concurrent.ConcurrentHashMap<>();

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
    if (payload == null) {
      publish(sessionId, "assistant.model.preview", Map.of());
      return;
    }
    Object finalPreview = payload.get("final");
    int operationCount =
        payload.get("operationCount") instanceof Number number ? number.intValue() : 0;
    boolean force =
        Boolean.TRUE.equals(finalPreview) || Boolean.TRUE.equals(payload.get("validated"));
    if (!force && operationCount > 0) {
      int count = previewCounters.merge(sessionId, 1, Integer::sum);
      if (count % PREVIEW_THROTTLE_EVERY != 0) {
        return;
      }
    }
    if (Boolean.TRUE.equals(finalPreview) || Boolean.TRUE.equals(payload.get("validated"))) {
      previewCounters.remove(sessionId);
    }
    publish(sessionId, "assistant.model.preview", payload);
  }

  public void toolStarted(String sessionId, String turnId, String toolName) {
    publish(
        sessionId,
        "assistant.tool.started",
        Map.of("turnId", safe(turnId), "tool", safe(toolName)));
  }

  public void toolCompleted(String sessionId, String turnId, String toolName) {
    publish(
        sessionId,
        "assistant.tool.completed",
        Map.of("turnId", safe(turnId), "tool", safe(toolName)));
  }

  public void deltaDrafted(String sessionId, String turnId, int operationCount, String phase) {
    publish(
        sessionId,
        "assistant.delta.drafted",
        Map.of(
            "turnId",
            safe(turnId),
            "operationCount",
            Math.max(0, operationCount),
            "phase",
            safe(phase)));
  }

  public void deltaValidated(String sessionId, String turnId, boolean valid, int issueCount) {
    publish(
        sessionId,
        "assistant.delta.validated",
        Map.of("turnId", safe(turnId), "valid", valid, "issueCount", Math.max(0, issueCount)));
  }

  public void sourceCoverage(
      String sessionId,
      int coveredChunks,
      int totalChunks,
      String chunkId,
      String status,
      String message) {
    publish(
        sessionId,
        "assistant.source.coverage",
        Map.of(
            "coveredChunks",
            Math.max(0, coveredChunks),
            "totalChunks",
            Math.max(0, totalChunks),
            "chunkId",
            safe(chunkId),
            "status",
            safe(status),
            "message",
            safe(message)));
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
