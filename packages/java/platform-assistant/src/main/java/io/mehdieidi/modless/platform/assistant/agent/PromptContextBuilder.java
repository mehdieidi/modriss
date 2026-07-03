package io.mehdieidi.modless.platform.assistant.agent;

import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import java.util.ArrayList;
import java.util.List;

/** Builds labeled, budgeted provider prompts for agent calls. */
public class PromptContextBuilder {

  private final ContextBudget budget;

  public PromptContextBuilder(ContextBudget budget) {
    this.budget = budget == null ? new ContextBudget(24000, 8000, 32) : budget;
  }

  /** Builds a prompt with explicit system block labels and budgeted snippets. */
  public AssistantModelProvider.AssistantPrompt build(
      AssistantModelProvider.AssistantPrompt base,
      List<PromptBlock> systemBlocks,
      List<AssistantModelProvider.ContextSnippet> mandatorySnippets,
      List<AssistantModelProvider.ContextSnippet> optionalSnippets) {
    List<AssistantModelProvider.ContextSnippet> snippets = new ArrayList<>();
    snippets.addAll(mandatorySnippets == null ? List.of() : mandatorySnippets);
    snippets.addAll(optionalSnippets == null ? List.of() : optionalSnippets);
    String system =
        (base == null ? "" : base.system())
            + "\n\n"
            + labeledBlocks(systemBlocks == null ? List.of() : systemBlocks);
    return new AssistantModelProvider.AssistantPrompt(
        base == null ? null : base.role(),
        system.trim(),
        base == null ? "" : base.user(),
        budget.apply(snippets));
  }

  private String labeledBlocks(List<PromptBlock> blocks) {
    StringBuilder result = new StringBuilder();
    for (PromptBlock block : blocks) {
      if (block.content().isBlank()) {
        continue;
      }
      result
          .append("<")
          .append(block.label())
          .append(block.mandatory() ? " mandatory=\"true\"" : "")
          .append(">\n")
          .append(block.content())
          .append("\n</")
          .append(block.label())
          .append(">\n\n");
    }
    return result.toString();
  }
}
