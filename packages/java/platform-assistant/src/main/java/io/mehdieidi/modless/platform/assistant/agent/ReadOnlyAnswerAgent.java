package io.mehdieidi.modless.platform.assistant.agent;

import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import java.util.List;

/** Non-mutating assistant path for explanation and analysis turns. */
public class ReadOnlyAnswerAgent {

  private final AssistantModelProvider provider;
  private final PromptContextBuilder prompts;

  public ReadOnlyAnswerAgent(AssistantModelProvider provider, PromptContextBuilder prompts) {
    this.provider = provider;
    this.prompts = prompts;
  }

  /** Answers without mutation schema generation, preview, validation, or apply tools. */
  public AssistantTurnPlan answer(AssistantModelProvider.AssistantPrompt prompt) {
    AssistantModelProvider.AssistantPrompt bounded =
        prompts.build(
            prompt,
            List.of(
                new PromptBlock(
                    "read-only-policy",
                    "Answer using backend-provided context only. Do not propose or emit model "
                        + "mutation JSON, ModelDelta, semantic patch, JSON Patch, XMI, or tool "
                        + "calls. If the user asks for a model change, say that the request must "
                        + "be handled by the mutation agent.",
                    true)),
            List.of(),
            prompt.snippets());
    AssistantModelProvider.AssistantReply reply = provider.completeStructured(bounded);
    return new AssistantTurnPlan(
        AssistantTurnPlan.Intent.INFORMATION,
        AssistantTurnPlan.Kind.ANSWER,
        reply.content(),
        List.of(),
        new SemanticModelPatch(List.of()));
  }
}
