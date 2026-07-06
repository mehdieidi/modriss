package io.mehdieidi.modless.platform.modeling.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    assertNestedOnly(level("pim"), "WorkflowState", "ApiRoute", "ErrorMapping", "ParallelBranch");
    assertRootStandalone(level("pim"), "Workflow", "Api", "Function", "HumanTask");

    assertNestedOnly(
        level("psm"),
        "Subnet",
        "SecurityGroupRule",
        "CfnParameter",
        "LambdaEnvironmentVariable",
        "NativeProperty");
    assertRootStandalone(level("psm"), "SamStack", "AwsStage");
  }

  @Test
  void viewPalettesExcludeContainedOnlyTypesForAllLevels() {
    for (String key : List.of("cim", "pim", "psm")) {
      Map<String, Object> modelingLevel = level(key);
      Map<String, Map<String, Object>> elementsByType =
          listOfMaps(modelingLevel.get("elements")).stream()
              .collect(Collectors.toMap(item -> String.valueOf(item.get("type")), item -> item));
      for (Map<String, Object> view : listOfMaps(modelingLevel.get("viewDefinitions"))) {
        for (String type : stringList(view.get("palette"))) {
          Map<String, Object> element = elementsByType.get(type);
          assertTrue(element != null, key + " " + view.get("id") + " unknown palette type " + type);
          assertFalse(
              Boolean.TRUE.equals(element.get("containedOnly")),
              key + " " + view.get("id") + " palette must not include contained-only type " + type);
        }
      }
    }
  }

  @Test
  void containerOwnersExposeContainmentPalettesForNestedTypes() {
    Map<String, Object> cimPalettes = map(level("cim").get("containmentPalettes"));
    assertTrue(
        stringList(map(cimPalettes.get("BusinessProcess")).get("types")).contains("StartStep"));

    Map<String, Object> pimPalettes = map(level("pim").get("containmentPalettes"));
    assertTrue(stringList(map(pimPalettes.get("Api")).get("types")).contains("ApiRoute"));
    assertTrue(stringList(map(pimPalettes.get("Workflow")).get("types")).contains("WorkflowState"));

    Map<String, Object> psmPalettes = map(level("psm").get("containmentPalettes"));
    assertTrue(stringList(map(psmPalettes.get("SamStack")).get("types")).contains("Vpc"));
    assertTrue(
        stringList(map(psmPalettes.get("SecurityGroup")).get("types"))
            .contains("SecurityGroupRule"));
  }

  @Test
  void workflowAndApiViewsOnlyExposeStandalonePaletteTypes() {
    Map<String, Object> pim = level("pim");
    List<String> workflowPalette = paletteForView(pim, "pim-workflow");
    assertTrue(workflowPalette.contains("Workflow"));
    assertFalse(workflowPalette.contains("WorkflowState"));
    assertFalse(workflowPalette.contains("WorkflowTransition"));

    List<String> apiPalette = paletteForView(pim, "pim-api-surface");
    assertTrue(apiPalette.contains("Api"));
    assertFalse(apiPalette.contains("ApiRoute"));
    assertFalse(apiPalette.contains("ErrorMapping"));
  }

  @Test
  void psmTopologyAndNetworkingViewsOnlyExposeStandalonePaletteTypes() {
    Map<String, Object> psm = level("psm");
    List<String> topologyPalette = paletteForView(psm, "psm-resource-topology");
    assertTrue(topologyPalette.contains("SamStack"));
    assertFalse(topologyPalette.contains("Vpc"));
    assertFalse(topologyPalette.contains("AwsLambdaFunction"));

    List<String> networkingPalette = paletteForView(psm, "psm-networking-security");
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
    return map(service.config().get(key));
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
