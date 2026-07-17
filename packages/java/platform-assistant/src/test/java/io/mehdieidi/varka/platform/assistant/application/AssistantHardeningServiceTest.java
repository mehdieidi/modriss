package io.mehdieidi.varka.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.mehdieidi.varka.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.varka.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.varka.platform.assistant.support.AssistantSettingsFixtures;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AssistantHardeningServiceTest {

  @org.junit.jupiter.api.AfterEach
  @SuppressWarnings("unused") // Invoked by JUnit after each test.
  void clearBudget() {
    ProviderCallBudget.clear();
  }

  @Test
  void enforcesPerUserRateLimit() {
    AssistantSettings properties =
        AssistantSettingsFixtures.withHardening(
            1, Duration.ofMinutes(1), 3, Duration.ofMinutes(1), 1, Duration.ZERO, 12);
    AssistantHardeningService hardening = new AssistantHardeningService(properties, null);

    hardening.checkRateLimit("user-1");

    PlatformException failure =
        assertThrows(PlatformException.class, () -> hardening.checkRateLimit("user-1"));
    assertEquals(429, failure.status());
  }

  @Test
  void retriesProviderCallBeforeSuccess() {
    AssistantSettings properties =
        AssistantSettingsFixtures.withHardening(
            30, Duration.ofMinutes(1), 3, Duration.ofMinutes(1), 2, Duration.ZERO, 12);
    AssistantHardeningService hardening = new AssistantHardeningService(properties, null);
    AtomicInteger attempts = new AtomicInteger();

    ProviderCallBudget.bind(2);
    String result =
        hardening.providerCall(
            AssistantModelRole.RESPONDER,
            "test",
            "model",
            () -> {
              if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("temporary");
              }
              return "ok";
            });

    assertEquals("ok", result);
    assertEquals(2, attempts.get());
    assertEquals(2, ProviderCallBudget.count());
  }

  @Test
  void preservesProviderFailureWhenNoBudgetRemainsForConfiguredRetry() {
    AssistantSettings properties =
        AssistantSettingsFixtures.withHardening(
            30, Duration.ofMinutes(1), 3, Duration.ofMinutes(1), 1, Duration.ZERO, 12);
    AssistantHardeningService hardening = new AssistantHardeningService(properties, null);
    AtomicInteger attempts = new AtomicInteger();

    ProviderCallBudget.bind(1);
    PlatformException failure =
        assertThrows(
            PlatformException.class,
            () ->
                hardening.providerCall(
                    AssistantModelRole.RESPONDER,
                    "test",
                    "model",
                    () -> {
                      attempts.incrementAndGet();
                      throw new IllegalStateException("temporary provider failure");
                    }));

    assertEquals(502, failure.status());
    assertEquals(1, attempts.get());
    assertEquals(1, ProviderCallBudget.count());
  }

  @Test
  void opensCircuitAfterFailures() {
    AssistantSettings properties =
        AssistantSettingsFixtures.withHardening(
            30, Duration.ofMinutes(1), 1, Duration.ofMinutes(1), 1, Duration.ZERO, 12);
    AssistantHardeningService hardening = new AssistantHardeningService(properties, null);

    PlatformException providerFailure =
        assertThrows(
            PlatformException.class,
            () ->
                hardening.providerCall(
                    AssistantModelRole.RESPONDER,
                    "test",
                    "model",
                    () -> {
                      throw new IllegalStateException("down");
                    }));
    assertEquals(502, providerFailure.status());

    PlatformException ex =
        assertThrows(
            PlatformException.class,
            () ->
                hardening.providerCall(
                    AssistantModelRole.RESPONDER, "test", "model", () -> "never"));
    assertEquals(503, ex.status());
  }

  @Test
  void returnsProviderRateLimitWithoutRetrying() {
    AssistantSettings properties =
        AssistantSettingsFixtures.withHardening(
            30, Duration.ofMinutes(1), 3, Duration.ofMinutes(1), 2, Duration.ZERO, 12);
    AssistantHardeningService hardening = new AssistantHardeningService(properties, null);
    AtomicInteger attempts = new AtomicInteger();

    PlatformException ex =
        assertThrows(
            PlatformException.class,
            () ->
                hardening.providerCall(
                    AssistantModelRole.RESPONDER,
                    "openai",
                    "model",
                    () -> {
                      attempts.incrementAndGet();
                      throw new IllegalStateException(
                          "429 - {\"error\":{\"code\":\"rate_limit_exceeded\"}}");
                    }));

    assertEquals(429, ex.status());
    assertEquals("AI provider rate limit reached. Try again shortly.", ex.getMessage());
    assertEquals(1, attempts.get());
  }

  @Test
  void returnsProviderTimeoutWithoutRetrying() {
    AssistantSettings properties =
        AssistantSettingsFixtures.withHardening(
            30, Duration.ofMinutes(1), 3, Duration.ofMinutes(1), 2, Duration.ZERO, 12);
    AssistantHardeningService hardening = new AssistantHardeningService(properties, null);
    AtomicInteger attempts = new AtomicInteger();

    PlatformException ex =
        assertThrows(
            PlatformException.class,
            () ->
                hardening.providerCall(
                    AssistantModelRole.RESPONDER,
                    "openai",
                    "model",
                    () -> {
                      attempts.incrementAndGet();
                      throw new IllegalStateException(new SocketTimeoutException("Read timed out"));
                    }));

    assertEquals(504, ex.status());
    assertEquals(
        "AI provider returned no response within 600 seconds. The provider or model is currently "
            + "too slow; try again shortly.",
        ex.getMessage());
    assertEquals(1, attempts.get());
  }
}
