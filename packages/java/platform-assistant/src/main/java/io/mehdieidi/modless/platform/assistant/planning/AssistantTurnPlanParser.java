package io.mehdieidi.modless.platform.assistant.planning;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.domain.AssistantChoice;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.SemanticModelPatchParser;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Parses the provider's structured turn decision without trusting surrounding prose. */
public class AssistantTurnPlanParser {

  private final ObjectMapper mapper;
  private final ObjectMapper tolerantMapper;
  private final SemanticModelPatchParser patchParser;

  public AssistantTurnPlanParser(ObjectMapper mapper) {
    this.mapper = mapper;
    this.tolerantMapper =
        mapper
            .copy()
            .enable(
                JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(),
                JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature(),
                JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature(),
                JsonReadFeature.ALLOW_YAML_COMMENTS.mappedFeature());
    this.patchParser = new SemanticModelPatchParser(mapper);
  }

  /** Parses one JSON turn plan. */
  public AssistantTurnPlan parse(String content) {
    if (content == null || content.isBlank()) {
      throw new PlatformException(502, "AI assistant returned an empty turn plan.");
    }
    String normalized = stripFence(content);
    List<String> orderedCandidates = new ArrayList<>(candidates(normalized));
    orderedCandidates.sort(Comparator.comparingInt(String::length).reversed());
    for (String candidate : orderedCandidates) {
      try {
        AssistantTurnPlan plan = parseCandidate(candidate);
        if (plan != null) {
          return plan;
        }
      } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ignored) {
        // Try the next bounded JSON candidate.
      }
    }
    try {
      SemanticModelPatch patch = patchParser.parse(normalized);
      if (!patch.operations().isEmpty()) {
        return new AssistantTurnPlan(
            AssistantTurnPlan.Intent.MUTATION, AssistantTurnPlan.Kind.PATCH, "", List.of(), patch);
      }
    } catch (RuntimeException ignored) {
      // Fall through to the invalid-plan error.
    }
    throw new PlatformException(502, "AI assistant returned an invalid structured turn plan.");
  }

  private AssistantTurnPlan parseCandidate(String candidate)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    JsonNode root = tolerantMapper.readTree(candidate);
    if (!(root instanceof ObjectNode object)) {
      return null;
    }
    AssistantTurnPlan.Kind kind = parseKind(object.path("kind").asText(""));
    AssistantTurnPlan.Intent intent = parseIntent(object.path("intent").asText(""), kind);
    List<AssistantChoice> questions = parseQuestions(object.path("questions"));
    SemanticModelPatch patch = parsePatch(object);
    if (!patch.operations().isEmpty()) {
      intent = AssistantTurnPlan.Intent.MUTATION;
      kind = AssistantTurnPlan.Kind.PATCH;
    }
    return new AssistantTurnPlan(intent, kind, object.path("message").asText(), questions, patch);
  }

  private SemanticModelPatch parsePatch(ObjectNode root)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    ObjectNode wrapper = mapper.createObjectNode();
    JsonNode operations = root.get("operations");
    if (operations == null || operations.isNull() || operations.isMissingNode()) {
      wrapper.set("operations", mapper.createArrayNode());
    } else {
      wrapper.set("operations", operations);
    }
    return patchParser.parse(mapper.writeValueAsString(wrapper));
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

  private AssistantTurnPlan.Intent parseIntent(String value, AssistantTurnPlan.Kind kind) {
    if (value == null || value.isBlank()) {
      return kind == AssistantTurnPlan.Kind.PATCH
          ? AssistantTurnPlan.Intent.MUTATION
          : AssistantTurnPlan.Intent.INFORMATION;
    }
    return switch (value.trim().toUpperCase(Locale.ROOT)) {
      case "MUTATION", "MUTATE", "EDIT", "CHANGE", "CREATE", "UPDATE" ->
          AssistantTurnPlan.Intent.MUTATION;
      case "INFORMATION", "INFO", "EXPLAIN", "EXPLANATION", "ANALYSIS", "ADVICE" ->
          AssistantTurnPlan.Intent.INFORMATION;
      default -> AssistantTurnPlan.Intent.valueOf(value.trim().toUpperCase(Locale.ROOT));
    };
  }

  private AssistantTurnPlan.Kind parseKind(String value) {
    if (value == null || value.isBlank()) {
      return AssistantTurnPlan.Kind.ANSWER;
    }
    return switch (value.trim().toUpperCase(Locale.ROOT)) {
      case "ANSWER", "RESPONSE", "EXPLAIN", "EXPLANATION" -> AssistantTurnPlan.Kind.ANSWER;
      case "CLARIFICATION", "CLARIFY", "QUESTION" -> AssistantTurnPlan.Kind.CLARIFICATION;
      case "PATCH", "MUTATION", "EDIT", "CHANGE" -> AssistantTurnPlan.Kind.PATCH;
      default -> AssistantTurnPlan.Kind.valueOf(value.trim().toUpperCase(Locale.ROOT));
    };
  }

  private AssistantChoice.SelectionMode parseSelectionMode(String value) {
    try {
      return AssistantChoice.SelectionMode.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (RuntimeException ignored) {
      return AssistantChoice.SelectionMode.SINGLE;
    }
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
