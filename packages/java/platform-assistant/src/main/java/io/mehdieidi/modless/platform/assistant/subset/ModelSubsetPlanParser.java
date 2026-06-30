package io.mehdieidi.modless.platform.assistant.subset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.domain.AssistantChoice;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Parses LLM JSON responses for the schema-guided model subset protocol. */
public class ModelSubsetPlanParser {

  private final ObjectMapper mapper;
  private final ObjectMapper tolerantMapper;

  public ModelSubsetPlanParser(ObjectMapper mapper) {
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

  /** Parses one JSON subset turn plan, accepting an optional Markdown JSON fence. */
  public ModelSubsetTurnPlan parse(String content) {
    if (content == null || content.isBlank()) {
      throw new PlatformException(502, "AI assistant returned an empty model subset plan.");
    }
    List<String> orderedCandidates = new ArrayList<>(candidates(stripFence(content)));
    orderedCandidates.sort(Comparator.comparingInt(String::length).reversed());
    for (String candidate : orderedCandidates) {
      try {
        JsonNode parsed = tolerantMapper.readTree(candidate);
        if (parsed instanceof ObjectNode object) {
          return parseObject(object);
        }
      } catch (RuntimeException | JsonProcessingException ignored) {
        // Try the next bounded JSON candidate.
      }
    }
    throw new PlatformException(502, "AI assistant returned an invalid model subset plan.");
  }

  private ModelSubsetTurnPlan parseObject(ObjectNode object) {
    AssistantTurnPlan.Intent intent = parseIntent(object.path("intent").asText(""));
    ModelSubsetTurnPlan.Kind kind = parseKind(object.path("kind").asText(""));
    List<AssistantChoice> questions = parseQuestions(object.path("questions"));
    ModelSubsetTurnPlan.ModelSubset subset =
        parseSubset(
            firstNonNull(
                object.get("subset"), object.get("modelSubset"), object.get("partialModel")));
    if (!subset.elements().isEmpty()
        || !subset.references().isEmpty()
        || !subset.attributeUpdates().isEmpty()
        || !subset.deletions().isEmpty()) {
      intent = AssistantTurnPlan.Intent.MUTATION;
      kind = ModelSubsetTurnPlan.Kind.MODEL_SUBSET;
    }
    return new ModelSubsetTurnPlan(
        intent, kind, object.path("message").asText(), questions, subset);
  }

  private ModelSubsetTurnPlan.ModelSubset parseSubset(JsonNode node) {
    if (!(node instanceof ObjectNode object)) {
      return ModelSubsetTurnPlan.ModelSubset.empty();
    }
    return new ModelSubsetTurnPlan.ModelSubset(
        text(object, "subsetId", "id"),
        text(object, "scope", "description"),
        parseElements(firstNonNull(object.get("elements"), object.get("nodes"))),
        parseReferences(
            firstNonNull(
                object.get("references"), object.get("relationships"), object.get("edges"))),
        parseAttributeUpdates(
            firstNonNull(
                object.get("attributeUpdates"), object.get("updates"), object.get("attributes"))),
        parseDeletions(firstNonNull(object.get("deletions"), object.get("deletedElements"))));
  }

  private List<ModelSubsetTurnPlan.Element> parseElements(JsonNode node) {
    if (!(node instanceof ArrayNode array)) {
      return List.of();
    }
    List<ModelSubsetTurnPlan.Element> elements = new ArrayList<>();
    for (JsonNode item : array) {
      if (!(item instanceof ObjectNode object)) {
        continue;
      }
      elements.add(
          new ModelSubsetTurnPlan.Element(
              text(object, "localId", "id", "key"),
              text(object, "eClass", "type", "elementType"),
              object.get("attributes") instanceof ObjectNode attributes
                  ? attributes.deepCopy()
                  : mapper.createObjectNode(),
              parseContainment(firstNonNull(object.get("containedBy"), object.get("owner"))),
              parseReferences(
                  firstNonNull(
                      object.get("references"),
                      object.get("relationships"),
                      object.get("edges")))));
    }
    return List.copyOf(elements);
  }

  private ModelSubsetTurnPlan.Containment parseContainment(JsonNode node) {
    if (!(node instanceof ObjectNode object)) {
      return null;
    }
    return new ModelSubsetTurnPlan.Containment(
        text(object, "ownerId", "sourceId", "owner", "parentId"),
        text(object, "referenceName", "reference", "containment", "feature"));
  }

  private List<ModelSubsetTurnPlan.Reference> parseReferences(JsonNode node) {
    if (!(node instanceof ArrayNode array)) {
      return List.of();
    }
    List<ModelSubsetTurnPlan.Reference> references = new ArrayList<>();
    for (JsonNode item : array) {
      if (!(item instanceof ObjectNode object)) {
        continue;
      }
      references.add(
          new ModelSubsetTurnPlan.Reference(
              text(object, "sourceId", "source"),
              text(object, "referenceName", "reference", "feature", "kind"),
              text(object, "targetId", "target")));
    }
    return List.copyOf(references);
  }

  private List<ModelSubsetTurnPlan.AttributeUpdate> parseAttributeUpdates(JsonNode node) {
    if (!(node instanceof ArrayNode array)) {
      return List.of();
    }
    List<ModelSubsetTurnPlan.AttributeUpdate> updates = new ArrayList<>();
    for (JsonNode item : array) {
      if (!(item instanceof ObjectNode object)) {
        continue;
      }
      updates.add(
          new ModelSubsetTurnPlan.AttributeUpdate(
              text(object, "elementId", "targetId", "id"),
              text(object, "attributeName", "attribute", "referenceName", "feature"),
              firstNonNull(object.get("value"), object.get("attributes"))));
    }
    return List.copyOf(updates);
  }

  private List<ModelSubsetTurnPlan.Deletion> parseDeletions(JsonNode node) {
    if (!(node instanceof ArrayNode array)) {
      return List.of();
    }
    List<ModelSubsetTurnPlan.Deletion> deletions = new ArrayList<>();
    for (JsonNode item : array) {
      if (item instanceof ObjectNode object) {
        deletions.add(
            new ModelSubsetTurnPlan.Deletion(text(object, "elementId", "id", "targetId")));
      } else if (item != null && item.isTextual()) {
        deletions.add(new ModelSubsetTurnPlan.Deletion(item.asText()));
      }
    }
    return List.copyOf(deletions);
  }

  private List<AssistantChoice> parseQuestions(JsonNode questionsNode) {
    List<AssistantChoice> questions = new ArrayList<>();
    if (!(questionsNode instanceof ArrayNode)) {
      return questions;
    }
    for (JsonNode question : questionsNode) {
      List<AssistantChoice.Option> options = new ArrayList<>();
      for (JsonNode option : question.path("options")) {
        options.add(
            new AssistantChoice.Option(
                option.path("id").asText(),
                option.path("label").asText(),
                option.path("description").asText()));
      }
      questions.add(
          new AssistantChoice(
              question.path("id").asText(),
              question.path("prompt").asText(),
              parseSelectionMode(question.path("selectionMode").asText()),
              options,
              question.path("allowFreeText").asBoolean(false)));
    }
    return questions;
  }

  private AssistantTurnPlan.Intent parseIntent(String value) {
    if (value == null || value.isBlank()) {
      return AssistantTurnPlan.Intent.INFORMATION;
    }
    return switch (value.trim().toUpperCase(Locale.ROOT)) {
      case "MUTATION", "MUTATE", "EDIT", "CHANGE", "CREATE", "UPDATE" ->
          AssistantTurnPlan.Intent.MUTATION;
      default -> AssistantTurnPlan.Intent.INFORMATION;
    };
  }

  private ModelSubsetTurnPlan.Kind parseKind(String value) {
    if (value == null || value.isBlank()) {
      return ModelSubsetTurnPlan.Kind.ANSWER;
    }
    return switch (value.trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
      case "MODEL_SUBSET", "SUBSET", "PATCH", "MUTATION", "EDIT", "CHANGE" ->
          ModelSubsetTurnPlan.Kind.MODEL_SUBSET;
      case "CLARIFICATION", "CLARIFY", "QUESTION" -> ModelSubsetTurnPlan.Kind.CLARIFICATION;
      default -> ModelSubsetTurnPlan.Kind.ANSWER;
    };
  }

  private AssistantChoice.SelectionMode parseSelectionMode(String value) {
    try {
      return AssistantChoice.SelectionMode.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (RuntimeException ignored) {
      return AssistantChoice.SelectionMode.SINGLE;
    }
  }

  private JsonNode firstNonNull(JsonNode... values) {
    for (JsonNode value : values) {
      if (value != null && !value.isNull() && !value.isMissingNode()) {
        return value;
      }
    }
    return null;
  }

  private String text(ObjectNode object, String... names) {
    for (String name : names) {
      JsonNode value = object.get(name);
      if (value != null && !value.isNull() && !value.asText("").isBlank()) {
        return value.asText();
      }
    }
    return "";
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

  private List<String> candidates(String content) {
    List<String> result = new ArrayList<>();
    String trimmed = content.trim();
    result.add(trimmed);
    for (int start = 0; start < trimmed.length(); start++) {
      char opener = trimmed.charAt(start);
      if (opener != '{' && opener != '[') {
        continue;
      }
      int end = matchingJsonEnd(trimmed, start);
      if (end > start) {
        result.add(trimmed.substring(start, end + 1));
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
