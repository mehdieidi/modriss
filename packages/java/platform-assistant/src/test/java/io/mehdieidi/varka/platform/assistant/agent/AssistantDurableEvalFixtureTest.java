package io.mehdieidi.varka.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Fails locally if the required durable evaluation matrix loses a CIM/PIM scenario. */
class AssistantDurableEvalFixtureTest {
  @Test
  void containsEveryRequiredCimPimReliabilityCase() throws Exception {
    try (InputStream input =
        getClass().getResourceAsStream("/assistant-durable-eval-fixtures.json")) {
      List<Fixture> fixtures =
          new ObjectMapper().readValue(input, new TypeReference<List<Fixture>>() {});
      Set<String> ids =
          fixtures.stream().map(Fixture::id).collect(java.util.stream.Collectors.toSet());
      assertTrue(
          ids.containsAll(
              Set.of(
                  "answer-only",
                  "create-cim",
                  "edit-pim",
                  "delete-confirmation",
                  "stale-revision",
                  "cancellation",
                  "malformed-repair",
                  "attachment-provenance-cim",
                  "labelled-requirements-pim",
                  "durable-resume")));
      assertTrue(fixtures.stream().allMatch(item -> !item.assertions().isEmpty()));
    }
  }

  @Test
  void v2AcceptanceContractUsesSemanticObligationsInsteadOfOperationCounts() throws Exception {
    try (InputStream input =
        getClass().getResourceAsStream("/assistant-v2-acceptance-fixtures.json")) {
      List<V2Fixture> fixtures =
          new ObjectMapper().readValue(input, new TypeReference<List<V2Fixture>>() {});

      assertTrue(
          fixtures.stream().anyMatch(item -> item.id().equals("document-to-cim-community-clinic")));
      assertTrue(
          fixtures.stream()
              .anyMatch(item -> item.id().equals("document-to-cim-marketplace-returns")));
      assertTrue(fixtures.stream().anyMatch(item -> item.level().equals("CIM")));
      assertTrue(fixtures.stream().anyMatch(item -> item.level().equals("PIM")));
      assertTrue(
          fixtures.stream()
              .anyMatch(
                  item ->
                      item.id().equals("unsupported-psm-assistant-request")
                          && item.level().equals("PSM")));
      assertTrue(
          fixtures.stream()
              .allMatch(
                  item ->
                      !item.semanticObligations().isEmpty()
                          && !item.structuralAssertions().isEmpty()
                          && !item.durabilityAssertions().isEmpty()
                          && !item.uxAssertions().isEmpty()));
      assertFalse(
          fixtures.stream()
              .flatMap(item -> item.semanticObligations().stream())
              .anyMatch(
                  value -> value.toLowerCase(java.util.Locale.ROOT).contains("minoperation")));
    }
  }

  private record Fixture(String id, String route, List<String> assertions) {}

  private record V2Fixture(
      String id,
      String level,
      List<String> semanticObligations,
      List<String> structuralAssertions,
      List<String> durabilityAssertions,
      List<String> uxAssertions) {}
}
