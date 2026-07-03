package io.mehdieidi.modless.platform.assistant.patch;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import io.mehdieidi.modless.platform.modeling.config.ModelingConfigService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Runtime metamodel view derived from the same Ecore-backed configuration used by the editor. */
public class AssistantMetamodelSchemaService {

  private final Map<ModelLevel, LevelSchema> levels;

  public AssistantMetamodelSchemaService() {
    this(new ModelingConfigService());
  }

  AssistantMetamodelSchemaService(ModelingConfigService modelingConfig) {
    this.levels = load(modelingConfig.config());
  }

  /** Resolves the level represented by a model root or element type. */
  public ModelLevel resolveLevel(JsonNode model, String elementType) {
    String declared = model == null ? "" : model.path("modelLevel").asText("");
    if (!declared.isBlank()) {
      try {
        return ModelLevel.valueOf(declared.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        // Fall through to EClass/type resolution.
      }
    }
    String rootType = model == null ? "" : model.path("eClass").asText("");
    return levels.entrySet().stream()
        .filter(entry -> entry.getValue().rootType().equalsIgnoreCase(rootType))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElseGet(
            () ->
                levels.entrySet().stream()
                    .filter(entry -> entry.getValue().type(elementType).isPresent())
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow(
                        () ->
                            new PlatformException(
                                422, "Element type is not defined by an active metamodel.")));
  }

  /** Returns the canonical case-sensitive EClass name. */
  public String canonicalType(ModelLevel level, String type) {
    LevelSchema schema = schema(level);
    Optional<TypeSchema> exact = schema.type(type);
    if (exact.isPresent()) {
      return exact.get().name();
    }
    String candidate = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    List<String> prefixMatches =
        candidate.length() < 5
            ? List.of()
            : schema.types().values().stream()
                .map(TypeSchema::name)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(candidate))
                .toList();
    if (prefixMatches.size() == 1) {
      return prefixMatches.get(0);
    }
    throw new PlatformException(422, "Unknown metamodel element type: " + type);
  }

  /** Returns the canonical root EClass for a modeling level. */
  public String rootType(ModelLevel level) {
    return schema(level).rootType();
  }

  /** Finds the root containment feature for a top-level element. */
  public Optional<String> rootCollection(ModelLevel level, String elementType) {
    return rootContainment(level, elementType)
        .filter(ReferenceSchema::many)
        .map(ReferenceSchema::name);
  }

  /**
   * Finds the root containment feature for a top-level element, including single-valued features.
   */
  public Optional<ReferenceSchema> rootContainment(ModelLevel level, String elementType) {
    LevelSchema schema = schema(level);
    String canonical = canonicalType(level, elementType);
    return schema.type(schema.rootType()).stream()
        .flatMap(type -> type.references().stream())
        .filter(ReferenceSchema::containment)
        .filter(reference -> schema.assignable(canonical, reference.targetType()))
        .sorted(
            java.util.Comparator.comparing(ReferenceSchema::many)
                .reversed()
                .thenComparing(reference -> reference.targetType().equals(canonical) ? 0 : 1))
        .findFirst();
  }

  /** Verifies that an owner feature is a legal containment for a child type. */
  public void requireContainment(
      ModelLevel level, String ownerType, String featureName, String childType) {
    LevelSchema schema = schema(level);
    ReferenceSchema reference =
        schema
            .reference(ownerType, featureName)
            .filter(ReferenceSchema::containment)
            .orElseThrow(
                () ->
                    new PlatformException(
                        422, "The requested containment is not in the metamodel."));
    if (!schema.assignable(canonicalType(level, childType), reference.targetType())) {
      throw new PlatformException(422, "The child type is not valid for the containment feature.");
    }
  }

  /** Verifies a writable attribute and canonicalizes enum values when possible. */
  public Optional<AttributeSchema> attribute(ModelLevel level, String type, String feature) {
    return schema(level).type(type).flatMap(value -> value.attribute(feature));
  }

  /** Returns a reference inherited by the supplied type. */
  public Optional<ReferenceSchema> reference(ModelLevel level, String type, String feature) {
    return schema(level).reference(type, feature);
  }

  /** Returns whether a writable relationship accepts the supplied target type. */
  public boolean acceptsReferenceTarget(
      ModelLevel level, String ownerType, String feature, String targetType) {
    LevelSchema schema = schema(level);
    return schema
        .reference(ownerType, feature)
        .filter(reference -> !reference.containment() && !reference.readonly())
        .filter(
            reference ->
                schema.assignable(canonicalType(level, targetType), reference.targetType()))
        .isPresent();
  }

  /** Returns every containment on an owner that accepts the supplied child type. */
  public List<ReferenceSchema> containments(ModelLevel level, String ownerType, String childType) {
    LevelSchema schema = schema(level);
    String canonicalChild = canonicalType(level, childType);
    return schema.type(ownerType).stream()
        .flatMap(type -> type.references().stream())
        .filter(ReferenceSchema::containment)
        .filter(reference -> schema.assignable(canonicalChild, reference.targetType()))
        .toList();
  }

  /** Returns a creatable concrete type for a declared containment/reference target. */
  public Optional<String> creatableTypeFor(ModelLevel level, String declaredType) {
    LevelSchema schema = schema(level);
    String canonical = canonicalType(level, declaredType);
    Optional<TypeSchema> direct = schema.type(canonical).filter(TypeSchema::creatable);
    if (direct.isPresent()) {
      return direct.map(TypeSchema::name);
    }
    return schema.types().values().stream()
        .filter(TypeSchema::creatable)
        .filter(type -> schema.assignable(type.name(), canonical))
        .sorted(
            java.util.Comparator.comparingInt(this::requiredReferenceComplexity)
                .thenComparingInt(this::concreteTypePreference)
                .thenComparing(TypeSchema::name))
        .map(TypeSchema::name)
        .findFirst();
  }

  private int requiredReferenceComplexity(TypeSchema type) {
    return (int)
        type.references().stream()
            .filter(ReferenceSchema::required)
            .filter(reference -> !reference.readonly())
            .count();
  }

  private int concreteTypePreference(TypeSchema type) {
    return switch (type.name()) {
      case "HumanTaskStep" -> 0;
      case "StartStep" -> 1;
      case "EndStep" -> 2;
      default -> 10;
    };
  }

  /** Compact, dynamically generated language index used for retrieval and planning. */
  public String languageIndex(ModelLevel level) {
    LevelSchema schema = schema(level);
    String root =
        schema.type(schema.rootType()).stream()
            .flatMap(type -> type.references().stream())
            .filter(ReferenceSchema::containment)
            .map(reference -> reference.name() + " -> " + reference.targetType())
            .collect(Collectors.joining(", "));
    String types =
        schema.types().values().stream()
            .filter(TypeSchema::creatable)
            .map(TypeSchema::name)
            .collect(Collectors.joining(", "));
    return "Root " + schema.rootType() + " containments: " + root + "\nCreatable types: " + types;
  }

  /** Returns coverage counts for the runtime metamodel surface available to the assistant. */
  public MetamodelCoverage coverage(ModelLevel level) {
    LevelSchema schema = schema(level);
    List<TypeSchema> creatable =
        schema.types().values().stream().filter(TypeSchema::creatable).toList();
    int attributes = creatable.stream().mapToInt(type -> type.attributes().size()).sum();
    int containments =
        creatable.stream()
            .mapToInt(
                type ->
                    (int)
                        type.references().stream()
                            .filter(ReferenceSchema::containment)
                            .filter(reference -> !reference.readonly())
                            .count())
            .sum();
    int relationships =
        creatable.stream()
            .mapToInt(
                type ->
                    (int)
                        type.references().stream()
                            .filter(reference -> !reference.containment())
                            .filter(reference -> !reference.readonly())
                            .count())
            .sum();
    return new MetamodelCoverage(
        level,
        schema.rootType(),
        creatable.size(),
        attributes,
        containments,
        relationships,
        creatable.stream().map(TypeSchema::name).toList());
  }

  /** Returns deterministic Ecore-derived feature contracts for creatable types. */
  public List<AssistantModelProvider.ContextSnippet> planningContracts(
      ModelLevel level, String request, int limit) {
    return planningContracts(level, request, limit, false);
  }

  /**
   * Returns type contracts from the Ecore-derived metamodel. Relevance is decided by the LLM
   * retrieval plan before this method is used; this fallback deliberately does not inspect prompt
   * words.
   *
   * @param level model level
   * @param request user message
   * @param limit maximum snippets
   * @param emptyModel whether the active model has no saved elements yet
   * @return ranked type contracts for planner context
   */
  public List<AssistantModelProvider.ContextSnippet> planningContracts(
      ModelLevel level, String request, int limit, boolean emptyModel) {
    int bounded = Math.max(0, limit);
    if (bounded == 0) {
      return List.of();
    }
    return allPlanningContracts(level).stream().limit(bounded).toList();
  }

  /** Returns writable contracts for every creatable type in the level metamodel. */
  public List<AssistantModelProvider.ContextSnippet> allPlanningContracts(ModelLevel level) {
    return schema(level).types().values().stream()
        .filter(TypeSchema::creatable)
        .map(type -> typeContract(level, type.name()))
        .toList();
  }

  /** Returns creatable metamodel types in canonical Ecore configuration order. */
  public List<String> relevantTypes(
      ModelLevel level, String request, boolean emptyModel, int limit) {
    int bounded = Math.max(0, limit);
    if (bounded == 0) {
      return List.of();
    }
    return schema(level).types().values().stream()
        .filter(TypeSchema::creatable)
        .map(TypeSchema::name)
        .limit(bounded)
        .toList();
  }

  /** Returns compact guidance for creating a substantive model on an empty canvas. */
  public String domainCreationBlueprint(ModelLevel level, String request) {
    List<String> types = relevantTypes(level, request, true, 8);
    if (types.isEmpty()) {
      return "Create a semantically complete model for the user's domain using the retrieved type "
          + "contracts. Include required containments and attributes in the same patch.";
    }
    String contracts =
        types.stream()
            .map(
                type ->
                    "- "
                        + type
                        + ": "
                        + typeSchema(level, type).stream()
                            .flatMap(schema -> schema.references().stream())
                            .filter(reference -> reference.required() && reference.containment())
                            .map(
                                reference ->
                                    "add " + reference.targetType() + " via " + reference.name())
                            .collect(Collectors.joining("; ")))
            .collect(Collectors.joining("\n"));
    return """
    The canvas is empty. Model only what the user asked for, using types and relationships allowed
    by the formal metamodel. Include required nested contracts and attributes for every element
    you add.
    Representative creatable types from the formal metamodel:
    """
        + contracts;
  }

  /** Returns the runtime schema for one metamodel type. */
  public Optional<TypeSchema> typeSchema(ModelLevel level, String typeName) {
    return schema(level).type(canonicalType(level, typeName));
  }

  /** Returns compact enum defaults derived from the runtime schema. */
  public String planningDefaults(ModelLevel level) {
    return schema(level).types().values().stream()
        .flatMap(
            type ->
                type.attributes().stream()
                    .filter(attribute -> !attribute.options().isEmpty())
                    .map(
                        attribute ->
                            type.name()
                                + "."
                                + attribute.name()
                                + "="
                                + attribute.options().get(0)))
        .limit(24)
        .collect(java.util.stream.Collectors.joining(", "));
  }

  /** Returns compact examples of required containment pairs for planner guidance. */
  public String containmentPlanningExamples(ModelLevel level, int limit) {
    return schema(level).types().values().stream()
        .filter(TypeSchema::creatable)
        .flatMap(
            type ->
                type.references().stream()
                    .filter(reference -> reference.required() && reference.containment())
                    .map(
                        reference ->
                            "When creating "
                                + type.name()
                                + ", always add "
                                + reference.targetType()
                                + " via "
                                + reference.name()
                                + " in the same patch."))
        .limit(Math.max(0, limit))
        .collect(Collectors.joining("\n"));
  }

  /** Returns the complete writable feature contract for one metamodel type. */
  public AssistantModelProvider.ContextSnippet typeContract(ModelLevel level, String typeName) {
    TypeSchema type =
        schema(level)
            .type(canonicalType(level, typeName))
            .orElseThrow(() -> new PlatformException(422, "Unknown metamodel type: " + typeName));
    String attributes =
        type.attributes().stream()
            .map(
                attribute ->
                    attribute.name()
                        + ":"
                        + attribute.type()
                        + (attribute.required() ? " required" : " optional")
                        + (attribute.options().isEmpty() ? "" : " options=" + attribute.options()))
            .collect(Collectors.joining(", "));
    String references =
        type.references().stream()
            .filter(reference -> !reference.readonly())
            .map(
                reference ->
                    reference.name()
                        + " -> "
                        + reference.targetType()
                        + (reference.required() ? " required" : " optional")
                        + (reference.many() ? " many" : " single")
                        + (reference.containment() ? " containment" : " relationship"))
            .collect(Collectors.joining(", "));
    return new AssistantModelProvider.ContextSnippet(
        "runtime-metamodel-type",
        type.name(),
        "Type "
            + type.name()
            + "\nAttributes: "
            + (attributes.isBlank() ? "none" : attributes)
            + "\nReferences: "
            + (references.isBlank() ? "none" : references));
  }

  private LevelSchema schema(ModelLevel level) {
    LevelSchema result = levels.get(level);
    if (result == null) {
      throw new PlatformException(500, "No runtime metamodel schema is available for " + level);
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private Map<ModelLevel, LevelSchema> load(Map<String, Object> config) {
    Map<ModelLevel, LevelSchema> result = new LinkedHashMap<>();
    Map<String, Object> configuredLevels = (Map<String, Object>) config.get("levels");
    for (ModelLevel level : ModelLevel.values()) {
      Map<String, Object> rawLevel = (Map<String, Object>) configuredLevels.get(level.apiName());
      Map<String, TypeSchema> types = new LinkedHashMap<>();
      for (Object item : (List<?>) rawLevel.get("elements")) {
        Map<String, Object> raw = (Map<String, Object>) item;
        List<AttributeSchema> attributes = new ArrayList<>();
        for (Object value : (List<?>) raw.getOrDefault("attributes", List.of())) {
          Map<String, Object> attribute = (Map<String, Object>) value;
          attributes.add(
              new AttributeSchema(
                  text(attribute, "name"),
                  text(attribute, "type"),
                  Boolean.TRUE.equals(attribute.get("required")),
                  strings(attribute.get("options"))));
        }
        List<ReferenceSchema> references = new ArrayList<>();
        for (Object value : (List<?>) raw.getOrDefault("references", List.of())) {
          Map<String, Object> reference = (Map<String, Object>) value;
          references.add(
              new ReferenceSchema(
                  text(reference, "name"),
                  text(reference, "targetType"),
                  Boolean.TRUE.equals(reference.get("required")),
                  Boolean.TRUE.equals(reference.get("many")),
                  Boolean.TRUE.equals(reference.get("containment")),
                  Boolean.TRUE.equals(reference.get("readonly"))));
        }
        TypeSchema type =
            new TypeSchema(
                text(raw, "type"),
                Boolean.TRUE.equals(raw.get("creatable")),
                strings(raw.get("supertypes")),
                text(raw, "label"),
                text(raw, "category"),
                attributes,
                references);
        types.put(type.name().toLowerCase(Locale.ROOT), type);
      }
      Map<String, Object> starter = (Map<String, Object>) rawLevel.get("starterTemplate");
      if (starter == null || starter.isEmpty()) {
        starter = (Map<String, Object>) rawLevel.get("rootTemplate");
      }
      result.put(level, new LevelSchema(text(starter, "eClass"), types));
    }
    return Map.copyOf(result);
  }

  private String text(Map<String, Object> map, String key) {
    Object value = map.get(key);
    return value == null ? "" : String.valueOf(value);
  }

  private List<String> strings(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    return list.stream().map(String::valueOf).toList();
  }

  /** Runtime level schema. */
  public record LevelSchema(String rootType, Map<String, TypeSchema> types) {
    Optional<TypeSchema> type(String name) {
      return Optional.ofNullable(types.get(name == null ? "" : name.toLowerCase(Locale.ROOT)));
    }

    Optional<ReferenceSchema> reference(String ownerType, String feature) {
      return type(ownerType).stream()
          .flatMap(type -> type.references().stream())
          .filter(reference -> reference.name().equals(feature))
          .findFirst();
    }

    boolean assignable(String actual, String declared) {
      return actual.equals(declared)
          || type(actual).map(type -> type.supertypes().contains(declared)).orElse(false);
    }
  }

  /** Runtime EClass schema. */
  public record TypeSchema(
      String name,
      boolean creatable,
      List<String> supertypes,
      String label,
      String category,
      List<AttributeSchema> attributes,
      List<ReferenceSchema> references) {
    Optional<AttributeSchema> attribute(String feature) {
      return attributes.stream().filter(value -> value.name().equals(feature)).findFirst();
    }
  }

  /** Runtime EAttribute schema. */
  public record AttributeSchema(String name, String type, boolean required, List<String> options) {}

  /** Runtime EReference schema. */
  public record ReferenceSchema(
      String name,
      String targetType,
      boolean required,
      boolean many,
      boolean containment,
      boolean readonly) {}

  /** Runtime metamodel coverage summary. */
  public record MetamodelCoverage(
      ModelLevel level,
      String rootType,
      int creatableTypes,
      int attributes,
      int containments,
      int relationships,
      List<String> typeNames) {}
}
