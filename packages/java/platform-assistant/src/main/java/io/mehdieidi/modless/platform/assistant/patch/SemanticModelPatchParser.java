package io.mehdieidi.modless.platform.assistant.patch;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Parses the provider-neutral semantic patch JSON contract. */
public class SemanticModelPatchParser {

  private static final Logger log = LoggerFactory.getLogger(SemanticModelPatchParser.class);
  private final ObjectMapper mapper;
  private final ObjectMapper tolerantMapper;

  public SemanticModelPatchParser(ObjectMapper mapper) {
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

  /**
   * Parses a semantic patch response, accepting an optional Markdown JSON fence.
   *
   * @param content provider response content
   * @return parsed semantic patch
   */
  public SemanticModelPatch parse(String content) {
    String value = stripFence(content);
    if (value.isBlank()) {
      throw new PlatformException(502, "AI planner returned an empty response.");
    }
    Exception lastFailure = null;
    ArrayNode standaloneOperations = mapper.createArrayNode();
    List<String> candidates = jsonCandidates(value);
    for (String json : candidates) {
      try {
        JsonNode parsed = tolerantMapper.readTree(json);
        if (isStandaloneOperation(parsed)) {
          standaloneOperations.add(parsed.deepCopy());
        }
        ObjectNode root = canonicalRoot(parsed);
        normalizeOperations(root);
        return mapper.treeToValue(root, SemanticModelPatch.class);
      } catch (Exception ex) {
        lastFailure = ex;
      }
    }
    if (!standaloneOperations.isEmpty()) {
      try {
        ObjectNode root = mapper.createObjectNode();
        root.set("operations", standaloneOperations);
        normalizeOperations(root);
        return mapper.treeToValue(root, SemanticModelPatch.class);
      } catch (Exception ex) {
        lastFailure = ex;
      }
    }
    log.warn(
        "AI planner semantic patch parse failed responseChars={} candidates={} cause={}: {}",
        value.length(),
        candidates.size(),
        lastFailure == null ? "unknown" : lastFailure.getClass().getSimpleName(),
        lastFailure == null ? "No JSON object or array found." : lastFailure.getMessage());
    throw new PlatformException(502, "AI planner returned an invalid semantic patch.");
  }

  private boolean isStandaloneOperation(JsonNode parsed) {
    return parsed instanceof ObjectNode object
        && (object.has("type") || object.has("operation") || object.has("op"))
        && (object.has("targetElementId")
            || object.has("element")
            || object.has("body")
            || object.has("sourceElementId")
            || object.has("source"));
  }

  private ObjectNode canonicalRoot(JsonNode parsed) {
    ObjectNode root = mapper.createObjectNode();
    if (parsed instanceof ArrayNode operations) {
      root.set("operations", operations);
    } else if (parsed instanceof ObjectNode object) {
      JsonNode wrapped = firstNonNull(object.get("semanticPatch"), object.get("patch"));
      if (wrapped instanceof ObjectNode wrappedObject) {
        root = wrappedObject;
      } else if (wrapped instanceof ArrayNode wrappedOperations) {
        root.set("operations", wrappedOperations);
      } else {
        root = object;
      }
    } else {
      throw new IllegalArgumentException("Planner response must be a JSON object or array.");
    }
    if (!(root.get("operations") instanceof ArrayNode)) {
      throw new IllegalArgumentException("Planner response does not contain an operations array.");
    }
    return root;
  }

  private JsonNode firstNonNull(JsonNode... values) {
    for (JsonNode value : values) {
      if (value != null && !value.isNull()) {
        return value;
      }
    }
    return null;
  }

  private void normalizeOperations(ObjectNode root) {
    if (!(root.get("operations") instanceof ArrayNode operations)) {
      return;
    }
    ArrayNode normalized = mapper.createArrayNode();
    operations.forEach(
        operation -> {
          if (!(operation instanceof ObjectNode object)) {
            normalized.add(operation);
            return;
          }
          normalizeAliases(object);
          if ("SET_ATTRIBUTE".equals(object.path("type").asText())
              && missingText(object, "referenceName")
              && object.get("attributes") instanceof ObjectNode attributes
              && !attributes.isEmpty()) {
            attributes
                .fields()
                .forEachRemaining(
                    attribute -> {
                      ObjectNode expanded = object.deepCopy();
                      expanded.put("referenceName", attribute.getKey());
                      expanded.set("attributes", attribute.getValue().deepCopy());
                      normalized.add(expanded);
                    });
            return;
          }
          normalized.add(object);
        });
    root.set("operations", normalized);
  }

  private void normalizeAliases(ObjectNode operation) {
    if (operation.get("body") instanceof ObjectNode body) {
      copyBodyAlias(operation, body, "id", "targetElementId");
      copyBodyAlias(operation, body, "type", "elementType");
      ObjectNode attributes = body.deepCopy();
      attributes.remove(java.util.List.of("id", "type"));
      if (!operation.has("attributes") && !attributes.isEmpty()) {
        operation.set("attributes", attributes);
      }
      operation.remove("body");
    }
    copyAlias(operation, "operation", "type");
    copyAlias(operation, "op", "type");
    copyAlias(operation, "element", "targetElementId");
    copyAlias(operation, "source", "sourceElementId");
    copyAlias(operation, "target", "targetElementId");
    copyAlias(operation, "reference", "referenceName");
    copyAlias(operation, "attributeName", "referenceName");
    copyAlias(operation, "attribute", "referenceName");
    normalizeOperationType(operation);
    if ("ADD_ELEMENT".equals(operation.path("type").asText())
        && operation.get("attributes") instanceof ObjectNode attributes) {
      if (missingText(operation, "targetElementId") && attributes.hasNonNull("id")) {
        operation.set("targetElementId", attributes.get("id").deepCopy());
      }
      if (missingText(operation, "elementType") && attributes.hasNonNull("eClass")) {
        operation.set("elementType", attributes.get("eClass").deepCopy());
      }
    }
    if ("SET_ATTRIBUTE".equals(operation.path("type").asText())
        && !missingText(operation, "referenceName")
        && operation.has("value")
        && !operation.has("attributes")) {
      operation.set("attributes", operation.get("value").deepCopy());
    }
    operation.remove(
        java.util.List.of(
            "operation",
            "op",
            "element",
            "source",
            "target",
            "reference",
            "attributeName",
            "attribute",
            "value"));
  }

  private void copyBodyAlias(
      ObjectNode operation, ObjectNode body, String alias, String canonical) {
    if (!operation.has(canonical) && body.has(alias)) {
      operation.set(canonical, body.get(alias).deepCopy());
    }
  }

  private void normalizeOperationType(ObjectNode operation) {
    if (!operation.hasNonNull("type")) {
      return;
    }
    String normalized = operation.path("type").asText("").trim().replace('-', '_').toUpperCase();
    normalized =
        switch (normalized) {
          case "CREATE_ELEMENT", "ADD", "CREATE" -> "ADD_ELEMENT";
          case "CONNECT", "CREATE_RELATIONSHIP", "ADD_RELATIONSHIP" -> "CONNECT_ELEMENTS";
          case "UPDATE_ATTRIBUTE", "UPDATEATTRIBUTE", "SET" -> "SET_ATTRIBUTE";
          case "DELETE", "REMOVE", "REMOVE_ELEMENT" -> "DELETE_ELEMENT";
          default -> normalized;
        };
    operation.put("type", normalized);
  }

  private void copyAlias(ObjectNode operation, String alias, String canonical) {
    if (!operation.has(canonical) && operation.has(alias)) {
      operation.set(canonical, operation.get(alias).deepCopy());
    }
  }

  private boolean missingText(ObjectNode operation, String field) {
    return !operation.hasNonNull(field) || operation.path(field).asText().isBlank();
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

  private List<String> jsonCandidates(String content) {
    String value = content == null ? "" : content.trim();
    java.util.ArrayList<String> candidates = new java.util.ArrayList<>();
    for (int start = 0; start < value.length(); start++) {
      char opener = value.charAt(start);
      if (opener != '{' && opener != '[') {
        continue;
      }
      int end = matchingJsonEnd(value, start);
      if (end > start) {
        candidates.add(value.substring(start, end + 1));
      }
    }
    return candidates.isEmpty() ? List.of(value) : List.copyOf(candidates);
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
      } else if (current == '[') {
        expectedClosers.push(']');
      } else if (current == '}' || current == ']') {
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
}
