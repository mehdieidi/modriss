package io.mehdieidi.modless.platform.core.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

/** Integration coverage for graph extraction from the research-grade XMI fixtures. */
class XmiModelImportServiceTest {

  /** Verifies every sample imports into a connected graph with resolvable edge endpoints. */
  @Test
  void importsEveryLevelWithResolvableGraphRelationships() throws Exception {
    XmiModelImportService service = new XmiModelImportService(new ObjectMapper());
    Map<String, Object> configuredLevels = map(new ModelingConfigService().config().get("levels"));
    for (Fixture fixture :
        List.of(
            new Fixture(ModelLevel.CIM, "cim.xmi"),
            new Fixture(ModelLevel.PIM, "pim.xmi"),
            new Fixture(ModelLevel.PSM, "psm.xmi"))) {
      Path sample = Path.of("..", "..", "..", "mde", "samples", fixture.fileName()).normalize();
      byte[] bytes = Files.readAllBytes(sample);
      JsonNode model = service.importModel(fixture.level(), bytes);
      JsonNode elements = model.path("graph").path("elements");
      JsonNode relationships = model.path("graph").path("relationships");
      assertTrue(elements.isArray() && elements.size() > 10, fixture.fileName());
      assertTrue(relationships.isArray() && relationships.size() > 10, fixture.fileName());

      Set<String> elementIds = new HashSet<>();
      elements.forEach(element -> elementIds.add(element.path("id").asText()));
      List<String> dangling =
          java.util.stream.StreamSupport.stream(relationships.spliterator(), false)
              .filter(
                  relationship ->
                      !elementIds.contains(relationship.path("sourceElementId").asText())
                          || !elementIds.contains(relationship.path("targetElementId").asText()))
              .map(
                  relationship ->
                      relationship.path("sourceType").asText("?")
                          + "."
                          + relationship.path("semanticFeature").asText("?")
                          + " -> "
                          + relationship.path("targetType").asText("?")
                          + " [sourceMissing="
                          + !elementIds.contains(relationship.path("sourceElementId").asText())
                          + ", targetMissing="
                          + !elementIds.contains(relationship.path("targetElementId").asText())
                          + "]")
              .distinct()
              .toList();
      assertTrue(dangling.isEmpty(), fixture.fileName() + " dangling graph edges: " + dangling);
      Map<String, Object> levelConfig = map(configuredLevels.get(fixture.level().apiName()));
      Set<String> knownKinds = new HashSet<>(stringList(levelConfig.get("relationshipKinds")));
      Set<String> relationshipIds = new HashSet<>();
      relationships.forEach(
          relationship -> {
            String kind = relationship.path("kind").asText();
            assertTrue(knownKinds.contains(kind), fixture.fileName() + " unknown kind: " + kind);
            String relationshipId = relationship.path("id").asText();
            assertTrue(
                !relationshipId.isBlank() && relationshipIds.add(relationshipId),
                fixture.fileName() + " duplicate or blank relationship id: " + relationshipId);
          });
      if (fixture.level() == ModelLevel.CIM) {
        long processTransitionCount =
            StreamSupport.stream(relationships.spliterator(), false)
                .filter(
                    relationship ->
                        "ProcessTransition".equals(relationship.path("eClass").asText()))
                .peek(
                    relationship ->
                        assertTrue(
                            "TRANSITION".equals(relationship.path("kind").asText()),
                            "CIM process transition must use the Business Process view kind"))
                .count();
        assertTrue(processTransitionCount > 0, "cim.xmi must import process transitions");
      }

      JsonNode metadata =
          new ObjectMapper()
              .readTree(
                  Files.readAllBytes(
                      Path.of("src", "main", "resources", "modeling", fixture.metadataFile())));
      for (JsonNode rule : metadata.path("semanticEdgeObjectRules")) {
        String eClass = rule.path("eClass").asText();
        Set<String> allowedKinds = new HashSet<>();
        rule.path("matchKinds").forEach(kind -> allowedKinds.add(kind.asText()));
        List<JsonNode> importedRelationships =
            StreamSupport.stream(relationships.spliterator(), false)
                .filter(relationship -> eClass.equals(relationship.path("eClass").asText()))
                .toList();
        assertTrue(
            importedRelationships.stream()
                .allMatch(
                    relationship -> allowedKinds.contains(relationship.path("kind").asText())),
            fixture.fileName()
                + " has relationship kinds inconsistent with metadata for "
                + eClass
                + ": "
                + importedRelationships.stream()
                    .map(relationship -> relationship.path("kind").asText())
                    .distinct()
                    .toList());
      }
    }
  }

  /** One sample fixture and its expected model level. */
  private record Fixture(ModelLevel level, String fileName) {
    private String metadataFile() {
      return level.name().toLowerCase() + "-ui-metadata.json";
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private List<String> stringList(Object value) {
    return (List<String>) value;
  }
}
