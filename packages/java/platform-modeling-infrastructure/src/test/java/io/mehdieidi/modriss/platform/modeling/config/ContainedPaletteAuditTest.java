package io.mehdieidi.modriss.platform.modeling.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Audits contained-only inference and view palettes across CIM, PIM, and PSM. */
class ContainedPaletteAuditTest {

  private ModelingConfigService service;

  @BeforeEach
  void setUp() {
    service = new ModelingConfigService();
  }

  @Test
  void infersContainedOnlyAcrossAllModelingLevels() {
    assertNestedOnly(level("cim"), "StartStep", "ExceptionScenario", "DecisionRule");
    assertRootStandalone(level("cim"), "BusinessProcess", "DecisionTable", "Policy");

    assertNestedOnly(
        level("pim"),
        "StartStep",
        "TaskStep",
        "ApiRoute",
        "ErrorMapping",
        "ParallelBranch",
        "Api",
        "Function",
        "Workflow",
        "Trigger",
        "Queue",
        "Topic",
        "EventBus",
        "Schedule",
        "DataStore",
        "ObjectStore",
        "ExternalAdapter");
    assertRootStandalone(
        level("pim"), "ServerlessService", "HumanTask", "Schema", "Flow", "DeploymentUnit");

    assertNestedOnly(
        level("psm"),
        "SecurityGroupRule",
        "CfnParameter",
        "LambdaEnvironmentVariable",
        "NativeProperty",
        "HttpApiRoute",
        "RestApiRoute",
        "LambdaPermission",
        "LambdaEventSourceMapping",
        "ApiGatewayStage");
    assertRootStandalone(
        level("psm"), "SamStack", "AwsStage", "Vpc", "HttpApi", "AwsLambdaFunction");
  }

  @Test
  void viewPalettesExcludeContainedOnlyTypesForAllLevels() {
    for (String key : List.of("cim", "pim", "psm")) {
      Map<String, Object> modelingLevel = level(key);
      Map<String, Map<String, Object>> elementsByType =
          listOfMaps(modelingLevel.get("elements")).stream()
              .collect(Collectors.toMap(item -> String.valueOf(item.get("type")), item -> item));
      for (Map<String, Object> view : listOfMaps(modelingLevel.get("viewDefinitions"))) {
        List<String> scopeTypes = stringList(view.get("scopeTypes"));
        for (String type : stringList(view.get("palette"))) {
          Map<String, Object> element = elementsByType.get(type);
          assertTrue(element != null, key + " " + view.get("id") + " unknown palette type " + type);
          if (scopeTypes.contains(type)) {
            continue;
          }
          assertFalse(
              Boolean.TRUE.equals(element.get("containedOnly")),
              key + " " + view.get("id") + " palette must not include contained-only type " + type);
        }
      }
    }
  }

  @Test
  void mutableContainmentsExposeCreatableChildren() {
    List<String> missing = new ArrayList<>();
    for (String key : List.of("cim", "pim", "psm")) {
      Map<String, Object> modelingLevel = level(key);
      List<Map<String, Object>> elements = listOfMaps(modelingLevel.get("elements"));
      Map<String, Object> palettes = map(modelingLevel.get("containmentPalettes"));
      for (Map<String, Object> owner : elements) {
        if (Boolean.TRUE.equals(owner.get("abstract"))
            || Boolean.TRUE.equals(owner.get("supportOnly"))) {
          continue;
        }
        String ownerType = String.valueOf(owner.get("type"));
        List<String> palette = stringList(map(palettes.get(ownerType)).get("types"));
        for (Map<String, Object> reference : listOfMaps(owner.get("references"))) {
          if (!Boolean.TRUE.equals(reference.get("containment"))
              || Boolean.TRUE.equals(reference.get("readonly"))) {
            continue;
          }
          String target = String.valueOf(reference.get("targetType"));
          List<String> candidates = new ArrayList<>();
          for (Map<String, Object> child : elements) {
            String childType = String.valueOf(child.get("type"));
            if ((target.equals(childType) || stringList(child.get("supertypes")).contains(target))
                && Boolean.TRUE.equals(child.get("creatable"))
                && !Boolean.TRUE.equals(child.get("abstract"))
                && !Boolean.TRUE.equals(child.get("supportOnly"))
                && !Boolean.TRUE.equals(child.get("relationshipElement"))) {
              candidates.add(childType);
            }
          }
          // Profiles may restrict an abstract target to its context-specific subtypes,
          // such as HTTP routes inside HttpApi, but must still expose the feature.
          if (!candidates.isEmpty() && candidates.stream().noneMatch(palette::contains)) {
            missing.add(key + " " + ownerType + "." + reference.get("name") + " -> " + target);
          }
        }
      }
    }
    assertTrue(
        missing.isEmpty(), "Missing containment palette entries:\n" + String.join("\n", missing));
  }

  @Test
  void sharedValuesAreCreatableOnlyInsideTheirOwners() {
    for (String key : List.of("cim", "pim", "psm")) {
      Map<String, Object> modelingLevel = level(key);
      for (Map<String, Object> element : listOfMaps(modelingLevel.get("elements"))) {
        String type = String.valueOf(element.get("type"));
        if (!List.of("Expression", "StructuredDocument", "Multiplicity", "Cardinality", "KeyValue")
            .contains(type)) {
          continue;
        }
        assertTrue(Boolean.TRUE.equals(element.get("creatable")), key + " " + type);
        assertEquals("Expressions & Values", element.get("category"), key + " " + type);
        assertFalse(Boolean.TRUE.equals(element.get("supportOnly")), key + " " + type);
        assertTrue(Boolean.TRUE.equals(element.get("containedOnly")), key + " " + type);
        for (Map<String, Object> view : listOfMaps(modelingLevel.get("viewDefinitions"))) {
          assertFalse(stringList(view.get("palette")).contains(type), key + " " + view.get("id"));
        }
      }
    }
    Map<String, Object> palettes = map(level("cim").get("containmentPalettes"));
    Map<String, Object> invariant = map(palettes.get("BusinessInvariant"));
    assertTrue(stringList(invariant.get("types")).contains("Expression"));
    assertTrue(stringList(invariant.get("canvas")).contains("Expression"));
    Map<String, Object> expressionFeature =
        listOfMaps(invariant.get("features")).stream()
            .filter(feature -> "expressionModel".equals(feature.get("feature")))
            .findFirst()
            .orElseThrow();
    assertEquals(Boolean.FALSE, expressionFeature.get("many"));
  }

  @Test
  void containerOwnersExposeContainmentPalettesForNestedTypes() {
    Map<String, Object> cimPalettes = map(level("cim").get("containmentPalettes"));
    assertTrue(
        stringList(map(cimPalettes.get("BusinessProcess")).get("types")).contains("StartStep"));
    assertTrue(
        stringList(map(cimPalettes.get("BusinessProcess")).get("types")).contains("DecisionTable"));
    assertFalse(
        stringList(map(cimPalettes.get("BusinessProcess")).get("types")).contains("DecisionRule"));
    assertTrue(
        stringList(map(cimPalettes.get("DecisionTable")).get("types")).contains("DecisionRule"));

    Map<String, Object> pimPalettes = map(level("pim").get("containmentPalettes"));
    assertTrue(stringList(map(pimPalettes.get("Api")).get("types")).contains("ApiRoute"));
    assertTrue(stringList(map(pimPalettes.get("Workflow")).get("types")).contains("StartStep"));
    assertTrue(stringList(map(pimPalettes.get("Workflow")).get("types")).contains("TaskStep"));
    assertTrue(stringList(map(pimPalettes.get("ServerlessService")).get("types")).contains("Api"));
    assertTrue(
        stringList(map(pimPalettes.get("ServerlessService")).get("types")).contains("Function"));

    Map<String, Object> psmPalettes = map(level("psm").get("containmentPalettes"));
    assertTrue(stringList(map(psmPalettes.get("SamStack")).get("types")).contains("Vpc"));
    assertTrue(stringList(map(psmPalettes.get("HttpApi")).get("types")).contains("HttpApiRoute"));
    assertTrue(
        stringList(map(psmPalettes.get("AwsLambdaFunction")).get("types"))
            .contains("LambdaPermission"));
  }

  @Test
  void workflowAndApiViewsOnlyExposeStandalonePaletteTypes() {
    Map<String, Object> pim = level("pim");
    List<String> workflowPalette = paletteForView(pim, "pim-workflow-designer");
    assertTrue(workflowPalette.contains("Workflow"));
    assertFalse(workflowPalette.contains("StartStep"));
    assertFalse(workflowPalette.contains("TaskStep"));
    assertFalse(workflowPalette.contains("HumanTask"));
    assertFalse(workflowPalette.contains("ApprovalTask"));
    assertFalse(workflowPalette.contains("EscalationPolicy"));

    List<String> apiPalette = paletteForView(pim, "pim-api-surface");
    assertTrue(apiPalette.contains("ServerlessService"));
    assertFalse(apiPalette.contains("Api"));
    assertFalse(apiPalette.contains("ApiRoute"));
    assertFalse(apiPalette.contains("ErrorMapping"));
  }

  @Test
  void psmTopologyAndNetworkingViewsOnlyExposeStandalonePaletteTypes() {
    Map<String, Object> psm = level("psm");
    List<String> topologyPalette = paletteForView(psm, "psm-resource-topology");
    assertTrue(topologyPalette.contains("SamStack"));
    assertTrue(topologyPalette.contains("AwsStage"));
    assertFalse(topologyPalette.contains("Vpc"));
    assertFalse(topologyPalette.contains("AwsLambdaFunction"));

    List<String> networkingPalette = paletteForView(psm, "psm-networking");
    assertFalse(networkingPalette.contains("Subnet"));
    assertFalse(networkingPalette.contains("SecurityGroupRule"));
  }

  private void assertNestedOnly(Map<String, Object> level, String... types) {
    Map<String, Map<String, Object>> elementsByType =
        listOfMaps(level.get("elements")).stream()
            .collect(Collectors.toMap(item -> String.valueOf(item.get("type")), item -> item));
    for (String type : types) {
      Map<String, Object> element = elementsByType.get(type);
      assertTrue(element != null, "Missing element definition for " + type);
      assertEquals(Boolean.TRUE, element.get("containedOnly"), type + " should be contained-only");
    }
  }

  private void assertRootStandalone(Map<String, Object> level, String... types) {
    Map<String, Map<String, Object>> elementsByType =
        listOfMaps(level.get("elements")).stream()
            .collect(Collectors.toMap(item -> String.valueOf(item.get("type")), item -> item));
    for (String type : types) {
      Map<String, Object> element = elementsByType.get(type);
      assertTrue(element != null, "Missing element definition for " + type);
      assertFalse(
          Boolean.TRUE.equals(element.get("containedOnly")), type + " should remain standalone");
    }
  }

  private List<String> paletteForView(Map<String, Object> level, String viewId) {
    return listOfMaps(level.get("viewDefinitions")).stream()
        .filter(view -> viewId.equals(view.get("id")))
        .findFirst()
        .map(view -> stringList(view.get("palette")))
        .orElseThrow();
  }

  private Map<String, Object> level(String key) {
    return map(map(service.config().get("levels")).get(key));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> listOfMaps(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    return list.stream()
        .filter(Map.class::isInstance)
        .map(item -> (Map<String, Object>) item)
        .toList();
  }

  private List<String> stringList(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    return list.stream().map(String::valueOf).toList();
  }
}
