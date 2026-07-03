package io.mehdieidi.modless.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptContextBuilderTest {

  @Test
  void labelsSystemBlocksAndAppliesSnippetBudget() {
    PromptContextBuilder builder = new PromptContextBuilder(new ContextBudget(100, 500, 2));
    AssistantModelProvider.AssistantPrompt prompt =
        builder.build(
            new AssistantModelProvider.AssistantPrompt(
                AssistantModelRole.PLANNER, "Base system", "User request", List.of()),
            List.of(new PromptBlock("model-delta-protocol", "Return ModelDelta.", true)),
            List.of(
                new AssistantModelProvider.ContextSnippet("schema", "Function", "x".repeat(650))),
            List.of(
                new AssistantModelProvider.ContextSnippet("methodology", "Optional one", "keep"),
                new AssistantModelProvider.ContextSnippet("methodology", "Optional two", "drop")));

    assertTrue(prompt.system().contains("<model-delta-protocol mandatory=\"true\">"));
    assertTrue(prompt.system().contains("Return ModelDelta."));
    assertEquals(2, prompt.snippets().size());
    assertTrue(prompt.snippets().get(0).content().endsWith("[truncated by context budget]"));
    assertEquals("Optional one", prompt.snippets().get(1).title());
  }
}
