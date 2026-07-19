package io.mehdieidi.varka.platform.assistant.agent;

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
                  "automatic-multi-slice")));
      assertTrue(fixtures.stream().allMatch(item -> !item.assertions().isEmpty()));
    }
  }

  private record Fixture(String id, String route, List<String> assertions) {}
}
