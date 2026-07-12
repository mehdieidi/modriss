package io.mehdieidi.varka.platform.modeling.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests the merged modeling configuration exposed to the UI. */
class ModelingConfigServiceTest {

  /** Service under test. */
  private final ModelingConfigService service = new ModelingConfigService();

  /** Verifies that Ecore-derived structural fields coexist with JSON-owned UI metadata. */
  @Test
  void exposesEcoreDerivedStructureAndJsonOwnedUiMetadata() {
    Map<String, Object> pim = level("pim");
    List<Map<String, Object>> elements = listOfMaps(pim.get("elements"));

    Map<String, Object> function = element(elements, "Function");
    assertTrue(stringList(function.get("supertypes")).contains("FunctionTarget"));
    assertNotNull(function.get("attributes"));
    assertNotNull(function.get("references"));

    assertEquals("Function", function.get("label"));
    assertEquals("functions", function.get("icon"));
    assertEquals("#2563EB", function.get("color"));
    assertEquals("Compute", function.get("category"));

    assertTrue(
        listOfMaps(pim.get("semanticReferenceRules")).stream()
            .anyMatch(
                rule ->
                    "ApiRoute".equals(rule.get("sourceType"))
                        && "functionIntegration".equals(rule.get("feature"))
                        && "ROUTES_TO".equals(rule.get("kind"))));
  }

  /** Verifies that each level exposes the JSON-owned editor sections required by the frontend. */
  @Test
  void requiresJsonOwnedEditorSectionsForEveryLevel() {
    for (String key : List.of("cim", "pim", "psm")) {
      Map<String, Object> level = level(key);
      assertFalse(stringList(level.get("relationshipKinds")).isEmpty());
      assertNotNull(level.get("relationshipKindLabels"));
      assertFalse(listOfMaps(level.get("relationshipVisualRules")).isEmpty());
      assertNotNull(level.get("relationshipRules"));
      assertNotNull(level.get("viewDefinitions"));
      assertNotNull(level.get("rootTemplate"));
    }
  }

  /** Verifies that every Ecore type receives complete visual metadata. */
  @Test
  void appliesJsonOwnedVisualRulesToEveryEcoreType() {
    for (String key : List.of("cim", "pim", "psm")) {
      Map<String, Object> level = level(key);
      for (Map<String, Object> element : listOfMaps(level.get("elements"))) {
        assertFalse(Boolean.TRUE.equals(element.get("uiMetadataMissing")));
        assertFalse(String.valueOf(element.get("label")).isBlank());
        assertFalse(String.valueOf(element.get("icon")).isBlank());
        assertFalse(String.valueOf(element.get("color")).isBlank());
        assertFalse(String.valueOf(element.get("category")).isBlank());
        assertNotNull(element.get("notation"));
        assertNotNull(element.get("visualRole"));
      }
    }

    Map<String, Object> modelElement =
        element(listOfMaps(level("pim").get("elements")), "ModelElement");
    assertEquals(Boolean.TRUE, modelElement.get("supportOnly"));
    assertEquals(Boolean.FALSE, modelElement.get("creatable"));
  }

  /** Verifies that every classifier has an executable, backend-owned canvas notation. */
  @Test
  void exposesExecutableNotationForEveryClassifier() {
    for (String key : List.of("cim", "pim", "psm")) {
      Map<String, Object> level = level(key);
      assertFalse(listOfMaps(level.get("badgeRules")).isEmpty());
      assertFalse(stringList(level.get("relationshipLabelFields")).isEmpty());
      for (Map<String, Object> element : listOfMaps(level.get("elements"))) {
        Map<String, Object> notation = map(element.get("notation"));
        assertFalse(String.valueOf(notation.get("geometry")).isBlank());
        assertNotNull(notation.get("detailFields"));
        Map<String, Object> size = map(notation.get("size"));
        assertTrue(((Number) size.get("width")).intValue() >= 48);
        assertTrue(((Number) size.get("height")).intValue() >= 40);
      }
    }

    List<Map<String, Object>> psmElements = listOfMaps(level("psm").get("elements"));
    assertEquals("container", element(psmElements, "AwsStage").get("visualRole"));
    assertEquals("container", element(psmElements, "SamStack").get("visualRole"));
    assertEquals(
        "stage-container", map(element(psmElements, "AwsStage").get("notation")).get("shape"));

    Map<String, Object> command = element(listOfMaps(level("cim").get("elements")), "Command");
    assertEquals("hexagon", map(command.get("notation")).get("geometry"));
  }

  /** Verifies attributes, enums, references, and containments all have generic editor coverage. */
  @Test
  void exposesEditorCoverageForEveryStructuralFeature() {
    for (String key : List.of("cim", "pim", "psm")) {
      Map<String, Object> level = level(key);
      List<Map<String, Object>> elements = listOfMaps(level.get("elements"));
      List<String> gaps = new java.util.ArrayList<>();

      for (Map<String, Object> element : elements) {
        String type = String.valueOf(element.get("type"));
        for (Map<String, Object> attribute : listOfMaps(element.get("attributes"))) {
          if (String.valueOf(attribute.getOrDefault("fieldType", "")).isBlank()) {
            gaps.add(type + "." + attribute.get("name") + " missing fieldType");
          }
          if ("select".equals(attribute.get("fieldType"))
              && stringList(attribute.get("options")).isEmpty()) {
            gaps.add(type + "." + attribute.get("name") + " enum has no options");
          }
        }
        for (Map<String, Object> reference : listOfMaps(element.get("references"))) {
          String feature = String.valueOf(reference.get("name"));
          String targetType = String.valueOf(reference.get("targetType"));
          List<Map<String, Object>> targets =
              elements.stream()
                  .filter(
                      candidate ->
                          targetType.equals(candidate.get("type"))
                              || stringList(candidate.get("supertypes")).contains(targetType))
                  .toList();
          if (targets.isEmpty()) {
            gaps.add(type + "." + feature + " has no target type " + targetType);
          }
          if (Boolean.TRUE.equals(reference.get("containment"))
              && !Boolean.TRUE.equals(reference.get("readonly"))
              && targets.stream()
                  .noneMatch(target -> !Boolean.TRUE.equals(target.get("abstract")))) {
            gaps.add(type + "." + feature + " has no concrete contained type");
          }
        }
      }
      assertTrue(gaps.isEmpty(), key.toUpperCase() + " structural editor gaps: " + gaps);
    }
  }

  /** Verifies every PSM shortcut creates intermediates in valid Ecore containments. */
  @Test
  void shortcutIntermediatesHaveSemanticOwners() {
    Map<String, Object> psm = level("psm");
    List<Map<String, Object>> elements = listOfMaps(psm.get("elements"));
    for (Map<String, Object> rule : listOfMaps(psm.get("shortcutConnectorRules"))) {
      List<String> intermediates = stringList(rule.get("intermediateTypes"));
      List<Map<String, Object>> bindings = listOfMaps(rule.get("containmentBindings"));
      for (String intermediate : intermediates) {
        Map<String, Object> binding =
            bindings.stream()
                .filter(item -> intermediate.equals(item.get("type")))
                .findFirst()
                .orElseThrow(
                    () ->
                        new AssertionError(
                            rule.get("viewType") + " has no owner for " + intermediate));
        String owner = String.valueOf(binding.get("owner"));
        if ("scaffold".equals(owner)) {
          assertContainmentAccepts(
              elements,
              String.valueOf(
                  "source".equals(binding.get("scaffoldOwner"))
                      ? rule.get("sourceType")
                      : rule.get("targetType")),
              String.valueOf(binding.get("scaffoldFeature")),
              String.valueOf(binding.get("scaffoldType")));
          assertContainmentAccepts(
              elements,
              String.valueOf(binding.get("scaffoldType")),
              String.valueOf(binding.get("feature")),
              intermediate);
        } else {
          String ownerType =
              switch (owner) {
                case "source" -> String.valueOf(rule.get("sourceType"));
                case "target" -> String.valueOf(rule.get("targetType"));
                case "stack" -> "SamStack";
                default -> throw new AssertionError("Unknown shortcut owner " + owner);
              };
          assertContainmentAccepts(
              elements, ownerType, String.valueOf(binding.get("feature")), intermediate);
        }
      }
    }
  }

  /** Verifies every concrete classifier can occur below the configured model root. */
  @Test
  void everyConcreteClassifierHasAContainmentPath() {
    java.util.Set<String> reachableKernelTypes = new java.util.LinkedHashSet<>();
    java.util.Set<String> concreteKernelTypes = new java.util.LinkedHashSet<>();
    for (String key : List.of("cim", "pim", "psm")) {
      Map<String, Object> level = level(key);
      List<Map<String, Object>> elements = listOfMaps(level.get("elements"));
      java.util.Set<String> reachable = new java.util.LinkedHashSet<>();
      reachable.add(String.valueOf(map(level.get("rootTemplate")).get("eClass")));
      boolean changed;
      do {
        changed = false;
        for (Map<String, Object> owner : elements) {
          if (!reachable.contains(String.valueOf(owner.get("type")))) {
            continue;
          }
          for (Map<String, Object> reference : listOfMaps(owner.get("references"))) {
            if (!Boolean.TRUE.equals(reference.get("containment"))) {
              continue;
            }
            String targetType = String.valueOf(reference.get("targetType"));
            for (Map<String, Object> target : elements) {
              String candidate = String.valueOf(target.get("type"));
              if ((targetType.equals(candidate)
                      || stringList(target.get("supertypes")).contains(targetType))
                  && reachable.add(candidate)) {
                changed = true;
              }
            }
          }
        }
      } while (changed);
      List<String> unreachable =
          elements.stream()
              .filter(element -> !Boolean.TRUE.equals(element.get("abstract")))
              .filter(element -> !Boolean.TRUE.equals(element.get("interface")))
              .peek(
                  element -> {
                    if ("kernel".equals(element.get("package"))) {
                      concreteKernelTypes.add(String.valueOf(element.get("type")));
                    }
                  })
              .map(element -> String.valueOf(element.get("type")))
              .filter(type -> !reachable.contains(type))
              .toList();
      elements.stream()
          .filter(element -> "kernel".equals(element.get("package")))
          .map(element -> String.valueOf(element.get("type")))
          .filter(reachable::contains)
          .forEach(reachableKernelTypes::add);
      List<String> unreachableLevelTypes =
          unreachable.stream()
              .filter(
                  type ->
                      elements.stream()
                          .filter(element -> type.equals(element.get("type")))
                          .noneMatch(element -> "kernel".equals(element.get("package"))))
              .toList();
      assertTrue(
          unreachableLevelTypes.isEmpty(),
          key.toUpperCase() + " unreachable level types: " + unreachableLevelTypes);
    }
    concreteKernelTypes.removeAll(reachableKernelTypes);
    assertTrue(
        concreteKernelTypes.isEmpty(), "Shared kernel unreachable types: " + concreteKernelTypes);
  }

  /** Verifies that the API reports structural and view coverage for every concrete syntax. */
  @Test
  void exposesConcreteSyntaxCoverage() {
    for (String key : List.of("cim", "pim", "psm")) {
      Map<String, Object> level = level(key);
      Map<String, Object> coverage = map(level.get("syntaxCoverage"));
      assertEquals(listOfMaps(level.get("elements")).size(), coverage.get("elementCount"));
      assertTrue(((Number) coverage.get("attributeCount")).intValue() > 0);
      assertTrue(((Number) coverage.get("referenceCount")).intValue() > 0);
      assertTrue(((Number) coverage.get("containmentCount")).intValue() > 0);
      assertTrue(((Number) coverage.get("containerCount")).intValue() > 0);
      List<String> uncovered = stringList(coverage.get("uncoveredViewTypes"));
      assertTrue(uncovered.isEmpty(), key.toUpperCase() + " uncovered view types: " + uncovered);
      for (String gapField :
          List.of(
              "uncoveredEnumFields",
              "uncoveredReferenceFields",
              "uncoveredContainmentFields",
              "uncoveredRelationshipObjectTypes",
              "unknownViewRelationshipKinds")) {
        List<String> gaps = stringList(coverage.get(gapField));
        assertTrue(gaps.isEmpty(), key.toUpperCase() + " " + gapField + ": " + gaps);
      }

      java.util.Set<String> edgeObjectTypes =
          listOfMaps(level.get("semanticEdgeObjectRules")).stream()
              .map(rule -> String.valueOf(rule.get("eClass")))
              .collect(java.util.stream.Collectors.toSet());
      List<String> uncoveredRelationshipObjects =
          listOfMaps(level.get("elements")).stream()
              .filter(element -> Boolean.TRUE.equals(element.get("relationshipElement")))
              .filter(element -> !Boolean.TRUE.equals(element.get("abstract")))
              .filter(element -> !Boolean.TRUE.equals(element.get("supportOnly")))
              .map(element -> String.valueOf(element.get("type")))
              .filter(type -> !edgeObjectTypes.contains(type))
              .toList();
      assertTrue(
          uncoveredRelationshipObjects.isEmpty(),
          key.toUpperCase() + " uncovered relationship objects: " + uncoveredRelationshipObjects);
      List<Map<String, Object>> connectorRules = listOfMaps(level.get("relationshipRules"));
      for (Map<String, Object> edgeRule : listOfMaps(level.get("semanticEdgeObjectRules"))) {
        assertTrue(
            connectorRules.stream()
                .anyMatch(
                    connector ->
                        edgeRule.get("sourceType").equals(connector.get("sourceType"))
                            && edgeRule.get("targetType").equals(connector.get("targetType"))
                            && stringList(connector.get("allowedKinds"))
                                .containsAll(stringList(edgeRule.get("matchKinds")))),
            key.toUpperCase() + " edge object is not connectable: " + edgeRule.get("eClass"));
      }
    }
  }

  /** Verifies that view palettes list only visible standalone drag/drop types. */
  @Test
  void exposesCompleteStandaloneViewPalettes() {
    for (String key : List.of("cim", "pim", "psm")) {
      Map<String, Object> level = level(key);
      List<String> relationshipKinds = stringList(level.get("relationshipKinds"));
      Map<String, Map<String, Object>> elementsByType =
          listOfMaps(level.get("elements")).stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      item -> String.valueOf(item.get("type")), item -> item));
      List<String> unknownViewKinds = new java.util.ArrayList<>();
      for (Map<String, Object> view : listOfMaps(level.get("viewDefinitions"))) {
        List<String> elementTypes = stringList(view.get("elementTypes"));
        List<String> palette = stringList(view.get("palette"));
        for (String kind : stringList(view.get("relationshipKinds"))) {
          if (!relationshipKinds.contains(kind)) {
            unknownViewKinds.add(view.get("id") + ":" + kind);
          }
        }
        assertTrue(elementTypes.containsAll(palette), view.get("id") + " palette must be visible");
        for (String type : palette) {
          Map<String, Object> element = elementsByType.get(type);
          assertNotNull(element, view.get("id") + " unknown palette type " + type);
          assertEquals(Boolean.TRUE, element.get("creatable"));
          assertFalse(Boolean.TRUE.equals(element.get("abstract")));
          assertFalse(Boolean.TRUE.equals(element.get("relationshipElement")));
          assertFalse(
              Boolean.TRUE.equals(element.get("containedOnly"))
                  && !stringList(view.get("scopeTypes")).contains(type));
          assertFalse(Boolean.TRUE.equals(element.get("supportOnly")));
        }
      }
      assertTrue(
          unknownViewKinds.isEmpty(),
          key.toUpperCase() + " unknown view kinds: " + unknownViewKinds);
    }
  }

  /** Verifies that root templates are sourced from JSON metadata. */
  @Test
  void rootTemplatesComeFromJsonMetadata() {
    assertEquals("CIMModel", map(level("cim").get("rootTemplate")).get("eClass"));
    assertEquals("PIMModel", map(level("pim").get("rootTemplate")).get("eClass"));
    assertEquals("AwsPsmModel", map(level("psm").get("rootTemplate")).get("eClass"));
  }

  /** Verifies that new elements do not receive an invalid empty enum literal. */
  @Test
  void leavesEnumAttributesUnsetByDefault() {
    Map<String, Object> goal = element(listOfMaps(level("cim").get("elements")), "BusinessGoal");
    Map<String, Object> priority = attribute(goal, "priority");

    assertEquals("select", priority.get("fieldType"));
    assertNull(priority.get("defaultValue"));
    assertTrue(stringList(priority.get("options")).contains("MEDIUM"));
  }

  /** Verifies every graph-facing rule uses one declared canonical relationship vocabulary. */
  @Test
  void relationshipKindsAreCanonicalAndFullyConfigured() {
    for (String key : List.of("cim", "pim", "psm")) {
      Map<String, Object> level = level(key);
      List<String> kinds = stringList(level.get("relationshipKinds"));
      assertEquals(kinds.size(), kinds.stream().distinct().count(), key + " duplicate kinds");
      assertEquals(
          kinds.stream().collect(java.util.stream.Collectors.toSet()),
          map(level.get("relationshipKindLabels")).keySet(),
          key + " label vocabulary");
      Map<String, Object> semantics = map(level.get("relationshipSemantics"));
      assertTrue(
          kinds.contains(String.valueOf(semantics.get("containmentKind"))),
          key + " containmentKind");
      assertTrue(kinds.contains(String.valueOf(semantics.get("traceKind"))), key + " traceKind");
      assertTrue(
          kinds.containsAll(stringList(semantics.get("containmentKinds"))),
          key + " containmentKinds");

      for (Map<String, Object> rule : listOfMaps(level.get("semanticReferenceRules"))) {
        assertTrue(
            kinds.contains(String.valueOf(rule.get("kind"))), key + " reference rule " + rule);
      }
      for (Map<String, Object> rule : listOfMaps(level.get("relationshipRules"))) {
        assertTrue(
            kinds.containsAll(stringList(rule.get("allowedKinds"))),
            key + " connector rule " + rule);
      }
      for (Map<String, Object> rule : listOfMaps(level.get("semanticEdgeObjectRules"))) {
        List<String> matchKinds = stringList(rule.get("matchKinds"));
        assertFalse(
            matchKinds.isEmpty(), key + " edge object without kinds: " + rule.get("eClass"));
        assertTrue(kinds.containsAll(matchKinds), key + " edge object rule " + rule);
        if (matchKinds.size() > 1) {
          assertTrue(
              rule.containsKey("kindField") || rule.containsKey("defaultKind"),
              key + " ambiguous edge object kind: " + rule.get("eClass"));
        }
        if (rule.containsKey("defaultKind")) {
          assertTrue(matchKinds.contains(String.valueOf(rule.get("defaultKind"))));
        }
        for (Object mappedKind : mapOrEmpty(rule.get("kindMap")).values()) {
          assertTrue(matchKinds.contains(String.valueOf(mappedKind)), key + " kindMap " + rule);
        }
      }
      for (Map<String, Object> view : listOfMaps(level.get("viewDefinitions"))) {
        assertTrue(
            kinds.containsAll(stringList(view.get("relationshipKinds"))),
            key + " view " + view.get("id"));
      }
      for (Map<String, Object> rule : listOfMaps(level.get("relationshipVisualRules"))) {
        assertTrue(
            kinds.containsAll(stringList(rule.get("matchKinds"))), key + " visual rule " + rule);
      }
      for (Map<String, Object> rule : listOfMaps(level.get("shortcutConnectorRules"))) {
        assertTrue(kinds.containsAll(stringList(rule.get("edgeKinds"))), key + " shortcut " + rule);
        if (rule.containsKey("summaryKind")) {
          assertTrue(
              kinds.contains(String.valueOf(rule.get("summaryKind"))),
              key + " shortcut summary " + rule);
        }
      }
    }
  }

  /**
   * Verifies connector rules are derived from the metamodel instead of catch-all JSON wildcards.
   */
  @Test
  void relationshipRulesComeFromMetamodelReferences() {
    Map<String, Object> cim = level("cim");
    List<Map<String, Object>> rules = listOfMaps(cim.get("relationshipRules"));
    assertFalse(rules.isEmpty());
    assertTrue(
        rules.stream()
            .filter(rule -> !rule.containsKey("edgeObjectType"))
            .noneMatch(this::isCatchAllRelationshipRule),
        "Catch-all wildcard connector rules must not be exposed to the UI");

    List<String> requirementKinds =
        rules.stream()
            .filter(
                rule ->
                    "Requirement".equals(rule.get("sourceType"))
                        && "Requirement".equals(rule.get("targetType")))
            .flatMap(rule -> stringList(rule.get("allowedKinds")).stream())
            .distinct()
            .toList();
    assertTrue(requirementKinds.contains("DEPENDS_ON"));
    assertTrue(requirementKinds.contains("CONFLICTS_WITH"));
    assertTrue(requirementKinds.contains("REFINES"));
  }

  /**
   * Verifies connector rules never expose containment features and reference metadata is accurate.
   */
  @Test
  void relationshipRulesExcludeContainmentAndReferenceKindsAreAccurate() {
    for (String key : List.of("cim", "pim", "psm")) {
      Map<String, Object> level = level(key);
      List<Map<String, Object>> rules = listOfMaps(level.get("relationshipRules"));
      assertTrue(
          rules.stream().noneMatch(rule -> Boolean.TRUE.equals(rule.get("containment"))),
          key.toUpperCase() + " connector rules must not include containment refs");

      for (Map<String, Object> element : listOfMaps(level.get("elements"))) {
        for (Map<String, Object> reference : listOfMaps(element.get("references"))) {
          boolean containment = Boolean.TRUE.equals(reference.get("containment"));
          String kind = String.valueOf(reference.get("kind"));
          if (containment) {
            assertEquals("containment", kind, element.get("type") + "." + reference.get("name"));
          } else if (!Boolean.TRUE.equals(reference.get("readonly"))) {
            assertEquals("reference", kind, element.get("type") + "." + reference.get("name"));
          }
        }
      }

      java.util.Set<String> semanticFeatures = new java.util.LinkedHashSet<>();
      for (Map<String, Object> rule : listOfMaps(level.get("semanticReferenceRules"))) {
        String featureKey =
            rule.get("sourceType") + "." + rule.get("feature") + "->" + rule.get("targetType");
        assertTrue(
            semanticFeatures.add(featureKey),
            key.toUpperCase() + " duplicate semantic reference rule: " + featureKey);
      }
    }
  }

  /** Verifies class-based relationship objects from metamodels are connectable in PIM and PSM. */
  @Test
  void classBasedRelationshipObjectsAreConnectable() {
    Map<String, Object> pim = level("pim");
    List<Map<String, Object>> pimRules = listOfMaps(pim.get("relationshipRules"));
    assertTrue(
        pimRules.stream()
            .anyMatch(
                rule ->
                    "ServerlessService".equals(rule.get("sourceType"))
                        && "DeployableElement".equals(rule.get("targetType"))
                        && stringList(rule.get("allowedKinds"))
                            .containsAll(List.of("OWNS", "EXPOSES"))),
        "ServiceElementMembership must be connectable");
    assertTrue(
        pimRules.stream()
            .anyMatch(
                rule ->
                    "InvocationSource".equals(rule.get("sourceType"))
                        && "RoutingTarget".equals(rule.get("targetType"))
                        && stringList(rule.get("allowedKinds")).contains("TARGETS")),
        "EventRoutingRule must be connectable");

    Map<String, Object> psm = level("psm");
    List<Map<String, Object>> psmRules = listOfMaps(psm.get("relationshipRules"));
    assertTrue(
        psmRules.stream()
            .anyMatch(
                rule ->
                    "AwsResource".equals(rule.get("sourceType"))
                        && "AwsResource".equals(rule.get("targetType"))
                        && stringList(rule.get("allowedKinds")).contains("DEPENDS_ON")),
        "AwsResource dependsOn must be connectable");
  }

  private boolean isCatchAllRelationshipRule(Map<String, Object> rule) {
    return "*".equals(String.valueOf(rule.getOrDefault("sourceType", "")))
        && "*".equals(String.valueOf(rule.getOrDefault("targetType", "")));
  }

  /**
   * Returns one level configuration from the service output.
   *
   * @param key level key
   * @return level configuration
   */
  private Map<String, Object> level(String key) {
    return map(map(service.config().get("levels")).get(key));
  }

  /**
   * Finds an element metadata entry by type.
   *
   * @param elements element metadata list
   * @param type element type
   * @return matching element metadata
   */
  private Map<String, Object> element(List<Map<String, Object>> elements, String type) {
    return elements.stream()
        .filter(item -> type.equals(item.get("type")))
        .findFirst()
        .orElseThrow();
  }

  /**
   * Finds an attribute metadata entry by name.
   *
   * @param element element metadata
   * @param name attribute name
   * @return matching attribute metadata
   */
  private Map<String, Object> attribute(Map<String, Object> element, String name) {
    return listOfMaps(element.get("attributes")).stream()
        .filter(item -> name.equals(item.get("name")))
        .findFirst()
        .orElseThrow();
  }

  /** Asserts that one owner containment accepts the requested concrete child type. */
  private void assertContainmentAccepts(
      List<Map<String, Object>> elements, String ownerType, String feature, String childType) {
    Map<String, Object> owner = element(elements, ownerType);
    Map<String, Object> reference =
        listOfMaps(owner.get("references")).stream()
            .filter(item -> feature.equals(item.get("name")))
            .findFirst()
            .orElseThrow(() -> new AssertionError(ownerType + " has no containment " + feature));
    assertEquals(Boolean.TRUE, reference.get("containment"), ownerType + "." + feature);
    String targetType = String.valueOf(reference.get("targetType"));
    Map<String, Object> child = element(elements, childType);
    assertTrue(
        targetType.equals(childType) || stringList(child.get("supertypes")).contains(targetType),
        ownerType + "." + feature + " cannot contain " + childType);
  }

  /**
   * Casts a value to a map after asserting it is present.
   *
   * @param value value to cast
   * @return cast map
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> map(Object value) {
    assertNotNull(value);
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> mapOrEmpty(Object value) {
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
  }

  /** Verifies container owners expose backend-derived containment palettes for nested editing. */
  @Test
  void exposesContainmentPalettesForContainerOwners() {
    Map<String, Object> pim = level("pim");
    Map<String, Object> palettes = map(pim.get("containmentPalettes"));
    assertFalse(palettes.isEmpty());

    Map<String, Object> apiPalette = map(palettes.get("Api"));
    List<String> apiTypes = stringList(apiPalette.get("types"));
    assertTrue(apiTypes.contains("ApiRoute"));

    boolean functionIsContainable =
        palettes.values().stream()
            .filter(Map.class::isInstance)
            .map(value -> map(value))
            .map(entry -> stringList(entry.get("types")))
            .anyMatch(types -> types.contains("Function"));
    assertTrue(functionIsContainable);

    Map<String, Object> canvasPolicy = map(pim.get("canvasPolicy"));
    Map<String, Object> containerFocus = map(canvasPolicy.get("containerFocus"));
    assertEquals("FOCUS", containerFocus.get("viewKind"));
    assertEquals("CONTAINER", containerFocus.get("scopeKind"));
    assertEquals("CONTAINER_FOCUS", containerFocus.get("layoutProfile"));
    assertTrue(stringList(canvasPolicy.get("standalonePaletteRoles")).contains("node"));
  }

  /** Verifies CVS v2 loading for CIM with element mappings and diagram editor config. */
  @Test
  void exposesCvsV2ElementMappingsAndDiagramEditorConfig() {
    Map<String, Object> config = service.config();
    Map<String, Object> diagramEditor = map(config.get("diagramEditor"));
    assertEquals("antv-g6", diagramEditor.get("renderer"));

    for (String levelKey : List.of("cim", "pim", "psm")) {
      Map<String, Object> level = level(levelKey);
      assertEquals(2, level.get("cvsVersion"));
      assertFalse(listOfMaps(level.get("elementMappings")).isEmpty());
      assertFalse(listOfMaps(level.get("cvsReferenceMappings")).isEmpty());
      assertFalse(listOfMaps(level.get("viewDefinitions")).isEmpty());
    }

    Map<String, Object> cim = level("cim");
    assertTrue(
        listOfMaps(cim.get("elementMappings")).stream()
            .anyMatch(
                mapping ->
                    "BusinessGoal".equals(map(mapping.get("match")).get("eClass"))
                        && mapping.get("visualRole") != null));
  }

  /** Verifies diagram editor settings can be overridden via environment variables. */
  @Test
  void diagramEditorConfigHonorsEnvironmentOverrides() {
    Map<String, Object> platform = Map.of("diagramEditor", Map.of("renderer", "antv-g6"));
    Map<String, Object> diagramEditor = service.diagramEditorConfig(platform, "antv-g6");
    assertEquals("antv-g6", diagramEditor.get("renderer"));
  }

  /** Verifies invalid renderer env values fall back to platform config. */
  @Test
  void diagramEditorConfigIgnoresInvalidRendererOverride() {
    Map<String, Object> platform = Map.of("diagramEditor", Map.of("renderer", "antv-g6"));
    Map<String, Object> diagramEditor = service.diagramEditorConfig(platform, "unknown-renderer");
    assertEquals("antv-g6", diagramEditor.get("renderer"));
  }

  /** Verifies PIM CVS exposes workflow/service container notation. */
  @Test
  void pimCvsIncludesServiceAndWorkflowViewpoints() {
    Map<String, Object> pim = level("pim");
    List<String> viewIds =
        listOfMaps(pim.get("viewDefinitions")).stream()
            .map(view -> String.valueOf(view.get("id")))
            .toList();
    assertTrue(viewIds.stream().anyMatch(id -> id.contains("service")));
    assertTrue(viewIds.stream().anyMatch(id -> id.contains("workflow")));
    assertFalse(listOfMaps(pim.get("relationshipVisualRules")).isEmpty());
  }

  /** Verifies semantic dashboard layout columns are owned by CVS, not backend Java code. */
  @Test
  void cimDashboardCvsOwnsSemanticLayoutColumns() {
    Map<String, Object> cim = level("cim");
    Map<String, Object> dashboard =
        listOfMaps(cim.get("viewDefinitions")).stream()
            .filter(view -> "dashboard".equals(view.get("id")))
            .findFirst()
            .orElseThrow();
    List<?> columns = (List<?>) dashboard.get("semanticDashboardColumns");
    assertNotNull(columns);
    assertFalse(columns.isEmpty());
  }

  /** Verifies PSM CVS exposes shortcut connector rules for integration views. */
  @Test
  void psmCvsIncludesShortcutConnectorRules() {
    Map<String, Object> psm = level("psm");
    assertFalse(listOfMaps(psm.get("shortcutConnectorRules")).isEmpty());
    assertTrue(
        listOfMaps(psm.get("elementMappings")).stream()
            .anyMatch(
                mapping -> "AwsLambdaFunction".equals(map(mapping.get("match")).get("eClass"))));
  }

  /**
   * Casts a value to a list of maps after asserting it is present.
   *
   * @param value value to cast
   * @return cast list
   */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> listOfMaps(Object value) {
    assertNotNull(value);
    return (List<Map<String, Object>>) value;
  }

  /**
   * Casts a value to a string list after asserting it is present.
   *
   * @param value value to cast
   * @return cast list
   */
  @SuppressWarnings("unchecked")
  private List<String> stringList(Object value) {
    assertNotNull(value);
    return (List<String>) value;
  }
}
