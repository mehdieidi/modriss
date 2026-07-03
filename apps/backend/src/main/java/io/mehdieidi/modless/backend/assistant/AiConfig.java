package io.mehdieidi.modless.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.application.AssistantHardeningService;
import io.mehdieidi.modless.platform.assistant.application.AssistantPromptGuard;
import io.mehdieidi.modless.platform.assistant.config.AiProperties;
import io.mehdieidi.modless.platform.assistant.delta.DeltaCompiler;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.provider.ConfiguredAssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.provider.ProxyAvailability;
import io.mehdieidi.modless.platform.assistant.provider.springai.GeminiAssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.provider.springai.OpenAiCompatibleAssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.modless.platform.assistant.tools.AssistantToolService;
import io.mehdieidi.modless.platform.model.application.ModelService;
import java.net.Proxy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Wires platform assistant providers and delivery-only HTTP clients for the backend. */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

  @Bean
  @Primary
  AssistantSettings assistantSettings(AiProperties properties) {
    return properties;
  }

  @Bean
  ProxyAvailability proxyAvailability(AiProperties properties) {
    return new ProxyAvailability(properties);
  }

  @Bean
  @Primary
  AssistantToolService assistantToolService(
      io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog catalogs,
      AssistantPatchCompiler patchCompiler,
      DeltaCompiler deltaCompiler,
      AssistantMetamodelSchemaService schemas,
      ModelService models,
      ObjectMapper mapper) {
    return new AssistantToolService(
        catalogs, patchCompiler, deltaCompiler, schemas, models, mapper);
  }

  @Bean
  OpenAiCompatibleAssistantModelProvider openAiCompatibleAssistantModelProvider(
      AiProperties properties,
      ProxyAvailability proxyAvailability,
      AssistantPromptGuard promptGuard,
      AssistantToolService tools,
      AssistantHardeningService hardening,
      @Qualifier("aiRestClientBuilder") RestClient.Builder restClientBuilder) {
    return new OpenAiCompatibleAssistantModelProvider(
        properties, proxyAvailability, promptGuard, tools, hardening, restClientBuilder);
  }

  @Bean
  GeminiAssistantModelProvider geminiAssistantModelProvider(
      AiProperties properties,
      ProxyAvailability proxyAvailability,
      AssistantPromptGuard promptGuard,
      AssistantToolService tools,
      AssistantHardeningService hardening) {
    return new GeminiAssistantModelProvider(
        properties, proxyAvailability, promptGuard, tools, hardening);
  }

  @Bean
  @Primary
  AssistantModelProvider assistantModelProvider(
      AiProperties properties,
      OpenAiCompatibleAssistantModelProvider openai,
      GeminiAssistantModelProvider gemini) {
    return new ConfiguredAssistantModelProvider(properties, openai, gemini);
  }

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
