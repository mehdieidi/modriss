package io.mehdieidi.modriss.platform.modeling.xmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.modeling.config.ModelingConfigService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Checks the evaluation sample through the same XMI conversion used by CIM imports. */
class CommunityWorkshopsSampleImportTest {

  @Test
  void importsAndRoundTripsTheCompleteCommunityWorkshopsCim() throws Exception {
    Path sample =
        Path.of(
                "../../../methodology/modeling-framework/samples/community-workshops/"
                    + "community-workshops.cim.xmi")
            .normalize();
    XmiModelImportService service = new XmiModelImportService(new ObjectMapper());
    byte[] source = Files.readAllBytes(sample);
    JsonNode imported = service.importModel(ModelLevel.CIM, source);

    assertEquals("CIMModel", imported.path("eClass").asText());
    assertEquals("Community Workshops", imported.path("name").asText());
    assertEquals(21, imported.path("requirements").size());
    assertEquals(3, imported.path("entities").size());
    assertEquals(10, imported.path("commands").size());
    assertEquals(4, imported.path("queries").size());
    assertEquals(14, imported.path("events").size());
    assertEquals(7, imported.path("processes").size());
    assertImportGraph(imported);

    byte[] exported = service.exportModel(ModelLevel.CIM, imported);
    Resource originalResource = service.loadResource(ModelLevel.CIM, source, "sample-original");
    Resource exportedResource = service.loadResource(ModelLevel.CIM, exported, "sample-exported");
    try {
      assertTrue(EcoreUtil.UnresolvedProxyCrossReferencer.find(originalResource).isEmpty());
      assertTrue(EcoreUtil.UnresolvedProxyCrossReferencer.find(exportedResource).isEmpty());
      Set<String> originalIds = identifiers(originalResource);
      assertEquals(352, originalIds.size());
      assertEquals(
          originalIds, identifiers(exportedResource), "Export must preserve every CIM element");
    } finally {
      originalResource.unload();
      exportedResource.unload();
    }

    JsonNode reimported = service.importModel(ModelLevel.CIM, exported);
    assertImportGraph(reimported);
    for (String feature :
        new String[] {
          "requirements",
          "goals",
          "actors",
          "roles",
          "capabilities",
          "boundedContexts",
          "entities",
          "relationships",
          "aggregates",
          "informationItems",
          "classifications",
          "commands",
          "queries",
          "events",
          "businessErrors",
          "conditions",
          "processes",
          "policies",
          "decisionTables",
          "risks",
          "assumptions",
          "transformationProfile"
        }) {
      assertEquals(
          imported.path(feature), reimported.path(feature), feature + " changed on export");
    }
  }

  private static Set<String> identifiers(Resource resource) {
    Set<String> ids = new HashSet<>();
    var iterator = resource.getAllContents();
    while (iterator.hasNext()) {
      EObject element = iterator.next();
      var feature = element.eClass().getEStructuralFeature("id");
      if (feature != null) {
        String id = String.valueOf(element.eGet(feature));
        assertFalse(id.isBlank());
        assertTrue(ids.add(id), "Duplicate identifier: " + id);
      }
    }
    return ids;
  }

  private static void assertImportGraph(JsonNode model) {
    JsonNode graph = model.path("graph");
    assertFalse(graph.path("elements").isEmpty());
    assertFalse(graph.path("relationships").isEmpty());
    Set<String> ids = new HashSet<>();
    graph.path("elements").forEach(element -> ids.add(element.path("id").asText()));
    JsonNode config = new ObjectMapper().valueToTree(new ModelingConfigService().config());
    Set<String> knownKinds = new HashSet<>();
    config
        .path("levels")
        .path("cim")
        .path("relationshipKinds")
        .forEach(kind -> knownKinds.add(kind.asText()));
    for (JsonNode relationship : graph.path("relationships")) {
      assertTrue(
          ids.contains(relationship.path("sourceElementId").asText()), relationship.toString());
      assertTrue(
          ids.contains(relationship.path("targetElementId").asText()), relationship.toString());
      assertTrue(knownKinds.contains(relationship.path("kind").asText()), relationship.toString());
    }
    assertTrue(ids.contains("agg-booking"));
    assertTrue(ids.contains("cmd-request"));
    assertTrue(ids.contains("R14"));
  }
}
