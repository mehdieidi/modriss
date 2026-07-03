package io.mehdieidi.modless.platform.assistant.application;

import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnDiagnostics;
import io.mehdieidi.modless.platform.assistant.spi.AssistantTurnExecutionStore;
import java.util.List;
import java.util.Map;

/** Persists user-safe turn diagnostics for latency, retrieval, repair, and validation analysis. */
public class AssistantTurnDiagnosticsService {

  private final AssistantTurnExecutionStore turnExecutions;

  public AssistantTurnDiagnosticsService(AssistantTurnExecutionStore turnExecutions) {
    this.turnExecutions =
        turnExecutions == null ? AssistantTurnExecutionStore.noop() : turnExecutions;
  }

  public AssistantTurnDiagnostics create(
      String stage,
      int snippetCount,
      int providerCalls,
      int toolCalls,
      int repairAttempts,
      String outcome,
      long startedAtMillis) {
    return new AssistantTurnDiagnostics(
        stage,
        snippetCount,
        providerCalls,
        toolCalls,
        repairAttempts,
        outcome,
        Math.max(0L, System.currentTimeMillis() - startedAtMillis));
  }

  public void persist(
      String turnId,
      String sessionId,
      AssistantTurnDiagnostics diagnostics,
      Map<String, Long> phaseTimings,
      Object retrievalDiagnostics,
      Object validationFeedback) {
    if (turnId == null || turnId.isBlank() || sessionId == null || sessionId.isBlank()) {
      return;
    }
    turnExecutions.recordDiagnostics(
        turnId,
        sessionId,
        diagnostics,
        phaseTimings == null ? Map.of() : phaseTimings,
        retrievalDiagnostics == null ? Map.of() : retrievalDiagnostics,
        validationFeedback == null ? List.of() : validationFeedback);
  }
}
