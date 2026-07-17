package io.mehdieidi.varka.backend.config;

import io.mehdieidi.varka.backend.observability.VarkaMetrics;
import io.mehdieidi.varka.platform.assistant.application.AssistantHardeningService;
import io.mehdieidi.varka.platform.assistant.application.AssistantPromptGuard;
import io.mehdieidi.varka.platform.assistant.metamodel.LexicalRetrievalIndex;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.varka.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.varka.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.varka.platform.assistant.patch.MetamodelContractGraph;
import io.mehdieidi.varka.platform.assistant.patch.ModelCommandCompiler;
import io.mehdieidi.varka.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.varka.platform.assistant.spi.AssistantMetrics;
import io.mehdieidi.varka.platform.assistant.spi.AssistantSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Minimal composition for the agentic assistant runtime. */
@Configuration
public class AssistantServicesConfig {
  @Bean
  AssistantSessionStore assistantSessionStore() {
    return new AssistantSessionStore();
  }

  @Bean
  AssistantMetamodelSchemaService assistantMetamodelSchemaService(
      io.mehdieidi.varka.platform.modeling.config.ModelingConfigService modelingConfig,
      io.mehdieidi.varka.platform.modeling.metamodel.MetamodelResolver resolver) {
    return new AssistantMetamodelSchemaService(modelingConfig, resolver);
  }

  @Bean
  MetamodelContractGraph metamodelContractGraph(
      io.mehdieidi.varka.platform.modeling.metamodel.MetamodelResolver resolver) {
    return new MetamodelContractGraph(resolver);
  }

  @Bean
  MetamodelKnowledgeService metamodelKnowledgeService(AssistantMetamodelSchemaService schemas) {
    return new MetamodelKnowledgeService(schemas);
  }

  @Bean
  AssistantPatchCompiler assistantPatchCompiler(AssistantMetamodelSchemaService schemas) {
    return new AssistantPatchCompiler(schemas);
  }

  @Bean
  ModelCommandCompiler modelCommandCompiler(AssistantPatchCompiler compiler) {
    return new ModelCommandCompiler(compiler);
  }

  @Bean
  LexicalRetrievalIndex lexicalRetrievalIndex(
      AssistantMetamodelSchemaService schemas,
      MetamodelKnowledgeService knowledge,
      org.springframework.beans.factory.ObjectProvider<
              org.springframework.ai.embedding.EmbeddingModel>
          embeddings) {
    return new LexicalRetrievalIndex(schemas, knowledge, null, embeddings.getIfAvailable());
  }

  @Bean
  AssistantPromptGuard assistantPromptGuard(AssistantSettings settings) {
    return new AssistantPromptGuard(settings);
  }

  @Bean
  AssistantMetrics assistantMetrics(VarkaMetrics metrics) {
    return new BackendAssistantMetrics(metrics);
  }

  @Bean
  AssistantHardeningService assistantHardeningService(
      AssistantSettings settings, AssistantMetrics metrics) {
    return new AssistantHardeningService(settings, metrics);
  }

  static final class BackendAssistantMetrics implements AssistantMetrics {
    private final VarkaMetrics metrics;

    BackendAssistantMetrics(VarkaMetrics metrics) {
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
      metrics.recordAssistantCircuitRejected(provider);
    }

    @Override
    public void recordAssistantProviderSuccess(String provider, String role, String model) {
      metrics.recordAssistantProviderSuccess(provider, role, model);
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
