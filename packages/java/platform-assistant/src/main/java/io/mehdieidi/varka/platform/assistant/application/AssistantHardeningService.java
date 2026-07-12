package io.mehdieidi.varka.platform.assistant.application;

import io.mehdieidi.varka.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.varka.platform.assistant.spi.AssistantMetrics;
import io.mehdieidi.varka.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Enforces assistant rate limits, circuit breaking, and metrics. */
public class AssistantHardeningService {

  private static final Logger log = LoggerFactory.getLogger(AssistantHardeningService.class);

  private final AssistantSettings properties;
  private final AssistantMetrics metrics;
  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  private final Map<String, Circuit> circuits = new ConcurrentHashMap<>();

  /** Creates the hardening service. */
  public AssistantHardeningService(AssistantSettings properties, AssistantMetrics metrics) {
    this(properties, metrics, Clock.systemUTC());
  }

  AssistantHardeningService(AssistantSettings properties, AssistantMetrics metrics, Clock clock) {
    this.properties = properties;
    this.metrics = metrics == null ? new NoOpAssistantMetrics() : metrics;
    this.clock = clock;
  }

  /**
   * Enforces the configured per-user request limit.
   *
   * @param userId user ID
   */
  public void checkRateLimit(String userId) {
    AssistantSettings.Hardening hardening = properties.hardening();
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
      metrics.recordAssistantRateLimited(userId);
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
    ProviderCallBudget.consume(role);
    Instant now = clock.instant();
    Circuit snapshot = circuits.getOrDefault(provider, new Circuit(0, Instant.EPOCH));
    if (now.isBefore(snapshot.openUntil())) {
      metrics.recordAssistantCircuitRejected(provider);
      log.warn(
          "AI provider circuit open provider={} role={} model={} assistantTurnId={} sessionId={} "
              + "requestId={} openUntil={}",
          provider,
          role,
          model,
          mdc("assistantTurnId"),
          mdc("assistantSessionId"),
          mdc("requestId"),
          snapshot.openUntil());
      throw new PlatformException(503, "AI provider circuit is open. Try again shortly.");
    }
    try {
      RuntimeException last = null;
      for (int attempt = 1; attempt <= properties.hardening().providerRetryAttempts(); attempt++) {
        long attemptStarted = System.nanoTime();
        try {
          T result = call.get();
          log.info(
              "AI provider call succeeded provider={} role={} model={} assistantTurnId={} "
                  + "sessionId={} requestId={} attempt={} elapsedMs={}",
              provider,
              role,
              model,
              mdc("assistantTurnId"),
              mdc("assistantSessionId"),
              mdc("requestId"),
              attempt,
              elapsedMillis(attemptStarted));
          circuits.remove(provider);
          metrics.recordAssistantProviderSuccess(provider, role.name(), model);
          return result;
        } catch (RuntimeException ex) {
          PlatformException providerFailure = classifyProviderFailure(ex);
          if (providerFailure != null) {
            log.warn(
                "AI provider call failed provider={} role={} model={} attempt={} elapsedMs={} "
                    + "configuredTimeoutMs={} assistantTurnId={} sessionId={} requestId={} "
                    + "status={} rootCause={}: {}",
                provider,
                role,
                model,
                attempt,
                elapsedMillis(attemptStarted),
                properties.requestTimeout().toMillis(),
                mdc("assistantTurnId"),
                mdc("assistantSessionId"),
                mdc("requestId"),
                providerFailure.status(),
                rootCause(ex).getClass().getSimpleName(),
                rootCause(ex).getMessage());
            recordFailure(provider);
            throw providerFailure;
          }
          last = ex;
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
          "AI provider call failed after retries provider={} role={} model={} assistantTurnId={} "
              + "sessionId={} requestId={}: {}",
          provider,
          role,
          model,
          mdc("assistantTurnId"),
          mdc("assistantSessionId"),
          mdc("requestId"),
          last == null ? "unknown" : last.toString());
      String diagnostic = providerFailureDiagnostic(last);
      throw new PlatformException(
          502,
          "AI provider request failed. Check provider base URL, model, API key, and proxy"
              + " settings."
              + (diagnostic.isBlank() ? "" : " Provider error: " + diagnostic));
    } finally {
      // Provider duration metrics are recorded by the backend metrics adapter.
    }
  }

  private void recordFailure(String provider) {
    AssistantSettings.Hardening hardening = properties.hardening();
    Circuit previous = circuits.getOrDefault(provider, new Circuit(0, Instant.EPOCH));
    int failures = previous.failures() + 1;
    Instant openUntil =
        failures >= hardening.circuitFailureThreshold()
            ? clock.instant().plus(hardening.circuitOpenDuration())
            : Instant.EPOCH;
    circuits.put(provider, new Circuit(failures, openUntil));
    log.warn(
        "AI provider failure recorded provider={} failures={} circuitOpenUntil={}"
            + " assistantTurnId={} sessionId={} requestId={}",
        provider,
        failures,
        openUntil,
        mdc("assistantTurnId"),
        mdc("assistantSessionId"),
        mdc("requestId"));
    metrics.recordAssistantProviderFailure(provider);
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
      if (message != null
          && (message.startsWith("402 ")
              || message.startsWith("402 -")
              || message.toLowerCase(java.util.Locale.ROOT).contains("usage limit reached"))) {
        return new PlatformException(
            429,
            "AI provider usage limit has been reached. Wait for the provider quota to reset or "
                + "configure a fallback provider.");
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

  private String providerFailureDiagnostic(Throwable failure) {
    if (failure == null) {
      return "";
    }
    Throwable root = rootCause(failure);
    String message = root.getMessage();
    if (message == null || message.isBlank()) {
      message = failure.getMessage();
    }
    if (message == null || message.isBlank()) {
      return root.getClass().getSimpleName();
    }
    String compact = message.replaceAll("\\s+", " ").trim();
    return compact.length() <= 240 ? compact : compact.substring(0, 237) + "...";
  }

  private String mdc(String key) {
    String value = MDC.get(key);
    return value == null ? "" : value;
  }

  private record NoOpAssistantMetrics() implements AssistantMetrics {}

  private record Window(Instant startedAt, int count) {}

  private record Circuit(int failures, Instant openUntil) {}
}
