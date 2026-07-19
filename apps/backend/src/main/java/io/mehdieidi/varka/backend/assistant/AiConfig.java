package io.mehdieidi.varka.backend.assistant;

import io.mehdieidi.varka.platform.assistant.agent.AgentTurnLoop;
import io.mehdieidi.varka.platform.assistant.application.AgenticAssistantFacade;
import io.mehdieidi.varka.platform.assistant.application.AgenticTurnService;
import io.mehdieidi.varka.platform.assistant.application.AssistantHardeningService;
import io.mehdieidi.varka.platform.assistant.application.AssistantPromptGuard;
import io.mehdieidi.varka.platform.assistant.config.AiProperties;
import io.mehdieidi.varka.platform.assistant.metamodel.LexicalRetrievalIndex;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelGuideGenerator;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.varka.platform.assistant.metamodel.TypeContractService;
import io.mehdieidi.varka.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.varka.platform.assistant.patch.ModelCommandCompiler;
import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.varka.platform.assistant.provider.ConfiguredAssistantModelProvider;
import io.mehdieidi.varka.platform.assistant.provider.ProxyAvailability;
import io.mehdieidi.varka.platform.assistant.provider.springai.GeminiAssistantModelProvider;
import io.mehdieidi.varka.platform.assistant.provider.springai.OpenAiCompatibleAssistantModelProvider;
import io.mehdieidi.varka.platform.assistant.source.SourceDocumentWorkers;
import io.mehdieidi.varka.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.varka.platform.assistant.tools.AgentModelTools;
import io.mehdieidi.varka.platform.model.application.ModelService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
  AgentModelTools agentModelTools(
      TypeContractService contracts, ModelService models, ModelCommandCompiler commandCompiler) {
    return new AgentModelTools(contracts, models, commandCompiler);
  }

  @Bean
  AgentTurnLoop agentTurnLoop(
      AssistantModelProvider provider,
      AgentModelTools tools,
      MetamodelGuideGenerator guides,
      LexicalRetrievalIndex retrieval,
      AssistantRealtimeHub realtime,
      AiProperties properties,
      io.mehdieidi.varka.platform.assistant.spi.AssistantMetrics metrics) {
    return new AgentTurnLoop(
        provider,
        tools,
        guides,
        retrieval,
        realtime,
        properties.turnTimeout(),
        properties.sourceTurnTimeout(),
        properties.maxAgentSteps(),
        properties.maxProviderCallsPerTurn(),
        properties.maxProviderCallsSourceTurn(),
        metrics);
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
      AgenticTurnService turns,
      io.mehdieidi.varka.platform.modeling.config.ModelingConfigService modelingConfig) {
    return new AgenticAssistantFacade(
        sessions,
        memory,
        chatMemory,
        projects,
        models,
        patches,
        realtime,
        provider,
        turns,
        modelingConfig);
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
      AssistantHardeningService hardening) {
    return new OpenAiCompatibleAssistantModelProvider(
        properties, proxyAvailability, promptGuard, hardening);
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
}
