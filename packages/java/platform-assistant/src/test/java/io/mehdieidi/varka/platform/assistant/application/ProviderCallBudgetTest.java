package io.mehdieidi.varka.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.mehdieidi.varka.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProviderCallBudgetTest {

  @AfterEach
  void tearDown() {
    ProviderCallBudget.clear();
  }

  @Test
  void tracksConsumedCallsUntilBudgetIsExceeded() {
    ProviderCallBudget.bind(2);

    ProviderCallBudget.consume(AssistantModelRole.RESPONDER);
    ProviderCallBudget.consume(AssistantModelRole.RESPONDER);

    assertEquals(2, ProviderCallBudget.count());
    assertThrows(
        PlatformException.class, () -> ProviderCallBudget.consume(AssistantModelRole.RESPONDER));
  }

  @Test
  void countsEveryConfiguredAgentCall() {
    ProviderCallBudget.bind(1);

    ProviderCallBudget.consume(AssistantModelRole.RESPONDER);

    assertEquals(1, ProviderCallBudget.count());
    assertThrows(
        PlatformException.class, () -> ProviderCallBudget.consume(AssistantModelRole.RESPONDER));
  }
}
