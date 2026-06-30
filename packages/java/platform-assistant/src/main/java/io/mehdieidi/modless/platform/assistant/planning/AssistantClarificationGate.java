package io.mehdieidi.modless.platform.assistant.planning;

import io.mehdieidi.modless.platform.assistant.domain.AssistantChoice;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Blocks clarifications about formal details the backend can derive safely. */
public class AssistantClarificationGate {

  private static final Pattern TRIVIAL_PROMPT =
      Pattern.compile(
          "(?i)\\b(id|uuid|ulid|identifier|stable[ -]?id|element[ -]?id|name|naming|"
              + "layout|ordering|order|sequence|position|format)\\b");

  private static final Pattern DEFERRABLE_PROMPT =
      Pattern.compile(
          "(?i)\\b(architecture|interaction[ -]?style|serverless|api[ -]?first|event[ -]?driven|"
              + "runtime|language|package[ -]?manager|pip|poetry|npm|maven|go[ -]?mod|gradle|"
              + "implementation[ -]?profile|persistence|data[ -]?store|object[ -]?store|"
              + "relational|correlation|framework|handler|deployment|environment|schema|"
              + "primary[ -]?language|domain[ -]?name)\\b");

  private static final Pattern EXPLICIT_USER_FORK =
      Pattern.compile(
          "(?i)\\b(which|should i|do you prefer|choose between|either .+ or .+)\\b.*\\?",
          Pattern.DOTALL);

  private static final Pattern FILE_CONTENT_PROMPT =
      Pattern.compile(
          "(?i)\\b(file|attachment|document|\\.md|\\.txt|\\.json)\\b.*\\b(content|contents|"
              + "upload|reattach|attach|paste|provide|read)\\b|\\b(reattach|upload|attach|"
              + "paste|provide)\\b.*\\b(file|attachment|document|contents?)\\b",
          Pattern.DOTALL);

  private static final Pattern DOCUMENT_BACKED_REQUEST =
      Pattern.compile(
          "(?i)\\b(attached|attachment|file|document|source|requirements?|user stor|event"
              + " storm|\\.md|\\.txt|\\.json)\\b",
          Pattern.DOTALL);

  /**
   * Filters trivial clarification questions and coerces mutation clarifications when possible.
   *
   * @param plan planner output
   * @return gated plan, or {@link AssistantTurnPlan.Kind#PATCH} when only deferrable questions
   *     remain for a mutation request
   */
  public AssistantTurnPlan apply(AssistantTurnPlan plan) {
    return apply(plan, "");
  }

  /**
   * Filters clarification questions using the originating user message for fork detection.
   *
   * @param plan planner output
   * @param userMessage original user request
   * @return gated plan
   */
  public AssistantTurnPlan apply(AssistantTurnPlan plan, String userMessage) {
    if (plan.kind() != AssistantTurnPlan.Kind.CLARIFICATION) {
      return plan;
    }
    List<AssistantChoice> meaningful = new ArrayList<>();
    for (AssistantChoice question : plan.questions()) {
      if (question == null || isDeferrable(question, userMessage)) {
        continue;
      }
      meaningful.add(question);
    }
    if (!meaningful.isEmpty()) {
      return new AssistantTurnPlan(
          plan.intent(), plan.kind(), plan.message(), meaningful, plan.patch());
    }
    if (plan.intent() == AssistantTurnPlan.Intent.MUTATION) {
      return new AssistantTurnPlan(
          plan.intent(),
          AssistantTurnPlan.Kind.PATCH,
          nonBlank(
              plan.message(),
              "Proceeding with safe metamodel defaults. Review the proposal before applying."),
          List.of(),
          plan.patch());
    }
    return new AssistantTurnPlan(
        plan.intent(),
        AssistantTurnPlan.Kind.ANSWER,
        nonBlank(plan.message(), "I can continue without those formal details."),
        List.of(),
        plan.patch());
  }

  /**
   * Returns whether a mutation clarification should be resolved by replanning with defaults instead
   * of interrupting the user.
   */
  public boolean shouldDeferToProposal(AssistantTurnPlan plan, String userMessage) {
    if (plan.intent() != AssistantTurnPlan.Intent.MUTATION
        || plan.kind() != AssistantTurnPlan.Kind.CLARIFICATION) {
      return false;
    }
    if (explicitUserForkInMessage(userMessage)) {
      return false;
    }
    if (plan.questions().isEmpty()) {
      return false;
    }
    return plan.questions().stream().allMatch(question -> isDeferrable(question, userMessage));
  }

  /** Returns whether every question in the plan is deferrable. */
  public boolean isDeferrableClarification(AssistantTurnPlan plan, String userMessage) {
    if (plan.kind() != AssistantTurnPlan.Kind.CLARIFICATION || plan.questions().isEmpty()) {
      return false;
    }
    return plan.questions().stream().allMatch(question -> isDeferrable(question, userMessage));
  }

  private boolean isDeferrable(AssistantChoice question, String userMessage) {
    if (question == null) {
      return true;
    }
    String prompt = question.prompt() == null ? "" : question.prompt().toLowerCase(Locale.ROOT);
    if (TRIVIAL_PROMPT.matcher(prompt).find() || DEFERRABLE_PROMPT.matcher(prompt).find()) {
      return true;
    }
    if (DOCUMENT_BACKED_REQUEST.matcher(userMessage == null ? "" : userMessage).find()
        && FILE_CONTENT_PROMPT.matcher(prompt).find()) {
      return true;
    }
    for (AssistantChoice.Option option : question.options()) {
      if (option == null) {
        continue;
      }
      String optionText = (option.label() + " " + option.description()).toLowerCase(Locale.ROOT);
      if (DEFERRABLE_PROMPT.matcher(optionText).find()
          || (DOCUMENT_BACKED_REQUEST.matcher(userMessage == null ? "" : userMessage).find()
              && FILE_CONTENT_PROMPT.matcher(optionText).find())) {
        return true;
      }
    }
    return false;
  }

  private boolean explicitUserForkInMessage(String userMessage) {
    if (userMessage == null || userMessage.isBlank()) {
      return false;
    }
    return EXPLICIT_USER_FORK.matcher(userMessage.trim()).find();
  }

  private String nonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
