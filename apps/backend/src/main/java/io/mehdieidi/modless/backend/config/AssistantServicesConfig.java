package io.mehdieidi.modless.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.backend.observability.ModlessMetrics;
import io.mehdieidi.modless.platform.assistant.agent.ContextBudget;
import io.mehdieidi.modless.platform.assistant.agent.IntentPlanner;
import io.mehdieidi.modless.platform.assistant.agent.ModelDeltaProviderClient;
import io.mehdieidi.modless.platform.assistant.agent.ModelingAgent;
import io.mehdieidi.modless.platform.assistant.agent.PromptContextBuilder;
import io.mehdieidi.modless.platform.assistant.agent.ReadOnlyAnswerAgent;
import io.mehdieidi.modless.platform.assistant.application.AssistantHardeningService;
import io.mehdieidi.modless.platform.assistant.application.AssistantOrchestrator;
import io.mehdieidi.modless.platform.assistant.application.AssistantPromptGuard;
import io.mehdieidi.modless.platform.assistant.application.AssistantValidationFeedbackResolver;
import io.mehdieidi.modless.platform.assistant.application.CancellationRegistry;
import io.mehdieidi.modless.platform.assistant.application.MetamodelCatalogService;
import io.mehdieidi.modless.platform.assistant.application.ModelContextIndexService;
import io.mehdieidi.modless.platform.assistant.application.TurnTransactionService;
import io.mehdieidi.modless.platform.assistant.config.AiProperties;
import io.mehdieidi.modless.platform.assistant.delta.DeltaCompiler;
import io.mehdieidi.modless.platform.assistant.delta.DeltaNormalizer;
import io.mehdieidi.modless.platform.assistant.delta.DeltaRepairService;
import io.mehdieidi.modless.platform.assistant.delta.ModelDeltaParser;
import io.mehdieidi.modless.platform.assistant.delta.ModelDeltaSchemaFactory;
import io.mehdieidi.modless.platform.assistant.delta.StructuralValidationGate;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelContractIndexService;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompleter;
import io.mehdieidi.modless.platform.assistant.planning.AssistantClarificationGate;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.retrieval.RetrievalCoordinator;
import io.mehdieidi.modless.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.modless.platform.assistant.source.SourceChunker;
import io.mehdieidi.modless.platform.assistant.source.SourceCoverageMatrix;
import io.mehdieidi.modless.platform.assistant.source.SourceEvidenceExtractor;
import io.mehdieidi.modless.platform.assistant.source.SourceEvidenceMerger;
import io.mehdieidi.modless.platform.assistant.source.SourceToModelDeltaPlanner;
import io.mehdieidi.modless.platform.assistant.source.SourceUnderstandingService;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.assistant.spi.AssistantChatMemory;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMemoryStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMetamodelContractStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMetrics;
import io.mehdieidi.modless.platform.assistant.spi.AssistantModelContextIndex;
import io.mehdieidi.modless.platform.assistant.spi.AssistantRealtimePublisher;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSourceEvidenceStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantToolBridge;
import io.mehdieidi.modless.platform.assistant.spi.AssistantTurnExecutionStore;
import io.mehdieidi.modless.platform.model.application.ModelService;
import io.mehdieidi.modless.platform.project.application.ProjectService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composes platform assistant services and their delivery-layer adapters. */
@Configuration
public class AssistantServicesConfig {

  @Bean
  AssistantSessionStore assistantSessionStore() {
    return new AssistantSessionStore();
  }

  @Bean
  CancellationRegistry cancellationRegistry() {
    return new CancellationRegistry();
  }

  @Bean
  AssistantMetamodelSchemaService assistantMetamodelSchemaService() {
    return new AssistantMetamodelSchemaService();
  }

  @Bean
  MetamodelKnowledgeService metamodelKnowledgeService(AssistantMetamodelSchemaService schemas) {
    return new MetamodelKnowledgeService(schemas);
  }

  @Bean
  MetamodelContractIndexService metamodelContractIndexService(
      MetamodelKnowledgeService metamodels,
      AssistantMetamodelContractStore contractStore,
      ObjectMapper mapper) {
    return new MetamodelContractIndexService(metamodels, contractStore, mapper);
  }

  @Bean
  RetrievalCoordinator retrievalCoordinator(
      MetamodelKnowledgeService metamodels,
      AssistantCatalog catalogs,
      AssistantMetamodelContractStore contractStore,
      AssistantModelProvider provider,
      AssistantSettings settings) {
    String embeddingProvider =
        settings instanceof AiProperties ai ? ai.embeddings().provider().name() : "";
    return new RetrievalCoordinator(
        metamodels, catalogs, contractStore, embeddingProvider, provider);
  }

  @Bean
  ModelDeltaSchemaFactory modelDeltaSchemaFactory(
      MetamodelKnowledgeService metamodels,
      AssistantMetamodelSchemaService schemas,
      ObjectMapper mapper) {
    return new ModelDeltaSchemaFactory(metamodels, schemas, mapper);
  }

  @Bean
  AssistantPatchCompiler assistantPatchCompiler(AssistantMetamodelSchemaService schemas) {
    return new AssistantPatchCompiler(schemas);
  }

  @Bean
  AssistantPatchCompleter assistantPatchCompleter(AssistantMetamodelSchemaService schemas) {
    return new AssistantPatchCompleter(schemas);
  }

  @Bean
  AssistantValidationFeedbackResolver assistantValidationFeedbackResolver(
      AssistantMetamodelSchemaService schemas) {
    return new AssistantValidationFeedbackResolver(schemas);
  }

  @Bean
  AssistantClarificationGate assistantClarificationGate() {
    return new AssistantClarificationGate();
  }

  @Bean
  AssistantPromptGuard assistantPromptGuard(AssistantSettings settings) {
    return new AssistantPromptGuard(settings);
  }

  @Bean
  AssistantMetrics assistantMetrics(ModlessMetrics metrics) {
    return new BackendAssistantMetrics(metrics);
  }

  @Bean
  AssistantHardeningService assistantHardeningService(
      AssistantSettings settings, AssistantMetrics metrics) {
    return new AssistantHardeningService(settings, metrics);
  }

  @Bean
  ModelDeltaParser modelDeltaParser(ObjectMapper mapper) {
    return new ModelDeltaParser(mapper);
  }

  @Bean
  DeltaCompiler deltaCompiler(AssistantMetamodelSchemaService schemas) {
    return new DeltaCompiler(schemas);
  }

  @Bean
  DeltaNormalizer deltaNormalizer(
      MetamodelKnowledgeService metamodels, AssistantMetamodelSchemaService schemas) {
    return new DeltaNormalizer(metamodels, schemas);
  }

  @Bean
  StructuralValidationGate structuralValidationGate(ModelService models) {
    return new StructuralValidationGate(models);
  }

  @Bean
  ContextBudget contextBudget(AssistantSettings settings) {
    return new ContextBudget(
        settings.maxPromptTokens(), settings.maxSnippetChars(), settings.maxContextSnippets());
  }

  @Bean
  PromptContextBuilder promptContextBuilder(ContextBudget budget) {
    return new PromptContextBuilder(budget);
  }

  @Bean
  ModelDeltaProviderClient modelDeltaProviderClient(
      AssistantModelProvider provider, ModelDeltaParser parser, DeltaNormalizer normalizer) {
    return new ModelDeltaProviderClient(provider, parser, normalizer);
  }

  @Bean
  ReadOnlyAnswerAgent readOnlyAnswerAgent(
      AssistantModelProvider provider, PromptContextBuilder prompts) {
    return new ReadOnlyAnswerAgent(provider, prompts);
  }

  @Bean
  IntentPlanner intentPlanner(
      AssistantModelProvider provider, ObjectMapper mapper, PromptContextBuilder prompts) {
    return new IntentPlanner(provider, mapper, prompts);
  }

  @Bean
  ModelingAgent modelingAgent(
      AssistantModelProvider provider,
      ModelDeltaSchemaFactory schemaFactory,
      ModelDeltaProviderClient providerClient,
      PromptContextBuilder prompts,
      DeltaCompiler compiler,
      AssistantSettings settings,
      AssistantToolBridge tools) {
    return new ModelingAgent(
        provider, schemaFactory, providerClient, prompts, compiler, settings, tools);
  }

  @Bean
  DeltaRepairService deltaRepairService(
      ModelingAgent modelingAgent,
      AssistantValidationFeedbackResolver feedbackResolver,
      AssistantMetamodelSchemaService schemas,
      AssistantCatalog catalogs,
      AssistantPatchCompleter patchCompleter,
      AssistantSettings settings) {
    return new DeltaRepairService(
        modelingAgent, feedbackResolver, schemas, catalogs, patchCompleter, settings);
  }

  @Bean
  TurnTransactionService turnTransactionService(
      AssistantPatchCompiler patchCompiler,
      StructuralValidationGate structuralValidation,
      ModelService models,
      AssistantMemoryStore memory,
      AssistantRealtimePublisher realtime) {
    return new TurnTransactionService(
        patchCompiler, structuralValidation, models, memory, realtime);
  }

  @Bean
  SourceChunker sourceChunker() {
    return new SourceChunker();
  }

  @Bean
  SourceEvidenceExtractor sourceEvidenceExtractor(
      AssistantModelProvider provider, ObjectMapper mapper) {
    return new LlmSourceEvidenceExtractor(provider, mapper);
  }

  @Bean
  SourceEvidenceMerger sourceEvidenceMerger() {
    return new SourceEvidenceMerger();
  }

  @Bean
  SourceCoverageMatrix sourceCoverageMatrix() {
    return new SourceCoverageMatrix();
  }

  @Bean
  SourceToModelDeltaPlanner sourceToModelDeltaPlanner(
      ObjectMapper mapper, SourceCoverageMatrix coverageMatrix) {
    return new SourceToModelDeltaPlanner(mapper, coverageMatrix);
  }

  @Bean
  SourceUnderstandingService sourceUnderstandingService(
      SourceChunker chunker, SourceEvidenceExtractor extractor, SourceEvidenceMerger merger) {
    return new SourceUnderstandingService(chunker, extractor, merger);
  }

  @Bean
  MetamodelCatalogService metamodelCatalogService(AssistantCatalog catalogs) {
    return new MetamodelCatalogService(catalogs);
  }

  @Bean
  ModelContextIndexService modelContextIndexService(AssistantModelContextIndex modelContexts) {
    return new ModelContextIndexService(modelContexts);
  }

  @Bean
  AssistantOrchestrator assistantOrchestrator(
      AssistantSettings settings,
      AssistantModelProvider provider,
      AssistantSessionStore sessions,
      AssistantMemoryStore memory,
      AssistantChatMemory chatMemory,
      AssistantCatalog catalogs,
      AssistantModelContextIndex modelContexts,
      AssistantSourceEvidenceStore sourceEvidenceStore,
      AssistantPatchCompiler patchCompiler,
      AssistantPatchCompleter patchCompleter,
      AssistantValidationFeedbackResolver feedbackResolver,
      AssistantClarificationGate clarificationGate,
      AssistantMetamodelSchemaService schemas,
      AssistantToolBridge tools,
      AssistantMetrics metrics,
      ObjectMapper mapper,
      AssistantRealtimePublisher realtime,
      AssistantHardeningService hardening,
      ModelService models,
      ProjectService projects,
      CancellationRegistry cancellations,
      AssistantTurnExecutionStore turnExecutions,
      StructuralValidationGate structuralValidation,
      IntentPlanner intentPlanner,
      ReadOnlyAnswerAgent readOnlyAnswerAgent,
      SourceUnderstandingService sourceUnderstanding,
      SourceToModelDeltaPlanner sourceToModelDeltaPlanner,
      RetrievalCoordinator retrievalCoordinator,
      DeltaRepairService repairService,
      TurnTransactionService turnTransactionService,
      ModelingAgent modelingAgent) {
    return new AssistantOrchestrator(
        settings,
        provider,
        sessions,
        memory,
        chatMemory,
        catalogs,
        modelContexts,
        sourceEvidenceStore,
        patchCompiler,
        patchCompleter,
        feedbackResolver,
        clarificationGate,
        schemas,
        tools,
        metrics,
        mapper,
        realtime,
        hardening,
        models,
        projects,
        cancellations,
        turnExecutions,
        structuralValidation,
        intentPlanner,
        readOnlyAnswerAgent,
        sourceUnderstanding,
        sourceToModelDeltaPlanner,
        retrievalCoordinator,
        repairService,
        turnTransactionService,
        modelingAgent);
  }

  /** Bridges Micrometer metrics to the platform assistant metrics port. */
  static final class BackendAssistantMetrics implements AssistantMetrics {

    private final ModlessMetrics metrics;

    BackendAssistantMetrics(ModlessMetrics metrics) {
      this.metrics = metrics;
    }

    @Override
    public void recordAssistantRequest() {
      metrics.recordAssistantRequest();
    }

    @Override
    public void recordAssistantTurnOutcome(String category, String outcome) {
      metrics.recordAssistantTurnOutcome(category, outcome);
    }

    @Override
    public void recordAssistantRepairAttempts(int attempts) {
      metrics.recordAssistantRepairAttempts(attempts);
    }

    @Override
    public void recordAssistantToolCalls(int toolCalls) {
      metrics.recordAssistantToolCalls(toolCalls);
    }

    @Override
    public void recordAssistantPhaseDuration(String phase, long millis) {
      metrics.recordAssistantPhaseDuration(phase, millis);
    }

    @Override
    public void recordAssistantCircuitRejected(String provider) {
      metrics.recordAssistantCircuitOpen();
    }
  }
}
