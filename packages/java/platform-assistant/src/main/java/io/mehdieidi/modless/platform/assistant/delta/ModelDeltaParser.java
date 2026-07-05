package io.mehdieidi.modless.platform.assistant.delta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.domain.AssistantChoice;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Strict parser for provider-returned ModelDelta JSON. */
public class ModelDeltaParser {

  private final ObjectMapper mapper;

  public ModelDeltaParser(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** Parses exactly one JSON object, with only an optional JSON Markdown fence stripped. */
  public ModelDelta parse(String content) {
    String value = extractJsonObject(stripFence(content));
    if (value.isBlank()) {
      throw new PlatformException(502, "AI assistant returned an empty ModelDelta.");
    }
    try {
      JsonNode parsed = mapper.readTree(value);
      if (!(parsed instanceof ObjectNode object)) {
        throw new PlatformException(502, "AI assistant ModelDelta must be a JSON object.");
      }
      coerceRootAliases(object);
      return parseObject(object);
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(502, "AI assistant returned invalid ModelDelta JSON.");
    }
  }

  private void coerceRootAliases(ObjectNode object) {
    if (object == null) {
      return;
    }
    boolean hasMutationPayload =
        object.has("elements")
            || object.has("references")
            || object.has("attributeUpdates")
            || object.has("deletions");
    if (!object.has("intent") && hasMutationPayload) {
      object.put("intent", "MUTATION");
    }
    if (!object.has("kind") && hasMutationPayload) {
      object.put("kind", "MODEL_DELTA");
    }
    if (!object.has("message")) {
      object.put("message", "Model update drafted by the assistant.");
    }
  }

  private ModelDelta parseObject(ObjectNode object) {
    requireOnlyProperties(
        object,
        "ModelDelta",
        Set.of(
            "intent",
            "kind",
            "message",
            "questions",
            "elements",
            "references",
            "attributeUpdates",
            "deletions",
            "assumptions"));
    AssistantTurnPlan.Intent intent = parseIntent(requireText(object, "intent", "ModelDelta"));
    ModelDelta.Kind kind = parseKind(requireText(object, "kind", "ModelDelta"));
    String message = requireText(object, "message", "ModelDelta");
    List<ModelDelta.Element> elements = parseElements(object.path("elements"));
    List<ModelDelta.Reference> references = parseReferences(object.path("references"));
    List<ModelDelta.AttributeUpdate> updates =
        parseAttributeUpdates(object.path("attributeUpdates"));
    List<ModelDelta.Deletion> deletions = parseDeletions(object.path("deletions"));
    if (!elements.isEmpty()
        || !references.isEmpty()
        || !updates.isEmpty()
        || !deletions.isEmpty()) {
      intent = AssistantTurnPlan.Intent.MUTATION;
      kind = ModelDelta.Kind.MODEL_DELTA;
    }
    return new ModelDelta(
        intent,
        kind,
        message,
        parseQuestions(object.path("questions")),
        elements,
        references,
        updates,
        deletions,
        strings(object.path("assumptions")));
  }

  private List<ModelDelta.Element> parseElements(JsonNode node) {
    if (node.isMissingNode()) {
      return List.of();
    }
    ArrayNode array = requireArray(node, "ModelDelta elements");
    List<ModelDelta.Element> result = new ArrayList<>();
    for (JsonNode item : array) {
      if (!(item instanceof ObjectNode object)) {
        throw new PlatformException(502, "ModelDelta elements must be JSON objects.");
      }
      coerceElementAliases(object);
      coerceEvidenceIdsAlias(object);
      requireOnlyProperties(
          object,
          "ModelDelta element",
          Set.of("localId", "eClass", "attributes", "placement", "references", "evidenceIds"));
      ObjectNode attributes = coerceAttributes(object.path("attributes"));
      result.add(
          new ModelDelta.Element(
              requireText(object, "localId", "ModelDelta element"),
              requireText(object, "eClass", "ModelDelta element"),
              attributes.deepCopy(),
              parseOptionalPlacement(object.path("placement")),
              parseReferences(object.path("references")),
              strings(object.path("evidenceIds"))));
    }
    return List.copyOf(result);
  }

  private ModelDelta.Placement parseOptionalPlacement(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    return parsePlacement(node);
  }

  private ModelDelta.Placement parsePlacement(JsonNode node) {
    ObjectNode object = requireObject(node, "ModelDelta placement");
    coercePlacementAliases(object);
    coerceReferenceNameAlias(object);
    if (!object.has("ownerId") || object.get("ownerId").isNull()) {
      object.put("ownerId", "root");
    }
    requireOnlyProperties(object, "ModelDelta placement", Set.of("ownerId", "referenceName"));
    return new ModelDelta.Placement(
        requireText(object, "ownerId", "ModelDelta placement"),
        requireText(object, "referenceName", "ModelDelta placement"));
  }

  private ObjectNode coerceAttributes(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return JsonNodeFactory.instance.objectNode();
    }
    if (node instanceof ObjectNode object) {
      return object;
    }
    if (node instanceof ArrayNode array) {
      ObjectNode converted = JsonNodeFactory.instance.objectNode();
      for (JsonNode item : array) {
        if (!(item instanceof ObjectNode entry)) {
          continue;
        }
        if (entry.has("name") && entry.has("value")) {
          converted.set(entry.get("name").asText(""), entry.get("value"));
        } else if (entry.has("attributeName") && entry.has("value")) {
          converted.set(entry.get("attributeName").asText(""), entry.get("value"));
        }
      }
      return converted;
    }
    throw new PlatformException(502, "ModelDelta element attributes must be a JSON object.");
  }

  private void coerceElementAliases(ObjectNode object) {
    if (object == null) {
      return;
    }
    if (!object.has("localId") && object.has("id")) {
      object.set("localId", object.get("id"));
    }
    object.remove("id");
    if (!object.has("eClass") && object.has("type")) {
      object.set("eClass", object.get("type"));
    }
    object.remove("type");
    if (!object.has("placement")
        && object.has("containment")
        && object.get("containment").isObject()) {
      object.set("placement", object.get("containment"));
    }
    object.remove("containment");
  }

  private void coercePlacementAliases(ObjectNode object) {
    if (object == null) {
      return;
    }
    if (!object.has("referenceName") && object.has("featureName")) {
      object.set("referenceName", object.get("featureName"));
    }
    if (!object.has("referenceName") && object.has("container")) {
      object.set("referenceName", object.get("container"));
    }
    if (!object.has("referenceName") && object.has("containment")) {
      object.set("referenceName", object.get("containment"));
    }
    object.remove("featureName");
    object.remove("container");
    object.remove("containment");
    if (!object.has("ownerId") && object.has("parentId")) {
      object.set("ownerId", object.get("parentId"));
    }
    object.remove("parentId");
  }

  private void coerceEvidenceIdsAlias(ObjectNode object) {
    if (object == null) {
      return;
    }
    if (object.has("sourceFactIds") && !object.has("evidenceIds")) {
      object.set("evidenceIds", object.get("sourceFactIds"));
    }
    object.remove("sourceFactIds");
  }

  private List<ModelDelta.Reference> parseReferences(JsonNode node) {
    if (node.isMissingNode()) {
      return List.of();
    }
    ArrayNode array = requireArray(node, "ModelDelta references");
    List<ModelDelta.Reference> result = new ArrayList<>();
    for (JsonNode item : array) {
      if (!(item instanceof ObjectNode object)) {
        throw new PlatformException(502, "ModelDelta references must be JSON objects.");
      }
      coerceReferenceNameAlias(object);
      coerceEvidenceIdsAlias(object);
      requireOnlyProperties(
          object,
          "ModelDelta reference",
          Set.of("sourceId", "ownerId", "referenceName", "targetId", "targetIds", "evidenceIds"));
      String sourceId = referenceSourceId(object);
      String referenceName = requireText(object, "referenceName", "ModelDelta reference");
      if (object.has("targetId")) {
        result.add(
            new ModelDelta.Reference(
                sourceId, referenceName, requireText(object, "targetId", "ModelDelta reference")));
        continue;
      }
      if (!object.has("targetIds")) {
        throw new PlatformException(502, "ModelDelta reference requires targetId or targetIds.");
      }
      for (String targetId : strings(object.path("targetIds"))) {
        result.add(new ModelDelta.Reference(sourceId, referenceName, targetId));
      }
    }
    return List.copyOf(result);
  }

  private String referenceSourceId(ObjectNode object) {
    if (object.has("sourceId")) {
      return requireText(object, "sourceId", "ModelDelta reference");
    }
    return requireText(object, "ownerId", "ModelDelta reference");
  }

  private List<ModelDelta.AttributeUpdate> parseAttributeUpdates(JsonNode node) {
    if (node.isMissingNode()) {
      return List.of();
    }
    ArrayNode array = requireArray(node, "ModelDelta attributeUpdates");
    List<ModelDelta.AttributeUpdate> result = new ArrayList<>();
    for (JsonNode item : array) {
      if (!(item instanceof ObjectNode object)) {
        throw new PlatformException(502, "ModelDelta attributeUpdates must be JSON objects.");
      }
      coerceAttributeNameAlias(object);
      coerceAttributeUpdateAliases(object);
      JsonNode nestedAttributes = object.get("attributes");
      if (nestedAttributes != null && nestedAttributes.isObject()) {
        String elementId = object.path("elementId").asText("");
        if (elementId.isBlank()) {
          throw new PlatformException(
              502, "ModelDelta attributeUpdate with attributes requires elementId.");
        }
        nestedAttributes
            .fields()
            .forEachRemaining(
                entry ->
                    result.add(
                        new ModelDelta.AttributeUpdate(
                            elementId, entry.getKey(), entry.getValue())));
        continue;
      }
      requireOnlyProperties(
          object, "ModelDelta attributeUpdate", Set.of("elementId", "attributeName", "value"));
      if (!object.has("value")) {
        throw new PlatformException(502, "ModelDelta attributeUpdate requires value.");
      }
      result.add(
          new ModelDelta.AttributeUpdate(
              requireText(object, "elementId", "ModelDelta attributeUpdate"),
              requireText(object, "attributeName", "ModelDelta attributeUpdate"),
              object.get("value")));
    }
    return List.copyOf(result);
  }

  private List<ModelDelta.Deletion> parseDeletions(JsonNode node) {
    if (node.isMissingNode()) {
      return List.of();
    }
    ArrayNode array = requireArray(node, "ModelDelta deletions");
    List<ModelDelta.Deletion> result = new ArrayList<>();
    for (JsonNode item : array) {
      if (!(item instanceof ObjectNode object)) {
        throw new PlatformException(502, "ModelDelta deletions must be JSON objects.");
      }
      requireOnlyProperties(object, "ModelDelta deletion", Set.of("elementId", "reason"));
      result.add(
          new ModelDelta.Deletion(
              requireText(object, "elementId", "ModelDelta deletion"),
              requireText(object, "reason", "ModelDelta deletion")));
    }
    return List.copyOf(result);
  }

  private List<AssistantChoice> parseQuestions(JsonNode node) {
    if (node.isMissingNode()) {
      return List.of();
    }
    ArrayNode array = requireArray(node, "ModelDelta questions");
    List<AssistantChoice> result = new ArrayList<>();
    for (JsonNode question : array) {
      if (!(question instanceof ObjectNode object)) {
        throw new PlatformException(502, "ModelDelta questions must be JSON objects.");
      }
      requireOnlyProperties(
          object,
          "ModelDelta question",
          Set.of("id", "prompt", "selectionMode", "options", "allowFreeText"));
      List<AssistantChoice.Option> options = new ArrayList<>();
      for (JsonNode option : requireArray(object.path("options"), "ModelDelta question options")) {
        if (!(option instanceof ObjectNode optionObject)) {
          throw new PlatformException(502, "ModelDelta question options must be JSON objects.");
        }
        requireOnlyProperties(
            optionObject, "ModelDelta question option", Set.of("id", "label", "description"));
        options.add(
            new AssistantChoice.Option(
                requireText(optionObject, "id", "ModelDelta question option"),
                requireText(optionObject, "label", "ModelDelta question option"),
                requireText(optionObject, "description", "ModelDelta question option")));
      }
      result.add(
          new AssistantChoice(
              requireText(object, "id", "ModelDelta question"),
              requireText(object, "prompt", "ModelDelta question"),
              parseSelectionMode(requireText(object, "selectionMode", "ModelDelta question")),
              options,
              object.path("allowFreeText").asBoolean(false)));
    }
    return List.copyOf(result);
  }

  private AssistantTurnPlan.Intent parseIntent(String value) {
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "INFORMATION",
          "EXPLAIN",
          "EXPLAIN_MODEL",
          "ANSWER",
          "READ",
          "QUERY",
          "QUESTION",
          "CLARIFICATION" ->
          AssistantTurnPlan.Intent.INFORMATION;
      case "MUTATION",
          "CREATE",
          "UPDATE",
          "EDIT",
          "DELETE",
          "MODIFY",
          "EXTEND",
          "BUILD",
          "MODEL",
          "MODELING",
          "CREATE_MODEL",
          "EXTEND_MODEL",
          "EDIT_MODEL",
          "DELETE_MODEL",
          "TRANSFORM_SOURCE_TO_MODEL",
          "REPAIR_STRUCTURE" ->
          AssistantTurnPlan.Intent.MUTATION;
      default -> throw new PlatformException(502, "ModelDelta intent is not allowed.");
    };
  }

  private ModelDelta.Kind parseKind(String value) {
    return switch (value.trim().toUpperCase(Locale.ROOT)) {
      case "MODEL_DELTA" -> ModelDelta.Kind.MODEL_DELTA;
      case "CLARIFICATION" -> ModelDelta.Kind.CLARIFICATION;
      case "ANSWER" -> ModelDelta.Kind.ANSWER;
      default -> throw new PlatformException(502, "ModelDelta kind is not allowed.");
    };
  }

  private AssistantChoice.SelectionMode parseSelectionMode(String value) {
    try {
      return AssistantChoice.SelectionMode.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (RuntimeException ignored) {
      throw new PlatformException(502, "ModelDelta question selectionMode is not allowed.");
    }
  }

  private List<String> strings(JsonNode node) {
    if (node.isMissingNode()) {
      return List.of();
    }
    ArrayNode array = requireArray(node, "ModelDelta string list");
    List<String> result = new ArrayList<>();
    for (JsonNode value : array) {
      if (value == null || !value.isTextual()) {
        throw new PlatformException(502, "ModelDelta string lists may contain only strings.");
      }
      if (!value.asText().isBlank()) {
        result.add(value.asText());
      }
    }
    return List.copyOf(result);
  }

  private String requireText(ObjectNode object, String name, String scope) {
    JsonNode value = object.get(name);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      throw new PlatformException(502, scope + " requires string field " + name + ".");
    }
    return value.asText("");
  }

  private ObjectNode requireObject(JsonNode node, String scope) {
    if (node instanceof ObjectNode object) {
      return object;
    }
    throw new PlatformException(502, scope + " must be a JSON object.");
  }

  private ArrayNode requireArray(JsonNode node, String scope) {
    if (node instanceof ArrayNode array) {
      return array;
    }
    throw new PlatformException(502, scope + " must be a JSON array.");
  }

  private void coerceReferenceNameAlias(ObjectNode object) {
    if (object != null && object.has("featureName") && !object.has("referenceName")) {
      object.set("referenceName", object.get("featureName"));
    }
    object.remove("featureName");
  }

  private void coerceAttributeUpdateAliases(ObjectNode object) {
    if (object == null) {
      return;
    }
    if (!object.has("elementId") && object.has("localId")) {
      object.set("elementId", object.get("localId"));
    }
    object.remove("localId");
    if (!object.has("elementId") && object.has("id")) {
      object.set("elementId", object.get("id"));
    }
    object.remove("id");
  }

  private void coerceAttributeNameAlias(ObjectNode object) {
    if (object == null) {
      return;
    }
    if (object.has("name") && !object.has("attributeName")) {
      object.set("attributeName", object.get("name"));
    }
    object.remove("name");
    if (object.has("featureName") && !object.has("attributeName")) {
      object.set("attributeName", object.get("featureName"));
    }
    object.remove("featureName");
  }

  private void requireOnlyProperties(ObjectNode object, String scope, Set<String> allowed) {
    object
        .fieldNames()
        .forEachRemaining(
            name -> {
              if (!allowed.contains(name)) {
                throw new PlatformException(
                    502, scope + " contains unsupported field " + name + ".");
              }
            });
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

  private String extractJsonObject(String content) {
    if (content == null || content.isBlank()) {
      return "";
    }
    int start = content.indexOf('{');
    if (start < 0) {
      return content.trim();
    }
    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int index = start; index < content.length(); index++) {
      char current = content.charAt(index);
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
        continue;
      }
      if (current == '{') {
        depth++;
      } else if (current == '}') {
        depth--;
        if (depth == 0) {
          return content.substring(start, index + 1).trim();
        }
      }
    }
    return content.substring(start).trim();
  }
}
