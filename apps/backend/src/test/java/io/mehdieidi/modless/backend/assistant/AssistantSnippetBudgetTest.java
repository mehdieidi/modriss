package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AssistantSnippetBudgetTest {

  @Test
  void reservesTierOneSnippetsWhenCatalogResultsFloodTheBudget() {
    AiProperties properties =
        new AiProperties(
            false, null, null, null, 0, 0, 0, 24, 0, 0, 0, 0, 0, 10, null, null, null, null, null,
            null, null);

    List<AssistantModelProvider.ContextSnippet> tier1 =
        List.of(
            contract("schema-contract", "Function"),
            contract("schema-contract", "Api"),
            contract("schema-contract", "DataStore"));
    List<AssistantModelProvider.ContextSnippet> tier4 = new ArrayList<>();
    IntStream.range(0, 30)
        .forEach(
            index ->
                tier4.add(
                    new AssistantModelProvider.ContextSnippet(
                        "catalog-" + index, "Catalog " + index, "content " + index)));

    List<AssistantModelProvider.ContextSnippet> assembled =
        AssistantSnippetBudget.assemble(properties, tier1, List.of(), List.of(), tier4);

    assertEquals(24, assembled.size());
    assertTrue(assembled.stream().anyMatch(snippet -> "Function".equals(snippet.title())));
    assertTrue(assembled.stream().anyMatch(snippet -> "Api".equals(snippet.title())));
    assertTrue(assembled.stream().anyMatch(snippet -> "DataStore".equals(snippet.title())));
  }

  private static AssistantModelProvider.ContextSnippet contract(String source, String title) {
    return new AssistantModelProvider.ContextSnippet(source, title, "contract " + title);
  }
}
