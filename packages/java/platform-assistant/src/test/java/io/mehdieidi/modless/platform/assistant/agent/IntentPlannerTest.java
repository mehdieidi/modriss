package io.mehdieidi.modless.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntentPlannerTest {

  @Test
  void usesStructuredProviderDecisionInsteadOfLocalKeywordRouting() {
    CapturingProvider provider =
        new CapturingProvider(
            """
            {
              "intent": "MUTATION",
              "taskKind": "EXTEND_MODEL",
              "sourceUse": false,
              "concepts": ["payment flow"],
              "candidateTypes": ["Function"]
            }
            """);
    IntentPlanner planner =
        new IntentPlanner(
            provider,
            new com.fasterxml.jackson.databind.ObjectMapper(),
            new PromptContextBuilder(null));

    IntentPlanner.IntentDecision decision =
        planner.classify(
            ModelLevel.PIM,
            new AssistantModelProvider.AssistantPrompt(
                AssistantModelRole.PLANNER, "system", "لطفا پرداخت را کامل تر کن", List.of()),
            List.of(
                new AssistantModelProvider.ContextSnippet("model", "current slice", "Checkout")));

    assertEquals(IntentPlanner.Intent.MUTATION, decision.intent());
    assertEquals("EXTEND_MODEL", decision.taskKind());
    assertEquals(List.of("Function"), decision.candidateTypes());
    assertEquals(1, provider.structuredCalls);
    assertTrue(provider.lastPrompt.system().contains("not from keyword matching"));
    assertTrue(provider.lastPrompt.user().contains("لطفا پرداخت"));
  }

  private static final class CapturingProvider implements AssistantModelProvider {
    private final String response;
    private int structuredCalls;
    private AssistantPrompt lastPrompt;

    private CapturingProvider(String response) {
      this.response = response;
    }

    @Override
    public AssistantProviderMetadata metadata() {
      return new AssistantProviderMetadata("test", "", "");
    }

    @Override
    public boolean available() {
      return true;
    }

    @Override
    public AssistantReply complete(AssistantPrompt prompt) {
      return completeStructured(prompt);
    }

    @Override
    public AssistantReply completeStructured(AssistantPrompt prompt) {
      structuredCalls++;
      lastPrompt = prompt;
      return new AssistantReply(response, "test", "test-model");
    }
  }
}
