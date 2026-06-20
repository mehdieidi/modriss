package io.mehdieidi.modless.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Parses the provider's structured turn decision without trusting surrounding prose. */
@Component
public class AssistantTurnPlanParser {

  private final ObjectMapper mapper;
  private final SemanticModelPatchParser patchParser;

  public AssistantTurnPlanParser(ObjectMapper mapper) {
    this.mapper = mapper;
    this.patchParser = new SemanticModelPatchParser(mapper);
  }

  /** Parses one JSON turn plan. */
  public AssistantTurnPlan parse(String content) {
    if (content == null || content.isBlank()) {
      throw new PlatformException(502, "AI assistant returned an empty turn plan.");
    }
    for (String candidate : candidates(content)) {
      try {
        JsonNode root = mapper.readTree(candidate);
        AssistantTurnPlan.Intent intent =
            AssistantTurnPlan.Intent.valueOf(root.path("intent").asText("").toUpperCase());
        AssistantTurnPlan.Kind kind =
            AssistantTurnPlan.Kind.valueOf(root.path("kind").asText("").toUpperCase());
        List<AssistantChoice> questions = new ArrayList<>();
        for (JsonNode question : root.path("questions")) {
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
        SemanticModelPatch patch =
            patchParser.parse(
                mapper.writeValueAsString(
                    mapper.createObjectNode().set("operations", root.path("operations"))));
        if (!patch.operations().isEmpty()) {
          intent = AssistantTurnPlan.Intent.MUTATION;
          kind = AssistantTurnPlan.Kind.PATCH;
        }
        return new AssistantTurnPlan(intent, kind, root.path("message").asText(), questions, patch);
      } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ignored) {
        // Try the next bounded JSON candidate.
      }
    }
    throw new PlatformException(502, "AI assistant returned an invalid structured turn plan.");
  }

  private AssistantChoice.SelectionMode parseSelectionMode(String value) {
    try {
      return AssistantChoice.SelectionMode.valueOf(value.toUpperCase());
    } catch (RuntimeException ignored) {
      return AssistantChoice.SelectionMode.SINGLE;
    }
  }

  private List<String> candidates(String content) {
    List<String> result = new ArrayList<>();
    result.add(content.trim());

    for (int start = 0; start < content.length(); start++) {
      if (content.charAt(start) != '{') {
        continue;
      }
      int end = objectEnd(content, start);
      if (end >= 0) {
        result.add(content.substring(start, end + 1));
      }
    }
    return result.stream().distinct().toList();
  }

  private int objectEnd(String content, int start) {
    boolean inString = false;
    boolean escaped = false;
    int depth = 0;
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
      } else if (current == '"') {
        inString = true;
      } else if (current == '{') {
        depth++;
      } else if (current == '}' && --depth == 0) {
        return index;
      }
    }
    return -1;
  }
}
