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
    return apply(plan, userMessage, false);
  }

  /**
   * Filters clarification questions using explicit backend context signals.
   *
   * @param plan planner output
   * @param userMessage original user request
   * @param sourceContextAvailable whether the backend already has source text for this turn
   * @return gated plan
   */
  public AssistantTurnPlan apply(
      AssistantTurnPlan plan, String userMessage, boolean sourceContextAvailable) {
    if (plan.kind() != AssistantTurnPlan.Kind.CLARIFICATION) {
      return plan;
    }
    List<AssistantChoice> meaningful = new ArrayList<>();
    for (AssistantChoice question : plan.questions()) {
      if (question == null || isDeferrable(question, sourceContextAvailable)) {
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
    return shouldDeferToProposal(plan, userMessage, false);
  }

  /**
   * Returns whether a mutation clarification should be resolved by replanning with defaults instead
   * of interrupting the user.
   */
  public boolean shouldDeferToProposal(
      AssistantTurnPlan plan, String userMessage, boolean sourceContextAvailable) {
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
    return plan.questions().stream()
        .allMatch(question -> isDeferrable(question, sourceContextAvailable));
  }

  /** Returns whether every question in the plan is deferrable. */
  public boolean isDeferrableClarification(AssistantTurnPlan plan, String userMessage) {
    return isDeferrableClarification(plan, userMessage, false);
  }

  /** Returns whether every question in the plan is deferrable. */
  public boolean isDeferrableClarification(
      AssistantTurnPlan plan, String userMessage, boolean sourceContextAvailable) {
    if (plan.kind() != AssistantTurnPlan.Kind.CLARIFICATION || plan.questions().isEmpty()) {
      return false;
    }
    return plan.questions().stream()
        .allMatch(question -> isDeferrable(question, sourceContextAvailable));
  }

  private boolean isDeferrable(AssistantChoice question, boolean sourceContextAvailable) {
    if (question == null) {
      return true;
    }
    String prompt = question.prompt() == null ? "" : question.prompt().toLowerCase(Locale.ROOT);
    if (TRIVIAL_PROMPT.matcher(prompt).find()) {
      return true;
    }
    if (sourceContextAvailable && FILE_CONTENT_PROMPT.matcher(prompt).find()) {
      return true;
    }
    for (AssistantChoice.Option option : question.options()) {
      if (option == null) {
        continue;
      }
      String optionText = (option.label() + " " + option.description()).toLowerCase(Locale.ROOT);
      if (sourceContextAvailable && FILE_CONTENT_PROMPT.matcher(optionText).find()) {
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
