package io.mehdieidi.modless.platform.assistant.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.List;

/** LLM-structured turn classifier; no keyword routing decides modeling intent. */
public class IntentPlanner {

  private final AssistantModelProvider provider;
  private final ObjectMapper mapper;
  private final PromptContextBuilder prompts;

  public IntentPlanner(
      AssistantModelProvider provider, ObjectMapper mapper, PromptContextBuilder prompts) {
    this.provider = provider;
    this.mapper = mapper;
    this.prompts = prompts;
  }

  /** Classifies one turn before choosing read-only or mutation-capable agent execution. */
  public IntentDecision classify(
      ModelLevel level,
      AssistantModelProvider.AssistantPrompt prompt,
      List<AssistantModelProvider.ContextSnippet> context) {
    String parentSystem = prompt == null || prompt.system() == null ? "" : prompt.system().trim();
    String classifyInstructions =
        """
Classify the user's turn using structured output. Decide intent semantically from
the full request, conversation memory, and context — not from keyword matching.
Return only one JSON object:
{"intent":"INFORMATION|MUTATION|CLARIFICATION","taskKind":"CREATE_MODEL|EXTEND_MODEL|EDIT_MODEL|DELETE_MODEL|EXPLAIN_MODEL|TRANSFORM_SOURCE_TO_MODEL|REPAIR_STRUCTURE","sourceUse":true|false,"concepts":["..."],"candidateTypes":["ExactEClass"]}
Do not produce ModelDelta, patch operations, prose, Markdown, or chain-of-thought.
Short confirmations such as "ok", "do it", "yes", or "go ahead" that continue an earlier
modeling request must be intent=MUTATION with the same modeling taskKind as the prior turn.
candidateTypes must list only exact creatable EClass names from the metamodel; omit invalid
guesses and domain service labels that are not formal EClasses.
Level:\
"""
            + level.apiName();
    String system =
        parentSystem.isBlank()
            ? classifyInstructions
            : parentSystem + "\n\n" + classifyInstructions;
    AssistantModelProvider.AssistantPrompt request =
        prompts.build(
            new AssistantModelProvider.AssistantPrompt(
                AssistantModelRole.PLANNER, system, prompt == null ? "" : prompt.user(), context),
            List.of(),
            List.of(),
            context);
    AssistantModelProvider.AssistantReply reply = provider.completeStructured(request);
    return parse(reply.content());
  }

  /** Returns whether the structured turn decision requires the mutation-capable agent path. */
  public static boolean requiresMutation(IntentDecision intent) {
    if (intent == null) {
      return false;
    }
    if (intent.intent() == Intent.MUTATION) {
      return true;
    }
    return switch (intent.taskKind().toUpperCase(java.util.Locale.ROOT)) {
      case "CREATE_MODEL",
          "EXTEND_MODEL",
          "EDIT_MODEL",
          "DELETE_MODEL",
          "TRANSFORM_SOURCE_TO_MODEL",
          "REPAIR_STRUCTURE" ->
          true;
      default -> false;
    };
  }

  private IntentDecision parse(String content) {
    try {
      JsonNode root = mapper.readTree(content == null ? "" : content.trim());
      String intent = root.path("intent").asText("INFORMATION").toUpperCase(java.util.Locale.ROOT);
      String taskKind = root.path("taskKind").asText("EXPLAIN_MODEL");
      boolean sourceUse = root.path("sourceUse").asBoolean(false);
      return new IntentDecision(
          switch (intent) {
            case "MUTATION" -> Intent.MUTATION;
            case "CLARIFICATION" -> Intent.CLARIFICATION;
            default -> Intent.INFORMATION;
          },
          taskKind,
          sourceUse,
          strings(root.path("concepts")),
          strings(root.path("candidateTypes")));
    } catch (Exception ex) {
      throw new PlatformException(502, "AI assistant returned an invalid intent plan.");
    }
  }

  private List<String> strings(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    java.util.ArrayList<String> result = new java.util.ArrayList<>();
    node.forEach(
        value -> {
          if (value != null && value.isTextual() && !value.asText().isBlank()) {
            result.add(value.asText());
          }
        });
    return List.copyOf(result);
  }

  public enum Intent {
    INFORMATION,
    MUTATION,
    CLARIFICATION
  }

  public record IntentDecision(
      Intent intent,
      String taskKind,
      boolean sourceUse,
      List<String> concepts,
      List<String> candidateTypes) {
    public IntentDecision {
      taskKind = taskKind == null || taskKind.isBlank() ? "EXPLAIN_MODEL" : taskKind.trim();
      concepts = concepts == null ? List.of() : List.copyOf(concepts);
      candidateTypes = candidateTypes == null ? List.of() : List.copyOf(candidateTypes);
    }
  }
}
