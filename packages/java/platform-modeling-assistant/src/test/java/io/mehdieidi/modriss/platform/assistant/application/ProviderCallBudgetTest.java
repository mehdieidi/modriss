package io.mehdieidi.modriss.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modriss.platform.kernel.PlatformException;
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

    ProviderCallBudget.consume();
    ProviderCallBudget.consume();

    assertEquals(2, ProviderCallBudget.count());
    assertThrows(PlatformException.class, ProviderCallBudget::consume);
  }

  @Test
  void countsEveryConfiguredAgentCall() {
    ProviderCallBudget.bind(1);

    ProviderCallBudget.consume();

    assertEquals(1, ProviderCallBudget.count());
    assertThrows(PlatformException.class, ProviderCallBudget::consume);
  }

  @Test
  void identifiesOnlyThisTurnBudgetFailures() {
    assertTrue(
        ProviderCallBudget.isExceeded(
            new PlatformException(429, "Assistant provider call budget exceeded for this turn.")));
    assertTrue(
        ProviderCallBudget.isExceeded(
            new PlatformException(
                429, "Conceptual generation exhausted its durable provider-call budget.")));
    assertFalse(ProviderCallBudget.isExceeded(new PlatformException(429, "Provider rate limit")));
  }
}
