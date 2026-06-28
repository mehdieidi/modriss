package io.mehdieidi.modless.platform.assistant.spi;

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
   * @param role model role
   * @param model model name
   */
  default void recordAssistantProviderSuccess(String provider, String role, String model) {}

  /**
   * Records a failed provider call.
   *
   * @param provider provider key
   */
  default void recordAssistantProviderFailure(String provider) {}

  /**
   * Records a rate-limited assistant request.
   *
   * @param userId user ID
   */
  default void recordAssistantRateLimited(String userId) {}
}
