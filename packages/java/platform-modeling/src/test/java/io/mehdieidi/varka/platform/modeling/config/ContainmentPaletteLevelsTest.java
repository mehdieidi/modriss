package io.mehdieidi.varka.platform.modeling.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Verifies nested containment palette rules across CIM, PIM, and PSM. */
class ContainmentPaletteLevelsTest {

  private final ModelingConfigService service = new ModelingConfigService();

  @Test
  void cimNestedProcessStepsAreContainedOnly() {
    Map<String, Object> cim = level("cim");
    assertEquals(Boolean.TRUE, element(cim, "StartStep").get("containedOnly"));
    assertEquals(Boolean.TRUE, element(cim, "ExceptionScenario").get("containedOnly"));
    assertFalse(Boolean.TRUE.equals(element(cim, "BusinessProcess").get("containedOnly")));
    assertPaletteExcludes(cim, "business-process", "StartStep", "EndStep");
    assertPaletteIncludes(cim, "business-process", "BusinessProcess");
    assertContainmentPaletteIncludes(cim, "BusinessProcess", "StartStep");
    assertPaletteExcludes(cim, "business-process", "DecisionTable", "DecisionRule");
    assertContainmentPaletteIncludes(cim, "BusinessProcess", "DecisionTable");
    assertContainmentPaletteExcludes(cim, "BusinessProcess", "DecisionRule");
    assertContainmentPaletteIncludes(cim, "DecisionTable", "DecisionRule");
  }

  @Test
  void pimNestedApiAndWorkflowDetailsAreContainedOnly() {
    Map<String, Object> pim = level("pim");
    assertEquals(Boolean.TRUE, element(pim, "ApiRoute").get("containedOnly"));
    assertEquals(Boolean.TRUE, element(pim, "StartStep").get("containedOnly"));
    assertEquals(Boolean.TRUE, element(pim, "TaskStep").get("containedOnly"));
    assertEquals(Boolean.TRUE, element(pim, "ErrorMapping").get("containedOnly"));
    assertEquals(Boolean.TRUE, element(pim, "Api").get("containedOnly"));
    assertEquals(Boolean.TRUE, element(pim, "Workflow").get("containedOnly"));
    assertPaletteExcludes(pim, "pim-api-surface", "Api", "ApiRoute", "ErrorMapping", "ApiContract");
    assertPaletteIncludes(pim, "pim-api-surface", "ServerlessService");
    assertPaletteExcludes(pim, "pim-workflow-designer", "StartStep", "TaskStep");
    assertContainmentPaletteIncludes(pim, "ServerlessService", "Api");
    assertContainmentPaletteIncludes(pim, "ServerlessService", "Workflow");
    assertContainmentPaletteIncludes(pim, "Api", "ApiRoute");
    assertContainmentPaletteIncludes(pim, "Workflow", "StartStep");
    assertContainmentPaletteIncludes(pim, "Workflow", "TaskStep");
  }

  @Test
  void psmStackResourcesRemainViewPlaceableWhileDetailsStayContained() {
    Map<String, Object> psm = level("psm");
    assertFalse(Boolean.TRUE.equals(element(psm, "Subnet").get("containedOnly")));
    assertFalse(Boolean.TRUE.equals(element(psm, "AwsLambdaFunction").get("containedOnly")));
    assertFalse(Boolean.TRUE.equals(element(psm, "HttpApi").get("containedOnly")));
    assertEquals(Boolean.TRUE, element(psm, "SecurityGroupRule").get("containedOnly"));
    assertEquals(Boolean.TRUE, element(psm, "CfnParameter").get("containedOnly"));
    assertFalse(Boolean.TRUE.equals(element(psm, "SamStack").get("containedOnly")));
    assertPaletteIncludes(psm, "psm-resource-topology", "SamStack", "AwsStage");
    assertPaletteExcludes(
        psm, "psm-resource-topology", "AwsLambdaFunction", "HttpApi", "Vpc", "SqsQueue");
    assertPaletteIncludes(psm, "psm-networking", "Vpc", "SecurityGroup");
    assertPaletteExcludes(psm, "psm-networking", "Subnet");
    assertEquals(Boolean.TRUE, element(psm, "HttpApiRoute").get("containedOnly"));
    assertPaletteIncludes(psm, "psm-api-edge", "HttpApi", "RestApi", "WebSocketApi");
    assertPaletteExcludes(psm, "psm-api-edge", "AwsLambdaFunction", "HttpApiRoute", "RestApiRoute");
    assertPaletteIncludes(psm, "psm-lambda-compute", "AwsLambdaFunction");
    assertContainmentPaletteIncludes(psm, "SamStack", "Subnet");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> level(String key) {
    Map<String, Object> levels = (Map<String, Object>) service.config().get("levels");
    return (Map<String, Object>) levels.get(key);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> element(Map<String, Object> level, String type) {
    List<Map<String, Object>> elements = (List<Map<String, Object>>) level.get("elements");
    return elements.stream()
        .filter(item -> type.equals(String.valueOf(item.get("type"))))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing element type " + type));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> view(Map<String, Object> level, String id) {
    List<Map<String, Object>> views = (List<Map<String, Object>>) level.get("viewDefinitions");
    return views.stream()
        .filter(item -> id.equals(String.valueOf(item.get("id"))))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing view " + id));
  }

  @SuppressWarnings("unchecked")
  private void assertPaletteExcludes(Map<String, Object> level, String viewId, String... types) {
    List<String> palette = (List<String>) view(level, viewId).get("palette");
    for (String type : types) {
      assertFalse(palette.contains(type), viewId + " palette must not include " + type);
    }
  }

  @SuppressWarnings("unchecked")
  private void assertPaletteIncludes(Map<String, Object> level, String viewId, String... types) {
    List<String> palette = (List<String>) view(level, viewId).get("palette");
    for (String type : types) {
      assertTrue(palette.contains(type), viewId + " palette must include " + type);
    }
  }

  @SuppressWarnings("unchecked")
  private void assertContainmentPaletteIncludes(
      Map<String, Object> level, String ownerType, String childType) {
    Map<String, Object> palettes = (Map<String, Object>) level.get("containmentPalettes");
    Map<String, Object> ownerPalette = (Map<String, Object>) palettes.get(ownerType);
    List<String> types = (List<String>) ownerPalette.get("types");
    assertTrue(
        types.contains(childType), ownerType + " containment palette must include " + childType);
  }

  @SuppressWarnings("unchecked")
  private void assertContainmentPaletteExcludes(
      Map<String, Object> level, String ownerType, String childType) {
    Map<String, Object> palettes = (Map<String, Object>) level.get("containmentPalettes");
    Map<String, Object> ownerPalette = (Map<String, Object>) palettes.get(ownerType);
    List<String> types = (List<String>) ownerPalette.get("types");
    assertFalse(
        types.contains(childType),
        ownerType + " containment palette must not include " + childType);
  }
}
