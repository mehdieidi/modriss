package io.mehdieidi.modless.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.backend.observability.ModlessMetrics;
import io.mehdieidi.modless.platform.assistant.application.AssistantHardeningService;
import io.mehdieidi.modless.platform.assistant.application.AssistantOrchestrator;
import io.mehdieidi.modless.platform.assistant.application.AssistantPromptGuard;
import io.mehdieidi.modless.platform.assistant.application.AssistantValidationFeedbackResolver;
import io.mehdieidi.modless.platform.assistant.application.MetamodelCatalogService;
import io.mehdieidi.modless.platform.assistant.application.ModelContextIndexService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompleter;
import io.mehdieidi.modless.platform.assistant.patch.SemanticModelPatchParser;
import io.mehdieidi.modless.platform.assistant.planning.AssistantClarificationGate;
import io.mehdieidi.modless.platform.assistant.planning.AssistantTurnPlanParser;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.assistant.spi.AssistantChatMemory;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMemoryStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMetrics;
import io.mehdieidi.modless.platform.assistant.spi.AssistantModelContextIndex;
import io.mehdieidi.modless.platform.assistant.spi.AssistantRealtimePublisher;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.modless.platform.assistant.spi.AssistantToolBridge;
import io.mehdieidi.modless.platform.assistant.subset.AssistantModelSubsetPlanner;
import io.mehdieidi.modless.platform.assistant.subset.AssistantModelingStrategy;
import io.mehdieidi.modless.platform.model.application.ModelService;
import io.mehdieidi.modless.platform.project.application.ProjectService;
import org.springframework.beans.factory.annotation.Value;
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
  SemanticModelPatchParser semanticModelPatchParser(ObjectMapper mapper) {
    return new SemanticModelPatchParser(mapper);
  }

  @Bean
  AssistantTurnPlanParser assistantTurnPlanParser(ObjectMapper mapper) {
    return new AssistantTurnPlanParser(mapper);
  }

  @Bean
  AssistantMetamodelSchemaService assistantMetamodelSchemaService() {
    return new AssistantMetamodelSchemaService();
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
  AssistantModelingStrategy assistantModelingStrategy(
      @Value("${modless.ai.modeling-strategy:model-subset}") String value) {
    return AssistantModelingStrategy.from(value);
  }

  @Bean
  AssistantModelSubsetPlanner assistantModelSubsetPlanner(
      AssistantModelProvider provider,
      AssistantMetamodelSchemaService schemas,
      ObjectMapper mapper) {
    return new AssistantModelSubsetPlanner(provider, schemas, mapper);
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
      AssistantModelingStrategy modelingStrategy,
      AssistantModelSubsetPlanner subsetPlanner) {
    return new AssistantOrchestrator(
        settings,
        provider,
        sessions,
        memory,
        chatMemory,
        catalogs,
        modelContexts,
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
        modelingStrategy,
        subsetPlanner);
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
    public void recordAssistantCircuitRejected(String provider) {
      metrics.recordAssistantCircuitOpen();
    }
  }
}
