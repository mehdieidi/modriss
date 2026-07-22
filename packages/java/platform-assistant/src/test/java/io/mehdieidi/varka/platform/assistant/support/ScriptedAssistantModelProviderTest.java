package io.mehdieidi.varka.platform.assistant.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.mehdieidi.varka.platform.assistant.application.ProviderCallBudget;
import io.mehdieidi.varka.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider.AssistantPrompt;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ScriptedAssistantModelProviderTest {
  @AfterEach
  void clearBudget() {
    ProviderCallBudget.clear();
  }

  @Test
  void replaysRepliesFailuresAndTokenUsageInOrder() {
    var provider =
        new ScriptedAssistantModelProvider(
            List.of(
                ScriptedAssistantModelProvider.reply("first", 11, 7),
                ScriptedAssistantModelProvider.failure(new PlatformException(502, "restart"))));
    ProviderCallBudget.bind(2);
    var prompt = new AssistantPrompt(AssistantModelRole.RESPONDER, "system", "user", List.of());

    var reply = provider.complete(prompt);

    assertEquals("first", reply.content());
    assertEquals(11, reply.usage().promptTokens());
    assertEquals(7, reply.usage().completionTokens());
    assertEquals(1, provider.prompts().size());
    assertThrows(PlatformException.class, () -> provider.complete(prompt));
    assertEquals(0, provider.remainingSteps());
    assertEquals(2, ProviderCallBudget.count());
  }
}
