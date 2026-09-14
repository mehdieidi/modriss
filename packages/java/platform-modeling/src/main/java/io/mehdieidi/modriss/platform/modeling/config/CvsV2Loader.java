package io.mehdieidi.modriss.platform.modeling.config;

import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.modeling.runtime.MdeRuntimeOptions;
import io.mehdieidi.modriss.platform.modeling.runtime.MdeRuntimePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Loads and validates Concrete Visual Syntax (CVS) v2 documents from {@code mde/notation/}. */
public final class CvsV2Loader {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final MdeRuntimePaths runtimePaths;

  /** Creates a loader using default runtime path resolution. */
  public CvsV2Loader() {
    this(new MdeRuntimePaths(MdeRuntimeOptions.defaults()));
  }

  /**
   * Creates a loader with explicit runtime paths.
   *
   * @param runtimePaths MDE asset path resolver
   */
  public CvsV2Loader(MdeRuntimePaths runtimePaths) {
    this.runtimePaths = runtimePaths;
  }

  /**
   * Returns whether a CVS v2 file exists for the given level.
   *
   * @param level lowercase level key
   * @return {@code true} when {@code mde/notation/{level}.cvs.json} exists
   */
  public boolean hasCvs(String level) {
    return Files.isRegularFile(cvsFile(level));
  }

  /**
   * Loads a CVS v2 document and converts it to the UI metadata shape consumed by {@link
   * ModelingConfigService}.
   *
   * @param level lowercase level key
   * @return metadata map equivalent to legacy ui-metadata
   */
  public Map<String, Object> loadAsUiMetadata(String level) {
    Path file = cvsFile(level);
    if (!Files.isRegularFile(file)) {
      throw new PlatformException(500, "Missing CVS v2 notation file: " + file);
    }
    try {
      Map<String, Object> cvs =
          objectMapper.readValue(Files.readString(file), new TypeReference<>() {});
      validateCvsDocument(level, cvs);
      return toUiMetadataShape(cvs);
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not load CVS v2 notation: " + file);
    }
  }

  /**
   * Returns the raw CVS document for API consumers.
   *
   * @param level lowercase level key
   * @return CVS document map
   */
  public Map<String, Object> loadRaw(String level) {
    Path file = cvsFile(level);
    if (!Files.isRegularFile(file)) {
      throw new PlatformException(500, "Missing CVS v2 notation file: " + file);
    }
    try {
      Map<String, Object> cvs =
          objectMapper.readValue(Files.readString(file), new TypeReference<>() {});
      validateCvsDocument(level, cvs);
      return cvs;
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not load CVS v2 notation: " + file);
    }
  }

  /**
   * Builds per-type element mappings from merged elements for diagram rendering.
   *
   * @param elements merged element definitions
   * @return element mapping list
   */
  public List<Map<String, Object>> buildElementMappings(List<Map<String, Object>> elements) {
    List<Map<String, Object>> mappings = new ArrayList<>();
    for (Map<String, Object> element : elements) {
      String type = String.valueOf(element.getOrDefault("type", ""));
      if (type.isBlank()) {
        continue;
      }
      Map<String, Object> mapping = new LinkedHashMap<>();
      mapping.put("match", Map.of("eClass", type));
      mapping.put("visualRole", element.getOrDefault("visualRole", "node"));
      mapping.put("icon", element.get("icon"));
      mapping.put("color", element.get("color"));
      mapping.put("category", element.get("category"));
      mappings.add(mapping);
    }
    return mappings;
  }

  private Path cvsFile(String level) {
    return runtimePaths.notationRoot().resolve(level + ".cvs.json").normalize();
  }

  private void validateCvsDocument(String level, Map<String, Object> cvs) {
    Object version = cvs.get("cvsVersion");
    if (!Integer.valueOf(2).equals(version)) {
      throw new PlatformException(
          500, "CVS document for " + level + " must declare cvsVersion: 2.");
    }
    Map<String, Object> metamodelRef = optionalMap(cvs, "metamodelRef");
    String configuredLevel = String.valueOf(metamodelRef.getOrDefault("level", ""));
    if (!level.equals(configuredLevel)) {
      throw new PlatformException(
          500,
          "CVS metamodelRef.level mismatch for "
              + level
              + ": expected "
              + level
              + ", found "
              + configuredLevel);
    }
    if (optionalList(cvs, "elements").isEmpty()) {
      throw new PlatformException(500, "CVS document for " + level + " must define elements.");
    }
    if (optionalList(cvs, "views").isEmpty()) {
      throw new PlatformException(500, "CVS document for " + level + " must define views.");
    }
    if (cvs.get("containers") == null) {
      throw new PlatformException(500, "CVS document for " + level + " must define containers.");
    }
    if (optionalMap(cvs, "canvasPolicy").isEmpty()) {
      throw new PlatformException(500, "CVS document for " + level + " must define canvasPolicy.");
    }
  }

  /**
   * Validates merged elements against CVS completeness requirements.
   *
   * @param level lowercase level key
   * @param elements merged element list
   */
  public void validateElementCoverage(String level, List<Map<String, Object>> elements) {
    List<String> missingRoles = new ArrayList<>();
    for (Map<String, Object> element : elements) {
      if (Boolean.TRUE.equals(element.get("abstract"))) {
        continue;
      }
      Object role = element.get("visualRole");
      if (role == null || String.valueOf(role).isBlank()) {
        missingRoles.add(String.valueOf(element.get("type")));
      }
    }
    if (!missingRoles.isEmpty()) {
      throw new PlatformException(
          500,
          "CVS coverage incomplete for " + level + ": missing visualRole on types " + missingRoles);
    }
  }

  private Map<String, Object> toUiMetadataShape(Map<String, Object> cvs) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("displayName", cvs.getOrDefault("displayName", ""));
    metadata.put("boundedContext", cvs.getOrDefault("boundedContext", Map.of()));
    metadata.put("elementVisualDefaults", cvs.getOrDefault("elementVisualDefaults", Map.of()));
    List<Map<String, Object>> elements = optionalListOfMaps(cvs, "elements");
    for (Map<String, Object> element : elements) {
      normalizeLegacyColor(element);
    }
    metadata.put("elements", elements);
    metadata.put("badgeRules", cvs.getOrDefault("badgeRules", List.of()));
    metadata.put("elementVisualRules", cvs.getOrDefault("elementVisualRules", List.of()));
    if (optionalList(cvs, "elements").isEmpty()) {
      metadata.put("elements", overridesToElements(optionalList(cvs, "elementOverrides")));
    }
    metadata.put("relationshipRules", cvs.getOrDefault("relationshipRules", List.of()));
    metadata.put("relationshipKinds", cvs.getOrDefault("relationshipKinds", List.of()));
    metadata.put("relationshipSemantics", cvs.getOrDefault("relationshipSemantics", Map.of()));
    metadata.put(
        "relationshipLabelFields", cvs.getOrDefault("relationshipLabelFields", List.of("name")));
    metadata.put("relationshipKindLabels", cvs.getOrDefault("relationshipKindLabels", Map.of()));
    metadata.put("relationshipVisualRules", cvs.getOrDefault("relationshipVisualRules", List.of()));
    metadata.put("semanticReferenceRules", cvs.getOrDefault("semanticReferenceRules", List.of()));
    metadata.put("semanticEdgeObjectRules", cvs.getOrDefault("semanticEdgeObjectRules", List.of()));
    metadata.put(
        "semanticReferenceKindMappings",
        cvs.getOrDefault("semanticReferenceKindMappings", Map.of()));
    metadata.put(
        "semanticReferenceExclusions", cvs.getOrDefault("semanticReferenceExclusions", List.of()));
    metadata.put("shortcutConnectorRules", cvs.getOrDefault("shortcutConnectorRules", List.of()));
    List<Map<String, Object>> views = viewsToViewDefinitions(optionalList(cvs, "views"));
    metadata.put("views", views);
    // Keep the existing runtime metadata name as an internal compatibility alias. CVS itself
    // exposes only `views`; the frontend config now also exposes the canonical `views` field.
    metadata.put("viewDefinitions", views);
    metadata.put("containers", optionalListOfMaps(cvs, "containers"));
    metadata.put("containmentPalettes", containersToPalettes(optionalList(cvs, "containers")));
    metadata.put("canvasPolicy", cvs.getOrDefault("canvasPolicy", Map.of()));
    metadata.put("complexityManagement", cvs.getOrDefault("complexityManagement", List.of()));
    metadata.put("workbench", cvs.getOrDefault("workbench", Map.of()));
    metadata.put("scaffoldRecipes", cvs.getOrDefault("scaffoldRecipes", List.of()));
    metadata.put("constraints", cvs.getOrDefault("constraints", List.of()));
    metadata.put(
        "strictnessModes",
        cvs.getOrDefault("strictnessModes", List.of("exploration", "methodology", "production")));
    metadata.put("rootTemplate", requireField(cvs, "rootTemplate"));
    metadata.put("starterTemplate", cvs.get("starterTemplate"));
    metadata.put("cvsVersion", cvs.get("cvsVersion"));
    metadata.put("cvsReferenceMappings", cvs.getOrDefault("referenceMappings", List.of()));
    metadata.put("cvsRelationshipMappings", cvs.getOrDefault("relationshipMappings", List.of()));
    metadata.put("cvsMetamodelRef", cvs.getOrDefault("metamodelRef", Map.of()));
    return metadata;
  }

  private List<Map<String, Object>> optionalListOfMaps(Map<String, Object> document, String field) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : optionalList(document, field)) {
      if (item instanceof Map<?, ?> raw) {
        result.add(stringKeyMap(raw));
      }
    }
    return result;
  }

  private Map<String, Object> containersToPalettes(List<?> containers) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Object item : containers) {
      if (!(item instanceof Map<?, ?> raw)) {
        continue;
      }
      Map<String, Object> container = stringKeyMap(raw);
      String type = String.valueOf(container.getOrDefault("elementType", ""));
      if (type.isBlank()) {
        continue;
      }
      Map<String, Object> palette = new LinkedHashMap<>();
      palette.put("types", optionalList(container, "palette"));
      palette.put("canvas", optionalList(container, "canvas"));
      palette.put("features", optionalList(container, "features"));
      result.put(type, palette);
    }
    return result;
  }

  private List<Map<String, Object>> overridesToElements(List<?> overrides) {
    List<Map<String, Object>> elements = new ArrayList<>();
    for (Object item : overrides) {
      if (!(item instanceof Map<?, ?> raw)) {
        continue;
      }
      Map<String, Object> override = stringKeyMap(raw);
      Map<String, Object> element = new LinkedHashMap<>(override);
      normalizeLegacyColor(element);
      String primitive = String.valueOf(override.getOrDefault("primitive", ""));
      Map<String, Object> card = optionalMap(override, "card");
      if (!primitive.isBlank() || !card.isEmpty()) {
        Map<String, Object> notation = new LinkedHashMap<>();
        if (!primitive.isBlank()) {
          notation.put("shape", primitive);
        }
        if (!card.isEmpty()) {
          notation.put("tag", card.get("tag"));
          notation.put("lineFields", card.getOrDefault("lineFields", List.of()));
          notation.put("detailFields", card.getOrDefault("detailFields", List.of()));
        }
        element.put("notation", notation);
      }
      element.remove("primitive");
      element.remove("card");
      elements.add(element);
    }
    return elements;
  }

  /**
   * Keeps the legacy UI configuration contract scalar while accepting CVS v2 theme colors.
   *
   * <p>The CVS document may provide a light/dark color object, but the existing configuration
   * endpoint exposes {@code color} as the light-theme accent. Preserve the richer value separately
   * so consumers that understand theme colors can opt into it without breaking existing clients.
   */
  private void normalizeLegacyColor(Map<String, Object> element) {
    Object color = element.get("color");
    if (!(color instanceof Map<?, ?> rawColor)) {
      return;
    }
    Map<String, Object> themeColor = stringKeyMap(rawColor);
    Object light = themeColor.get("light");
    if (light != null && !String.valueOf(light).isBlank()) {
      element.put("themeColor", themeColor);
      element.put("color", String.valueOf(light).toUpperCase());
    }
  }

  private List<Map<String, Object>> viewsToViewDefinitions(List<?> viewpoints) {
    List<Map<String, Object>> views = new ArrayList<>();
    for (Object item : viewpoints) {
      if (item instanceof Map<?, ?> raw) {
        Map<String, Object> view = stringKeyMap(raw);
        List<?> canvas = optionalList(view, "canvas");
        view.put("elementTypes", new ArrayList<>(canvas));
        views.add(view);
      }
    }
    return views;
  }

  private Object requireField(Map<String, Object> cvs, String field) {
    Object value = cvs.get(field);
    if (value == null) {
      throw new PlatformException(500, "CVS document is missing required field: " + field);
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> optionalMap(Map<String, Object> metadata, String field) {
    Object value = metadata.get(field);
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) stringKeyMap(map);
    }
    return Map.of();
  }

  private List<?> optionalList(Map<String, Object> metadata, String field) {
    Object value = metadata.get(field);
    return value instanceof List<?> list ? list : List.of();
  }

  private Map<String, Object> stringKeyMap(Map<?, ?> raw) {
    Map<String, Object> result = new LinkedHashMap<>();
    raw.forEach((key, value) -> result.put(String.valueOf(key), value));
    return result;
  }
}
