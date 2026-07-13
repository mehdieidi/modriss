package io.mehdieidi.varka.backend.assistant;

import io.mehdieidi.varka.platform.assistant.config.AiProperties;
import io.mehdieidi.varka.platform.assistant.provider.ProxyAvailability;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports local assistant readiness and proxy reachability. */
@Component("ai")
public class AiHealthIndicator implements HealthIndicator {

  private final AiProperties properties;
  private final ProxyAvailability proxyAvailability;

  /**
   * Creates the AI health indicator.
   *
   * @param properties AI settings
   * @param proxyAvailability proxy checker
   */
  public AiHealthIndicator(AiProperties properties, ProxyAvailability proxyAvailability) {
    this.properties = properties;
    this.proxyAvailability = proxyAvailability;
  }

  @Override
  public Health health() {
    ProxyAvailability.ProxyCheck proxy = proxyAvailability.check();
    Health.Builder builder =
        properties.enabled() && !proxy.available() ? Health.down() : Health.up();
    return builder
        .withDetail("enabled", properties.enabled())
        .withDetail("provider", properties.provider())
        .withDetail("proxy", proxy.message())
        .build();
  }
}
