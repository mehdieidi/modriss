package io.mehdieidi.modless.backend.assistant;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
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
        .withDetail("mode", properties.mode())
        .withDetail("provider", properties.provider())
        .withDetail("proxy", proxy.message())
        .build();
  }
}
