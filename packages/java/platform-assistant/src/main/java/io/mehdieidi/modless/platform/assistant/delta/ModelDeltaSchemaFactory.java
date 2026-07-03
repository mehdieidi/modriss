package io.mehdieidi.modless.platform.assistant.delta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;

/** Builds turn-scoped ModelDelta schema and metamodel contract prompt blocks. */
public class ModelDeltaSchemaFactory {

  private final AssistantMetamodelSchemaService schemas;
  private final ObjectMapper mapper;

  public ModelDeltaSchemaFactory(AssistantMetamodelSchemaService schemas, ObjectMapper mapper) {
    this.schemas = schemas;
    this.mapper = mapper;
  }

  /** Prompt snippets that describe the single provider-facing mutation protocol. */
  public List<AssistantModelProvider.ContextSnippet> snippets(ModelLevel level) {
    return List.of(
        new AssistantModelProvider.ContextSnippet(
            "model-delta-json-schema", "ModelDelta JSON Schema", pretty(responseSchema(level))),
        new AssistantModelProvider.ContextSnippet(
            "model-delta-metamodel-contract",
            "Ecore-derived writable contracts",
            metamodelContract(level)));
  }

  /** JSON Schema for a structured ModelDelta response. */
  public ObjectNode responseSchema(ModelLevel level) {
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
    properties.set("elements", arrayOf(elementSchema(level)));
    properties.set("references", arrayOf(referenceSchema()));
    properties.set("attributeUpdates", arrayOf(attributeUpdateSchema()));
    properties.set("deletions", arrayOf(deletionSchema()));
    properties.set("assumptions", arrayOf(stringSchema()));
    return root;
  }

  private ObjectNode elementSchema(ModelLevel level) {
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.putArray("required").add("localId").add("eClass").add("attributes").add("placement");
    ObjectNode properties = schema.putObject("properties");
    properties.putObject("localId").put("type", "string");
    properties.set(
        "eClass", enumSchema(schemas.coverage(level).typeNames().toArray(String[]::new)));
    properties.putObject("attributes").put("type", "object");
    properties.set("placement", placementSchema());
    properties.set("references", arrayOf(referenceSchema()));
    properties.set("evidenceIds", arrayOf(stringSchema()));
    return schema;
  }

  private ObjectNode placementSchema() {
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

  private String metamodelContract(ModelLevel level) {
    AssistantMetamodelSchemaService.MetamodelCoverage coverage = schemas.coverage(level);
    return "Canonical source: Ecore/runtime metamodel\nRoot EClass: "
        + schemas.rootType(level)
        + "\n"
        + schemas.languageIndex(level)
        + "\nCoverage: "
        + coverage.creatableTypes()
        + " creatable EClasses, "
        + coverage.attributes()
        + " writable attributes, "
        + coverage.containments()
        + " containments, "
        + coverage.relationships()
        + " writable relationships.\nUse only the EClasses and feature names in these contracts.";
  }

  private String pretty(JsonNode node) {
    try {
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    } catch (Exception ignored) {
      return node.toString();
    }
  }
}
