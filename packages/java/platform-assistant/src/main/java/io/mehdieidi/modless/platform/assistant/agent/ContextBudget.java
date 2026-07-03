package io.mehdieidi.modless.platform.assistant.agent;

import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import java.util.ArrayList;
import java.util.List;

/** Applies prompt budgets before provider calls while preserving mandatory contract blocks. */
public class ContextBudget {

  private final int maxPromptChars;
  private final int maxSnippetChars;
  private final int maxSnippets;

  public ContextBudget(int maxPromptTokens, int maxSnippetChars, int maxSnippets) {
    this.maxPromptChars = Math.max(4000, maxPromptTokens) * 4;
    this.maxSnippetChars = Math.max(500, maxSnippetChars);
    this.maxSnippets = Math.max(1, maxSnippets);
  }

  /** Returns snippets trimmed to configured count/character budgets. */
  public List<AssistantModelProvider.ContextSnippet> apply(
      List<AssistantModelProvider.ContextSnippet> snippets) {
    List<AssistantModelProvider.ContextSnippet> result = new ArrayList<>();
    int chars = 0;
    for (AssistantModelProvider.ContextSnippet snippet :
        snippets == null ? List.<AssistantModelProvider.ContextSnippet>of() : snippets) {
      if (result.size() >= maxSnippets || chars >= maxPromptChars) {
        break;
      }
      String content = trim(snippet.content(), Math.min(maxSnippetChars, maxPromptChars - chars));
      if (content.isBlank() && !snippet.content().isBlank()) {
        continue;
      }
      chars += snippet.source().length() + snippet.title().length() + content.length();
      result.add(
          new AssistantModelProvider.ContextSnippet(snippet.source(), snippet.title(), content));
    }
    return List.copyOf(result);
  }

  private String trim(String value, int limit) {
    String text = value == null ? "" : value.trim();
    if (text.length() <= limit) {
      return text;
    }
    return text.substring(0, Math.max(0, limit - 20)) + "\n[truncated by context budget]";
  }
}
