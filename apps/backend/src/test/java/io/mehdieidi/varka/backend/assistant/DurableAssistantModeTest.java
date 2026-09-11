package io.mehdieidi.varka.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.varka.platform.assistant.turn.AssistantTurn;
import org.junit.jupiter.api.Test;

class DurableAssistantModeTest {
  @Test
  void recognizesPersistedConceptualPlansAsDurableRecoveryState() throws Exception {
    var mapper = new tools.jackson.databind.ObjectMapper();

    assertTrue(
        DurableAssistantTurnWorker.isDurablePlan(
            mapper.readTree(
                "{\"obligationLedger\":{\"obligations\":[]},\"selectedTypes\":[\"Api\"],\"blueprint\":{\"objects\":[]}}")));
  }

  @Test
  void automaticallyRecoversProviderFailureOnFirstPersistedConceptualSlice() {
    assertTrue(DurableAssistantTurnWorker.shouldAutomaticallyRecover(false, false, true, 502));
    assertTrue(DurableAssistantTurnWorker.shouldAutomaticallyRecover(false, false, true, 422));
  }

  @Test
  void doesNotReplayNonRecoverableFailureWithoutConceptualProgress() {
    assertEquals(
        false, DurableAssistantTurnWorker.shouldAutomaticallyRecover(false, false, true, 400));
    assertEquals(
        false, DurableAssistantTurnWorker.shouldAutomaticallyRecover(false, false, false, 502));
  }

  @Test
  void recoveryBudgetResetsWhenConceptualWorkAdvances() {
    var plan = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    plan.put("automaticRecoveryWorkItemId", "turn:conceptual:9");
    plan.put("automaticRecoveryCount", 2);

    assertEquals(2, DurableAssistantTurnWorker.automaticRecoveryCount(plan, "turn:conceptual:9"));
    assertEquals(0, DurableAssistantTurnWorker.automaticRecoveryCount(plan, "turn:conceptual:10"));
    assertEquals(0, DurableAssistantTurnWorker.automaticRecoveryCount(plan, null));
  }

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

  @Test
  void cancellationTakesPrecedenceOverAConcurrentProviderFailure() {
    assertEquals(
        AssistantTurn.State.CANCELLED,
        DurableAssistantTurnWorker.failureState(403, "Provider rejected the request", true));
    assertEquals(
        AssistantTurn.State.FAILED,
        DurableAssistantTurnWorker.failureState(403, "Provider rejected the request", false));
  }
}
