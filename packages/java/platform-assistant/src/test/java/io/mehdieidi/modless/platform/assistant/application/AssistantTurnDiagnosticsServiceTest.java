package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnDiagnostics;
import io.mehdieidi.modless.platform.assistant.spi.AssistantTurnExecutionStore;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssistantTurnDiagnosticsServiceTest {

  @Test
  void createsAndPersistsDurableTurnDiagnostics() {
    CapturingStore store = new CapturingStore();
    AssistantTurnDiagnosticsService service = new AssistantTurnDiagnosticsService(store);

    AssistantTurnDiagnostics diagnostics =
        service.create("APPLIED", 7, 2, 3, 1, "APPLIED", System.currentTimeMillis() - 10);
    service.persist(
        "turn-1",
        "session-1",
        diagnostics,
        Map.of("retrieval", 12L),
        Map.of("selectedContracts", 4),
        java.util.List.of("ok"));

    assertEquals("turn-1", store.turnId);
    assertEquals("session-1", store.sessionId);
    assertEquals(7, store.diagnostics.snippetCount());
    assertEquals(2, store.diagnostics.providerCalls());
    assertEquals(3, store.diagnostics.toolCalls());
    assertEquals(1, store.diagnostics.repairAttempts());
    assertTrue(store.diagnostics.latencyMs() >= 0);
    assertEquals(12L, store.phaseTimings.get("retrieval"));
    assertEquals(4, ((Map<?, ?>) store.retrievalDiagnostics).get("selectedContracts"));
  }

  private static final class CapturingStore implements AssistantTurnExecutionStore {
    private String turnId;
    private String sessionId;
    private AssistantTurnDiagnostics diagnostics;
    private Map<String, Long> phaseTimings;
    private Object retrievalDiagnostics;

    @Override
    public void recordDiagnostics(
        String turnId,
        String sessionId,
        AssistantTurnDiagnostics diagnostics,
        Map<String, Long> phaseTimings,
        Object retrievalDiagnostics,
        Object validationFeedback) {
      this.turnId = turnId;
      this.sessionId = sessionId;
      this.diagnostics = diagnostics;
      this.phaseTimings = phaseTimings;
      this.retrievalDiagnostics = retrievalDiagnostics;
    }
  }
}
