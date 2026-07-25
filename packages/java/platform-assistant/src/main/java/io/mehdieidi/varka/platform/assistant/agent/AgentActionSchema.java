package io.mehdieidi.varka.platform.assistant.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.AttributeContract;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import java.util.ArrayList;
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

  /** Returns a closed schema for one tool, with type-specific patch variants when available. */
  public static Map<String, Object> toolSchema(String toolName, List<TypeContract> patchContracts) {
    if (!toolNames().contains(toolName)) {
      throw new IllegalArgumentException("Unknown assistant tool: " + toolName);
    }
    return switch (toolName) {
      case "respond_to_user" -> object(Map.of("message", stringSchema()), List.of("message"));
      case "inspect_model" -> object(Map.of("id", stringSchema()), List.of("id"));
      case "describe_types" ->
          object(Map.of("names", array(stringSchema(), null)), List.of("names"));
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
            Map.of("source", stringSchema(), "reference", stringSchema(), "target", stringSchema()),
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
            "creates", array(createItem, null),
            "updates", array(update, 0),
            "connections", array(connection, null),
            "deletions", array(deletion, null),
            "evidence", array(evidence, null),
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

  private static Map<String, Object> createSchema(List<TypeContract> contracts) {
    List<Map<String, Object>> variants = new ArrayList<>();
    for (TypeContract type : contracts) {
      if (!type.creatable()) continue;
      Map<String, Object> attributes = new LinkedHashMap<>();
      List<String> requiredAttributes = new ArrayList<>();
      for (AttributeContract attribute : type.attributes()) {
        attributes.put(attribute.name(), attributeSchema(attribute));
        if (attribute.required()) requiredAttributes.add(attribute.name());
      }
      Map<String, Object> properties = new LinkedHashMap<>();
      properties.put("clientRef", stringSchema());
      properties.put("eClass", Map.of("const", type.eClass()));
      properties.put("attributes", object(attributes, requiredAttributes));
      properties.put("owner", stringSchema());
      properties.put("reference", stringSchema());
      properties.put("provenance", stringSchema());
      variants.add(
          object(
              properties,
              List.of("clientRef", "eClass", "attributes", "owner", "reference", "provenance")));
    }
    if (variants.isEmpty()) {
      // Before describe_types no mutation shape is permitted. The provider can still choose a
      // read/answer action, then receives a regenerated schema after contracts are retrieved.
      return Map.of("not", Map.of());
    }
    return Map.of("oneOf", variants);
  }

  private static Map<String, Object> attributeSchema(AttributeContract attribute) {
    Map<String, Object> schema = new LinkedHashMap<>();
    if (!attribute.enumLiterals().isEmpty()) schema.put("enum", attribute.enumLiterals());
    else schema.put("type", jsonType(attribute.type()));
    return schema;
  }

  private static String jsonType(String ecoreType) {
    String type = ecoreType == null ? "" : ecoreType.toLowerCase(java.util.Locale.ROOT);
    if (type.contains("boolean")) return "boolean";
    if (type.contains("byte")
        || type.contains("short")
        || type.contains("int")
        || type.contains("long")) return "integer";
    if (type.contains("float") || type.contains("double") || type.contains("decimal"))
      return "number";
    return "string";
  }

  private static Map<String, Object> stringSchema() {
    return Map.of("type", "string");
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

  public static List<String> toolNames() {
    return List.of(
        "inspect_model",
        "search_language",
        "describe_types",
        "apply_draft_patch",
        "complete_checkpoint",
        "respond_to_user");
  }
}
