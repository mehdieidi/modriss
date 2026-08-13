package io.mehdieidi.varka.platform.assistant.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.AttributeContract;
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
            {"analyze_source_units", "analyze_source_units"},
            {"plan_cim_blueprint", "plan_cim_blueprint"},
            {"plan_model_edit", "plan_model_edit"},
            {"commit_model_batch", "commit_model_batch"},
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
    if (!toolNames().contains(toolName) && !"apply_draft_patch".equals(toolName)) {
      throw new IllegalArgumentException("Unknown assistant tool: " + toolName);
    }
    return switch (toolName) {
      case "respond_to_user" ->
          object(Map.of("message", nonEmptyStringSchema()), List.of("message"));
      case "analyze_source_units" -> sourceAnalysisSchema();
      case "plan_cim_blueprint" -> cimBlueprintSchema();
      case "plan_model_edit" -> planModelEditSchema();
      case "inspect_model" -> inspectModelSchema();
      case "describe_types" ->
          object(Map.of("names", array(stringSchema(), null)), List.of("names"));
      case "commit_model_batch", "apply_draft_patch" -> patchSchema(patchContracts);
      default -> throw new IllegalArgumentException("Unknown assistant tool: " + toolName);
    };
  }

  private static Map<String, Object> patchSchema(List<TypeContract> contracts) {
    Map<String, Object> createItem = createSchema(contracts == null ? List.of() : contracts);
    Map<String, Object> update =
        object(
            Map.of(
                "elementId", stringSchema(),
                // The executor checks the target element's exact EClass contract.  The provider
                // schema deliberately exposes only attributes from the retrieved contracts so a
                // valid edit is possible without reopening arbitrary JSON properties.
                "attributes", updateAttributesSchema(contracts == null ? List.of() : contracts),
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
            "creates", array(createItem, 48),
            "updates", array(update, 48),
            "connections", array(connection, 96),
            "deletions", array(deletion, null),
            "evidence", array(evidence, 64),
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

  private static Map<String, Object> sourceAnalysisSchema() {
    Map<String, Object> concept =
        object(
            Map.of(
                "key",
                stringSchema(),
                "kind",
                enumStringSchema(
                    List.of(
                        "actor",
                        "goal",
                        "capability",
                        "requirement",
                        "command",
                        "query",
                        "domain_entity",
                        "information_item",
                        "domain_event",
                        "policy",
                        "business_rule",
                        "relationship",
                        "interaction",
                        "dependency")),
                "name",
                stringSchema(),
                "summary",
                stringSchema(),
                "sourceUnitIds",
                array(stringSchema(), 6)),
            List.of("key", "kind", "name", "summary", "sourceUnitIds"));
    Map<String, Object> sourceUnitProperties = new LinkedHashMap<>();
    sourceUnitProperties.put("sourceUnitId", stringSchema());
    sourceUnitProperties.put("actors", array(stringSchema(), 8));
    sourceUnitProperties.put("goals", array(stringSchema(), 8));
    sourceUnitProperties.put("capabilities", array(stringSchema(), 8));
    sourceUnitProperties.put("requirements", array(stringSchema(), 12));
    sourceUnitProperties.put("commands", array(stringSchema(), 8));
    sourceUnitProperties.put("queries", array(stringSchema(), 8));
    sourceUnitProperties.put("domainEntities", array(stringSchema(), 8));
    sourceUnitProperties.put("informationItems", array(stringSchema(), 12));
    sourceUnitProperties.put("domainEvents", array(stringSchema(), 8));
    sourceUnitProperties.put("policies", array(stringSchema(), 8));
    sourceUnitProperties.put("relationships", array(stringSchema(), 12));
    Map<String, Object> sourceUnit =
        object(
            sourceUnitProperties,
            List.of(
                "sourceUnitId",
                "actors",
                "goals",
                "capabilities",
                "requirements",
                "commands",
                "queries",
                "domainEntities",
                "informationItems",
                "domainEvents",
                "policies",
                "relationships"));
    return object(
        Map.of(
            "domain",
            stringSchema(),
            "sourceUnits",
            array(sourceUnit, 40),
            "concepts",
            array(concept, 160),
            "crossUnitRelationships",
            array(concept, 80)),
        List.of("domain", "sourceUnits", "concepts", "crossUnitRelationships"));
  }

  private static Map<String, Object> cimBlueprintSchema() {
    Map<String, Object> candidate =
        object(
            Map.of(
                "logicalKey",
                stringSchema(),
                "reuseKey",
                stringSchema(),
                "eClass",
                stringSchema(),
                "name",
                stringSchema(),
                "sourceUnitIds",
                array(stringSchema(), 6),
                "conceptKeys",
                array(stringSchema(), 12),
                "owner",
                stringSchema(),
                "containment",
                stringSchema(),
                "requiredContracts",
                array(stringSchema(), 12),
                "slice",
                integerSchema(1, 24)),
            List.of(
                "logicalKey",
                "reuseKey",
                "eClass",
                "name",
                "sourceUnitIds",
                "conceptKeys",
                "owner",
                "containment",
                "requiredContracts",
                "slice"));
    Map<String, Object> relationship =
        object(
            Map.of(
                "sourceKey",
                stringSchema(),
                "targetKey",
                stringSchema(),
                "reference",
                stringSchema(),
                "relationshipEClass",
                stringSchema(),
                "sourceUnitIds",
                array(stringSchema(), 6),
                "conceptKeys",
                array(stringSchema(), 12),
                "slice",
                integerSchema(1, 24)),
            List.of(
                "sourceKey",
                "targetKey",
                "reference",
                "relationshipEClass",
                "sourceUnitIds",
                "conceptKeys",
                "slice"));
    Map<String, Object> slice =
        object(
            Map.of(
                "focus",
                stringSchema(),
                "sourceUnitIds",
                array(stringSchema(), 8),
                "requiredContracts",
                array(stringSchema(), 16),
                "candidateKeys",
                array(stringSchema(), 24),
                "relationshipKeys",
                array(stringSchema(), 24)),
            List.of(
                "focus",
                "sourceUnitIds",
                "requiredContracts",
                "candidateKeys",
                "relationshipKeys"));
    return object(
        Map.of(
            "domain",
            stringSchema(),
            "candidates",
            array(candidate, 220),
            "relationships",
            array(relationship, 160),
            "slices",
            array(slice, 24)),
        List.of("domain", "candidates", "relationships", "slices"));
  }

  private static Map<String, Object> planModelEditSchema() {
    Map<String, Object> slice =
        object(
            Map.of(
                "label",
                stringSchema(),
                "purpose",
                stringSchema(),
                "requiredContracts",
                array(stringSchema(), null),
                "sourceUnitIds",
                array(stringSchema(), 8)),
            List.of("label", "purpose", "requiredContracts"));
    return object(
        Map.of(
            "intent",
            enumStringSchema(List.of("CREATE_MODEL", "ADD_FEATURES", "EDIT_MODEL", "EXPLAIN")),
            "features",
            array(stringSchema(), null),
            "reuseTargets",
            array(stringSchema(), null),
            "newElements",
            array(stringSchema(), null),
            "requiredContracts",
            array(stringSchema(), null),
            "slices",
            array(slice, 8)),
        List.of(
            "intent", "features", "reuseTargets", "newElements", "requiredContracts", "slices"));
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
    List<TypeContract> creatable = contracts.stream().filter(TypeContract::creatable).toList();
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
    if (creatable.size() > 1) {
      Map<String, Object> schema = new LinkedHashMap<>();
      schema.put(
          "oneOf",
          creatable.stream()
              .sorted(java.util.Comparator.comparing(TypeContract::eClass))
              .map(type -> createVariant(type, contracts))
              .toList());
      return schema;
    }
    return createVariant(creatable.get(0), contracts);
  }

  private static Map<String, Object> createVariant(
      TypeContract type, List<TypeContract> contracts) {
    List<TypeContract> singleton = List.of(type);
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("clientRef", stringSchema());
    properties.put("eClass", Map.of("type", "string", "const", type.eClass()));
    Map<String, Object> attributes = new LinkedHashMap<>();
    type.attributes().stream()
        .filter(attribute -> !"id".equals(attribute.name()))
        .forEach(
            attribute -> {
              Map<String, Object> value =
                  attribute.enumLiterals().isEmpty()
                      ? openValueSchema()
                      : enumStringSchema(attribute.enumLiterals());
              attributes.put(attribute.name(), attribute.many() ? array(value, null) : value);
            });
    List<String> requiredAttributes =
        type.attributes().stream()
            .filter(AttributeContract::required)
            .map(AttributeContract::name)
            .filter(name -> !"id".equals(name))
            .toList();
    properties.put("attributes", object(attributes, requiredAttributes));
    String ownership = containmentOwnershipDescription(singleton, contracts);
    properties.put(
        "owner",
        stringSchema(
            "Exact existing id or prior clientRef of the owner EClass listed in: " + ownership));
    Map<String, Object> reference =
        new LinkedHashMap<>(containmentReferenceSchema(singleton, contracts));
    reference.put(
        "description",
        "Exact containment for this create's EClass. Follow this Ecore mapping: " + ownership);
    properties.put("reference", reference);
    properties.put("provenance", stringSchema());
    return object(
        properties,
        List.of("clientRef", "eClass", "attributes", "owner", "reference", "provenance"));
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

  private static Map<String, Object> updateAttributesSchema(List<TypeContract> contracts) {
    Map<String, Object> attributes = new LinkedHashMap<>();
    for (TypeContract contract : contracts) {
      contract.attributes().stream()
          .forEach(attribute -> attributes.putIfAbsent(attribute.name(), openValueSchema()));
    }
    // Before contracts are known, do not make mutations schema-valid. This mirrors creates and
    // ensures the agent must retrieve authoritative Ecore contracts first.
    return attributes.isEmpty() ? object(Map.of(), List.of()) : object(attributes, List.of());
  }

  private static boolean accepts(TypeContract createdType, String targetType) {
    return createdType.eClass().equals(targetType) || createdType.supertypes().contains(targetType);
  }

  private static Map<String, Object> stringSchema() {
    return Map.of("type", "string");
  }

  private static Map<String, Object> nonEmptyStringSchema() {
    return Map.of("type", "string", "minLength", 1);
  }

  private static Map<String, Object> stringSchema(String description) {
    return Map.of("type", "string", "description", description);
  }

  private static String containmentOwnershipDescription(
      List<TypeContract> createdTypes, List<TypeContract> owners) {
    return createdTypes.stream()
        .flatMap(
            child ->
                owners.stream()
                    .flatMap(
                        owner ->
                            owner.references().stream()
                                .filter(ReferenceContract::containment)
                                .filter(reference -> accepts(child, reference.targetType()))
                                .map(
                                    reference ->
                                        child.eClass()
                                            + " <- "
                                            + owner.eClass()
                                            + "."
                                            + reference.name())))
        .distinct()
        .sorted()
        .collect(java.util.stream.Collectors.joining("; "));
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

  private static Map<String, Object> openValueSchema() {
    return Map.of();
  }

  public static List<String> toolNames() {
    return List.of(
        "analyze_source_units",
        "plan_cim_blueprint",
        "plan_model_edit",
        "inspect_model",
        "describe_types",
        "commit_model_batch",
        "respond_to_user");
  }
}
