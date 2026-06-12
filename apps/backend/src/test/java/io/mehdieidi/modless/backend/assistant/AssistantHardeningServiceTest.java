package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.mehdieidi.modless.platform.core.PlatformException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AssistantHardeningServiceTest {

    @Test
    void enforcesPerUserRateLimit() {
        AiProperties properties = new AiProperties(true, null, null, null, 0, 0,
                new AiProperties.Hardening(1, Duration.ofMinutes(1), 3,
                        Duration.ofMinutes(1), 1, Duration.ZERO, 12),
                null, null, null, null);
        AssistantHardeningService hardening = new AssistantHardeningService(properties, null);

        hardening.checkRateLimit("user-1");

        assertThrows(PlatformException.class, () -> hardening.checkRateLimit("user-1"));
    }

    @Test
    void retriesProviderCallBeforeSuccess() {
        AiProperties properties = new AiProperties(true, null, null, null, 0, 0,
                new AiProperties.Hardening(30, Duration.ofMinutes(1), 3,
                        Duration.ofMinutes(1), 2, Duration.ZERO, 12),
                null, null, null, null);
        AssistantHardeningService hardening = new AssistantHardeningService(properties, null);
        AtomicInteger attempts = new AtomicInteger();

        String result = hardening.providerCall(AssistantModelRole.RESPONDER, "test", "model",
                () -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("temporary");
                    }
                    return "ok";
                });

        assertEquals("ok", result);
        assertEquals(2, attempts.get());
    }

    @Test
    void opensCircuitAfterFailures() {
        AiProperties properties = new AiProperties(true, null, null, null, 0, 0,
                new AiProperties.Hardening(30, Duration.ofMinutes(1), 1,
                        Duration.ofMinutes(1), 1, Duration.ZERO, 12),
                null, null, null, null);
        AssistantHardeningService hardening = new AssistantHardeningService(properties, null);

        PlatformException providerFailure = assertThrows(PlatformException.class,
                () -> hardening.providerCall(
                AssistantModelRole.RESPONDER, "test", "model",
                () -> {
                    throw new IllegalStateException("down");
                }));
        assertEquals(502, providerFailure.status());

        PlatformException ex = assertThrows(PlatformException.class,
                () -> hardening.providerCall(AssistantModelRole.RESPONDER, "test", "model",
                        () -> "never"));
        assertEquals(503, ex.status());
    }

    @Test
    void returnsProviderRateLimitWithoutRetrying() {
        AiProperties properties = new AiProperties(true, null, null, null, 0, 0,
                new AiProperties.Hardening(30, Duration.ofMinutes(1), 3,
                        Duration.ofMinutes(1), 2, Duration.ZERO, 12),
                null, null, null, null);
        AssistantHardeningService hardening = new AssistantHardeningService(properties, null);
        AtomicInteger attempts = new AtomicInteger();

        PlatformException ex = assertThrows(PlatformException.class,
                () -> hardening.providerCall(AssistantModelRole.RESPONDER, "groq", "model",
                        () -> {
                            attempts.incrementAndGet();
                            throw new IllegalStateException(
                                    "429 - {\"error\":{\"code\":\"rate_limit_exceeded\"}}");
                        }));

        assertEquals(429, ex.status());
        assertEquals("AI provider rate limit reached. Try again shortly.", ex.getMessage());
        assertEquals(1, attempts.get());
    }
}
