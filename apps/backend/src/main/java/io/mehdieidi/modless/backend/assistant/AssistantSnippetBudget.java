package io.mehdieidi.modless.backend.assistant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reserves tier-1 schema snippets before filling the remaining retrieval budget. */
final class AssistantSnippetBudget {

  private AssistantSnippetBudget() {}

  static List<AssistantModelProvider.ContextSnippet> assemble(
      AiProperties properties,
      List<AssistantModelProvider.ContextSnippet> tier1,
      List<AssistantModelProvider.ContextSnippet> tier2,
      List<AssistantModelProvider.ContextSnippet> tier3,
      List<AssistantModelProvider.ContextSnippet> tier4) {
    int maxTotal = properties.maxContextSnippets();
    int reservedTier1 = Math.min(properties.reservedSchemaSnippets(), maxTotal);
    List<AssistantModelProvider.ContextSnippet> compactTier1 =
        deduplicateCompact(tier1, reservedTier1);
    List<AssistantModelProvider.ContextSnippet> result = new ArrayList<>(compactTier1);
    int remaining = Math.max(0, maxTotal - result.size());
    if (remaining == 0) {
      return result;
    }
    List<AssistantModelProvider.ContextSnippet> lowerTiers = new ArrayList<>();
    lowerTiers.addAll(tier2);
    lowerTiers.addAll(tier3);
    lowerTiers.addAll(tier4);
    result.addAll(deduplicateCompact(lowerTiers, remaining));
    return result;
  }

  private static List<AssistantModelProvider.ContextSnippet> deduplicateCompact(
      List<AssistantModelProvider.ContextSnippet> snippets, int limit) {
    Map<String, AssistantModelProvider.ContextSnippet> result = new LinkedHashMap<>();
    for (AssistantModelProvider.ContextSnippet snippet : snippets) {
      if (snippet == null || result.size() >= limit) {
        continue;
      }
      String content = snippet.content() == null ? "" : snippet.content().trim();
      int max = snippet.source().startsWith("runtime-") ? 5000 : 2200;
      if (content.length() > max) {
        content = content.substring(0, max) + "\n[truncated]";
      }
      AssistantModelProvider.ContextSnippet compact =
          new AssistantModelProvider.ContextSnippet(snippet.source(), snippet.title(), content);
      result.putIfAbsent(snippet.source() + "#" + snippet.title(), compact);
    }
    return List.copyOf(result.values());
  }
}
