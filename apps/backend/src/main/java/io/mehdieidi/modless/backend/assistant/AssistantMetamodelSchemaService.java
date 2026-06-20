package io.mehdieidi.modless.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.stereotype.Service;

/** Runtime metamodel view derived from the same Ecore-backed configuration used by the editor. */
@Service
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

  /** Finds the root containment feature for a top-level element. */
  public Optional<String> rootCollection(ModelLevel level, String elementType) {
    LevelSchema schema = schema(level);
    String canonical = canonicalType(level, elementType);
    return schema.type(schema.rootType()).stream()
        .flatMap(type -> type.references().stream())
        .filter(ReferenceSchema::containment)
        .filter(ReferenceSchema::many)
        .filter(reference -> schema.assignable(canonical, reference.targetType()))
        .map(ReferenceSchema::name)
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

  /** Returns Ecore-derived feature contracts for creatable types named in a user request. */
  public List<AssistantModelProvider.ContextSnippet> planningContracts(
      ModelLevel level, String request, int limit) {
    String normalized = request == null ? "" : request.toLowerCase(Locale.ROOT);
    return schema(level).types().values().stream()
        .filter(TypeSchema::creatable)
        .filter(type -> normalized.contains(type.name().toLowerCase(Locale.ROOT)))
        .limit(Math.max(0, limit))
        .map(type -> typeContract(level, type.name()))
        .toList();
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
}
