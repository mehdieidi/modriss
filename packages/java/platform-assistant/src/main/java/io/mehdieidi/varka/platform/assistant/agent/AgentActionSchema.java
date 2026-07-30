package io.mehdieidi.varka.platform.assistant.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.ReferenceContract;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict, provider-facing action schemas generated from the exact Ecore contracts in a turn. */
public final class AgentActionSchema {
  private AgentActionSchema() {}

  public static String json() {
    return json(List.of());
  }

  /**
   * Builds the fallback action envelope. Once {@code describe_types} has run, patch attributes are
   * closed over precisely those Ecore contracts; an invented attribute cannot reach EMF.
   */
  public static String json(List<TypeContract> patchContracts) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      var variants = mapper.createArrayNode();
      for (String[] action :
          new String[][] {
            {"commit_model_batch", "apply_draft_patch"},
            {"plan_source_model", "plan_source_model"},
            {"inspect_model", "inspect_model"},
            {"describe_types", "describe_types"},
            {"answer_user", "respond_to_user"},
            {"ask_user", "respond_to_user"}
          }) {
        var variant = variants.addObject();
        variant.put("type", "object");
        variant.put("additionalProperties", false);
        variant.putArray("required").add("action").add("arguments");
        var properties = variant.putObject("properties");
        properties.putObject("action").put("const", action[0]);
        properties.set("arguments", mapper.valueToTree(toolSchema(action[1], patchContracts)));
      }
      var root = mapper.createObjectNode();
      root.put("type", "object");
      root.put("additionalProperties", false);
      root.set("oneOf", variants);
      return mapper.writeValueAsString(root);
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("Unable to encode action schema", ex);
    }
  }

  public static Map<String, Object> toolSchema(String toolName) {
    return toolSchema(toolName, List.of());
  }

  /** Returns a closed schema for one tool, constrained by the described Ecore contracts. */
  public static Map<String, Object> toolSchema(String toolName, List<TypeContract> patchContracts) {
    if (!toolNames().contains(toolName)) {
      throw new IllegalArgumentException("Unknown assistant tool: " + toolName);
    }
    return switch (toolName) {
      case "respond_to_user" -> object(Map.of("message", stringSchema()), List.of("message"));
      case "inspect_model" -> inspectModelSchema();
      case "describe_types" ->
          object(Map.of("names", array(stringSchema(), null)), List.of("names"));
      case "plan_source_model" ->
          object(
              Map.of(
                  "domain",
                  stringSchema(),
                  "slices",
                  array(
                      object(
                          Map.of(
                              "focus",
                              stringSchema(),
                              "sourceUnitIds",
                              array(stringSchema(), null)),
                          List.of("focus", "sourceUnitIds")),
                      null)),
              List.of("domain", "slices"));
      case "apply_draft_patch" -> patchSchema(patchContracts);
      // V2 names are advertised for capability parity; this legacy executor deliberately rejects
      // them with an actionable response until their independent workflow states are enabled.
      case "search_language", "complete_checkpoint" -> object(Map.of(), List.of());
      default -> throw new IllegalArgumentException("Unknown assistant tool: " + toolName);
    };
  }

  private static Map<String, Object> patchSchema(List<TypeContract> contracts) {
    Map<String, Object> createItem = createSchema(contracts == null ? List.of() : contracts);
    Map<String, Object> update =
        object(
            Map.of(
                "elementId", stringSchema(),
                // Updates are not safe without the inspected element's exact EClass contract.
                // The executor will expose an update variant only when that data is available.
                "attributes", object(Map.of(), List.of()),
                "preconditionHash", stringSchema()),
            List.of("elementId", "attributes", "preconditionHash"));
    Map<String, Object> connection =
        object(
            Map.of(
                "source",
                stringSchema(),
                "reference",
                writableRelationshipReferenceSchema(contracts == null ? List.of() : contracts),
                "target",
                stringSchema()),
            List.of("source", "reference", "target"));
    Map<String, Object> deletion =
        object(
            Map.of("elementId", stringSchema(), "preconditionHash", stringSchema()),
            List.of("elementId", "preconditionHash"));
    Map<String, Object> evidence =
        object(
            Map.of(
                "elementRef", stringSchema(),
                "sourceUnitId", stringSchema(),
                "requirementId", stringSchema(),
                "kind", stringSchema(),
                "assumption", stringSchema()),
            List.of("elementRef", "sourceUnitId", "requirementId", "kind", "assumption"));
    return object(
        Map.of(
            "creates", array(createItem, 12),
            "updates", array(update, 0),
            "connections", array(connection, 18),
            "deletions", array(deletion, null),
            "evidence", array(evidence, 12),
            "planSummary", stringSchema(),
            "turnComplete", Map.of("type", "boolean")),
        List.of(
            "creates",
            "updates",
            "connections",
            "deletions",
            "evidence",
            "planSummary",
            "turnComplete"));
  }

  private static Map<String, Object> inspectModelSchema() {
    return object(
        Map.of(
            "id",
            stringSchema(),
            "ids",
            array(stringSchema(), null),
            "eClasses",
            array(stringSchema(), null),
            "ownerIds",
            array(stringSchema(), null),
            "query",
            stringSchema(),
            "page",
            integerSchema(0, null),
            "pageSize",
            integerSchema(1, 100)),
        List.of("id", "ids", "eClasses", "ownerIds", "query", "page", "pageSize"));
  }

  private static Map<String, Object> createSchema(List<TypeContract> contracts) {
    List<TypeContract> creatable =
        contracts.stream()
            .filter(type -> type.creatable() && !isRootModelType(type.eClass()))
            .toList();
    if (creatable.isEmpty()) {
      // Before describe_types no mutation shape is permitted. The provider can still choose a
      // read/answer action, then receives a regenerated schema after contracts are retrieved. Use
      // an ordinary object schema with an impossible enum instead of {"not":{}} because several
      // OpenAI-compatible tool validators accept only a subset of JSON Schema keywords.
      return object(
          Map.of(
              "clientRef",
              stringSchema(),
              "eClass",
              enumStringSchema(List.of("__describe_types_required__")),
              "attributes",
              openObjectSchema(),
              "owner",
              stringSchema(),
              "reference",
              enumStringSchema(List.of("__describe_types_required__")),
              "provenance",
              stringSchema()),
          List.of("clientRef", "eClass", "attributes", "owner", "reference", "provenance"));
    }
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("clientRef", stringSchema());
    properties.put(
        "eClass",
        enumStringSchema(
            creatable.stream().map(TypeContract::eClass).distinct().sorted().toList()));
    // Attribute names and value types are validated by the EMF patch compiler against the exact
    // described Ecore contracts. Keeping the provider-facing ACI flat avoids malformed oneOf output
    // from OpenAI-compatible structured-output providers while preserving structural validation.
    properties.put("attributes", openObjectSchema());
    properties.put("owner", stringSchema());
    properties.put("reference", containmentReferenceSchema(creatable, contracts));
    properties.put("provenance", stringSchema());
    return object(
        properties,
        List.of("clientRef", "eClass", "attributes", "owner", "reference", "provenance"));
  }

  private static boolean isRootModelType(String eClass) {
    return "CIMModel".equals(eClass) || "PIMModel".equals(eClass) || "AwsPsmModel".equals(eClass);
  }

  private static Map<String, Object> containmentReferenceSchema(
      List<TypeContract> creatableTypes, List<TypeContract> contracts) {
    List<String> names =
        contracts.stream()
            .flatMap(owner -> owner.references().stream())
            .filter(ReferenceContract::containment)
            .filter(
                reference ->
                    creatableTypes.stream()
                        .anyMatch(createdType -> accepts(createdType, reference.targetType())))
            .map(ReferenceContract::name)
            .distinct()
            .sorted()
            .toList();
    return names.isEmpty() ? stringSchema() : enumStringSchema(names);
  }

  private static Map<String, Object> writableRelationshipReferenceSchema(
      List<TypeContract> contracts) {
    List<String> names =
        contracts.stream()
            .flatMap(type -> type.references().stream())
            .filter(reference -> !reference.containment())
            .filter(reference -> !reference.readonly())
            .map(ReferenceContract::name)
            .distinct()
            .sorted()
            .toList();
    return names.isEmpty() ? stringSchema() : enumStringSchema(names);
  }

  private static boolean accepts(TypeContract createdType, String targetType) {
    return createdType.eClass().equals(targetType) || createdType.supertypes().contains(targetType);
  }

  private static Map<String, Object> stringSchema() {
    return Map.of("type", "string");
  }

  private static Map<String, Object> integerSchema(Integer minimum, Integer maximum) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "integer");
    if (minimum != null) schema.put("minimum", minimum);
    if (maximum != null) schema.put("maximum", maximum);
    return schema;
  }

  private static Map<String, Object> enumStringSchema(List<String> values) {
    return Map.of("type", "string", "enum", values);
  }

  private static Map<String, Object> array(Map<String, Object> items, Integer maxItems) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "array");
    schema.put("items", items);
    if (maxItems != null) schema.put("maxItems", maxItems);
    return schema;
  }

  private static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.put("properties", properties);
    schema.put("required", required);
    return schema;
  }

  private static Map<String, Object> openObjectSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("additionalProperties", true);
    return schema;
  }

  public static List<String> toolNames() {
    return List.of(
        "inspect_model",
        "plan_source_model",
        "search_language",
        "describe_types",
        "apply_draft_patch",
        "complete_checkpoint",
        "respond_to_user");
  }
}
