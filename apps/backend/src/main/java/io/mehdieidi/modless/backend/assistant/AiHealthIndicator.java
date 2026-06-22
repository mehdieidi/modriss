package io.mehdieidi.modless.backend.assistant;

import io.mehdieidi.modless.platform.assistant.persistence.embedding.EmbeddingSettings;
import io.mehdieidi.modless.platform.assistant.persistence.embedding.LocalEmbeddingService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Reports local assistant readiness and proxy reachability. */
@Component("ai")
public class AiHealthIndicator implements HealthIndicator {

  private final AiProperties properties;
  private final ProxyAvailability proxyAvailability;
  private final LocalEmbeddingService embeddings;

  /**
   * Creates the AI health indicator.
   *
   * @param properties AI settings
   * @param proxyAvailability proxy checker
   * @param embeddings local embedding service
   */
  public AiHealthIndicator(
      AiProperties properties,
      ProxyAvailability proxyAvailability,
      LocalEmbeddingService embeddings) {
    this.properties = properties;
    this.proxyAvailability = proxyAvailability;
    this.embeddings = embeddings;
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
        .withDetail("embeddingConfigured", properties.embeddings().provider())
        .withDetail("embeddingActive", toAiProvider(embeddings.activeProvider()))
        .withDetail("embeddingFallbackToHash", properties.embeddings().fallbackToHash())
        .withDetail(
            "embeddingProductionReady",
            properties.embeddings().provider() == AiProperties.EmbeddingProvider.ONNX
                && toAiProvider(embeddings.activeProvider()) == AiProperties.EmbeddingProvider.ONNX
                && !properties.embeddings().fallbackToHash())
        .build();
  }

  private static AiProperties.EmbeddingProvider toAiProvider(EmbeddingSettings.Provider provider) {
    return provider == EmbeddingSettings.Provider.HASH
        ? AiProperties.EmbeddingProvider.HASH
        : AiProperties.EmbeddingProvider.ONNX;
  }
}
