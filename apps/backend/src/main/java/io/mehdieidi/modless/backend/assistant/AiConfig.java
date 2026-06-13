package io.mehdieidi.modless.backend.assistant;

import java.net.Proxy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** AI-specific backend configuration. */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

  /**
   * Selects the configured provider while keeping orchestrator code provider-neutral.
   *
   * @param properties AI settings
   * @param openai OpenAI-compatible adapter
   * @param gemini Gemini adapter
   * @return configured provider delegate
   */
  @Bean
  @Primary
  AssistantModelProvider assistantModelProvider(
      AiProperties properties,
      OpenAiCompatibleAssistantModelProvider openai,
      GeminiAssistantModelProvider gemini) {
    return new ConfiguredAssistantModelProvider(properties, openai, gemini);
  }

  /**
   * Creates a RestClient builder whose proxy applies only to AI provider calls.
   *
   * @param properties AI settings
   * @return proxy-aware RestClient builder
   */
  @Bean("aiRestClientBuilder")
  RestClient.Builder aiRestClientBuilder(AiProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(
        properties.requestTimeout().compareTo(java.time.Duration.ofSeconds(10)) < 0
            ? properties.requestTimeout()
            : java.time.Duration.ofSeconds(10));
    factory.setReadTimeout(properties.requestTimeout());
    AiProperties.Proxy proxy = properties.proxy();
    if (proxy.enabled() && proxy.type() != AiProperties.ProxyType.DIRECT) {
      Proxy.Type type =
          proxy.type() == AiProperties.ProxyType.SOCKS ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
      factory.setProxy(new Proxy(type, proxy.address()));
    }
    return RestClient.builder().requestFactory(factory);
  }
}
