package io.mehdieidi.modless.backend.assistant;

import java.util.List;

/** One structured decision made by the LLM for an assistant turn. */
public record AssistantTurnPlan(
    Intent intent,
    Kind kind,
    String message,
    List<AssistantChoice> questions,
    SemanticModelPatch patch) {

  /** Normalizes provider output at the trust boundary. */
  public AssistantTurnPlan {
    intent = intent == null ? (kind == Kind.PATCH ? Intent.MUTATION : Intent.INFORMATION) : intent;
    kind = kind == null ? Kind.CLARIFICATION : kind;
    message = message == null ? "" : message.trim();
    questions = questions == null ? List.of() : List.copyOf(questions);
    patch = patch == null ? new SemanticModelPatch(List.of()) : patch;
  }

  /** Compatibility constructor for callers that predate independent intent classification. */
  public AssistantTurnPlan(
      Kind kind, String message, List<AssistantChoice> questions, SemanticModelPatch patch) {
    this(
        kind == Kind.PATCH ? Intent.MUTATION : Intent.INFORMATION, kind, message, questions, patch);
  }

  /** The user's requested outcome, classified independently from the response shape. */
  public enum Intent {
    INFORMATION,
    MUTATION
  }

  /** Supported LLM decisions. */
  public enum Kind {
    /** Answer without changing the model. */
    ANSWER,
    /** Ask the user for consequential missing information. */
    CLARIFICATION,
    /** Propose semantic model operations. */
    PATCH
  }
}
