package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.kernel.PlatformException;
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

    ProviderCallBudget.consume(AssistantModelRole.PLANNER);
    ProviderCallBudget.consume(AssistantModelRole.RESPONDER);

    assertEquals(2, ProviderCallBudget.count());
    assertThrows(
        PlatformException.class, () -> ProviderCallBudget.consume(AssistantModelRole.PLANNER));
  }

  @Test
  void ignoresSummarizerCalls() {
    ProviderCallBudget.bind(1);

    ProviderCallBudget.consume(AssistantModelRole.SUMMARIZER);
    ProviderCallBudget.consume(AssistantModelRole.PLANNER);

    assertEquals(1, ProviderCallBudget.count());
    assertThrows(
        PlatformException.class, () -> ProviderCallBudget.consume(AssistantModelRole.RESPONDER));
  }
}
