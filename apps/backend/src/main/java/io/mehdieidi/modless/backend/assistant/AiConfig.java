package io.mehdieidi.modless.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.agent.AgentTurnLoop;
import io.mehdieidi.modless.platform.assistant.application.AgenticAssistantFacade;
import io.mehdieidi.modless.platform.assistant.application.AgenticTurnService;
import io.mehdieidi.modless.platform.assistant.application.AssistantHardeningService;
import io.mehdieidi.modless.platform.assistant.application.AssistantPromptGuard;
import io.mehdieidi.modless.platform.assistant.config.AiProperties;
import io.mehdieidi.modless.platform.assistant.delta.DeltaCompiler;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelGuideGenerator;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.modless.platform.assistant.metamodel.TypeContractService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.provider.ConfiguredAssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.provider.ProxyAvailability;
import io.mehdieidi.modless.platform.assistant.provider.springai.GeminiAssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.provider.springai.OpenAiCompatibleAssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.source.SourceDocumentWorkers;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.modless.platform.assistant.tools.AgentModelTools;
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
    return new SourceDocumentWorkers(
        provider, realtime, 2, 12000);
  }

  @Bean
  AgenticTurnService agenticTurnService(
      ModelService models,
      AssistantPatchCompiler patches,
      AgentTurnLoop loop,
      SourceDocumentWorkers workers,
      AssistantRealtimeHub realtime,
      io.mehdieidi.modless.platform.assistant.spi.AssistantMemoryStore memory) {
    return new AgenticTurnService(models, patches, loop, workers, realtime, memory);
  }

  @Bean
  AgenticAssistantFacade agenticAssistantFacade(
      io.mehdieidi.modless.platform.assistant.session.AssistantSessionStore sessions,
      io.mehdieidi.modless.platform.assistant.spi.AssistantMemoryStore memory,
      io.mehdieidi.modless.platform.assistant.spi.AssistantChatMemory chatMemory,
      io.mehdieidi.modless.platform.project.application.ProjectService projects,
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
