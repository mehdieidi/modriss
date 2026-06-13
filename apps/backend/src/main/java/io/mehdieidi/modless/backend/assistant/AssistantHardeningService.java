package io.mehdieidi.modless.backend.assistant;

import io.mehdieidi.modless.platform.core.PlatformException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/** Enforces assistant rate limits, circuit breaking, and metrics. */
@Service
public class AssistantHardeningService {

  private static final Logger log = LoggerFactory.getLogger(AssistantHardeningService.class);

  private final AiProperties properties;
  private final MeterRegistry meterRegistry;
  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  private volatile Circuit circuit = new Circuit(0, Instant.EPOCH);

  /** Creates the hardening service. */
  @Autowired
  public AssistantHardeningService(AiProperties properties, @Nullable MeterRegistry meterRegistry) {
    this(properties, meterRegistry, Clock.systemUTC());
  }

  AssistantHardeningService(
      AiProperties properties, @Nullable MeterRegistry meterRegistry, Clock clock) {
    this.properties = properties;
    this.meterRegistry = meterRegistry;
    this.clock = clock;
  }

  /**
   * Enforces the configured per-user request limit.
   *
   * @param userId user ID
   */
  public void checkRateLimit(String userId) {
    AiProperties.Hardening hardening = properties.hardening();
    Instant now = clock.instant();
    Window window =
        windows.compute(
            userId,
            (key, current) -> {
              if (current == null
                  || now.isAfter(current.startedAt().plus(hardening.rateLimitWindow()))) {
                return new Window(now, 1);
              }
              return new Window(current.startedAt(), current.count() + 1);
            });
    if (window.count() > hardening.perUserRequestsPerWindow()) {
      counter("assistant.rate_limited", "user", userId);
      throw new PlatformException(429, "Assistant rate limit exceeded. Try again shortly.");
    }
  }

  /**
   * Executes one provider call behind the circuit breaker and metrics.
   *
   * @param role model role
   * @param provider provider key
   * @param model model name
   * @param call provider call
   * @param <T> return type
   * @return provider result
   */
  public <T> T providerCall(
      AssistantModelRole role, String provider, String model, Supplier<T> call) {
    Instant now = clock.instant();
    Circuit snapshot = circuit;
    if (now.isBefore(snapshot.openUntil())) {
      counter("assistant.circuit.rejected", "provider", provider);
      throw new PlatformException(503, "AI provider circuit is open. Try again shortly.");
    }
    Timer.Sample sample = meterRegistry == null ? null : Timer.start(meterRegistry);
    try {
      RuntimeException last = null;
      for (int attempt = 1; attempt <= properties.hardening().providerRetryAttempts(); attempt++) {
        long attemptStarted = System.nanoTime();
        try {
          T result = call.get();
          circuit = new Circuit(0, Instant.EPOCH);
          counter(
              "assistant.provider.success",
              "provider",
              provider,
              "role",
              role.name(),
              "model",
              model);
          return result;
        } catch (RuntimeException ex) {
          PlatformException providerFailure = classifyProviderFailure(ex);
          if (providerFailure != null) {
            log.warn(
                "AI provider call failed provider={} role={} model={} attempt={} elapsedMs={} "
                    + "configuredTimeoutMs={} status={} rootCause={}: {}",
                provider,
                role,
                model,
                attempt,
                elapsedMillis(attemptStarted),
                properties.requestTimeout().toMillis(),
                providerFailure.status(),
                rootCause(ex).getClass().getSimpleName(),
                rootCause(ex).getMessage());
            recordFailure(provider);
            throw providerFailure;
          }
          last = ex;
          counter(
              "assistant.provider.retry", "provider", provider, "attempt", String.valueOf(attempt));
          if (attempt < properties.hardening().providerRetryAttempts()) {
            sleep();
          }
        }
      }
      recordFailure(provider);
      if (last instanceof PlatformException platformException) {
        throw platformException;
      }
      log.warn(
          "AI provider call failed after retries for provider={} role={} model={}: {}",
          provider,
          role,
          model,
          last == null ? "unknown" : last.toString());
      throw new PlatformException(
          502,
          "AI provider request failed. Check provider base URL, model, API key, and proxy"
              + " settings.");
    } finally {
      if (sample != null) {
        sample.stop(
            Timer.builder("assistant.provider.duration")
                .tag("provider", provider)
                .tag("role", role.name())
                .tag("model", model)
                .register(meterRegistry));
      }
    }
  }

  private void recordFailure(String provider) {
    AiProperties.Hardening hardening = properties.hardening();
    Circuit previous = circuit;
    int failures = previous.failures() + 1;
    Instant openUntil =
        failures >= hardening.circuitFailureThreshold()
            ? clock.instant().plus(hardening.circuitOpenDuration())
            : Instant.EPOCH;
    circuit = new Circuit(failures, openUntil);
    counter("assistant.provider.failure", "provider", provider);
  }

  private PlatformException classifyProviderFailure(RuntimeException failure) {
    Throwable current = failure;
    while (current != null) {
      String message = current.getMessage();
      if (message != null
          && (message.startsWith("429 ")
              || message.startsWith("429 -")
              || message.contains("\"code\":\"rate_limit_exceeded\""))) {
        return new PlatformException(429, "AI provider rate limit reached. Try again shortly.");
      }
      if (current instanceof SocketTimeoutException) {
        return new PlatformException(
            504,
            "AI provider returned no response within "
                + properties.requestTimeout().toSeconds()
                + " seconds. The provider or model is currently too slow; try again shortly.");
      }
      current = current.getCause();
    }
    return null;
  }

  private void sleep() {
    try {
      Thread.sleep(properties.hardening().retryBackoff().toMillis());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new PlatformException(503, "AI provider retry was interrupted.");
    }
  }

  private long elapsedMillis(long started) {
    return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
  }

  private Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current;
  }

  private void counter(String name, String... tags) {
    if (meterRegistry != null) {
      meterRegistry.counter(name, tags).increment();
    }
  }

  private record Window(Instant startedAt, int count) {}

  private record Circuit(int failures, Instant openUntil) {}
}
