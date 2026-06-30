package io.mehdieidi.modless.platform.assistant.subset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;

/** Builds the JSON Schema and Ecore-derived contracts sent to the subset planner. */
public class ModelSubsetSchemaService {

  private final AssistantMetamodelSchemaService schemas;
  private final ObjectMapper mapper;

  public ModelSubsetSchemaService(AssistantMetamodelSchemaService schemas, ObjectMapper mapper) {
    this.schemas = schemas;
    this.mapper = mapper;
  }

  /** Returns prompt snippets for the predefined subset response schema and metamodel contracts. */
  public List<AssistantModelProvider.ContextSnippet> snippets(ModelLevel level) {
    return List.of(
        new AssistantModelProvider.ContextSnippet(
            "model-subset-json-schema", "LLM response JSON Schema", pretty(responseSchema(level))),
        new AssistantModelProvider.ContextSnippet(
            "model-subset-metamodel-contract",
            "Writable Ecore contracts",
            metamodelContract(level)));
  }

  /** JSON Schema for LLM responses in the model-subset strategy. */
  public ObjectNode responseSchema(ModelLevel level) {
    ObjectNode root = mapper.createObjectNode();
    root.put("$schema", "https://json-schema.org/draft/2020-12/schema");
    root.put("title", "ModlessModelSubsetTurn");
    root.put("type", "object");
    root.putArray("required").add("intent").add("kind").add("message");
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("intent", enumSchema("INFORMATION", "MUTATION"));
    properties.set("kind", enumSchema("ANSWER", "CLARIFICATION", "MODEL_SUBSET"));
    properties.putObject("message").put("type", "string");
    properties.set("questions", questionsSchema());
    properties.set("subset", subsetSchema(level));
    return root;
  }

  private ObjectNode subsetSchema(ModelLevel level) {
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.putArray("required").add("subsetId").add("scope").add("elements").add("references");
    ObjectNode properties = schema.putObject("properties");
    properties.putObject("subsetId").put("type", "string");
    properties.putObject("scope").put("type", "string");
    properties.set("elements", arrayOf(elementSchema(level)));
    properties.set("references", arrayOf(referenceSchema()));
    properties.set("attributeUpdates", arrayOf(attributeUpdateSchema()));
    properties.set("deletions", arrayOf(deletionSchema()));
    return schema;
  }

  private ObjectNode elementSchema(ModelLevel level) {
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.putArray("required").add("localId").add("eClass").add("attributes");
    ObjectNode properties = schema.putObject("properties");
    properties.putObject("localId").put("type", "string");
    properties.set(
        "eClass", enumSchema(schemas.coverage(level).typeNames().toArray(String[]::new)));
    properties.putObject("attributes").put("type", "object");
    properties.set("containedBy", containmentSchema());
    properties.set("references", arrayOf(referenceSchema()));
    return schema;
  }

  private ObjectNode containmentSchema() {
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.putArray("required").add("ownerId").add("referenceName");
    ObjectNode properties = schema.putObject("properties");
    properties.putObject("ownerId").put("type", "string");
    properties.putObject("referenceName").put("type", "string");
    return schema;
  }

  private ObjectNode referenceSchema() {
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.putArray("required").add("sourceId").add("referenceName").add("targetId");
    ObjectNode properties = schema.putObject("properties");
    properties.putObject("sourceId").put("type", "string");
    properties.putObject("referenceName").put("type", "string");
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
    schema.putArray("required").add("elementId");
    schema.putObject("properties").putObject("elementId").put("type", "string");
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

  private String metamodelContract(ModelLevel level) {
    AssistantMetamodelSchemaService.MetamodelCoverage coverage = schemas.coverage(level);
    return "Root EClass: "
        + schemas.rootType(level)
        + "\n"
        + schemas.languageIndex(level)
        + "\nCoverage: "
        + coverage.creatableTypes()
        + " creatable types, "
        + coverage.attributes()
        + " attributes, "
        + coverage.containments()
        + " containments, "
        + coverage.relationships()
        + " relationships."
        + "\nAll creatable EClasses are allowed by the response JSON Schema enum. Use retrieved "
        + "runtime-metamodel-type snippets for exact attributes, containments, references, enum "
        + "literals, and required features.";
  }

  private String pretty(JsonNode node) {
    try {
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    } catch (Exception ignored) {
      return node.toString();
    }
  }
}
