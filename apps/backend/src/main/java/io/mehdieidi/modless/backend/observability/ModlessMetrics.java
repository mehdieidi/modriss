package io.mehdieidi.modless.backend.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Application-specific Prometheus metrics for MDE and assistant operations. */
@Component
public class ModlessMetrics {

  private final Counter mdeJobsSubmitted;
  private final Counter mdeJobsFailed;
  private final Counter assistantRequests;
  private final Counter assistantCircuitOpen;

  /**
   * Registers Modless domain counters on the shared Micrometer registry.
   *
   * @param registry Micrometer meter registry
   */
  public ModlessMetrics(MeterRegistry registry) {
    mdeJobsSubmitted =
        Counter.builder("modless.mde.jobs.submitted")
            .description("MDE jobs accepted by the platform")
            .register(registry);
    mdeJobsFailed =
        Counter.builder("modless.mde.jobs.failed")
            .description("MDE jobs that ended in a failed state")
            .register(registry);
    assistantRequests =
        Counter.builder("modless.assistant.requests")
            .description("Assistant messages accepted by the API")
            .register(registry);
    assistantCircuitOpen =
        Counter.builder("modless.assistant.circuit.open")
            .description("Assistant provider calls rejected while the circuit is open")
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
}
