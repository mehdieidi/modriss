package io.mehdieidi.varka.platform.assistant.source;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Scores labelled source requirements against model-like generated output, not a copied id set. */
class RequirementBenchmarkFixtureTest {
  @Test
  void labelledCimAndPimRequirementsMeetTheirFixtureThresholds() throws Exception {
    try (InputStream input =
        getClass().getResourceAsStream("/assistant-requirement-benchmarks.json")) {
      List<Fixture> fixtures =
          new ObjectMapper().readValue(input, new TypeReference<List<Fixture>>() {});
      for (Fixture fixture : fixtures) {
        Set<String> required = Set.copyOf(fixture.expectedElements().keySet());
        Set<String> represented = representedRequirements(fixture.modelOutput());
        var score = RequirementCoverageEvaluator.score(required, represented);
        assertTrue(score.recall() >= fixture.minimumRecall(), fixture.name() + " recall");
        assertTrue(score.precision() >= fixture.minimumPrecision(), fixture.name() + " precision");
      }
    }
  }

  private Set<String> representedRequirements(JsonNode output) {
    Set<String> result = new LinkedHashSet<>();
    output
        .path("elements")
        .forEach(element -> element.path("requirementIds").forEach(id -> result.add(id.asText())));
    return result;
  }

  private record Fixture(
      String name,
      String level,
      String source,
      Map<String, List<String>> expectedElements,
      JsonNode modelOutput,
      double minimumRecall,
      double minimumPrecision) {}
}
