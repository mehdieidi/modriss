package io.mehdieidi.varka.backend.assistant;

import io.mehdieidi.varka.platform.assistant.agent.AgentTurnLoop;
import io.mehdieidi.varka.platform.assistant.application.AgenticAssistantFacade;
import io.mehdieidi.varka.platform.assistant.application.AgenticTurnService;
import io.mehdieidi.varka.platform.assistant.application.AssistantHardeningService;
import io.mehdieidi.varka.platform.assistant.application.AssistantPromptGuard;
import io.mehdieidi.varka.platform.assistant.config.AiProperties;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelGuideGenerator;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.varka.platform.assistant.metamodel.TypeContractService;
import io.mehdieidi.varka.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.varka.platform.assistant.provider.ConfiguredAssistantModelProvider;
import io.mehdieidi.varka.platform.assistant.provider.ProxyAvailability;
import io.mehdieidi.varka.platform.assistant.provider.springai.GeminiAssistantModelProvider;
import io.mehdieidi.varka.platform.assistant.provider.springai.OpenAiCompatibleAssistantModelProvider;
import io.mehdieidi.varka.platform.assistant.source.SourceDocumentWorkers;
import io.mehdieidi.varka.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.varka.platform.assistant.tools.AgentModelTools;
import io.mehdieidi.varka.platform.model.application.ModelService;
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
  MetamodelGuideGenerator metamodelGuideGenerator(MetamodelKnowledgeService knowledge) {
    return new MetamodelGuideGenerator(knowledge);
  }

  @Bean
  TypeContractService typeContractService(MetamodelKnowledgeService knowledge) {
    return new TypeContractService(knowledge);
  }

  @Bean
  AgentModelTools agentModelTools(TypeContractService contracts, ModelService models) {
    return new AgentModelTools(contracts, models);
  }

  @Bean
  AgentTurnLoop agentTurnLoop(
      AssistantModelProvider provider,
      AgentModelTools tools,
      MetamodelGuideGenerator guides,
      AssistantRealtimeHub realtime,
      AiProperties properties) {
    return new AgentTurnLoop(
        provider,
        tools,
        guides,
        realtime,
        properties.turnTimeout(),
        properties.sourceTurnTimeout(),
        Math.min(properties.maxAgentSteps(), 3),
        Math.min(properties.maxProviderCallsPerTurn(), 3));
  }

  @Bean(destroyMethod = "close")
  SourceDocumentWorkers sourceDocumentWorkers(
      AssistantModelProvider provider, AssistantRealtimeHub realtime) {
    return new SourceDocumentWorkers(provider, realtime, 2, 12000);
  }

  @Bean
  AgenticTurnService agenticTurnService(
      ModelService models,
      AssistantPatchCompiler patches,
      AgentTurnLoop loop,
      SourceDocumentWorkers workers,
      AssistantRealtimeHub realtime,
      io.mehdieidi.varka.platform.assistant.spi.AssistantMemoryStore memory) {
    return new AgenticTurnService(models, patches, loop, workers, realtime, memory);
  }

  @Bean
  AgenticAssistantFacade agenticAssistantFacade(
      io.mehdieidi.varka.platform.assistant.session.AssistantSessionStore sessions,
      io.mehdieidi.varka.platform.assistant.spi.AssistantMemoryStore memory,
      io.mehdieidi.varka.platform.assistant.spi.AssistantChatMemory chatMemory,
      io.mehdieidi.varka.platform.project.application.ProjectService projects,
      ModelService models,
      AssistantPatchCompiler patches,
      AssistantRealtimeHub realtime,
      AssistantModelProvider provider,
      AgenticTurnService turns) {
    return new AgenticAssistantFacade(
        sessions, memory, chatMemory, projects, models, patches, realtime, provider, turns);
  }

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
  OpenAiCompatibleAssistantModelProvider openAiCompatibleAssistantModelProvider(
      AiProperties properties,
      ProxyAvailability proxyAvailability,
      AssistantPromptGuard promptGuard,
      AssistantHardeningService hardening,
      @Qualifier("aiRestClientBuilder") RestClient.Builder restClientBuilder) {
    return new OpenAiCompatibleAssistantModelProvider(
        properties, proxyAvailability, promptGuard, hardening, restClientBuilder);
  }

  @Bean
  GeminiAssistantModelProvider geminiAssistantModelProvider(
      AiProperties properties,
      ProxyAvailability proxyAvailability,
      AssistantPromptGuard promptGuard,
      AssistantHardeningService hardening) {
    return new GeminiAssistantModelProvider(properties, proxyAvailability, promptGuard, hardening);
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
