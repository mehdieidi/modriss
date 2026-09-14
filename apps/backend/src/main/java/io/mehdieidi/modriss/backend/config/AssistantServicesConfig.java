package io.mehdieidi.modriss.backend.config;

import io.mehdieidi.modriss.backend.observability.ModrissMetrics;
import io.mehdieidi.modriss.platform.assistant.application.AssistantHardeningService;
import io.mehdieidi.modriss.platform.assistant.application.AssistantPromptGuard;
import io.mehdieidi.modriss.platform.assistant.config.AiProperties;
import io.mehdieidi.modriss.platform.assistant.metamodel.AssistantMetamodelProfile;
import io.mehdieidi.modriss.platform.assistant.metamodel.LexicalRetrievalIndex;
import io.mehdieidi.modriss.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.modriss.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modriss.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modriss.platform.assistant.patch.MetamodelContractGraph;
import io.mehdieidi.modriss.platform.assistant.patch.ModelCommandCompiler;
import io.mehdieidi.modriss.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.modriss.platform.assistant.spi.AssistantMetrics;
import io.mehdieidi.modriss.platform.assistant.spi.AssistantSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Minimal composition for the agentic assistant runtime. */
@Configuration
public class AssistantServicesConfig {
  @Bean
  @SuppressWarnings("unused") // Invoked by Spring while building the application context.
  AssistantSessionStore assistantSessionStore() {
    return new AssistantSessionStore();
  }

  @Bean
  @SuppressWarnings("unused") // Invoked by Spring while building the application context.
  AssistantMetamodelProfile assistantMetamodelProfile(AiProperties properties) {
    return AssistantMetamodelProfile.forMode(properties.metamodelMode());
  }

  @Bean
  @SuppressWarnings("unused") // Invoked by Spring while building the application context.
  AssistantMetamodelSchemaService assistantMetamodelSchemaService(
      io.mehdieidi.modriss.platform.modeling.config.ModelingConfigService modelingConfig,
      io.mehdieidi.modriss.platform.modeling.metamodel.MetamodelResolver resolver,
      AssistantMetamodelProfile profile) {
    return new AssistantMetamodelSchemaService(modelingConfig, resolver, profile);
  }

  @Bean
  @SuppressWarnings("unused") // Invoked by Spring while building the application context.
  MetamodelContractGraph metamodelContractGraph(
      io.mehdieidi.modriss.platform.modeling.metamodel.MetamodelResolver resolver) {
    return new MetamodelContractGraph(resolver);
  }

  @Bean
  @SuppressWarnings("unused") // Invoked by Spring while building the application context.
  MetamodelKnowledgeService metamodelKnowledgeService(
      AssistantMetamodelSchemaService schemas,
      io.mehdieidi.modriss.platform.modeling.config.ModelingConfigService modelingConfig) {
    return new MetamodelKnowledgeService(schemas, modelingConfig);
  }

  @Bean
  @SuppressWarnings("unused") // Invoked by Spring while building the application context.
  AssistantPatchCompiler assistantPatchCompiler(AssistantMetamodelSchemaService schemas) {
    return new AssistantPatchCompiler(schemas);
  }

  @Bean
  @SuppressWarnings("unused") // Invoked by Spring while building the application context.
  ModelCommandCompiler modelCommandCompiler(AssistantPatchCompiler compiler) {
    return new ModelCommandCompiler(compiler);
  }

  @Bean
  @SuppressWarnings("unused") // Invoked by Spring while building the application context.
  LexicalRetrievalIndex lexicalRetrievalIndex(
      AssistantMetamodelSchemaService schemas,
      MetamodelKnowledgeService knowledge,
      org.springframework.beans.factory.ObjectProvider<
              org.springframework.ai.embedding.EmbeddingModel>
          embeddings) {
    return new LexicalRetrievalIndex(schemas, knowledge, null, embeddings.getIfAvailable());
  }

  @Bean
  @SuppressWarnings("unused") // Invoked by Spring while building the application context.
  AssistantPromptGuard assistantPromptGuard(AssistantSettings settings) {
    return new AssistantPromptGuard(settings);
  }

  @Bean
  @SuppressWarnings("unused") // Invoked by Spring while building the application context.
  AssistantMetrics assistantMetrics(ModrissMetrics metrics) {
    return new BackendAssistantMetrics(metrics);
  }

  @Bean
  @SuppressWarnings("unused") // Invoked by Spring while building the application context.
  AssistantHardeningService assistantHardeningService(
      AssistantSettings settings, AssistantMetrics metrics) {
    return new AssistantHardeningService(settings, metrics);
  }

  static final class BackendAssistantMetrics implements AssistantMetrics {
    private final ModrissMetrics metrics;

    BackendAssistantMetrics(ModrissMetrics metrics) {
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
    public void recordAssistantTokenEstimate(String direction, String provider, long tokens) {
      metrics.recordAssistantTokenEstimate(direction, provider, tokens);
    }

    @Override
    public void recordAssistantTokenUsage(String direction, String provider, long tokens) {
      metrics.recordAssistantTokenUsage(direction, provider, tokens);
    }

    @Override
    public void recordAssistantRetrievalChars(int chars) {
      metrics.recordAssistantRetrievalChars(chars);
    }

    @Override
    public void recordAssistantMalformedAction(String reason) {
      metrics.recordAssistantMalformedAction(reason);
    }

    @Override
    public void recordAssistantRepairReason(String reason) {
      metrics.recordAssistantRepairReason(reason);
    }

    @Override
    public void recordAssistantAction(String action, int step) {
      metrics.recordAssistantAction(action, step);
    }

    @Override
    public void recordAssistantStructuralValidation(boolean valid) {
      metrics.recordAssistantStructuralValidation(valid);
    }

    @Override
    public void recordAssistantCheckpoint(String operation) {
      metrics.recordAssistantCheckpoint(operation);
    }

    @Override
    public void recordAssistantCircuitRejected(String provider) {
      metrics.recordAssistantCircuitRejected(provider);
    }

    @Override
    public void recordAssistantProviderSuccess(String provider, String model) {
      metrics.recordAssistantProviderSuccess(provider, model);
    }

    @Override
    public void recordAssistantProviderFailure(String provider) {
      metrics.recordAssistantProviderFailure(provider);
    }

    @Override
    public void recordAssistantRateLimited(String userId) {
      metrics.recordAssistantRateLimited(userId);
    }
  }
}
