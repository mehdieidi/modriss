package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSettings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AssistantSnippetBudgetTest {

  @Test
  void reservesTierOneSnippetsWhenCatalogResultsFloodTheBudget() {
    AssistantSettings properties = settings(24, 10);

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

  private static AssistantSettings settings(int maxContextSnippets, int reservedSchemaSnippets) {
    return new AssistantSettings() {
      @Override
      public boolean enabled() {
        return false;
      }

      @Override
      public int validationRepairAttempts() {
        return 0;
      }

      @Override
      public int maxContextSnippets() {
        return maxContextSnippets;
      }

      @Override
      public int maxToolCalls() {
        return 0;
      }

      @Override
      public int maxAutoApplyOperations() {
        return 0;
      }

      @Override
      public int maxSnippetChars() {
        return 0;
      }

      @Override
      public int maxSystemChars() {
        return 0;
      }

      @Override
      public int reservedSchemaSnippets() {
        return reservedSchemaSnippets;
      }

      @Override
      public Duration requestTimeout() {
        return Duration.ZERO;
      }

      @Override
      public AssistantSettings.Hardening hardening() {
        return null;
      }
    };
  }
}
