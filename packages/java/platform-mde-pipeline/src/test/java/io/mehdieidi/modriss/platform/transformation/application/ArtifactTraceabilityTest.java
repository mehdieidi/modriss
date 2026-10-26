package io.mehdieidi.modriss.platform.transformation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ArtifactTraceabilityTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void matchesTheActualGeneratorKeyRatherThanEveryDisplayAlias() {
    var model =
        mapper.readTree(
            """
            {"id":"root","name":"Project","resources":[
              {"id":"a","traceId":"trace-a","name":"Shared","logicalId":"Other"},
              {"id":"b","name":"Shared"},
              {"id":"c","logicalId":"Logical","name":"Shared"},
              {"id":"d","externalId":"external-d","name":"Shared"},
              {"id":"e","sourceQualifiedName":"qualified-e","name":"Shared"}
            ],"graph":{"elements":[{"id":"a","name":"Shared"}]}}
            """);
    var report =
        mapper.readTree(
            """
            {"artifacts":[
              {"path":"a.go","sourceStableIds":["trace-a","trace-a"]},
              {"path":"b.go","sourceStableIds":["Shared"]},
              {"path":"c.go","sourceStableIds":["Logical"]},
              {"path":"d.go","sourceStableIds":["external-d"]},
              {"path":"e.go","sourceStableIds":["qualified-e"]},
              {"path":"unknown.go","sourceStableIds":["missing"]}
            ]}
            """);
    var traceability = ArtifactTraceability.build(model, report, mapper);
    assertEquals(
        mapper.readTree(
            """
            {"a":["a.go"],"b":["b.go"],"c":["c.go"],"d":["d.go"],"e":["e.go"]}
            """),
        traceability);
  }

  @Test
  void preservesSharedFilesAndDoesNotInventPathsForUntracedElements() {
    var model =
        mapper.readTree(
            """
            {"id":"root","elements":[{"id":"a","traceId":"A"},{"id":"b","traceId":"B"},{"id":"c"}]}
            """);
    var report =
        mapper.readTree(
            """
            {"artifacts":[{"path":"shared.yaml","sourceStableIds":["A","B"]},
              {"path":"untraced.txt"},{"path":"","sourceStableIds":["c"]}]}
            """);
    var result = ArtifactTraceability.build(model, report, mapper);
    assertEquals("shared.yaml", result.path("a").get(0).asText());
    assertEquals("shared.yaml", result.path("b").get(0).asText());
    assertTrue(result.path("c").isMissingNode());
  }

  @Test
  void exactSourceIdsDisambiguateInheritedTraceKeysAndLegacyAmbiguityStaysUnresolved() {
    var model =
        mapper.readTree(
            """
            {"id":"root","resources":[
              {"id":"lambda","traceId":"same-pim-trace","name":"Handler"},
              {"id":"alarm","traceId":"same-pim-trace","name":"Handler"}
            ]}
            """);
    var report =
        mapper.readTree(
            """
{"artifacts":[
  {"path":"handler.go","sourceStableIds":["same-pim-trace"],"sourceElementIds":["lambda"]},
  {"path":"legacy.go","sourceStableIds":["same-pim-trace"]},
  {"path":"missing.go","sourceStableIds":["same-pim-trace"],"sourceElementIds":["missing"]}
]}
""");
    var result = ArtifactTraceability.build(model, report, mapper);
    assertEquals(mapper.readTree("{\"lambda\":[\"handler.go\"]}"), result);
  }
}
