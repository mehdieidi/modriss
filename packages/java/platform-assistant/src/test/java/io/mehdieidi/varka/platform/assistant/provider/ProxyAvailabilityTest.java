package io.mehdieidi.varka.platform.assistant.provider;

import static org.junit.jupiter.api.Assertions.assertSame;

import io.mehdieidi.varka.platform.assistant.config.AiProperties;
import org.junit.jupiter.api.Test;

class ProxyAvailabilityTest {

  @Test
  void cachesDirectProxyChecksWithinTtl() {
    AiProperties properties =
        new AiProperties(
            true,
            null,
            null,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            null,
            null,
            new AiProperties.Proxy(false, AiProperties.ProxyType.DIRECT, null, null, null),
            null,
            null,
            null,
            null,
            null,
            0,
            0,
            0,
            0,
            false,
            0,
            0,
            false,
            null,
            0,
            true);
    ProxyAvailability availability = new ProxyAvailability(properties);

    ProxyAvailability.ProxyCheck first = availability.check();
    ProxyAvailability.ProxyCheck second = availability.check();

    assertSame(first, second);
  }
}
