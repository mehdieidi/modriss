package io.mehdieidi.modless.platform.assistant.delta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService.AttributeContract;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService.ReferenceContract;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Builds turn-scoped ModelDelta schema and metamodel contract prompt blocks. */
public class ModelDeltaSchemaFactory {

  private final MetamodelKnowledgeService metamodels;
  private final AssistantMetamodelSchemaService schemas;
  private final ObjectMapper mapper;
  private final ConcurrentHashMap<String, ObjectNode> schemaCache = new ConcurrentHashMap<>();
  private final AtomicLong schemaCacheHits = new AtomicLong();
  private final AtomicLong schemaCacheMisses = new AtomicLong();

  public ModelDeltaSchemaFactory(
      MetamodelKnowledgeService metamodels,
      AssistantMetamodelSchemaService schemas,
      ObjectMapper mapper) {
    this.metamodels = metamodels;
    this.schemas = schemas == null ? new AssistantMetamodelSchemaService() : schemas;
    this.mapper = mapper;
  }

  public ModelDeltaSchemaFactory(AssistantMetamodelSchemaService schemas, ObjectMapper mapper) {
    this(new MetamodelKnowledgeService(schemas), schemas, mapper);
  }

  /** Prompt snippets that describe the single provider-facing mutation protocol. */
  public List<AssistantModelProvider.ContextSnippet> snippets(ModelLevel level) {
    return snippets(level, List.of());
  }

  /** Turn-scoped snippets with candidate type closure for schema and contracts. */
  public List<AssistantModelProvider.ContextSnippet> snippets(
      ModelLevel level, List<String> candidateTypes) {
    return List.of(
        new AssistantModelProvider.ContextSnippet(
            "model-delta-json-schema",
            "ModelDelta JSON Schema",
            pretty(responseSchema(level, candidateTypes))),
        new AssistantModelProvider.ContextSnippet(
            "model-delta-metamodel-contract",
            "Ecore-derived writable contracts",
            metamodelContract(level, candidateTypes)));
  }

  /** JSON Schema for a structured ModelDelta response. */
  public ObjectNode responseSchema(ModelLevel level) {
    return responseSchema(level, List.of());
  }

  /** JSON Schema scoped to candidate creatable types when provided. */
  public ObjectNode responseSchema(ModelLevel level, List<String> candidateTypes) {
    String cacheKey = schemaCacheKey(level, candidateTypes);
    ObjectNode cached = schemaCache.get(cacheKey);
    if (cached != null) {
      schemaCacheHits.incrementAndGet();
      return cached.deepCopy();
    }
    schemaCacheMisses.incrementAndGet();
    ObjectNode schema = buildResponseSchema(level, candidateTypes);
    schemaCache.put(cacheKey, schema.deepCopy());
    return schema;
  }

  /** Clears cached schema fragments after metamodel refresh. */
  public void clearCache() {
    schemaCache.clear();
  }

  public long schemaCacheHits() {
    return schemaCacheHits.get();
  }

  public long schemaCacheMisses() {
    return schemaCacheMisses.get();
  }

  private ObjectNode buildResponseSchema(ModelLevel level, List<String> candidateTypes) {
    ObjectNode root = mapper.createObjectNode();
    root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    root.put("title", "ModelDeltaTurn");
    root.put("type", "object");
    root.put("additionalProperties", false);
    root.putArray("required").add("intent").add("kind").add("message");
    ObjectNode properties = root.putObject("properties");
    properties.set("intent", enumSchema("INFORMATION", "MUTATION"));
    properties.set("kind", enumSchema("ANSWER", "CLARIFICATION", "MODEL_DELTA"));
    properties.putObject("message").put("type", "string");
    properties.set("questions", questionsSchema());
    properties.set("elements", arrayOf(elementSchema(level, candidateTypes)));
    properties.set("references", arrayOf(referenceSchema(level, candidateTypes)));
    properties.set("attributeUpdates", arrayOf(attributeUpdateSchema()));
    properties.set("deletions", arrayOf(deletionSchema()));
    properties.set("assumptions", arrayOf(stringSchema()));
    return root;
  }

  private String schemaCacheKey(ModelLevel level, List<String> candidateTypes) {
    LinkedHashSet<String> types = new LinkedHashSet<>();
    if (candidateTypes != null) {
      candidateTypes.stream()
          .filter(type -> type != null && !type.isBlank())
          .map(type -> metamodels.canonicalType(level, type))
          .forEach(types::add);
    }
    return level.name() + "|" + metamodels.metamodelHash(level) + "|" + String.join(",", types);
  }

  private JsonNode elementSchema(ModelLevel level, List<String> candidateTypes) {
    String[] types = scopedTypeNames(level, candidateTypes);
    if (types.length == 0) {
      return typedElementSchema(level, schemas.rootType(level));
    }
    if (types.length == 1) {
      return typedElementSchema(level, types[0]);
    }
    ObjectNode oneOf = mapper.createObjectNode();
    ArrayNode branches = oneOf.putArray("oneOf");
    for (String type : types) {
      branches.add(typedElementSchema(level, type));
    }
    return oneOf;
  }

  private ObjectNode typedElementSchema(ModelLevel level, String typeName) {
    TypeContract type = metamodels.typeContract(level, typeName);
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.putArray("required").add("localId").add("eClass").add("attributes").add("placement");
    ObjectNode properties = schema.putObject("properties");
    properties.putObject("localId").put("type", "string");
    properties.set("eClass", enumSchema(type.eClass()));
    properties.set("attributes", attributesSchema(type));
    properties.set("placement", placementSchema(level, type));
    properties.set("references", arrayOf(typedReferenceSchema(type)));
    properties.set("evidenceIds", arrayOf(stringSchema()));
    return schema;
  }

  private ObjectNode attributesSchema(TypeContract type) {
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    ObjectNode properties = schema.putObject("properties");
    ArrayNode required = schema.putArray("required");
    for (AttributeContract attribute : type.attributes()) {
      properties.set(attribute.name(), attributeSchema(attribute));
      if (attribute.required()) {
        required.add(attribute.name());
      }
    }
    return schema;
  }

  private JsonNode attributeSchema(AttributeContract attribute) {
    if (!attribute.enumLiterals().isEmpty()) {
      return enumSchema(attribute.enumLiterals().toArray(String[]::new));
    }
    String normalized = attribute.type() == null ? "" : attribute.type().toLowerCase();
    if (normalized.contains("bool")) {
      return mapper.createObjectNode().put("type", "boolean");
    }
    if (normalized.contains("int")
        || normalized.contains("long")
        || normalized.contains("double")) {
      return mapper.createObjectNode().put("type", "number");
    }
    return mapper.createObjectNode().put("type", "string");
  }

  private ObjectNode placementSchema(ModelLevel level, TypeContract type) {
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.putArray("required").add("ownerId").add("referenceName");
    ObjectNode properties = schema.putObject("properties");
    properties.putObject("ownerId").put("type", "string");
    List<String> containmentNames = containmentReferenceNames(level, type.eClass());
    properties.set(
        "referenceName",
        containmentNames.isEmpty()
            ? stringSchema()
            : enumSchema(containmentNames.toArray(String[]::new)));
    return schema;
  }

  private List<String> containmentReferenceNames(ModelLevel level, String childType) {
    LinkedHashSet<String> names = new LinkedHashSet<>();
    String rootType = schemas.rootType(level);
    schemas.containments(level, rootType, childType).stream()
        .map(AssistantMetamodelSchemaService.ReferenceSchema::name)
        .forEach(names::add);
    metamodels.typeContracts(level).stream()
        .flatMap(type -> type.references().stream())
        .filter(ReferenceContract::containment)
        .filter(reference -> reference.targetType().equals(childType))
        .map(ReferenceContract::name)
        .forEach(names::add);
    return List.copyOf(names);
  }

  private ObjectNode typedReferenceSchema(TypeContract type) {
    List<String> writableRefs =
        type.references().stream()
            .filter(reference -> !reference.containment())
            .filter(reference -> !reference.readonly())
            .map(ReferenceContract::name)
            .toList();
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.putArray("required").add("referenceName").add("targetId");
    ObjectNode properties = schema.putObject("properties");
    properties.set(
        "referenceName",
        writableRefs.isEmpty() ? stringSchema() : enumSchema(writableRefs.toArray(String[]::new)));
    properties.putObject("targetId").put("type", "string");
    return schema;
  }

  private ObjectNode referenceSchema(ModelLevel level, List<String> candidateTypes) {
    LinkedHashSet<String> writableRefs = new LinkedHashSet<>();
    for (String typeName : scopedTypeNames(level, candidateTypes)) {
      metamodels
          .typeContract(level, typeName)
          .references()
          .forEach(
              reference -> {
                if (!reference.containment() && !reference.readonly()) {
                  writableRefs.add(reference.name());
                }
              });
    }
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.putArray("required").add("sourceId").add("referenceName").add("targetId");
    ObjectNode properties = schema.putObject("properties");
    properties.putObject("sourceId").put("type", "string");
    properties.set(
        "referenceName",
        writableRefs.isEmpty() ? stringSchema() : enumSchema(writableRefs.toArray(String[]::new)));
    properties.putObject("targetId").put("type", "string");
    return schema;
  }

  private ObjectNode attributeUpdateSchema() {
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.putArray("required").add("elementId").add("attributeName").add("value");
    ObjectNode properties = schema.putObject("properties");
    properties.putObject("elementId").put("type", "string");
    properties.putObject("attributeName").put("type", "string");
    properties.set("value", mapper.createObjectNode());
    return schema;
  }

  private ObjectNode deletionSchema() {
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.putArray("required").add("elementId").add("reason");
    ObjectNode properties = schema.putObject("properties");
    properties.putObject("elementId").put("type", "string");
    properties.putObject("reason").put("type", "string");
    return schema;
  }

  private ObjectNode questionsSchema() {
    ObjectNode option = mapper.createObjectNode();
    option.put("type", "object");
    option.put("additionalProperties", false);
    option.putArray("required").add("id").add("label").add("description");
    ObjectNode optionProperties = option.putObject("properties");
    optionProperties.putObject("id").put("type", "string");
    optionProperties.putObject("label").put("type", "string");
    optionProperties.putObject("description").put("type", "string");

    ObjectNode question = mapper.createObjectNode();
    question.put("type", "object");
    question.put("additionalProperties", false);
    question.putArray("required").add("id").add("prompt").add("selectionMode").add("options");
    ObjectNode properties = question.putObject("properties");
    properties.putObject("id").put("type", "string");
    properties.putObject("prompt").put("type", "string");
    properties.set("selectionMode", enumSchema("SINGLE", "MULTIPLE"));
    properties.putObject("allowFreeText").put("type", "boolean");
    properties.set("options", arrayOf(option));
    return arrayOf(question);
  }

  private ObjectNode stringSchema() {
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "string");
    return schema;
  }

  private ObjectNode arrayOf(JsonNode item) {
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "array");
    schema.set("items", item);
    return schema;
  }

  private ObjectNode enumSchema(String... values) {
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "string");
    ArrayNode allowed = schema.putArray("enum");
    for (String value : values) {
      allowed.add(value);
    }
    return schema;
  }

  private String metamodelContract(ModelLevel level, List<String> candidateTypes) {
    String[] types = scopedTypeNames(level, candidateTypes);
    String scoped =
        types.length == 0
            ? ""
            : "\nCandidate types for this turn: " + String.join(", ", types) + "\n";
    int attributeCount = 0;
    int containmentCount = 0;
    int relationshipCount = 0;
    for (String type : types) {
      TypeContract contract = metamodels.typeContract(level, type);
      attributeCount += contract.attributes().size();
      containmentCount +=
          (int) contract.references().stream().filter(ReferenceContract::containment).count();
      relationshipCount +=
          (int)
              contract.references().stream()
                  .filter(reference -> !reference.containment())
                  .filter(reference -> !reference.readonly())
                  .count();
    }
    return "Canonical source: combined Ecore metamodel\nRoot EClass: "
        + schemas.rootType(level)
        + scoped
        + "\nCoverage: "
        + types.length
        + " candidate EClasses, "
        + attributeCount
        + " writable attributes, "
        + containmentCount
        + " containments, "
        + relationshipCount
        + " writable relationships.\nUse only the EClasses and feature names in these contracts.";
  }

  private String[] scopedTypeNames(ModelLevel level, List<String> candidateTypes) {
    if (candidateTypes == null || candidateTypes.isEmpty()) {
      return metamodels.typeContracts(level).stream()
          .filter(TypeContract::creatable)
          .map(TypeContract::eClass)
          .toArray(String[]::new);
    }
    LinkedHashSet<String> names = new LinkedHashSet<>();
    for (String candidate : candidateTypes) {
      if (candidate != null && !candidate.isBlank()) {
        names.add(schemas.canonicalType(level, candidate));
      }
    }
    if (names.isEmpty()) {
      return metamodels.typeContracts(level).stream()
          .filter(TypeContract::creatable)
          .map(TypeContract::eClass)
          .toArray(String[]::new);
    }
    return names.toArray(String[]::new);
  }

  private String pretty(JsonNode node) {
    try {
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    } catch (Exception ignored) {
      return node.toString();
    }
  }
}
