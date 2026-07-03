package io.mehdieidi.modless.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadOnlyAnswerAgentTest {

  @Test
  void answersWithoutMutationPayload() {
    CapturingProvider provider = new CapturingProvider("The model contains one function.");
    ReadOnlyAnswerAgent agent =
        new ReadOnlyAnswerAgent(
            provider, new PromptContextBuilder(new ContextBudget(100, 1000, 4)));

    AssistantTurnPlan plan =
        agent.answer(
            new AssistantModelProvider.AssistantPrompt(
                AssistantModelRole.RESPONDER,
                "Explain the model.",
                "What does it do?",
                List.of(
                    new AssistantModelProvider.ContextSnippet(
                        "model-context", "Selected element", "Function: Checkout"))));

    assertEquals(AssistantTurnPlan.Intent.INFORMATION, plan.intent());
    assertEquals(AssistantTurnPlan.Kind.ANSWER, plan.kind());
    assertEquals("The model contains one function.", plan.message());
    assertTrue(plan.patch().operations().isEmpty());
    assertEquals(1, provider.structuredCalls);
    assertTrue(provider.lastPrompt.system().contains("<read-only-policy mandatory=\"true\">"));
    assertTrue(provider.lastPrompt.system().contains("Do not propose or emit model mutation JSON"));
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
