package io.mehdieidi.modriss.backend.assistant;

import io.mehdieidi.modriss.platform.assistant.agent.AgentTurnLoop;
import io.mehdieidi.modriss.platform.assistant.agent.ConceptualInstanceModelWorkflow;
import io.mehdieidi.modriss.platform.assistant.application.AgenticAssistantFacade;
import io.mehdieidi.modriss.platform.assistant.application.AgenticTurnService;
import io.mehdieidi.modriss.platform.assistant.application.AssistantHardeningService;
import io.mehdieidi.modriss.platform.assistant.application.AssistantPromptGuard;
import io.mehdieidi.modriss.platform.assistant.config.AiProperties;
import io.mehdieidi.modriss.platform.assistant.metamodel.LexicalRetrievalIndex;
import io.mehdieidi.modriss.platform.assistant.metamodel.MetamodelGuideGenerator;
import io.mehdieidi.modriss.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.modriss.platform.assistant.metamodel.TypeContractService;
import io.mehdieidi.modriss.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modriss.platform.assistant.patch.ModelCommandCompiler;
import io.mehdieidi.modriss.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modriss.platform.assistant.provider.ConfiguredAssistantModelProvider;
import io.mehdieidi.modriss.platform.assistant.provider.ProxyAvailability;
import io.mehdieidi.modriss.platform.assistant.provider.springai.GeminiAssistantModelProvider;
import io.mehdieidi.modriss.platform.assistant.provider.springai.OpenAiCompatibleAssistantModelProvider;
import io.mehdieidi.modriss.platform.assistant.source.SourceDocumentWorkers;
import io.mehdieidi.modriss.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.modriss.platform.assistant.tools.AgentModelTools;
import io.mehdieidi.modriss.platform.model.application.ModelService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Wires platform assistant providers and delivery-only HTTP clients for the backend. */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

  @Bean
  public MetamodelGuideGenerator metamodelGuideGenerator(MetamodelKnowledgeService knowledge) {
    return new MetamodelGuideGenerator(knowledge);
  }

  @Bean
  public TypeContractService typeContractService(MetamodelKnowledgeService knowledge) {
    return new TypeContractService(knowledge);
  }

  @Bean
  public AgentModelTools agentModelTools(
      TypeContractService contracts, ModelService models, ModelCommandCompiler commandCompiler) {
    return new AgentModelTools(contracts, models, commandCompiler);
  }

  @Bean
  public AgentTurnLoop agentTurnLoop(
      AssistantModelProvider provider,
      AgentModelTools tools,
      MetamodelGuideGenerator guides,
      LexicalRetrievalIndex retrieval,
      AssistantRealtimeHub realtime,
      AiProperties properties,
      io.mehdieidi.modriss.platform.assistant.spi.AssistantMetrics metrics,
      ConceptualInstanceModelWorkflow conceptualWorkflow) {
    AgentTurnLoop loop =
        new AgentTurnLoop(
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
    loop.setConceptualInstanceModelWorkflow(conceptualWorkflow);
    return loop;
  }

  @Bean
  public ConceptualInstanceModelWorkflow conceptualInstanceModelWorkflow(
      AssistantModelProvider provider,
      MetamodelGuideGenerator guides,
      TypeContractService contracts,
      AiProperties properties,
      io.mehdieidi.modriss.platform.assistant.turn.AssistantTurnStore turns) {
    return new ConceptualInstanceModelWorkflow(provider, guides, contracts, properties, turns);
  }

  @Bean(destroyMethod = "close")
  public SourceDocumentWorkers sourceDocumentWorkers(
      AssistantModelProvider provider, AssistantRealtimeHub realtime) {
    return new SourceDocumentWorkers(provider, realtime, 2, 12000);
  }

  @Bean
  public AgenticTurnService agenticTurnService(
      ModelService models,
      AssistantPatchCompiler patches,
      AgentTurnLoop loop,
      SourceDocumentWorkers workers,
      io.mehdieidi.modriss.platform.assistant.spi.AssistantMemoryStore memory) {
    return new AgenticTurnService(models, patches, loop, workers, memory);
  }

  @Bean
  public AgenticAssistantFacade agenticAssistantFacade(
      io.mehdieidi.modriss.platform.assistant.session.AssistantSessionStore sessions,
      io.mehdieidi.modriss.platform.assistant.spi.AssistantMemoryStore memory,
      io.mehdieidi.modriss.platform.assistant.spi.AssistantChatMemory chatMemory,
      io.mehdieidi.modriss.platform.project.application.ProjectService projects,
      ModelService models,
      AssistantPatchCompiler patches,
      AssistantRealtimeHub realtime,
      AssistantModelProvider provider,
      AgenticTurnService turns,
      io.mehdieidi.modriss.platform.modeling.config.ModelingConfigService modelingConfig) {
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
  public AssistantSettings assistantSettings(AiProperties properties) {
    return properties;
  }

  @Bean
  public ProxyAvailability proxyAvailability(AiProperties properties) {
    return new ProxyAvailability(properties);
  }

  @Bean
  public OpenAiCompatibleAssistantModelProvider openAiCompatibleAssistantModelProvider(
      AiProperties properties,
      ProxyAvailability proxyAvailability,
      AssistantPromptGuard promptGuard,
      AssistantHardeningService hardening) {
    return new OpenAiCompatibleAssistantModelProvider(
        properties, proxyAvailability, promptGuard, hardening);
  }

  @Bean
  public GeminiAssistantModelProvider geminiAssistantModelProvider(
      AiProperties properties,
      ProxyAvailability proxyAvailability,
      AssistantPromptGuard promptGuard,
      AssistantHardeningService hardening) {
    return new GeminiAssistantModelProvider(properties, proxyAvailability, promptGuard, hardening);
  }

  @Bean
  @Primary
  public AssistantModelProvider assistantModelProvider(
      AiProperties properties,
      OpenAiCompatibleAssistantModelProvider openai,
      GeminiAssistantModelProvider gemini) {
    return new ConfiguredAssistantModelProvider(properties, openai, gemini);
  }
}
