package io.mehdieidi.varka.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DurableAssistantModeTest {
  @Test
  void acceptsOnlyUnifiedAndExplicitTestOverrides() {
    assertEquals(
        DurableAssistantTurnWorker.AssistantMode.UNIFIED,
        DurableAssistantTurnWorker.AssistantMode.parse("unified"));
    assertEquals(
        DurableAssistantTurnWorker.AssistantMode.AGENT_TEST,
        DurableAssistantTurnWorker.AssistantMode.parse("agent-test"));
    assertEquals(
        DurableAssistantTurnWorker.AssistantMode.CONCEPTUAL_TEST,
        DurableAssistantTurnWorker.AssistantMode.parse("conceptual-test"));
    assertThrows(
        IllegalArgumentException.class,
        () -> DurableAssistantTurnWorker.AssistantMode.parse("conceptual-instance"));
  }
}
