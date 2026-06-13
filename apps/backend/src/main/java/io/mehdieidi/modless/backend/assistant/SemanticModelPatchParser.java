package io.mehdieidi.modless.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.core.PlatformException;
import org.springframework.stereotype.Component;

/** Parses the provider-neutral semantic patch JSON contract. */
@Component
public class SemanticModelPatchParser {

  private final ObjectMapper mapper;

  public SemanticModelPatchParser(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * Parses a semantic patch response, accepting an optional Markdown JSON fence.
   *
   * @param content provider response content
   * @return parsed semantic patch
   */
  public SemanticModelPatch parse(String content) {
    String json = stripFence(content);
    if (json.isBlank()) {
      throw new PlatformException(502, "AI planner returned an empty response.");
    }
    try {
      ObjectNode root = (ObjectNode) mapper.readTree(json);
      normalizeOperations(root);
      return mapper.treeToValue(root, SemanticModelPatch.class);
    } catch (Exception ex) {
      throw new PlatformException(502, "AI planner returned an invalid semantic patch.");
    }
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
    copyAlias(operation, "operation", "type");
    copyAlias(operation, "op", "type");
    copyAlias(operation, "attributeName", "referenceName");
    copyAlias(operation, "attribute", "referenceName");
    if ("SET_ATTRIBUTE".equals(operation.path("type").asText())
        && !missingText(operation, "referenceName")
        && operation.has("value")
        && !operation.has("attributes")) {
      operation.set("attributes", operation.get("value").deepCopy());
    }
    operation.remove(java.util.List.of("operation", "op", "attributeName", "attribute", "value"));
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
}
