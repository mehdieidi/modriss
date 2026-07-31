package io.mehdieidi.varka.platform.assistant.spi;

/** Observability hooks for assistant operations. */
public interface AssistantMetrics {

  /** Records an assistant request accepted by the API layer. */
  default void recordAssistantRequest() {}

  /**
   * Records one assistant turn outcome.
   *
   * @param category eval or routing category
   * @param outcome result such as APPLIED, EXPLAINED, or FAILED
   */
  default void recordAssistantTurnOutcome(String category, String outcome) {}

  /**
   * Records validator-guided repair attempts for one turn.
   *
   * @param attempts number of repair passes executed
   */
  default void recordAssistantRepairAttempts(int attempts) {}

  /**
   * Records tool invocations executed during an agent loop.
   *
   * @param toolCalls number of tool calls
   */
  default void recordAssistantToolCalls(int toolCalls) {}

  /**
   * Records a provider call rejected because the circuit is open.
   *
   * @param provider provider key
   */
  default void recordAssistantCircuitRejected(String provider) {}

  /**
   * Records a successful provider call.
   *
   * @param provider provider key
   * @param model model name
   */
  default void recordAssistantProviderSuccess(String provider, String model) {}

  /**
   * Records a failed provider call.
   *
   * @param provider provider key
   */
  default void recordAssistantProviderFailure(String provider) {}

  /**
   * Records wall-clock duration for one assistant turn phase.
   *
   * @param phase canonical phase name
   * @param millis elapsed milliseconds
   */
  default void recordAssistantPhaseDuration(String phase, long millis) {}

  /** Records estimated prompt and completion tokens where a provider does not return usage. */
  default void recordAssistantTokenEstimate(String direction, String provider, long tokens) {}

  /** Records provider-reported (rather than estimated) token usage. */
  default void recordAssistantTokenUsage(String direction, String provider, long tokens) {}

  /** Records the amount of deterministic/retrieved context attached to a provider request. */
  default void recordAssistantRetrievalChars(int chars) {}

  /** Records malformed or backend-rejected model action envelopes. */
  default void recordAssistantMalformedAction(String reason) {}

  /** Records a validator/tool repair reason without retaining user or source content. */
  default void recordAssistantRepairReason(String reason) {}

  /** Records the ordered action selected by the provider without retaining prompt content. */
  default void recordAssistantAction(String action, int step) {}

  /** Records a structural validation outcome. */
  default void recordAssistantStructuralValidation(boolean valid) {}

  /** Records a durable checkpoint operation, including safe undo. */
  default void recordAssistantCheckpoint(String operation) {}

  /**
   * Records a rate-limited assistant request.
   *
   * @param userId user ID
   */
  default void recordAssistantRateLimited(String userId) {}
}
