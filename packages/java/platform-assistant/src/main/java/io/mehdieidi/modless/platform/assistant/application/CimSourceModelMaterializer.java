package io.mehdieidi.modless.platform.assistant.application;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Turns LLM-classified CIM source evidence into formally typed semantic operations. */
final class CimSourceModelMaterializer {

  private final AssistantMetamodelSchemaService schemas;
  private final ObjectMapper mapper;
  private final ObjectMapper tolerantMapper;

  CimSourceModelMaterializer(AssistantMetamodelSchemaService schemas, ObjectMapper mapper) {
    this.schemas = schemas;
    this.mapper = mapper;
    this.tolerantMapper =
        mapper
            .copy()
            .enable(
                JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(),
                JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature(),
                JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature(),
                JsonReadFeature.ALLOW_YAML_COMMENTS.mappedFeature());
  }

  Optional<SemanticModelPatch> materialize(String sourceAnalysis) {
    Optional<JsonNode> parsed = parseJsonObject(sourceAnalysis);
    if (parsed.isEmpty()) {
      return Optional.empty();
    }
    JsonNode root = parsed.get();
    JsonNode elementsNode = firstArray(root, "elements", "cimElements", "modelElements");
    if (elementsNode.isMissingNode() || elementsNode.isEmpty()) {
      return Optional.empty();
    }
    Map<String, String> localIds = new LinkedHashMap<>();
    Map<String, String> localTypes = new LinkedHashMap<>();
    List<SemanticModelPatch.Operation> operations = new ArrayList<>();
    for (JsonNode element : elementsNode) {
      createElement(element)
          .ifPresent(
              operation -> {
                operations.add(operation);
                String localKey = localKey(element, operation);
                if (!localKey.isBlank()) {
                  localIds.put(localKey, operation.targetElementId());
                  localTypes.put(localKey, operation.elementType());
                }
              });
    }
    JsonNode relationshipsNode = firstArray(root, "relationships", "links", "references");
    for (JsonNode relationship : relationshipsNode) {
      createRelationship(relationship, localIds, localTypes).ifPresent(operations::add);
    }
    return operations.isEmpty()
        ? Optional.empty()
        : Optional.of(new SemanticModelPatch(operations));
  }

  private Optional<SemanticModelPatch.Operation> createElement(JsonNode element) {
    String rawType = firstText(element, "type", "eClass", "metamodelType");
    if (rawType.isBlank()) {
      return Optional.empty();
    }
    String type;
    try {
      type = schemas.canonicalType(ModelLevel.CIM, rawType);
    } catch (PlatformException failure) {
      return Optional.empty();
    }
    boolean creatable =
        schemas
            .typeSchema(ModelLevel.CIM, type)
            .map(AssistantMetamodelSchemaService.TypeSchema::creatable)
            .orElse(false);
    if (!creatable) {
      return Optional.empty();
    }
    ObjectNode attributes = mapper.createObjectNode();
    mergeAttributes(type, element.path("attributes"), attributes);
    mergeDirectAttributes(type, element, attributes);
    ensureMeaningfulLabel(type, element, attributes);
    String id = java.util.UUID.randomUUID().toString();
    return Optional.of(
        new SemanticModelPatch.Operation(
            SemanticModelPatch.OperationType.ADD_ELEMENT, id, type, attributes, null, null));
  }

  private Optional<SemanticModelPatch.Operation> createRelationship(
      JsonNode relationship, Map<String, String> localIds, Map<String, String> localTypes) {
    String sourceKey = firstText(relationship, "source", "sourceKey", "from");
    String targetKey = firstText(relationship, "target", "targetKey", "to");
    String sourceId = localIds.getOrDefault(sourceKey, sourceKey);
    String targetId = localIds.getOrDefault(targetKey, targetKey);
    String reference = firstText(relationship, "referenceName", "feature", "reference");
    if (sourceId.isBlank() || targetId.isBlank() || reference.isBlank()) {
      return Optional.empty();
    }
    String sourceType = localTypes.get(sourceKey);
    String targetType = localTypes.get(targetKey);
    if (sourceType != null
        && targetType != null
        && !schemas.acceptsReferenceTarget(ModelLevel.CIM, sourceType, reference, targetType)) {
      return Optional.empty();
    }
    return Optional.of(
        new SemanticModelPatch.Operation(
            SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
            targetId,
            null,
            null,
            sourceId,
            reference));
  }

  private void mergeAttributes(String type, JsonNode source, ObjectNode target) {
    if (source == null || !source.isObject()) {
      return;
    }
    source
        .fields()
        .forEachRemaining(
            entry -> {
              if (schemas.attribute(ModelLevel.CIM, type, entry.getKey()).isPresent()) {
                target.set(entry.getKey(), entry.getValue().deepCopy());
              }
            });
  }

  private void mergeDirectAttributes(String type, JsonNode source, ObjectNode target) {
    if (source == null || !source.isObject()) {
      return;
    }
    source
        .fields()
        .forEachRemaining(
            entry -> {
              if (!entry.getValue().isValueNode() || reservedElementField(entry.getKey())) {
                return;
              }
              if (schemas.attribute(ModelLevel.CIM, type, entry.getKey()).isPresent()) {
                target.set(entry.getKey(), entry.getValue().deepCopy());
              }
            });
  }

  private void ensureMeaningfulLabel(String type, JsonNode source, ObjectNode attributes) {
    if (attributes.hasNonNull("name") && !attributes.path("name").asText("").isBlank()) {
      return;
    }
    String name = firstText(source, "name", "label", "title");
    if (name.isBlank()) {
      name = firstText(source, "summary", "description", "sourceExcerpt");
    }
    if (name.isBlank()) {
      name = "Modeled " + type.replaceAll("([a-z])([A-Z])", "$1 $2");
    }
    if (schemas.attribute(ModelLevel.CIM, type, "name").isPresent()) {
      attributes.put("name", name);
    } else if (schemas.attribute(ModelLevel.CIM, type, "summary").isPresent()) {
      attributes.put("summary", name);
    }
  }

  private Optional<JsonNode> parseJsonObject(String content) {
    if (content == null || content.isBlank()) {
      return Optional.empty();
    }
    String trimmed = stripFence(content);
    for (String candidate : jsonCandidates(trimmed)) {
      try {
        JsonNode parsed = tolerantMapper.readTree(candidate);
        if (parsed != null && parsed.isObject()) {
          return Optional.of(parsed);
        }
      } catch (Exception ignored) {
        // Try the next bounded JSON object.
      }
    }
    return Optional.empty();
  }

  private List<String> jsonCandidates(String value) {
    List<String> result = new ArrayList<>();
    result.add(value.trim());
    for (int start = 0; start < value.length(); start++) {
      if (value.charAt(start) != '{') {
        continue;
      }
      int end = matchingJsonEnd(value, start);
      if (end > start) {
        result.add(value.substring(start, end + 1));
      }
    }
    return result.stream().distinct().toList();
  }

  private int matchingJsonEnd(String value, int start) {
    java.util.ArrayDeque<Character> expectedClosers = new java.util.ArrayDeque<>();
    boolean inString = false;
    boolean escaped = false;
    for (int index = start; index < value.length(); index++) {
      char current = value.charAt(index);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (current == '\\') {
          escaped = true;
        } else if (current == '"') {
          inString = false;
        }
        continue;
      }
      if (current == '"') {
        inString = true;
      } else if (current == '{') {
        expectedClosers.push('}');
      } else if (current == '}') {
        if (expectedClosers.isEmpty() || expectedClosers.pop() != current) {
          return -1;
        }
        if (expectedClosers.isEmpty()) {
          return index;
        }
      }
    }
    return -1;
  }

  private JsonNode firstArray(JsonNode root, String... names) {
    for (String name : names) {
      JsonNode candidate = root.path(name);
      if (candidate.isArray()) {
        return candidate;
      }
    }
    return mapper.createArrayNode();
  }

  private String firstText(JsonNode node, String... names) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return "";
    }
    for (String name : names) {
      String value = node.path(name).asText("");
      if (!value.isBlank()) {
        return value.trim();
      }
    }
    return "";
  }

  private String localKey(JsonNode element, SemanticModelPatch.Operation operation) {
    String key = firstText(element, "key", "id", "sourceKey", "localId");
    return key.isBlank() ? operation.attributes().path("name").asText("") : key;
  }

  private boolean reservedElementField(String field) {
    return List.of("type", "eClass", "metamodelType", "key", "id", "sourceKey", "localId")
        .contains(field);
  }

  private String stripFence(String content) {
    String value = content == null ? "" : content.trim();
    if (!value.startsWith("```")) {
      return value;
    }
    int firstLineEnd = value.indexOf('\n');
    int closingFence = value.lastIndexOf("```");
    if (firstLineEnd < 0 || closingFence <= firstLineEnd) {
      return value;
    }
    return value.substring(firstLineEnd + 1, closingFence).trim();
  }
}
