package io.mehdieidi.modless.platform.modeling.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import io.mehdieidi.modless.platform.modeling.runtime.MdeRuntimeOptions;
import io.mehdieidi.modless.platform.modeling.runtime.MdeRuntimePaths;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Builds the modeling UI configuration by merging JSON-owned visual metadata with structure derived
 * from Ecore metamodels.
 */
public final class ModelingConfigService {

  /** Mapper used to read modeling metadata resources. */
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** Loader for CVS v2 notation documents. */
  private final CvsV2Loader cvsLoader = new CvsV2Loader();

  /**
   * Returns the complete modeling configuration for all supported levels.
   *
   * @return configuration map consumed by the platform UI
   */
  public Map<String, Object> config() {
    Map<String, Object> platform = readPlatformConfig();
    Map<String, Object> configuredLevels = optionalMap(platform, "levels");
    Map<String, Object> levels = new LinkedHashMap<>();
    for (String key : configuredLevelOrder(platform, configuredLevels)) {
      Map<String, Object> configuredLevel = optionalMap(configuredLevels, key);
      Map<String, Object> mergedLevel = new LinkedHashMap<>(configuredLevel);
      mergeInto(mergedLevel, level(key));
      mergedLevel.putIfAbsent("apiType", key);
      mergedLevel.putIfAbsent("chatType", key.toUpperCase());
      mergedLevel.putIfAbsent("modelNameTemplate", key + "-model");
      levels.put(key, mergedLevel);
    }
    return Map.ofEntries(
        Map.entry("version", platform.getOrDefault("version", 0)),
        Map.entry(
            "dynamicPersistenceEnabled",
            !Boolean.FALSE.equals(platform.get("dynamicPersistenceEnabled"))),
        Map.entry("defaultLevel", platform.getOrDefault("defaultLevel", firstKey(levels))),
        Map.entry("levelOrder", new ArrayList<>(levels.keySet())),
        Map.entry("levels", levels),
        Map.entry("transformations", optionalMap(platform, "transformations")),
        Map.entry("artifactAction", optionalMap(platform, "artifactAction")),
        Map.entry("impactAnalysis", optionalMap(platform, "impactAnalysis")),
        Map.entry("layoutStrategies", optionalList(platform, "layoutStrategies")),
        Map.entry("diagramEditor", diagramEditorConfig(platform)));
  }

  /**
   * Creates a starter model from the JSON-owned starter template for a level.
   *
   * @param level modeling level
   * @param name model name
   * @return interpolated starter model
   */
  public ObjectNode starterModel(ModelLevel level, String name) {
    String key = level.apiName();
    Map<String, Object> metadata = readMetadata(key);
    Map<String, Object> template = optionalMap(metadata, "starterTemplate");
    if (template.isEmpty()) {
      template = requireMap(metadata, "rootTemplate", key);
    }
    String modelName = name == null || name.isBlank() ? key + "-starter-model" : name;
    JsonNode node = objectMapper.valueToTree(template).deepCopy();
    JsonNode interpolated = interpolateTemplate(node, modelName);
    if (!(interpolated instanceof ObjectNode root)) {
      throw new PlatformException(500, "Modeling starterTemplate for " + key + " must be object.");
    }
    root.put("name", modelName);
    if (!root.hasNonNull("id")) {
      root.put("id", safeIdentifier(modelName + "-root"));
    }
    ObjectNode diagram =
        root.path("diagram").isObject()
            ? (ObjectNode) root.path("diagram")
            : root.putObject("diagram");
    if (!diagram.path("elements").isArray()) {
      diagram.putArray("elements");
    }
    if (!diagram.path("relationships").isArray()) {
      diagram.putArray("relationships");
    }
    if (!root.path("views").isArray()) {
      root.putArray("views");
    }
    if (!root.path("fragments").isArray()) {
      root.putArray("fragments");
    }
    return root;
  }

  /**
   * Returns structural metamodel elements read directly from combined Ecore for a level.
   *
   * <p>Assistant contract indexing uses this as the canonical structural source so retrieval and
   * ModelDelta schema generation do not depend on UI metadata drift.
   *
   * @param level modeling level
   * @return Ecore-derived element metadata maps
   */
  public List<Map<String, Object>> ecoreDerivedElements(ModelLevel level) {
    String key = level.apiName();
    Map<String, Object> metadata = readMetadata(key);
    String rootType =
        String.valueOf(requireMap(metadata, "rootTemplate", key).getOrDefault("eClass", ""));
    return readEcoreMetamodel(key, rootType).elements();
  }

  /**
   * Builds one level configuration from UI metadata and Ecore-derived structure.
   *
   * @param key lowercase level key
   * @return level configuration map
   */
  private Map<String, Object> level(String key) {
    Map<String, Object> metadata = readMetadata(key);
    metadata = mergeEcoreStructure(key, metadata);
    List<String> relationshipKinds = relationshipKinds(metadata);
    Map<String, Object> syntaxCoverage = syntaxCoverage(metadata);
    List<Map<String, Object>> elementMaps =
        requireList(metadata, "elements", key).stream()
            .filter(Map.class::isInstance)
            .map(item -> stringKeyMap((Map<?, ?>) item))
            .toList();
    LinkedHashSet<String> relationshipElementTypes = new LinkedHashSet<>();
    for (Map<String, Object> element : elementMaps) {
      if (Boolean.TRUE.equals(element.get("relationshipElement"))) {
        relationshipElementTypes.add(String.valueOf(element.get("type")));
      }
    }
    Map<String, Object> containmentPalettes =
        buildContainmentPalettes(elementMaps, relationshipElementTypes);
    cvsLoader.validateElementCoverage(key, elementMaps);
    List<Map<String, Object>> elementMappings = cvsLoader.buildElementMappings(elementMaps);
    List<Map<String, Object>> cvsReferenceMappings =
        buildReferenceMappings(requireList(metadata, "semanticReferenceRules", key));
    return Map.ofEntries(
        Map.entry("displayName", metadata.getOrDefault("displayName", key.toUpperCase())),
        Map.entry("elementsPath", "/diagram/elements"),
        Map.entry("relationshipsPath", "/diagram/relationships"),
        Map.entry("labelField", "name"),
        Map.entry("relationshipKinds", relationshipKinds),
        Map.entry("relationshipSemantics", requireMap(metadata, "relationshipSemantics", key)),
        Map.entry(
            "relationshipLabelFields",
            metadata.getOrDefault("relationshipLabelFields", List.of("name"))),
        Map.entry("elements", requireList(metadata, "elements", key)),
        Map.entry("relationshipRules", requireList(metadata, "relationshipRules", key)),
        Map.entry("relationshipKindLabels", requireMap(metadata, "relationshipKindLabels", key)),
        Map.entry(
            "relationshipVisualRules", metadata.getOrDefault("relationshipVisualRules", List.of())),
        Map.entry("badgeRules", metadata.getOrDefault("badgeRules", List.of())),
        Map.entry(
            "semanticReferenceRules", metadata.getOrDefault("semanticReferenceRules", List.of())),
        Map.entry(
            "semanticReferenceKindMappings",
            metadata.getOrDefault("semanticReferenceKindMappings", Map.of())),
        Map.entry(
            "semanticReferenceExclusions",
            metadata.getOrDefault("semanticReferenceExclusions", List.of())),
        Map.entry(
            "semanticEdgeObjectRules", metadata.getOrDefault("semanticEdgeObjectRules", List.of())),
        Map.entry(
            "shortcutConnectorRules", metadata.getOrDefault("shortcutConnectorRules", List.of())),
        Map.entry("workbench", metadata.getOrDefault("workbench", Map.of())),
        Map.entry("scaffoldRecipes", metadata.getOrDefault("scaffoldRecipes", List.of())),
        Map.entry("boundedContext", metadata.getOrDefault("boundedContext", Map.of())),
        Map.entry("viewDefinitions", requireList(metadata, "viewDefinitions", key)),
        Map.entry("universalSyntax", metadata.getOrDefault("universalSyntax", List.of())),
        Map.entry("kernelSyntax", metadata.getOrDefault("kernelSyntax", List.of())),
        Map.entry(
            "kernelNotation",
            metadata.getOrDefault(
                "kernelNotation", metadata.getOrDefault("kernelSyntax", List.of()))),
        Map.entry("complexityManagement", metadata.getOrDefault("complexityManagement", List.of())),
        Map.entry("canvasPolicy", completeCanvasPolicy(metadata)),
        Map.entry("containmentPalettes", containmentPalettes),
        Map.entry("syntaxCoverage", syntaxCoverage),
        Map.entry("elementMappings", elementMappings),
        Map.entry("cvsVersion", metadata.getOrDefault("cvsVersion", 1)),
        Map.entry("cvsPrimitives", metadata.getOrDefault("cvsPrimitives", Map.of())),
        Map.entry("cvsReferenceMappings", cvsReferenceMappings),
        Map.entry(
            "cvsRelationshipMappings", metadata.getOrDefault("cvsRelationshipMappings", List.of())),
        Map.entry("cvsMetamodelRef", metadata.getOrDefault("cvsMetamodelRef", Map.of())),
        Map.entry(
            "strictnessModes",
            metadata.getOrDefault(
                "strictnessModes", List.of("exploration", "methodology", "production"))),
        Map.entry("constraints", metadata.getOrDefault("constraints", List.of())),
        Map.entry("rootTemplate", requireMap(metadata, "rootTemplate", key)),
        Map.entry("starterTemplate", optionalMap(metadata, "starterTemplate")));
  }

  /**
   * Merges structural Ecore data into JSON-owned UI metadata.
   *
   * @param key lowercase level key
   * @param metadata metadata loaded from resources
   * @return merged metadata
   */
  private Map<String, Object> mergeEcoreStructure(String key, Map<String, Object> metadata) {
    Map<String, Object> merged = new LinkedHashMap<>(metadata);
    String rootType =
        String.valueOf(requireMap(metadata, "rootTemplate", key).getOrDefault("eClass", ""));
    CimMetamodel metamodel = readEcoreMetamodel(key, rootType);
    List<Map<String, Object>> mergedElements =
        mergeElements(key, metamodel.elements(), requireList(metadata, "elements", key), metadata);
    inferContainedOnlyFlags(mergedElements, rootType);
    inferApiGatewayRouteContainedOnly(mergedElements);
    for (Map<String, Object> element : mergedElements) {
      element.put("visualRole", visualRole(element));
    }
    merged.put("elements", mergedElements);
    List<Map<String, Object>> semanticEdgeObjectRules =
        mergeSemanticEdgeObjectRules(
            optionalList(metadata, "semanticEdgeObjectRules"),
            requireList(merged, "elements", key),
            metamodel.rootContainmentFeatures());
    merged.put("semanticEdgeObjectRules", semanticEdgeObjectRules);
    List<Map<String, Object>> relationshipRules =
        mergeRelationshipRules(
            optionalList(metadata, "relationshipRules"),
            optionalMap(metadata, "semanticReferenceKindMappings"),
            objectStringList(metadata.get("semanticReferenceExclusions")),
            semanticEdgeObjectRules,
            objectStringList(metadata.get("relationshipKinds")),
            String.valueOf(
                requireMap(metadata, "relationshipSemantics", key).get("containmentKind")),
            metamodel.relationshipRules());
    relationshipRules =
        mergeEdgeObjectRelationshipRules(relationshipRules, semanticEdgeObjectRules);
    merged.put("relationshipRules", relationshipRules);
    merged.put(
        "semanticReferenceRules",
        mergeSemanticReferenceRules(
            optionalList(metadata, "semanticReferenceRules"),
            optionalMap(metadata, "semanticReferenceKindMappings"),
            objectStringList(metadata.get("semanticReferenceExclusions")),
            semanticEdgeObjectRules,
            objectStringList(metadata.get("relationshipKinds")),
            metamodel.semanticReferenceRules()));
    merged.put(
        "viewDefinitions",
        normalizeViewDefinitions(
            requireList(metadata, "viewDefinitions", key),
            requireList(merged, "elements", key),
            standalonePaletteRoles(optionalMap(metadata, "canvasPolicy"))));
    merged.put(
        "relationshipKinds",
        mergeRelationshipKinds(metadata, requireList(merged, "relationshipRules", key)));
    merged.put(
        "relationshipKindLabels",
        relationshipKindLabels(
            requireMap(metadata, "relationshipKindLabels", key),
            requireList(merged, "relationshipKinds", key)));
    requireList(merged, "viewDefinitions", key);
    requireMap(merged, "rootTemplate", key);
    return merged;
  }

  /** Makes every class-based relationship available from the connector legal-kind matrix. */
  private List<Map<String, Object>> mergeEdgeObjectRelationshipRules(
      List<Map<String, Object>> relationshipRules, List<?> edgeObjectRules) {
    List<Map<String, Object>> result = new ArrayList<>(relationshipRules);
    for (Object item : edgeObjectRules) {
      if (!(item instanceof Map<?, ?> raw)) {
        continue;
      }
      Map<String, Object> edgeRule = stringKeyMap(raw);
      String sourceType = String.valueOf(edgeRule.getOrDefault("sourceType", ""));
      String targetType = String.valueOf(edgeRule.getOrDefault("targetType", ""));
      List<String> kinds = objectStringList(edgeRule.get("matchKinds"));
      if (sourceType.isBlank() || targetType.isBlank() || kinds.isEmpty()) {
        continue;
      }
      Map<String, Object> connectorRule =
          Map.of(
              "sourceType", sourceType,
              "targetType", targetType,
              "allowedKinds", kinds,
              "edgeObjectType", String.valueOf(edgeRule.getOrDefault("eClass", "")));
      if (!result.contains(connectorRule)) {
        result.add(connectorRule);
      }
    }
    return result;
  }

  /**
   * Completes each view palette from its related element types and ensures every palette type is
   * visible in that view. Only standalone node/container concepts are draggable from the canvas
   * palette; relationship, contained-detail, support, and abstract concepts use their dedicated
   * syntax.
   *
   * @param configuredViews JSON-owned view definitions
   * @param elements merged Ecore/UI element definitions
   * @return normalized view definitions
   */
  private List<Map<String, Object>> normalizeViewDefinitions(
      List<?> configuredViews, List<?> elements, List<String> standalonePaletteRoles) {
    Map<String, Map<String, Object>> elementsByType = new LinkedHashMap<>();
    for (Object item : elements) {
      if (item instanceof Map<?, ?> raw) {
        Map<String, Object> element = stringKeyMap(raw);
        elementsByType.put(String.valueOf(element.getOrDefault("type", "")), element);
      }
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : configuredViews) {
      if (!(item instanceof Map<?, ?> raw)) {
        throw new PlatformException(500, "Modeling viewDefinitions entries must be objects.");
      }
      Map<String, Object> view = stringKeyMap(raw);
      List<String> configuredPalette = objectStringList(view.get("palette"));
      List<String> scopeTypes = objectStringList(view.get("scopeTypes"));
      LinkedHashSet<String> relatedTypes =
          new LinkedHashSet<>(objectStringList(view.get("elementTypes")));
      LinkedHashSet<String> palette = new LinkedHashSet<>();
      if (!configuredPalette.isEmpty()) {
        for (String type : configuredPalette) {
          Map<String, Object> element = elementsByType.get(type);
          if (element == null) {
            continue;
          }
          if (standalonePaletteElement(element, standalonePaletteRoles)
              || (scopeTypes.contains(type) && paletteEntryTypeAllowed(element))) {
            palette.add(type);
          }
        }
        relatedTypes.addAll(palette);
      } else if (!scopeTypes.isEmpty()) {
        relatedTypes.addAll(configuredPalette);
      } else {
        relatedTypes.addAll(configuredPalette);
        for (String relatedType : relatedTypes) {
          for (Map<String, Object> element : elementsByType.values()) {
            if (typeMatches(relatedType, element)
                && standalonePaletteElement(element, standalonePaletteRoles)) {
              palette.add(String.valueOf(element.get("type")));
            }
          }
        }
        palette.removeIf(
            type -> {
              Map<String, Object> element = elementsByType.get(type);
              return element == null || !standalonePaletteElement(element, standalonePaletteRoles);
            });
        relatedTypes.addAll(palette);
      }
      view.put("elementTypes", new ArrayList<>(relatedTypes));
      view.put("palette", new ArrayList<>(palette));
      result.add(view);
    }
    return result;
  }

  /**
   * Checks whether an element type satisfies an exact or inherited view type.
   *
   * @param expectedType view type selector
   * @param element merged element definition
   * @return whether the element belongs to the selector
   */
  private boolean typeMatches(String expectedType, Map<String, Object> element) {
    return expectedType.equals(element.get("type"))
        || objectStringList(element.get("supertypes")).contains(expectedType);
  }

  /**
   * Checks whether an element is valid as a standalone draggable canvas item.
   *
   * @param element merged element definition
   * @return whether the element belongs in a view palette
   */
  private boolean standalonePaletteElement(
      Map<String, Object> element, List<String> standalonePaletteRoles) {
    return Boolean.TRUE.equals(element.get("creatable"))
        && !Boolean.TRUE.equals(element.get("abstract"))
        && !Boolean.TRUE.equals(element.get("relationshipElement"))
        && !Boolean.TRUE.equals(element.get("containedOnly"))
        && !Boolean.TRUE.equals(element.get("supportOnly"))
        && standalonePaletteRoles.contains(
            String.valueOf(element.getOrDefault("visualRole", "node")));
  }

  private boolean paletteEntryTypeAllowed(Map<String, Object> element) {
    return Boolean.TRUE.equals(element.get("creatable"))
        && !Boolean.TRUE.equals(element.get("abstract"))
        && !Boolean.TRUE.equals(element.get("relationshipElement"))
        && !Boolean.TRUE.equals(element.get("supportOnly"));
  }

  /**
   * Marks types that are only reachable through nested {@code val} containment as {@code
   * containedOnly}. Applied to every modeling level (CIM, PIM, PSM) during Ecore merge.
   *
   * <p>Root-contained types remain standalone palette candidates. UI metadata may explicitly keep a
   * root-contained type draggable by setting {@code containedOnly} to {@code false}.
   *
   * @param elements merged element metadata
   * @param rootType root model class name
   */
  private void inferContainedOnlyFlags(List<Map<String, Object>> elements, String rootType) {
    Map<String, Map<String, Object>> elementsByType = new LinkedHashMap<>();
    for (Map<String, Object> element : elements) {
      elementsByType.put(String.valueOf(element.get("type")), element);
    }
    Set<String> rootContainable =
        containmentTargetTypes(elementsByType.get(rootType), elementsByType);
    Set<String> nestedContainable = new LinkedHashSet<>();
    for (Map<String, Object> owner : elements) {
      if (rootType.equals(String.valueOf(owner.get("type")))) {
        continue;
      }
      nestedContainable.addAll(detailContainmentTargetTypes(owner, elementsByType));
    }
    Set<String> inferContainedOnly = new LinkedHashSet<>(nestedContainable);
    inferContainedOnly.removeAll(rootContainable);
    for (Map<String, Object> element : elements) {
      String type = String.valueOf(element.get("type"));
      if (!inferContainedOnly.contains(type)) {
        continue;
      }
      if (element.containsKey("containedOnly")
          && Boolean.FALSE.equals(element.get("containedOnly"))
          && rootContainable.contains(type)) {
        continue;
      }
      if (!Boolean.TRUE.equals(element.get("containedOnly"))) {
        element.put("containedOnly", true);
      }
    }
  }

  /**
   * Collects contained types that belong inside a focus container, excluding membership buckets
   * such as {@code SamStack.resources} where children remain draggable on level views.
   *
   * @param owner owner element metadata
   * @param elementsByType element lookup by type
   * @return detail-contained concrete type names
   */
  private Set<String> detailContainmentTargetTypes(
      Map<String, Object> owner, Map<String, Map<String, Object>> elementsByType) {
    Set<String> result = new LinkedHashSet<>();
    if (owner == null) {
      return result;
    }
    for (Object item : optionalList(owner, "references")) {
      if (!(item instanceof Map<?, ?> raw)) {
        continue;
      }
      Map<String, Object> reference = stringKeyMap(raw);
      if (!Boolean.TRUE.equals(reference.get("containment"))
          || Boolean.TRUE.equals(reference.get("readonly"))
          || isMembershipContainmentReference(owner, reference)) {
        continue;
      }
      String targetType = String.valueOf(reference.getOrDefault("targetType", ""));
      if (targetType.isBlank()) {
        continue;
      }
      result.addAll(concreteTypesFor(elementsByType, targetType));
    }
    return result;
  }

  /**
   * Checks whether a containment reference groups deployable resources on a view canvas rather than
   * hiding them as inner-only details.
   *
   * @param owner owner element metadata
   * @param reference containment reference metadata
   * @return whether children stay standalone palette candidates
   */
  private boolean isMembershipContainmentReference(
      Map<String, Object> owner, Map<String, Object> reference) {
    String shape = String.valueOf(optionalMap(owner, "notation").getOrDefault("shape", ""));
    String feature = String.valueOf(reference.getOrDefault("name", ""));
    return "stack-container".equals(shape) && "resources".equals(feature);
  }

  /**
   * Marks concrete API Gateway route types as container-only because routes are owned by APIs via
   * composition references rather than root/stack placement.
   *
   * @param elements merged element metadata
   */
  private void inferApiGatewayRouteContainedOnly(List<Map<String, Object>> elements) {
    for (Map<String, Object> element : elements) {
      if (Boolean.TRUE.equals(element.get("abstract"))) {
        continue;
      }
      String type = String.valueOf(element.get("type"));
      if (!type.endsWith("Route")) {
        continue;
      }
      if (objectStringList(element.get("supertypes")).contains("ApiGatewayRoute")) {
        element.put("containedOnly", true);
      }
    }
  }

  /**
   * Collects concrete types accepted by an owner's mutable containment references.
   *
   * @param owner owner element metadata
   * @param elementsByType element lookup by type
   * @return contained concrete type names
   */
  private Set<String> containmentTargetTypes(
      Map<String, Object> owner, Map<String, Map<String, Object>> elementsByType) {
    Set<String> result = new LinkedHashSet<>();
    if (owner == null) {
      return result;
    }
    for (Object item : optionalList(owner, "references")) {
      if (!(item instanceof Map<?, ?> raw)) {
        continue;
      }
      Map<String, Object> reference = stringKeyMap(raw);
      if (!Boolean.TRUE.equals(reference.get("containment"))
          || Boolean.TRUE.equals(reference.get("readonly"))) {
        continue;
      }
      String targetType = String.valueOf(reference.getOrDefault("targetType", ""));
      if (targetType.isBlank()) {
        continue;
      }
      result.addAll(concreteTypesFor(elementsByType, targetType));
    }
    return result;
  }

  private List<String> standalonePaletteRoles(Map<String, Object> canvasPolicy) {
    List<String> roles = objectStringList(canvasPolicy.get("standalonePaletteRoles"));
    if (roles.isEmpty()) {
      return List.of("node", "container");
    }
    return roles;
  }

  /**
   * Completes canvas interaction policy with defaults for palette roles and container focus.
   *
   * @param metadata merged level metadata
   * @return normalized canvas policy
   */
  private Map<String, Object> completeCanvasPolicy(Map<String, Object> metadata) {
    Map<String, Object> policy = new LinkedHashMap<>(optionalMap(metadata, "canvasPolicy"));
    policy.putIfAbsent("standalonePaletteRoles", List.of("node", "container"));
    Map<String, Object> containerFocus = new LinkedHashMap<>(optionalMap(policy, "containerFocus"));
    containerFocus.putIfAbsent("viewKind", "FOCUS");
    containerFocus.putIfAbsent("scopeKind", "CONTAINER");
    containerFocus.putIfAbsent("layoutProfile", "CONTAINER_FOCUS");
    policy.put("containerFocus", containerFocus);
    return policy;
  }

  /**
   * Builds per-owner containment palettes from merged element references.
   *
   * @param elements merged element definitions
   * @param relationshipElementTypes relationship object types excluded from drag/drop palettes
   * @return owner type to palette types and containment features
   */
  private Map<String, Object> buildContainmentPalettes(
      List<Map<String, Object>> elements, Set<String> relationshipElementTypes) {
    Map<String, Map<String, Object>> elementsByType = new LinkedHashMap<>();
    for (Map<String, Object> element : elements) {
      elementsByType.put(String.valueOf(element.get("type")), element);
    }
    Map<String, Object> palettes = new LinkedHashMap<>();
    for (Map<String, Object> owner : elements) {
      boolean hasPaletteExtras = !optionalList(owner, "containmentPaletteExtras").isEmpty();
      if (!hasContainment(owner) && !hasPaletteExtras) {
        continue;
      }
      String ownerType = String.valueOf(owner.get("type"));
      LinkedHashSet<String> paletteTypes = new LinkedHashSet<>();
      List<Map<String, Object>> features = new ArrayList<>();
      for (Object item : optionalList(owner, "references")) {
        if (!(item instanceof Map<?, ?> raw)) {
          continue;
        }
        Map<String, Object> reference = stringKeyMap(raw);
        if (!Boolean.TRUE.equals(reference.get("containment"))
            || Boolean.TRUE.equals(reference.get("readonly"))) {
          continue;
        }
        String feature = String.valueOf(reference.getOrDefault("name", ""));
        String targetType = String.valueOf(reference.getOrDefault("targetType", ""));
        if (feature.isBlank() || targetType.isBlank()) {
          continue;
        }
        List<String> types =
            concreteTypesFor(elementsByType, targetType).stream()
                .filter(
                    type ->
                        containmentPaletteElement(
                            elementsByType.get(type), relationshipElementTypes))
                .toList();
        if (types.isEmpty() || isRelationshipOnlyContainment(types, relationshipElementTypes)) {
          continue;
        }
        paletteTypes.addAll(types);
        features.add(
            Map.ofEntries(
                Map.entry("feature", feature),
                Map.entry("targetType", targetType),
                Map.entry("types", new ArrayList<>(types)),
                Map.entry(
                    "many",
                    reference.get("many") == null || Boolean.TRUE.equals(reference.get("many")))));
      }
      paletteTypes.addAll(
          containmentPaletteExtras(owner, elementsByType, relationshipElementTypes));
      if (!paletteTypes.isEmpty()) {
        palettes.put(
            ownerType,
            Map.ofEntries(
                Map.entry("types", new ArrayList<>(paletteTypes)),
                Map.entry("features", features)));
      }
    }
    return palettes;
  }

  /**
   * Adds UI-configured palette types for referenced concepts that are edited inside a focus
   * container even when they are not val-owned by that container.
   *
   * @param owner owner element metadata
   * @param elementsByType element lookup by type
   * @param relationshipElementTypes relationship object types excluded from palettes
   * @return extra palette type names
   */
  private Set<String> containmentPaletteExtras(
      Map<String, Object> owner,
      Map<String, Map<String, Object>> elementsByType,
      Set<String> relationshipElementTypes) {
    LinkedHashSet<String> paletteTypes = new LinkedHashSet<>();
    for (Object item : optionalList(owner, "containmentPaletteExtras")) {
      String extraType = String.valueOf(item);
      if (extraType.isBlank()) {
        continue;
      }
      List<String> types =
          concreteTypesFor(elementsByType, extraType).stream()
              .filter(
                  type ->
                      containmentPaletteElement(elementsByType.get(type), relationshipElementTypes))
              .toList();
      paletteTypes.addAll(types);
    }
    return paletteTypes;
  }

  private List<String> concreteTypesFor(
      Map<String, Map<String, Object>> elementsByType, String expectedType) {
    List<String> result = new ArrayList<>();
    for (Map<String, Object> element : elementsByType.values()) {
      if (Boolean.TRUE.equals(element.get("abstract"))) {
        continue;
      }
      if (typeMatches(expectedType, element)) {
        result.add(String.valueOf(element.get("type")));
      }
    }
    return result.isEmpty() ? List.of(expectedType) : result;
  }

  private boolean containmentPaletteElement(
      Map<String, Object> element, Set<String> relationshipElementTypes) {
    if (element == null || Boolean.TRUE.equals(element.get("abstract"))) {
      return false;
    }
    if (Boolean.TRUE.equals(element.get("supportOnly"))
        || Boolean.TRUE.equals(element.get("relationshipElement"))) {
      return false;
    }
    String type = String.valueOf(element.get("type"));
    return !relationshipElementTypes.contains(type);
  }

  private boolean isRelationshipOnlyContainment(
      List<String> types, Set<String> relationshipElementTypes) {
    return !types.isEmpty() && types.stream().allMatch(relationshipElementTypes::contains);
  }

  /**
   * Combines Ecore element structure with UI visual metadata for each type.
   *
   * @param key lowercase level key
   * @param structuralElements Ecore-derived element descriptions
   * @param uiElements JSON-owned UI element metadata
   * @param metadata full metadata map containing defaults and rules
   * @return merged element metadata
   */
  private List<Map<String, Object>> mergeElements(
      String key,
      List<Map<String, Object>> structuralElements,
      List<?> uiElements,
      Map<String, Object> metadata) {
    Map<String, Map<String, Object>> uiByType = new LinkedHashMap<>();
    for (Object item : uiElements) {
      if (!(item instanceof Map<?, ?> raw)) {
        throw new PlatformException(
            500, "Modeling UI metadata element entries must be objects for " + key + ".");
      }
      Object type = raw.get("type");
      if (type == null || String.valueOf(type).isBlank()) {
        throw new PlatformException(
            500, "Modeling UI metadata element is missing type for " + key + ".");
      }
      uiByType.put(String.valueOf(type), stringKeyMap(raw));
    }

    Map<String, Object> visualDefaults = optionalMap(metadata, "elementVisualDefaults");
    List<Map<String, Object>> visualRules =
        optionalList(metadata, "elementVisualRules").stream()
            .map(
                item -> {
                  if (item instanceof Map<?, ?> raw) {
                    return stringKeyMap(raw);
                  }
                  throw new PlatformException(
                      500, "Modeling UI metadata visual rules must be objects for " + key + ".");
                })
            .toList();
    List<Map<String, Object>> elements = new ArrayList<>();
    for (Map<String, Object> structural : structuralElements) {
      String type = String.valueOf(structural.get("type"));
      Map<String, Object> ui = uiByType.get(type);
      Map<String, Object> merged = new LinkedHashMap<>(structural);
      mergeInto(merged, visualDefaults);
      for (Map<String, Object> rule : visualRules) {
        if (visualRuleMatches(rule, structural)) {
          mergeInto(merged, optionalMap(rule, "metadata"));
        }
      }
      if (ui != null) {
        mergeInto(merged, ui);
        if (Boolean.TRUE.equals(ui.get("containedOnly"))) {
          merged.put("containedOnly", true);
        }
      }
      completeElementVisualMetadata(type, merged, metadata);
      requireElementVisualMetadata(key, type, merged);
      elements.add(merged);
    }
    return elements;
  }

  /**
   * Deep-merges one metadata map into another for nested object values.
   *
   * @param target target map to mutate
   * @param source source values to merge
   */
  @SuppressWarnings("unchecked")
  private void mergeInto(Map<String, Object> target, Map<String, Object> source) {
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      Object value = entry.getValue();
      Object existing = target.get(entry.getKey());
      if (existing instanceof Map<?, ?> existingMap && value instanceof Map<?, ?> valueMap) {
        Map<String, Object> nested = new LinkedHashMap<>();
        existingMap.forEach((key, nestedValue) -> nested.put(String.valueOf(key), nestedValue));
        valueMap.forEach((key, nestedValue) -> nested.put(String.valueOf(key), nestedValue));
        target.put(entry.getKey(), nested);
      } else {
        target.put(entry.getKey(), value);
      }
    }
  }

  /**
   * Checks whether an element visual rule applies to an Ecore-derived element.
   *
   * @param rule visual rule metadata
   * @param element structural element metadata
   * @return {@code true} when the rule matches
   */
  private boolean visualRuleMatches(Map<String, Object> rule, Map<String, Object> element) {
    Map<String, Object> match = optionalMap(rule, "match");
    if (match.isEmpty()) {
      return false;
    }
    String type = String.valueOf(element.getOrDefault("type", ""));
    String packageName = String.valueOf(element.getOrDefault("package", ""));
    List<String> supertypes = objectStringList(element.get("supertypes"));
    if (!matchesAny(match.get("packages"), packageName)) {
      return false;
    }
    if (!matchesAny(match.get("types"), type)) {
      return false;
    }
    if (!matchesAny(match.get("supertypes"), supertypes)) {
      return false;
    }
    if (!matchesTypeAffixes(match, type)) {
      return false;
    }
    Object abstractMatch = match.get("abstract");
    return !(abstractMatch instanceof Boolean expected) || expected.equals(element.get("abstract"));
  }

  /**
   * Matches a scalar actual value against an optional list of expected values.
   *
   * @param expected configured expected values
   * @param actual actual value
   * @return {@code true} when no expectation is configured or the value matches
   */
  private boolean matchesAny(Object expected, String actual) {
    List<String> values = objectStringList(expected);
    return values.isEmpty() || values.contains(actual);
  }

  /**
   * Matches any actual value against an optional list of expected values.
   *
   * @param expected configured expected values
   * @param actual actual values
   * @return {@code true} when no expectation is configured or any value matches
   */
  private boolean matchesAny(Object expected, List<String> actual) {
    List<String> values = objectStringList(expected);
    return values.isEmpty() || actual.stream().anyMatch(values::contains);
  }

  /**
   * Matches type prefix, suffix, and substring selectors.
   *
   * @param match rule match object
   * @param type Ecore type name
   * @return {@code true} when all configured affix checks match
   */
  private boolean matchesTypeAffixes(Map<String, Object> match, String type) {
    List<String> prefixes = objectStringList(match.get("typePrefixes"));
    if (!prefixes.isEmpty() && prefixes.stream().noneMatch(type::startsWith)) {
      return false;
    }
    List<String> suffixes = objectStringList(match.get("typeSuffixes"));
    if (!suffixes.isEmpty() && suffixes.stream().noneMatch(type::endsWith)) {
      return false;
    }
    List<String> contains = objectStringList(match.get("typeContains"));
    return contains.isEmpty() || contains.stream().anyMatch(type::contains);
  }

  /**
   * Fills missing visual metadata with safe defaults.
   *
   * @param type element type name
   * @param element element metadata to mutate
   */
  private void completeElementVisualMetadata(
      String type, Map<String, Object> element, Map<String, Object> metadata) {
    element.putIfAbsent("label", humanize(type));
    element.putIfAbsent("displayName", element.get("label"));
    element.putIfAbsent("icon", "category");
    element.putIfAbsent("color", "#475569");
    element.putIfAbsent("category", "Metamodel");
    if (!element.containsKey("notation")) {
      element.put(
          "notation",
          Map.of(
              "tag",
              stereotypeToken(type),
              "shape",
              "concept-card",
              "lineFields",
              element.getOrDefault("visibleFields", List.of())));
    }
    if (!element.containsKey("creatable")) {
      element.put("creatable", !Boolean.TRUE.equals(element.get("abstract")));
    }
    element.putIfAbsent("relationshipElement", Boolean.FALSE);
    element.putIfAbsent("containedOnly", Boolean.FALSE);
    element.putIfAbsent("supportOnly", Boolean.FALSE);
    element.putIfAbsent("visualRole", visualRole(element));
    completeNotationMetadata(element, metadata);
  }

  /**
   * Completes the executable notation contract consumed by the generic canvas renderer.
   *
   * @param element merged element metadata
   * @param metadata level metadata containing canvas and primitive policies
   */
  private void completeNotationMetadata(Map<String, Object> element, Map<String, Object> metadata) {
    Map<String, Object> notation = new LinkedHashMap<>(optionalMap(element, "notation"));
    Map<String, Object> primitive =
        optionalMap(
            optionalMap(metadata, "notationPrimitives"), String.valueOf(notation.get("shape")));
    mergeMissing(notation, primitive);

    Map<String, Object> canvasPolicy = optionalMap(metadata, "canvasPolicy");
    Map<String, Object> roleSizes = optionalMap(canvasPolicy, "roleSizes");
    Map<String, Object> roleSize =
        optionalMap(roleSizes, String.valueOf(element.getOrDefault("visualRole", "node")));
    if (roleSize.isEmpty()) {
      roleSize = optionalMap(roleSizes, "node");
    }
    if (!notation.containsKey("size") && !roleSize.isEmpty()) {
      notation.put("size", new LinkedHashMap<>(roleSize));
    }
    notation.putIfAbsent("geometry", "rectangle");
    notation.putIfAbsent("cornerRadius", 8);
    notation.putIfAbsent("detailFields", notation.getOrDefault("lineFields", List.of()));
    element.put("notation", notation);
  }

  /** Adds values that are not already defined by type-specific notation metadata. */
  private void mergeMissing(Map<String, Object> target, Map<String, Object> defaults) {
    for (Map.Entry<String, Object> entry : defaults.entrySet()) {
      target.putIfAbsent(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Derives the primary visual role used by generic frontend interactions.
   *
   * @param element merged structural and visual element metadata
   * @return relationship, support, detail, container, or node
   */
  private String visualRole(Map<String, Object> element) {
    if (Boolean.TRUE.equals(element.get("relationshipElement"))) {
      return "relationship";
    }
    if (Boolean.TRUE.equals(element.get("supportOnly"))
        || Boolean.TRUE.equals(element.get("abstract"))) {
      return "support";
    }
    if (Boolean.TRUE.equals(element.get("containedOnly"))) {
      return "detail";
    }
    if (hasContainment(element)) {
      return "container";
    }
    return "node";
  }

  /**
   * Checks whether an element owns at least one mutable containment feature.
   *
   * @param element merged element metadata
   * @return whether the element acts as a semantic container
   */
  private boolean hasContainment(Map<String, Object> element) {
    for (Object item : optionalList(element, "references")) {
      if (item instanceof Map<?, ?> raw
          && Boolean.TRUE.equals(raw.get("containment"))
          && !Boolean.TRUE.equals(raw.get("readonly"))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Builds a machine-readable coverage report for the delivered concrete syntax.
   *
   * @param metadata complete merged level metadata
   * @return syntax coverage counts and uncovered view types
   */
  private Map<String, Object> syntaxCoverage(Map<String, Object> metadata) {
    List<?> elements = optionalList(metadata, "elements");
    List<Map<String, Object>> elementMaps =
        elements.stream()
            .filter(Map.class::isInstance)
            .map(item -> stringKeyMap((Map<?, ?>) item))
            .toList();
    Set<String> referenceExclusions =
        new LinkedHashSet<>(objectStringList(metadata.get("semanticReferenceExclusions")));
    LinkedHashSet<String> edgeObjectTypes = new LinkedHashSet<>();
    for (Object item : optionalList(metadata, "semanticEdgeObjectRules")) {
      if (item instanceof Map<?, ?> raw) {
        edgeObjectTypes.add(String.valueOf(raw.get("eClass")));
      }
    }
    int attributes = 0;
    int enumAttributes = 0;
    int references = 0;
    int containments = 0;
    int relationshipElements = 0;
    int containers = 0;
    LinkedHashSet<String> viewTypes = new LinkedHashSet<>();
    for (Object item : optionalList(metadata, "viewDefinitions")) {
      if (item instanceof Map<?, ?> raw) {
        viewTypes.addAll(objectStringList(raw.get("elementTypes")));
        viewTypes.addAll(objectStringList(raw.get("palette")));
      }
    }
    List<String> uncoveredViewTypes = new ArrayList<>();
    List<String> uncoveredEnumFields = new ArrayList<>();
    List<String> uncoveredReferenceFields = new ArrayList<>();
    List<String> uncoveredContainmentFields = new ArrayList<>();
    List<String> uncoveredRelationshipObjectTypes = new ArrayList<>();
    LinkedHashSet<String> knownKinds = new LinkedHashSet<>(relationshipKinds(metadata));
    List<String> unknownViewRelationshipKinds = new ArrayList<>();
    for (Object item : optionalList(metadata, "viewDefinitions")) {
      if (item instanceof Map<?, ?> raw) {
        for (String kind : objectStringList(raw.get("relationshipKinds"))) {
          if (!knownKinds.contains(kind)) {
            unknownViewRelationshipKinds.add(raw.get("id") + ":" + kind);
          }
        }
      }
    }
    for (Object item : elements) {
      if (!(item instanceof Map<?, ?> raw)) {
        continue;
      }
      Map<String, Object> element = stringKeyMap(raw);
      String type = String.valueOf(raw.containsKey("type") ? raw.get("type") : "");
      List<?> elementAttributes = optionalList(element, "attributes");
      attributes += elementAttributes.size();
      for (Object attributeItem : elementAttributes) {
        if (!(attributeItem instanceof Map<?, ?> attribute)) {
          continue;
        }
        if ("select".equals(attribute.get("fieldType"))) {
          enumAttributes++;
          if (objectStringList(attribute.get("options")).isEmpty()) {
            uncoveredEnumFields.add(type + "." + attribute.get("name"));
          }
        }
      }
      List<?> elementReferences = optionalList(stringKeyMap(raw), "references");
      references += elementReferences.size();
      containments +=
          (int)
              elementReferences.stream()
                  .filter(
                      reference ->
                          reference instanceof Map<?, ?> referenceMap
                              && Boolean.TRUE.equals(referenceMap.get("containment")))
                  .count();
      for (Object referenceItem : elementReferences) {
        if (!(referenceItem instanceof Map<?, ?> reference)
            || Boolean.TRUE.equals(reference.get("readonly"))) {
          continue;
        }
        String feature = String.valueOf(reference.get("name"));
        String targetType = String.valueOf(reference.get("targetType"));
        boolean hasConcreteTarget =
            elementMaps.stream()
                .anyMatch(
                    target ->
                        !Boolean.TRUE.equals(target.get("abstract"))
                            && (targetType.equals(target.get("type"))
                                || objectStringList(target.get("supertypes"))
                                    .contains(targetType)));
        if (Boolean.TRUE.equals(reference.get("containment"))) {
          if (!hasConcreteTarget) {
            uncoveredContainmentFields.add(type + "." + feature);
          }
        } else if (!edgeObjectTypes.contains(type) && !referenceExclusions.contains(feature)) {
          if (!hasConcreteTarget) {
            uncoveredReferenceFields.add(type + "." + feature);
          }
        }
      }
      if (Boolean.TRUE.equals(raw.get("relationshipElement"))) {
        relationshipElements++;
      }
      if ("container".equals(raw.get("visualRole"))) {
        containers++;
      }
      if (Boolean.TRUE.equals(raw.get("relationshipElement"))
          && !Boolean.TRUE.equals(raw.get("abstract"))
          && !Boolean.TRUE.equals(raw.get("supportOnly"))
          && !edgeObjectTypes.contains(type)) {
        uncoveredRelationshipObjectTypes.add(type);
      }
      if (Boolean.TRUE.equals(raw.get("creatable"))
          && !Boolean.TRUE.equals(raw.get("containedOnly"))
          && !Boolean.TRUE.equals(raw.get("supportOnly"))
          && !Boolean.TRUE.equals(raw.get("relationshipElement"))
          && !viewTypes.contains(type)) {
        uncoveredViewTypes.add(type);
      }
    }
    return Map.ofEntries(
        Map.entry("elementCount", elements.size()),
        Map.entry("attributeCount", attributes),
        Map.entry("enumAttributeCount", enumAttributes),
        Map.entry("referenceCount", references),
        Map.entry("containmentCount", containments),
        Map.entry("relationshipElementCount", relationshipElements),
        Map.entry("containerCount", containers),
        Map.entry("viewCount", optionalList(metadata, "viewDefinitions").size()),
        Map.entry("uncoveredViewTypes", uncoveredViewTypes),
        Map.entry("uncoveredEnumFields", uncoveredEnumFields),
        Map.entry("uncoveredReferenceFields", uncoveredReferenceFields),
        Map.entry("uncoveredContainmentFields", uncoveredContainmentFields),
        Map.entry("uncoveredRelationshipObjectTypes", uncoveredRelationshipObjectTypes),
        Map.entry("unknownViewRelationshipKinds", unknownViewRelationshipKinds));
  }

  /**
   * Derives a short stereotype-like token from a CamelCase type name.
   *
   * @param type element type name
   * @return uppercase stereotype token
   */
  private String stereotypeToken(String type) {
    StringBuilder token = new StringBuilder();
    for (String word : type.split("(?=[A-Z])")) {
      if (!word.isBlank()) {
        token.append(Character.toUpperCase(word.charAt(0)));
      }
      if (token.length() == 4) {
        break;
      }
    }
    return token.isEmpty() ? type.toUpperCase() : token.toString();
  }

  /**
   * Ensures required visual fields exist for a UI element.
   *
   * @param key lowercase level key
   * @param type element type name
   * @param item merged element metadata
   */
  private void requireElementVisualMetadata(String key, String type, Map<String, Object> item) {
    for (String field : List.of("label", "icon", "color", "category")) {
      Object value = item.get(field);
      if (value == null || String.valueOf(value).isBlank()) {
        throw new PlatformException(
            500,
            "Modeling UI metadata for "
                + key.toUpperCase()
                + " type "
                + type
                + " is missing "
                + field
                + ".");
      }
    }
  }

  /**
   * Copies a raw map into a map with string keys.
   *
   * @param raw raw metadata map
   * @return map with string keys
   */
  private Map<String, Object> stringKeyMap(Map<?, ?> raw) {
    Map<String, Object> result = new LinkedHashMap<>();
    raw.forEach((key, value) -> result.put(String.valueOf(key), value));
    return result;
  }

  /**
   * Reads an optional list metadata field.
   *
   * @param metadata metadata map
   * @param field field name
   * @return list value or an empty list
   */
  private List<?> optionalList(Map<String, Object> metadata, String field) {
    Object value = metadata.get(field);
    if (value == null) {
      return List.of();
    }
    if (value instanceof List<?> list) {
      return list;
    }
    throw new PlatformException(500, "Modeling UI metadata field '" + field + "' must be a list.");
  }

  /**
   * Reads an optional object metadata field.
   *
   * @param metadata metadata map
   * @param field field name
   * @return object value or an empty map
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> optionalMap(Map<String, Object> metadata, String field) {
    Object value = metadata.get(field);
    if (value == null) {
      return Map.of();
    }
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) stringKeyMap(map);
    }
    throw new PlatformException(
        500, "Modeling UI metadata field '" + field + "' must be an object.");
  }

  /**
   * Converts a scalar or list value to a list of non-blank strings.
   *
   * @param value raw value
   * @return string list
   */
  private List<String> objectStringList(Object value) {
    if (value == null) {
      return List.of();
    }
    if (value instanceof List<?> list) {
      return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
    }
    String text = String.valueOf(value);
    return text.isBlank() ? List.of() : List.of(text);
  }

  /**
   * Merges configured and Ecore-derived relationship rules by stable key.
   *
   * @param configuredRules JSON-owned rules
   * @param ecoreRules Ecore-derived rules
   * @return merged rules
   */
  private List<Map<String, Object>> mergeRelationshipRules(
      List<?> configuredRules,
      Map<String, Object> configuredKindMappings,
      List<String> configuredExclusions,
      List<?> edgeObjectRules,
      List<String> canonicalKinds,
      String containmentKind,
      List<Map<String, Object>> ecoreRules) {
    Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
    Set<String> edgeObjectTypes = new LinkedHashSet<>();
    for (Object item : edgeObjectRules) {
      if (item instanceof Map<?, ?> raw) {
        edgeObjectTypes.add(String.valueOf(raw.get("eClass")));
      }
    }
    for (Map<String, Object> rawRule : ecoreRules) {
      String sourceType = String.valueOf(rawRule.getOrDefault("sourceType", ""));
      String feature = String.valueOf(rawRule.getOrDefault("feature", ""));
      if (edgeObjectTypes.contains(sourceType) || configuredExclusions.contains(feature)) {
        continue;
      }
      Map<String, Object> rule = new LinkedHashMap<>(rawRule);
      String mappedKind =
          String.valueOf(
              configuredKindMappings.getOrDefault(
                  feature, objectStringList(rule.get("allowedKinds")).get(0)));
      if (!canonicalKinds.contains(mappedKind)) {
        continue;
      }
      rule.put("allowedKinds", List.of(mappedKind));
      byKey.put(relationshipRuleKey(rule), rule);
    }
    for (Object item : configuredRules) {
      if (!(item instanceof Map<?, ?> raw)) {
        throw new PlatformException(500, "Modeling relationshipRules entries must be objects.");
      }
      Map<String, Object> rule = stringKeyMap(raw);
      if (isCatchAllRelationshipRule(rule)) {
        continue;
      }
      byKey.putIfAbsent(relationshipRuleKey(rule), rule);
    }
    return byKey.values().stream()
        .filter(rule -> !Boolean.TRUE.equals(rule.get("containment")))
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
  }

  /**
   * Checks whether a relationship rule is a catch-all wildcard connector rule.
   *
   * @param rule relationship rule
   * @return {@code true} when both source and target are wildcards
   */
  private boolean isCatchAllRelationshipRule(Map<String, Object> rule) {
    return "*".equals(String.valueOf(rule.getOrDefault("sourceType", "")))
        && "*".equals(String.valueOf(rule.getOrDefault("targetType", "")));
  }

  /**
   * Merges JSON-owned edge-object rules with metamodel-derived defaults for classes that expose
   * {@code source}/{@code target} reference endpoints.
   *
   * @param configuredRules JSON-owned rules
   * @param elements merged element metadata
   * @param rootContainmentFeatures root containment feature names keyed by contained type
   * @return merged edge-object rules
   */
  private List<Map<String, Object>> mergeSemanticEdgeObjectRules(
      List<?> configuredRules, List<?> elements, Map<String, String> rootContainmentFeatures) {
    Map<String, Map<String, Object>> byClass = new LinkedHashMap<>();
    for (Object item : configuredRules) {
      if (!(item instanceof Map<?, ?> raw)) {
        throw new PlatformException(
            500, "Modeling semanticEdgeObjectRules entries must be objects.");
      }
      Map<String, Object> rule = stringKeyMap(raw);
      byClass.put(String.valueOf(rule.getOrDefault("eClass", "")), rule);
    }
    for (Object item : elements) {
      if (!(item instanceof Map<?, ?> raw)) {
        continue;
      }
      Map<String, Object> element = stringKeyMap(raw);
      String type = String.valueOf(element.getOrDefault("type", ""));
      if (type.isBlank()
          || byClass.containsKey(type)
          || Boolean.TRUE.equals(element.get("abstract"))
          || Boolean.TRUE.equals(element.get("interface"))
          || Boolean.TRUE.equals(element.get("relationshipElement"))) {
        continue;
      }
      Map<String, Object> derived =
          deriveEdgeObjectRule(element, rootContainmentFeatures.get(type));
      if (derived != null) {
        byClass.putIfAbsent(type, derived);
      }
    }
    return new ArrayList<>(byClass.values());
  }

  /**
   * Derives a default edge-object rule for classes with paired {@code source}/{@code target}
   * references.
   *
   * @param element element metadata
   * @param rootFeature root containment feature when known
   * @return derived rule or {@code null}
   */
  private Map<String, Object> deriveEdgeObjectRule(
      Map<String, Object> element, String rootFeature) {
    List<Map<String, Object>> references = listOfMaps(element.get("references"));
    Map<String, Object> sourceRef = referenceByName(references, "source");
    Map<String, Object> targetRef = referenceByName(references, "target");
    if (sourceRef == null || targetRef == null) {
      return null;
    }
    if (Boolean.TRUE.equals(sourceRef.get("containment"))
        || Boolean.TRUE.equals(targetRef.get("containment"))
        || Boolean.TRUE.equals(sourceRef.get("readonly"))
        || Boolean.TRUE.equals(targetRef.get("readonly"))) {
      return null;
    }
    String sourceType = genericTarget(String.valueOf(sourceRef.getOrDefault("targetType", "")));
    String targetType = genericTarget(String.valueOf(targetRef.getOrDefault("targetType", "")));
    if (sourceType.isBlank() || targetType.isBlank()) {
      return null;
    }
    String type = String.valueOf(element.get("type"));
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("eClass", type);
    rule.put("matchKinds", List.of(relationshipKind(type)));
    rule.put("sourceType", sourceType);
    rule.put("targetType", targetType);
    rule.put("sourceFeature", "source");
    rule.put("targetFeature", "target");
    if (rootFeature != null && !rootFeature.isBlank()) {
      rule.put("rootFeature", rootFeature);
    }
    return rule;
  }

  private Map<String, Object> referenceByName(
      List<Map<String, Object>> references, String featureName) {
    return references.stream()
        .filter(reference -> featureName.equals(reference.get("name")))
        .findFirst()
        .orElse(null);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> listOfMaps(Object value) {
    if (!(value instanceof List<?> rawList)) {
      return List.of();
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : rawList) {
      if (item instanceof Map<?, ?> raw) {
        result.add(stringKeyMap(raw));
      }
    }
    return result;
  }

  /**
   * Maps contained relationship-object types to the root-model feature that owns them.
   *
   * @param classByType class lookup by type name
   * @param rootType root model class name
   * @return contained type to root feature map
   */
  private Map<String, String> rootContainmentFeatures(
      Map<String, EcoreClass> classByType, String rootType) {
    Map<String, String> result = new LinkedHashMap<>();
    EcoreClass root = classByType.get(rootType);
    if (root == null) {
      return result;
    }
    for (EmfaticFeature feature : root.references()) {
      if (!feature.containment() || feature.type().isBlank()) {
        continue;
      }
      result.put(genericTarget(feature.type()), feature.name());
    }
    return result;
  }

  /**
   * Creates a stable de-duplication key for a relationship rule.
   *
   * @param rule relationship rule
   * @return rule key
   */
  private String relationshipRuleKey(Map<String, Object> rule) {
    return String.join(
        "|",
        String.valueOf(rule.getOrDefault("sourceType", "")),
        String.valueOf(rule.getOrDefault("targetType", "")),
        String.valueOf(rule.getOrDefault("feature", "")));
  }

  /**
   * Merges configured and Ecore-derived semantic reference rules by stable key.
   *
   * @param configuredRules JSON-owned rules
   * @param ecoreRules Ecore-derived rules
   * @return merged rules
   */
  private List<Map<String, Object>> mergeSemanticReferenceRules(
      List<?> configuredRules,
      Map<String, Object> configuredKindMappings,
      List<String> configuredExclusions,
      List<?> edgeObjectRules,
      List<String> canonicalKinds,
      List<Map<String, Object>> ecoreRules) {
    Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
    Set<String> edgeObjectTypes = new LinkedHashSet<>();
    for (Object item : edgeObjectRules) {
      if (item instanceof Map<?, ?> raw) {
        edgeObjectTypes.add(String.valueOf(raw.get("eClass")));
      }
    }
    for (Object item : configuredRules) {
      if (!(item instanceof Map<?, ?> raw)) {
        throw new PlatformException(
            500, "Modeling semanticReferenceRules entries must be objects.");
      }
      Map<String, Object> rule = stringKeyMap(raw);
      byKey.put(semanticReferenceRuleKey(rule), rule);
    }
    for (Map<String, Object> rawRule : ecoreRules) {
      if (edgeObjectTypes.contains(String.valueOf(rawRule.get("sourceType")))) {
        continue;
      }
      if (configuredExclusions.contains(String.valueOf(rawRule.get("feature")))) {
        continue;
      }
      Map<String, Object> rule = new LinkedHashMap<>(rawRule);
      String feature = String.valueOf(rule.getOrDefault("feature", ""));
      if (configuredKindMappings.containsKey(feature)) {
        rule.put("kind", String.valueOf(configuredKindMappings.get(feature)));
      }
      if (!canonicalKinds.contains(String.valueOf(rule.get("kind")))) {
        continue;
      }
      byKey.putIfAbsent(semanticReferenceRuleKey(rule), rule);
    }
    return new ArrayList<>(byKey.values());
  }

  /**
   * Creates a stable de-duplication key for a semantic reference rule.
   *
   * @param rule semantic reference rule
   * @return rule key
   */
  private String semanticReferenceRuleKey(Map<String, Object> rule) {
    return String.join(
        "|",
        String.valueOf(rule.getOrDefault("sourceType", "")),
        String.valueOf(rule.getOrDefault("targetType", "")),
        String.valueOf(rule.getOrDefault("feature", "")));
  }

  /**
   * Combines configured relationship kinds with kinds implied by rules.
   *
   * @param metadata metadata map
   * @param relationshipRules merged relationship rules
   * @return ordered relationship kinds
   */
  private List<String> mergeRelationshipKinds(
      Map<String, Object> metadata, List<?> relationshipRules) {
    LinkedHashSet<String> result =
        new LinkedHashSet<>(objectStringList(metadata.get("relationshipKinds")));
    LinkedHashSet<String> unknownKinds = new LinkedHashSet<>();
    for (Object item : relationshipRules) {
      if (item instanceof Map<?, ?> raw) {
        for (String kind : objectStringList(raw.get("allowedKinds"))) {
          if (!result.contains(kind)) {
            unknownKinds.add(kind);
          }
        }
      }
    }
    if (result.isEmpty()) {
      throw new PlatformException(500, "Modeling UI metadata must define relationshipKinds.");
    }
    if (!unknownKinds.isEmpty()) {
      throw new PlatformException(
          500, "Relationship rules use undeclared relationshipKinds: " + unknownKinds);
    }
    return new ArrayList<>(result);
  }

  /**
   * Ensures every relationship kind has a display label.
   *
   * @param configured configured label map
   * @param relationshipKinds relationship kind values
   * @return completed label map
   */
  private Map<String, Object> relationshipKindLabels(
      Map<String, Object> configured, List<?> relationshipKinds) {
    LinkedHashSet<String> expected =
        relationshipKinds.stream()
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    LinkedHashSet<String> actual = new LinkedHashSet<>(configured.keySet());
    if (!expected.equals(actual)) {
      LinkedHashSet<String> missing = new LinkedHashSet<>(expected);
      missing.removeAll(actual);
      LinkedHashSet<String> extra = new LinkedHashSet<>(actual);
      extra.removeAll(expected);
      throw new PlatformException(
          500, "relationshipKindLabels mismatch; missing=" + missing + ", extra=" + extra);
    }
    return new LinkedHashMap<>(configured);
  }

  /**
   * Reads the platform-level modeling configuration from the classpath.
   *
   * @return platform configuration map
   */
  private Map<String, Object> readPlatformConfig() {
    String resource = "modeling/platform-config.json";
    try (InputStream input =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
      if (input == null) {
        throw new PlatformException(500, "Missing modeling platform config resource: " + resource);
      }
      return objectMapper.readValue(input, new TypeReference<>() {});
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not load modeling platform config: " + resource);
    }
  }

  /**
   * Resolves the ordered level keys from platform configuration.
   *
   * @param platform platform configuration
   * @param configuredLevels level metadata keyed by level
   * @return ordered level keys
   */
  private List<String> configuredLevelOrder(
      Map<String, Object> platform, Map<String, Object> configuredLevels) {
    LinkedHashSet<String> result =
        new LinkedHashSet<>(objectStringList(platform.get("levelOrder")));
    result.addAll(configuredLevels.keySet());
    if (result.isEmpty()) {
      throw new PlatformException(500, "Modeling platform config must define at least one level.");
    }
    return new ArrayList<>(result);
  }

  /**
   * Returns the first key from a map.
   *
   * @param values keyed values
   * @return first key or an empty string
   */
  private String firstKey(Map<String, Object> values) {
    return values.keySet().stream().findFirst().orElse("");
  }

  /**
   * Interpolates supported placeholders in a JSON template.
   *
   * @param node template node
   * @param modelName requested model name
   * @return interpolated copy
   */
  private JsonNode interpolateTemplate(JsonNode node, String modelName) {
    if (node == null || node.isNull()) {
      return objectMapper.getNodeFactory().nullNode();
    }
    if (node.isTextual()) {
      return objectMapper.getNodeFactory().textNode(interpolateText(node.asText(), modelName));
    }
    if (node.isArray()) {
      ArrayNode array = objectMapper.getNodeFactory().arrayNode();
      node.forEach(item -> array.add(interpolateTemplate(item, modelName)));
      return array;
    }
    if (node.isObject()) {
      ObjectNode object = objectMapper.getNodeFactory().objectNode();
      node.fields()
          .forEachRemaining(
              entry ->
                  object.set(entry.getKey(), interpolateTemplate(entry.getValue(), modelName)));
      return object;
    }
    return node.deepCopy();
  }

  /**
   * Interpolates placeholders in a scalar template string.
   *
   * @param text template text
   * @param modelName requested model name
   * @return interpolated text
   */
  private String interpolateText(String text, String modelName) {
    String result = text.replace("${modelName}", modelName);
    result = result.replace("${safeModelName}", safeIdentifier(modelName));
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("\\$\\{safe:([^}]+)}").matcher(result);
    StringBuffer buffer = new StringBuffer();
    while (matcher.find()) {
      String replacement = safeIdentifier(matcher.group(1).replace("modelName", modelName));
      matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  /**
   * Converts user-facing names into stable identifier-safe tokens.
   *
   * @param value raw value
   * @return lowercase identifier token
   */
  private String safeIdentifier(String value) {
    String normalized =
        value == null
            ? "model"
            : value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
    return normalized.isBlank() ? "model" : normalized;
  }

  /**
   * Reads a required list metadata field.
   *
   * @param metadata metadata map
   * @param field field name
   * @param key lowercase level key
   * @return list value
   */
  private List<?> requireList(Map<String, Object> metadata, String field, String key) {
    Object value = metadata.get(field);
    if (value instanceof List<?> list) {
      return list;
    }
    throw new PlatformException(
        500,
        "Modeling UI metadata for "
            + key.toUpperCase()
            + " must define list field '"
            + field
            + "'.");
  }

  /**
   * Reads a required object metadata field.
   *
   * @param metadata metadata map
   * @param field field name
   * @param key lowercase level key
   * @return object value
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> requireMap(Map<String, Object> metadata, String field, String key) {
    Object value = metadata.get(field);
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    throw new PlatformException(
        500,
        "Modeling UI metadata for "
            + key.toUpperCase()
            + " must define object field '"
            + field
            + "'.");
  }

  /**
   * Reads relationship kinds from merged metadata.
   *
   * @param metadata merged metadata map
   * @return distinct relationship kind values
   */
  private List<String> relationshipKinds(Map<String, Object> metadata) {
    Object configured = metadata.get("relationshipKinds");
    if (configured instanceof List<?> list && !list.isEmpty()) {
      return list.stream().map(String::valueOf).distinct().toList();
    }
    throw new PlatformException(500, "Modeling UI metadata must define relationshipKinds.");
  }

  /**
   * Returns the placeholder transformation configuration used by the UI.
   *
   * @return transformation configuration map
   */
  private Map<String, Object> transformation() {
    return Map.of("enabled", true, "elementMappings", List.of(), "relationshipMappings", List.of());
  }

  /**
   * Builds CVS reference mappings from merged semantic reference rules.
   *
   * @param semanticReferenceRules merged semantic reference rules
   * @return CVS reference mapping list
   */
  private List<Map<String, Object>> buildReferenceMappings(List<?> semanticReferenceRules) {
    List<Map<String, Object>> mappings = new ArrayList<>();
    for (Object item : semanticReferenceRules) {
      if (!(item instanceof Map<?, ?> raw)) {
        continue;
      }
      Map<String, Object> rule = stringKeyMap(raw);
      mappings.add(
          Map.of(
              "sourceType", rule.getOrDefault("sourceType", ""),
              "targetType", rule.getOrDefault("targetType", ""),
              "eReference", rule.getOrDefault("feature", ""),
              "edgeKind", rule.getOrDefault("kind", ""),
              "directed", !Boolean.TRUE.equals(rule.get("reverse"))));
    }
    return mappings;
  }

  private static final String DEFAULT_DIAGRAM_RENDERER = "antv-g6";

  /**
   * Returns diagram editor renderer configuration from platform config with defaults.
   *
   * <p>Environment variable {@code MODLESS_DIAGRAM_RENDERER} overrides {@code platform-config.json}
   * when set to {@code antv-g6}.
   *
   * @param platform platform configuration map
   * @return diagram editor settings
   */
  private Map<String, Object> diagramEditorConfig(Map<String, Object> platform) {
    return diagramEditorConfig(platform, System.getenv("MODLESS_DIAGRAM_RENDERER"));
  }

  /**
   * Package-visible for unit tests.
   *
   * @param platform platform configuration map
   * @param envRenderer optional renderer override from the environment
   * @return diagram editor settings
   */
  Map<String, Object> diagramEditorConfig(Map<String, Object> platform, String envRenderer) {
    Map<String, Object> configured = optionalMap(platform, "diagramEditor");
    Map<String, Object> result = new LinkedHashMap<>(configured);
    result.put("renderer", resolveDiagramRenderer(configured, envRenderer));
    return result;
  }

  private String resolveDiagramRenderer(Map<String, Object> configured, String envRenderer) {
    if (envRenderer != null && !envRenderer.isBlank()) {
      String normalized = envRenderer.trim();
      if (DEFAULT_DIAGRAM_RENDERER.equals(normalized)) {
        return normalized;
      }
    }
    Object fromConfig = configured.get("renderer");
    if (fromConfig != null) {
      String normalized = String.valueOf(fromConfig).trim();
      if (DEFAULT_DIAGRAM_RENDERER.equals(normalized)) {
        return normalized;
      }
    }
    return DEFAULT_DIAGRAM_RENDERER;
  }

  /**
   * Reads level-specific UI metadata from the classpath.
   *
   * @param key lowercase level key
   * @return metadata map
   */
  private Map<String, Object> readMetadata(String key) {
    if (cvsLoader.hasCvs(key)) {
      return cvsLoader.loadAsUiMetadata(key);
    }
    String resource = "modeling/" + key + "-ui-metadata.json";
    try (InputStream input =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
      if (input == null) {
        throw new PlatformException(500, "Missing modeling UI metadata resource: " + resource);
      }
      Map<String, Object> metadata = objectMapper.readValue(input, new TypeReference<>() {});
      Object elements = metadata.get("elements");
      if (!(elements instanceof List<?> list) || list.isEmpty()) {
        throw new PlatformException(500, "Modeling UI metadata contains no elements: " + resource);
      }
      return metadata;
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not load modeling UI metadata: " + resource);
    }
  }

  /**
   * Reads an Ecore metamodel and derives UI structural metadata from it.
   *
   * @param key lowercase level key
   * @return derived metamodel metadata
   */
  private CimMetamodel readEcoreMetamodel(String key, String rootType) {
    Path ecoreFile = ecoreFile(key);
    try (InputStream input = Files.newInputStream(ecoreFile)) {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setNamespaceAware(false);
      Document document = factory.newDocumentBuilder().parse(input);
      NodeList packages = document.getElementsByTagName("ecore:EPackage");
      Map<String, String> typeByPath = typeByPath(packages);
      Map<String, List<String>> enumLiteralsByType = enumLiteralsByType(packages);
      Map<String, EcoreClass> classByType = ecoreClasses(packages, typeByPath);
      List<Map<String, Object>> elements = new ArrayList<>();
      List<Map<String, Object>> relationshipRules = new ArrayList<>();
      List<Map<String, Object>> semanticReferenceRules = new ArrayList<>();
      for (EcoreClass modelClass : classByType.values()) {
        elements.add(metamodelElement(modelClass, classByType, enumLiteralsByType));
        collectReferenceRules(key, modelClass, relationshipRules, semanticReferenceRules);
      }
      relationshipRules.sort(
          Comparator.comparing(
              rule ->
                  String.valueOf(rule.get("sourceType"))
                      + String.valueOf(rule.get("targetType"))
                      + String.valueOf(rule.get("feature"))));
      return new CimMetamodel(
          elements,
          relationshipRules,
          semanticReferenceRules,
          rootContainmentFeatures(classByType, rootType));
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(
          500, "Could not read " + key.toUpperCase() + " Ecore metamodel: " + ecoreFile);
    }
  }

  /**
   * Resolves the combined Ecore file for a level key.
   *
   * @param key lowercase level key
   * @return Ecore file path
   */
  private Path ecoreFile(String key) {
    ModelLevel level =
        switch (key) {
          case "cim" -> ModelLevel.CIM;
          case "pim" -> ModelLevel.PIM;
          case "psm" -> ModelLevel.PSM;
          default -> throw new PlatformException(500, "Unknown modeling level: " + key);
        };
    return new MdeRuntimePaths(MdeRuntimeOptions.defaults()).metamodelFile(level);
  }

  /**
   * Builds lookup entries for local Ecore path references.
   *
   * @param packages EPackage DOM nodes
   * @return type names keyed by path fragment
   */
  private Map<String, String> typeByPath(NodeList packages) {
    Map<String, String> result = new HashMap<>();
    for (int packageIndex = 0; packageIndex < packages.getLength(); packageIndex++) {
      Element ePackage = (Element) packages.item(packageIndex);
      int classifierIndex = 0;
      NodeList children = ePackage.getChildNodes();
      for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
        Node node = children.item(childIndex);
        if (node instanceof Element classifier && "eClassifiers".equals(classifier.getTagName())) {
          result.put(
              "#/" + packageIndex + "/" + classifier.getAttribute("name"),
              classifier.getAttribute("name"));
          classifierIndex++;
        }
      }
    }
    return result;
  }

  /**
   * Extracts all EClass definitions from Ecore DOM packages.
   *
   * @param packages EPackage DOM nodes
   * @param typeByPath type lookup for Ecore references
   * @return classes keyed by type name
   */
  private Map<String, EcoreClass> ecoreClasses(NodeList packages, Map<String, String> typeByPath) {
    Map<String, EcoreClass> result = new LinkedHashMap<>();
    for (int packageIndex = 0; packageIndex < packages.getLength(); packageIndex++) {
      Element ePackage = (Element) packages.item(packageIndex);
      String packageName = ePackage.getAttribute("name");
      NodeList children = ePackage.getChildNodes();
      for (int classIndex = 0; classIndex < children.getLength(); classIndex++) {
        Node node = children.item(classIndex);
        if (!(node instanceof Element classifier)
            || !"eClassifiers".equals(classifier.getTagName())
            || !"ecore:EClass".equals(classifier.getAttribute("xsi:type"))) {
          continue;
        }
        List<String> superTypes = new ArrayList<>();
        for (String rawSuperType : classifier.getAttribute("eSuperTypes").split("\\s+")) {
          String superType = typeName(rawSuperType, typeByPath);
          if (!superType.isBlank()) {
            superTypes.add(superType);
          }
        }
        List<EmfaticFeature> attributes = new ArrayList<>();
        List<EmfaticFeature> references = new ArrayList<>();
        NodeList featureNodes = classifier.getChildNodes();
        for (int featureIndex = 0; featureIndex < featureNodes.getLength(); featureIndex++) {
          Node featureNode = featureNodes.item(featureIndex);
          if (!(featureNode instanceof Element feature)
              || !"eStructuralFeatures".equals(feature.getTagName())) {
            continue;
          }
          String xsiType = feature.getAttribute("xsi:type");
          if ("ecore:EAttribute".equals(xsiType)) {
            attributes.add(ecoreFeature(feature, typeByPath, "attribute"));
          } else if ("ecore:EReference".equals(xsiType)) {
            references.add(ecoreFeature(feature, typeByPath, "reference"));
          }
        }
        String type = classifier.getAttribute("name");
        result.put(
            type,
            new EcoreClass(
                packageName,
                type,
                superTypes,
                "true".equals(classifier.getAttribute("abstract")),
                "true".equals(classifier.getAttribute("interface")),
                attributes,
                references));
      }
    }
    return result;
  }

  /**
   * Converts an Ecore structural feature DOM element to the internal feature representation.
   *
   * @param feature Ecore feature element
   * @param typeByPath type lookup for Ecore references
   * @param kind feature kind
   * @return normalized feature
   */
  private EmfaticFeature ecoreFeature(
      Element feature, Map<String, String> typeByPath, String kind) {
    int lower = lowerBound(feature);
    int upper = upperBound(feature);
    String multiplicity = multiplicity(lower, upper);
    boolean containment = "true".equals(feature.getAttribute("containment"));
    boolean readonly =
        "false".equals(feature.getAttribute("changeable"))
            || "true".equals(feature.getAttribute("transient"));
    return new EmfaticFeature(
        feature.getAttribute("name"),
        kind,
        typeName(feature.getAttribute("eType"), typeByPath),
        containment,
        multiplicity,
        feature.getAttribute("eOpposite"),
        readonly);
  }

  /**
   * Converts lower and upper bounds to compact multiplicity notation.
   *
   * @param lower lower bound
   * @param upper upper bound, with {@code -1} for unbounded
   * @return multiplicity token
   */
  private String multiplicity(int lower, int upper) {
    if (upper == -1 || upper > 1) {
      return lower > 0 ? "+" : "*";
    }
    return lower > 0 ? "1" : "";
  }

  /**
   * Builds a UI element description from an Ecore class.
   *
   * @param modelClass Ecore class metadata
   * @param classByType class lookup by type
   * @param enumLiteralsByType enum literals by enum type
   * @return UI element metadata
   */
  private Map<String, Object> metamodelElement(
      EcoreClass modelClass,
      Map<String, EcoreClass> classByType,
      Map<String, List<String>> enumLiteralsByType) {
    String type = modelClass.name();
    List<Map<String, Object>> attributes = new ArrayList<>();
    List<Map<String, Object>> references = new ArrayList<>();
    inheritedEcoreFeatures(modelClass, classByType, true)
        .forEach(feature -> attributes.add(cimAttribute(feature, enumLiteralsByType)));
    inheritedEcoreFeatures(modelClass, classByType, false)
        .forEach(feature -> references.add(cimReference(feature)));
    boolean abstractType = modelClass.abstractType();
    boolean interfaceType = modelClass.interfaceType();
    List<String> supertypes = allEcoreSuperTypes(modelClass, classByType);
    Map<String, Object> element = new LinkedHashMap<>();
    element.put("type", type);
    element.put("package", modelClass.packageName());
    element.put("visibleFields", defaultVisibleFields(type, attributes, references));
    element.put("attributes", attributes);
    element.put("references", references);
    element.put("supertypes", supertypes);
    element.put("abstract", abstractType);
    element.put("interface", interfaceType);
    element.put("relationshipElement", false);
    element.put("containedOnly", false);
    element.put("supportOnly", false);
    element.put("creatable", !abstractType && !interfaceType);
    return element;
  }

  /**
   * Chooses a small set of default fields to show for a type.
   *
   * @param type element type name
   * @param attributes attribute metadata
   * @param references reference metadata
   * @return ordered visible field names
   */
  private List<String> defaultVisibleFields(
      String type, List<Map<String, Object>> attributes, List<Map<String, Object>> references) {
    LinkedHashSet<String> fields = new LinkedHashSet<>();
    for (String baseline : List.of("name", "id")) {
      if (attributes.stream().anyMatch(attribute -> baseline.equals(attribute.get("name")))) {
        fields.add(baseline);
      }
    }
    for (Map<String, Object> attribute : attributes) {
      if (fields.size() >= 5) {
        break;
      }
      String name = String.valueOf(attribute.getOrDefault("name", ""));
      if (!name.isBlank()) {
        fields.add(name);
      }
    }
    for (Map<String, Object> reference : references) {
      if (fields.size() >= 5) {
        break;
      }
      String name = String.valueOf(reference.getOrDefault("name", ""));
      if (!name.isBlank() && !Boolean.TRUE.equals(reference.get("containment"))) {
        fields.add(name);
      }
    }
    if (fields.isEmpty()) {
      fields.add(type.endsWith("Model") ? "name" : "id");
    }
    return new ArrayList<>(fields);
  }

  /**
   * Returns inherited and local Ecore features in override order.
   *
   * @param modelClass model class metadata
   * @param classByType class lookup by type
   * @param attributes whether to return attributes instead of references
   * @return inherited feature list
   */
  private List<EmfaticFeature> inheritedEcoreFeatures(
      EcoreClass modelClass, Map<String, EcoreClass> classByType, boolean attributes) {
    LinkedHashMap<String, EmfaticFeature> result = new LinkedHashMap<>();
    for (String superType : modelClass.superTypes()) {
      EcoreClass parent = classByType.get(superType);
      if (parent == null) {
        continue;
      }
      for (EmfaticFeature feature : inheritedEcoreFeatures(parent, classByType, attributes)) {
        result.put(feature.name(), feature);
      }
    }
    List<EmfaticFeature> local = attributes ? modelClass.attributes() : modelClass.references();
    for (EmfaticFeature feature : local) {
      result.put(feature.name(), feature);
    }
    return new ArrayList<>(result.values());
  }

  /**
   * Returns all transitive Ecore supertypes for a class.
   *
   * @param modelClass model class metadata
   * @param classByType class lookup by type
   * @return ordered supertype names
   */
  private List<String> allEcoreSuperTypes(
      EcoreClass modelClass, Map<String, EcoreClass> classByType) {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    collectEcoreSuperTypes(modelClass, classByType, result);
    return new ArrayList<>(result);
  }

  /**
   * Recursively collects transitive Ecore supertypes.
   *
   * @param modelClass current class
   * @param classByType class lookup by type
   * @param result mutable ordered supertype sink
   */
  private void collectEcoreSuperTypes(
      EcoreClass modelClass, Map<String, EcoreClass> classByType, LinkedHashSet<String> result) {
    for (String superType : modelClass.superTypes()) {
      if (!result.add(superType)) {
        continue;
      }
      EcoreClass parent = classByType.get(superType);
      if (parent != null) {
        collectEcoreSuperTypes(parent, classByType, result);
      }
    }
  }

  /**
   * Extracts EEnum literal names from Ecore DOM packages.
   *
   * @param packages EPackage DOM nodes
   * @return enum literal names keyed by enum type
   */
  private Map<String, List<String>> enumLiteralsByType(NodeList packages) {
    Map<String, List<String>> result = new HashMap<>();
    for (int packageIndex = 0; packageIndex < packages.getLength(); packageIndex++) {
      Element ePackage = (Element) packages.item(packageIndex);
      NodeList children = ePackage.getChildNodes();
      for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
        Node node = children.item(childIndex);
        if (!(node instanceof Element classifier)
            || !"eClassifiers".equals(classifier.getTagName())
            || !"ecore:EEnum".equals(classifier.getAttribute("xsi:type"))) {
          continue;
        }
        List<String> literals = new ArrayList<>();
        NodeList enumChildren = classifier.getChildNodes();
        for (int literalIndex = 0; literalIndex < enumChildren.getLength(); literalIndex++) {
          Node literalNode = enumChildren.item(literalIndex);
          if (literalNode instanceof Element literal && "eLiterals".equals(literal.getTagName())) {
            literals.add(literal.getAttribute("name"));
          }
        }
        result.put(classifier.getAttribute("name"), literals);
      }
    }
    return result;
  }

  /**
   * Converts an internal feature to UI attribute metadata.
   *
   * @param feature normalized feature
   * @param enumLiteralsByType enum literals keyed by enum type
   * @return attribute metadata
   */
  private Map<String, Object> cimAttribute(
      EmfaticFeature feature, Map<String, List<String>> enumLiteralsByType) {
    Map<String, Object> attribute = new LinkedHashMap<>();
    attribute.put("name", feature.name());
    attribute.put("kind", "attribute");
    attribute.put("type", feature.type());
    attribute.put("required", required(feature.multiplicity()));
    attribute.put("many", many(feature.multiplicity()));
    attribute.put("fieldType", fieldType(feature.type(), enumLiteralsByType));
    attribute.put(
        "defaultValue", defaultValue(feature.type(), feature.multiplicity(), enumLiteralsByType));
    if (enumLiteralsByType.containsKey(feature.type())) {
      attribute.put("options", enumLiteralsByType.get(feature.type()));
    }
    return attribute;
  }

  /**
   * Converts an internal feature to UI reference metadata.
   *
   * @param feature normalized feature
   * @return reference metadata
   */
  private Map<String, Object> cimReference(EmfaticFeature feature) {
    Map<String, Object> reference = new LinkedHashMap<>();
    reference.put("name", feature.name());
    reference.put("kind", feature.containment() ? "containment" : "reference");
    reference.put("targetType", feature.type());
    reference.put("required", required(feature.multiplicity()));
    reference.put("many", many(feature.multiplicity()));
    reference.put("containment", feature.containment());
    reference.put("opposite", feature.opposite());
    reference.put("readonly", feature.readonly());
    reference.put("defaultValue", many(feature.multiplicity()) ? List.of() : null);
    return reference;
  }

  /**
   * Converts an Ecore attribute DOM element to UI attribute metadata.
   *
   * @param feature Ecore attribute element
   * @param typeByPath type lookup for Ecore references
   * @param enumLiteralsByType enum literals keyed by enum type
   * @return attribute metadata
   */
  private Map<String, Object> cimAttribute(
      Element feature,
      Map<String, String> typeByPath,
      Map<String, List<String>> enumLiteralsByType) {
    String type = typeName(feature.getAttribute("eType"), typeByPath);
    Map<String, Object> attribute = new LinkedHashMap<>();
    attribute.put("name", feature.getAttribute("name"));
    attribute.put("kind", "attribute");
    attribute.put("type", type);
    attribute.put("required", lowerBound(feature) > 0);
    attribute.put("many", upperBound(feature) == -1 || upperBound(feature) > 1);
    attribute.put("fieldType", fieldType(type, enumLiteralsByType));
    attribute.put("defaultValue", defaultValue(type, upperBound(feature), enumLiteralsByType));
    if (enumLiteralsByType.containsKey(type)) {
      attribute.put("options", enumLiteralsByType.get(type));
    }
    return attribute;
  }

  /**
   * Converts an Ecore reference DOM element to UI reference metadata.
   *
   * @param feature Ecore reference element
   * @param typeByPath type lookup for Ecore references
   * @return reference metadata
   */
  private Map<String, Object> cimReference(Element feature, Map<String, String> typeByPath) {
    Map<String, Object> reference = new LinkedHashMap<>();
    boolean containment = "true".equals(feature.getAttribute("containment"));
    reference.put("name", feature.getAttribute("name"));
    reference.put("kind", containment ? "containment" : "reference");
    reference.put("targetType", typeName(feature.getAttribute("eType"), typeByPath));
    reference.put("required", lowerBound(feature) > 0);
    reference.put("many", upperBound(feature) == -1 || upperBound(feature) > 1);
    reference.put("containment", "true".equals(feature.getAttribute("containment")));
    reference.put("opposite", feature.getAttribute("eOpposite"));
    reference.put(
        "readonly",
        "false".equals(feature.getAttribute("changeable"))
            || "true".equals(feature.getAttribute("transient")));
    reference.put(
        "defaultValue", upperBound(feature) == -1 || upperBound(feature) > 1 ? List.of() : null);
    return reference;
  }

  /**
   * Derives relationship and semantic reference rules from Ecore references on a DOM classifier.
   *
   * @param classifier Ecore classifier element
   * @param typeByPath type lookup for Ecore references
   * @param relationshipRules mutable relationship rule sink
   * @param semanticReferenceRules mutable semantic reference rule sink
   */
  private void collectReferenceRules(
      Element classifier,
      Map<String, String> typeByPath,
      List<Map<String, Object>> relationshipRules,
      List<Map<String, Object>> semanticReferenceRules) {
    String sourceType = classifier.getAttribute("name");
    NodeList children = classifier.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      Node node = children.item(index);
      if (!(node instanceof Element feature)
          || !"eStructuralFeatures".equals(feature.getTagName())
          || !"ecore:EReference".equals(feature.getAttribute("xsi:type"))) {
        continue;
      }
      String featureName = feature.getAttribute("name");
      boolean containment = "true".equals(feature.getAttribute("containment"));
      String targetType = typeName(feature.getAttribute("eType"), typeByPath);
      if (targetType.isBlank()) {
        continue;
      }
      String kind = relationshipKind(featureName);
      if (!containment) {
        relationshipRules.add(
            Map.of(
                "sourceType",
                sourceType,
                "targetType",
                genericTarget(targetType),
                "allowedKinds",
                List.of(kind),
                "feature",
                featureName));
        semanticReferenceRules.add(
            Map.of(
                "sourceType",
                sourceType,
                "targetType",
                genericTarget(targetType),
                "feature",
                featureName,
                "kind",
                kind,
                "reverse",
                false));
      }
    }
  }

  /**
   * Derives relationship and semantic reference rules from an Emfatic class.
   *
   * @param modelClass Emfatic class metadata
   * @param relationshipRules mutable relationship rule sink
   * @param semanticReferenceRules mutable semantic reference rule sink
   */
  private void collectReferenceRules(
      EmfaticClass modelClass,
      List<Map<String, Object>> relationshipRules,
      List<Map<String, Object>> semanticReferenceRules) {
    String sourceType = modelClass.name();
    for (EmfaticFeature feature : modelClass.references()) {
      if (feature.type().isBlank() || feature.readonly()) {
        continue;
      }
      String kind = relationshipKind(feature.name());
      if (!feature.containment()) {
        relationshipRules.add(
            Map.of(
                "sourceType",
                sourceType,
                "targetType",
                genericTarget(feature.type()),
                "allowedKinds",
                List.of(kind),
                "feature",
                feature.name()));
        semanticReferenceRules.add(
            Map.of(
                "sourceType",
                sourceType,
                "targetType",
                genericTarget(feature.type()),
                "feature",
                feature.name(),
                "kind",
                kind,
                "reverse",
                false));
      }
    }
  }

  /**
   * Derives relationship and semantic reference rules from an Ecore class.
   *
   * @param levelKey lowercase level key
   * @param modelClass Ecore class metadata
   * @param relationshipRules mutable relationship rule sink
   * @param semanticReferenceRules mutable semantic reference rule sink
   */
  private void collectReferenceRules(
      String levelKey,
      EcoreClass modelClass,
      List<Map<String, Object>> relationshipRules,
      List<Map<String, Object>> semanticReferenceRules) {
    String sourceType = modelClass.name();
    for (EmfaticFeature feature : modelClass.references()) {
      if (feature.type().isBlank() || feature.readonly()) {
        continue;
      }
      String kind = relationshipKind(feature.name());
      if (!feature.containment()) {
        relationshipRules.add(
            Map.of(
                "sourceType",
                sourceType,
                "targetType",
                genericTarget(feature.type()),
                "allowedKinds",
                List.of(kind),
                "feature",
                feature.name()));
        semanticReferenceRules.add(
            Map.of(
                "sourceType",
                sourceType,
                "targetType",
                genericTarget(feature.type()),
                "feature",
                feature.name(),
                "kind",
                kind,
                "reverse",
                false));
      }
    }
  }

  /**
   * Resolves an Ecore type reference to a classifier name.
   *
   * @param eType raw Ecore type reference
   * @param typeByPath type lookup for path fragments
   * @return classifier name or raw value
   */
  private String typeName(String eType, Map<String, String> typeByPath) {
    if (eType == null || eType.isBlank()) {
      return "";
    }
    if (eType.startsWith("#/")) {
      return typeByPath.getOrDefault(eType, eType.substring(eType.lastIndexOf('/') + 1));
    }
    int marker = eType.indexOf("#//");
    if (marker >= 0) {
      return eType.substring(marker + 3);
    }
    return eType;
  }

  /**
   * Extracts direct Ecore supertypes from a DOM classifier.
   *
   * @param classifier Ecore classifier element
   * @param typeByPath type lookup for Ecore references
   * @return direct supertype names
   */
  private List<String> directSuperTypes(Element classifier, Map<String, String> typeByPath) {
    List<String> result = new ArrayList<>();
    for (String rawSuperType : classifier.getAttribute("eSuperTypes").split("\\s+")) {
      String superType = typeName(rawSuperType, typeByPath);
      if (!superType.isBlank()) {
        result.add(superType);
      }
    }
    return result;
  }

  /**
   * Keeps target types intact so the UI can resolve legality through Ecore inheritance.
   *
   * @param targetType target type name
   * @return target type
   */
  private String genericTarget(String targetType) {
    return targetType;
  }

  /**
   * Converts an Ecore reference name and containment flag to a relationship kind.
   *
   * @param featureName reference feature name
   * @return relationship kind
   */
  private String relationshipKind(String featureName) {
    return featureNameToKind(featureName);
  }

  /**
   * Reads an Ecore lower bound with the Ecore default of zero.
   *
   * @param feature Ecore feature element
   * @return lower bound
   */
  private int lowerBound(Element feature) {
    String value = feature.getAttribute("lowerBound");
    return value == null || value.isBlank() ? 0 : Integer.parseInt(value);
  }

  /**
   * Reads an Ecore upper bound with the Ecore default of one.
   *
   * @param feature Ecore feature element
   * @return upper bound
   */
  private int upperBound(Element feature) {
    String value = feature.getAttribute("upperBound");
    return value == null || value.isBlank() ? 1 : Integer.parseInt(value);
  }

  /**
   * Checks whether multiplicity requires at least one value.
   *
   * @param multiplicity multiplicity token
   * @return {@code true} when required
   */
  private boolean required(String multiplicity) {
    return "1".equals(multiplicity) || "+".equals(multiplicity);
  }

  /**
   * Checks whether multiplicity allows multiple values.
   *
   * @param multiplicity multiplicity token
   * @return {@code true} when many-valued
   */
  private boolean many(String multiplicity) {
    return "*".equals(multiplicity) || "+".equals(multiplicity);
  }

  /**
   * Maps an Ecore data type to a UI field type.
   *
   * @param type Ecore type name
   * @param enumLiteralsByType enum literal lookup
   * @return UI field type
   */
  private String fieldType(String type, Map<String, List<String>> enumLiteralsByType) {
    if (type.contains("Boolean")) {
      return "boolean";
    }
    if (type.contains("Integer")
        || type.contains("Int")
        || type.contains("Double")
        || type.contains("Float")) {
      return "number";
    }
    if ("EDate".equals(type)) {
      return "date";
    }
    if (enumLiteralsByType.containsKey(type)) {
      return "select";
    }
    return "text";
  }

  /**
   * Supplies a default UI value from type and upper bound.
   *
   * @param type Ecore type name
   * @param upperBound Ecore upper bound
   * @param enumLiteralsByType enum literals keyed by enum type
   * @return default value
   */
  private Object defaultValue(
      String type, int upperBound, Map<String, List<String>> enumLiteralsByType) {
    if (upperBound == -1 || upperBound > 1) {
      return List.of();
    }
    if (enumLiteralsByType.containsKey(type)) {
      return null;
    }
    if (type.contains("Boolean")) {
      return false;
    }
    if (type.contains("Integer")
        || type.contains("Int")
        || type.contains("Double")
        || type.contains("Float")) {
      return 0;
    }
    return "";
  }

  /**
   * Supplies a default UI value from type and multiplicity token.
   *
   * @param type Ecore type name
   * @param multiplicity multiplicity token
   * @param enumLiteralsByType enum literals keyed by enum type
   * @return default value
   */
  private Object defaultValue(
      String type, String multiplicity, Map<String, List<String>> enumLiteralsByType) {
    if (many(multiplicity)) {
      return List.of();
    }
    if (enumLiteralsByType.containsKey(type)) {
      return null;
    }
    if (type.contains("Boolean")) {
      return false;
    }
    if (type.contains("Integer")
        || type.contains("Int")
        || type.contains("Double")
        || type.contains("Float")) {
      return 0;
    }
    return "";
  }

  /**
   * Converts a feature name to an uppercase relationship kind.
   *
   * @param featureName feature name
   * @return relationship kind
   */
  private String featureNameToKind(String featureName) {
    return featureName.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
  }

  /**
   * Converts an identifier-like type name to display text.
   *
   * @param type type name
   * @return display text
   */
  private String humanize(String type) {
    return type.replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
  }

  /**
   * Ecore-derived structural metadata for a modeling level.
   *
   * @param elements element metadata
   * @param relationshipRules relationship rules
   * @param semanticReferenceRules semantic reference rules
   */
  private record CimMetamodel(
      List<Map<String, Object>> elements,
      List<Map<String, Object>> relationshipRules,
      List<Map<String, Object>> semanticReferenceRules,
      Map<String, String> rootContainmentFeatures) {}

  /**
   * Internal Ecore class description.
   *
   * @param packageName Ecore package name
   * @param name class name
   * @param superTypes direct supertype names
   * @param abstractType whether the class is abstract
   * @param interfaceType whether the class is an Ecore interface
   * @param attributes attribute features
   * @param references reference features
   */
  private record EcoreClass(
      String packageName,
      String name,
      List<String> superTypes,
      boolean abstractType,
      boolean interfaceType,
      List<EmfaticFeature> attributes,
      List<EmfaticFeature> references) {}

  /**
   * Internal Emfatic class description retained for compatibility with Emfatic-derived metadata
   * helpers.
   *
   * @param packageName package name
   * @param name class name
   * @param superType direct supertype
   * @param abstractType whether the class is abstract
   * @param attributes attribute features
   * @param references reference features
   */
  private record EmfaticClass(
      String packageName,
      String name,
      String superType,
      boolean abstractType,
      List<EmfaticFeature> attributes,
      List<EmfaticFeature> references) {}

  /**
   * Internal structural feature description used by both DOM and Emfatic helpers.
   *
   * @param name feature name
   * @param kind attribute or reference
   * @param type target or data type
   * @param containment whether the reference is containment
   * @param multiplicity compact multiplicity token
   * @param opposite Ecore opposite reference
   * @param readonly whether the feature should be treated as read-only
   */
  private record EmfaticFeature(
      String name,
      String kind,
      String type,
      boolean containment,
      String multiplicity,
      String opposite,
      boolean readonly) {}
}
