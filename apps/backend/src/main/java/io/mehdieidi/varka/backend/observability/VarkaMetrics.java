package io.mehdieidi.varka.backend.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** Application-specific Prometheus metrics for MDE and assistant operations. */
@Component
public class VarkaMetrics {

  private final Counter mdeJobsSubmitted;
  private final Counter mdeJobsFailed;
  private final Counter assistantRequests;
  private final Counter assistantCircuitOpen;
  private final MeterRegistry registry;
  private final Counter assistantRepairAttempts;
  private final Counter assistantToolCalls;

  /**
   * Registers Varka domain counters on the shared Micrometer registry.
   *
   * @param registry Micrometer meter registry
   */
  public VarkaMetrics(MeterRegistry registry) {
    this.registry = registry;
    mdeJobsSubmitted =
        Counter.builder("varka.mde.jobs.submitted")
            .description("MDE jobs accepted by the platform")
            .register(registry);
    mdeJobsFailed =
        Counter.builder("varka.mde.jobs.failed")
            .description("MDE jobs that ended in a failed state")
            .register(registry);
    assistantRequests =
        Counter.builder("varka.assistant.requests")
            .description("Assistant messages accepted by the API")
            .register(registry);
    assistantCircuitOpen =
        Counter.builder("varka.assistant.circuit.open")
            .description("Assistant provider calls rejected while the circuit is open")
            .register(registry);
    assistantRepairAttempts =
        Counter.builder("varka.assistant.repair.attempts")
            .description("Validator-guided repair passes executed per turn")
            .register(registry);
    assistantToolCalls =
        Counter.builder("varka.assistant.tool.calls")
            .description("Whitelisted assistant tool invocations during agent loops")
            .register(registry);
  }

  /** Records a newly submitted MDE job. */
  public void recordMdeJobSubmitted() {
    mdeJobsSubmitted.increment();
  }

  /** Records an MDE job that failed during execution. */
  public void recordMdeJobFailed() {
    mdeJobsFailed.increment();
  }

  /** Records an assistant request accepted by the API layer. */
  public void recordAssistantRequest() {
    assistantRequests.increment();
  }

  /** Records a provider call rejected because the assistant circuit breaker is open. */
  public void recordAssistantCircuitOpen() {
    assistantCircuitOpen.increment();
  }

  /** Records a provider call rejected because the assistant circuit breaker is open. */
  public void recordAssistantCircuitRejected(String provider) {
    assistantCircuitOpen.increment();
    registry
        .counter("varka.assistant.provider.circuit.rejected", "provider", safeTag(provider))
        .increment();
  }

  /** Records a successful assistant provider call. */
  public void recordAssistantProviderSuccess(String provider, String model) {
    registry
        .counter(
            "varka.assistant.provider.calls",
            "provider",
            safeTag(provider),
            "model",
            safeTag(model),
            "result",
            "success")
        .increment();
  }

  /** Records a failed assistant provider call. */
  public void recordAssistantProviderFailure(String provider) {
    registry
        .counter(
            "varka.assistant.provider.calls",
            "provider",
            safeTag(provider),
            "model",
            "unknown",
            "result",
            "failure")
        .increment();
  }

  /** Records an assistant request rejected by the user-facing rate limit. */
  public void recordAssistantRateLimited(String userId) {
    registry.counter("varka.assistant.rate.limited", "user", safeTag(userId)).increment();
  }

  /**
   * Records one assistant turn outcome.
   *
   * @param category eval or routing category
   * @param outcome result such as APPLIED, EXPLAINED, or FAILED
   */
  public void recordAssistantTurnOutcome(String category, String outcome) {
    registry
        .counter(
            "varka.assistant.turn.outcome",
            "category",
            safeTag(category),
            "outcome",
            safeTag(outcome))
        .increment();
  }

  /**
   * Records validator-guided repair attempts for one turn.
   *
   * @param attempts number of repair passes executed
   */
  public void recordAssistantRepairAttempts(int attempts) {
    if (attempts > 0) {
      assistantRepairAttempts.increment(attempts);
    }
  }

  /**
   * Records tool invocations executed during an agent loop.
   *
   * @param toolCalls number of tool calls
   */
  public void recordAssistantToolCalls(int toolCalls) {
    if (toolCalls > 0) {
      assistantToolCalls.increment(toolCalls);
    }
  }

  /**
   * Records wall-clock duration for one assistant turn phase.
   *
   * @param phase canonical phase name
   * @param millis elapsed milliseconds
   */
  public void recordAssistantPhaseDuration(String phase, long millis) {
    if (millis <= 0L) {
      return;
    }
    Timer.builder("varka.assistant.phase")
        .description("Assistant turn phase latency")
        .tag("phase", safeTag(phase))
        .register(registry)
        .record(millis, TimeUnit.MILLISECONDS);
  }

  public void recordAssistantTokenEstimate(String direction, String provider, long tokens) {
    if (tokens > 0) {
      registry
          .counter(
              "varka.assistant.tokens.estimated",
              "direction",
              safeTag(direction),
              "provider",
              safeTag(provider))
          .increment(tokens);
    }
  }

  public void recordAssistantTokenUsage(String direction, String provider, long tokens) {
    if (tokens > 0) {
      registry
          .counter(
              "varka.assistant.tokens.reported",
              "direction",
              safeTag(direction),
              "provider",
              safeTag(provider))
          .increment(tokens);
    }
  }

  public void recordAssistantRetrievalChars(int chars) {
    if (chars > 0) registry.summary("varka.assistant.retrieval.chars").record(chars);
  }

  public void recordAssistantMalformedAction(String reason) {
    registry.counter("varka.assistant.action.malformed", "reason", safeTag(reason)).increment();
  }

  public void recordAssistantRepairReason(String reason) {
    registry.counter("varka.assistant.repair.reason", "reason", safeTag(reason)).increment();
  }

  public void recordAssistantUserSignal(String signal) {
    registry.counter("varka.assistant.user.signal", "signal", safeTag(signal)).increment();
  }

  public void recordAssistantAction(String action, int step) {
    registry
        .counter(
            "varka.assistant.action.sequence",
            "action",
            safeTag(action),
            "step",
            String.valueOf(Math.max(0, step)))
        .increment();
  }

  public void recordAssistantStructuralValidation(boolean valid) {
    registry
        .counter("varka.assistant.structural.validation", "result", valid ? "valid" : "invalid")
        .increment();
  }

  public void recordAssistantCheckpoint(String operation) {
    registry.counter("varka.assistant.checkpoint", "operation", safeTag(operation)).increment();
  }

  private static String safeTag(String value) {
    return value == null || value.isBlank() ? "unknown" : value.trim();
  }
}
