package io.mehdieidi.modless.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.config.AiProperties;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelContractIndexService;
import io.mehdieidi.modless.platform.assistant.persistence.embedding.EmbeddingSettings;
import io.mehdieidi.modless.platform.assistant.persistence.embedding.LocalEmbeddingService;
import io.mehdieidi.modless.platform.assistant.persistence.jdbc.JdbcAssistantCatalog;
import io.mehdieidi.modless.platform.assistant.persistence.jdbc.JdbcAssistantMemoryStore;
import io.mehdieidi.modless.platform.assistant.persistence.jdbc.JdbcAssistantMetamodelContractStore;
import io.mehdieidi.modless.platform.assistant.persistence.jdbc.JdbcAssistantModelContextIndex;
import io.mehdieidi.modless.platform.assistant.persistence.jdbc.JdbcAssistantSourceEvidenceStore;
import io.mehdieidi.modless.platform.assistant.persistence.jdbc.JdbcAssistantTurnExecutionStore;
import io.mehdieidi.modless.platform.assistant.persistence.memory.SpringAiJdbcChatMemory;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.assistant.spi.AssistantChatMemory;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMemoryStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMetamodelContractStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantModelContextIndex;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSourceEvidenceStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantTurnExecutionStore;
import io.mehdieidi.modless.platform.modeling.runtime.MdeRuntimePaths;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wires assistant persistence implementations from the platform-assistant feature package. */
@Configuration
public class AssistantPersistenceConfig {

  private static final Logger log = LoggerFactory.getLogger(AssistantPersistenceConfig.class);

  @Bean
  AssistantMemoryStore assistantMemoryStore(JdbcTemplate jdbc, ObjectMapper mapper) {
    return new JdbcAssistantMemoryStore(jdbc, mapper);
  }

  @Bean
  AssistantTurnExecutionStore assistantTurnExecutionStore(JdbcTemplate jdbc, ObjectMapper mapper) {
    return new JdbcAssistantTurnExecutionStore(jdbc, mapper);
  }

  @Bean
  AssistantSourceEvidenceStore assistantSourceEvidenceStore(
      JdbcTemplate jdbc, ObjectMapper mapper) {
    return new JdbcAssistantSourceEvidenceStore(jdbc, mapper);
  }

  @Bean
  AssistantMetamodelContractStore assistantMetamodelContractStore(
      JdbcTemplate jdbc, ObjectMapper mapper, LocalEmbeddingService embeddings) {
    return new JdbcAssistantMetamodelContractStore(jdbc, mapper, embeddings);
  }

  @Bean
  AssistantChatMemory assistantChatMemory(JdbcTemplate jdbc) {
    return new SpringAiJdbcChatMemory(jdbc);
  }

  @Bean
  LocalEmbeddingService localEmbeddingService(AiProperties properties) {
    return new LocalEmbeddingService(toEmbeddingSettings(properties.embeddings()));
  }

  @Bean
  AssistantCatalog assistantCatalog(
      JdbcTemplate jdbc, LocalEmbeddingService embeddings, MdeRuntimePaths mdePaths) {
    return new JdbcAssistantCatalog(jdbc, embeddings, mdePaths);
  }

  @Bean
  ApplicationRunner assistantCatalogRefreshRunner(AssistantCatalog catalog) {
    return args ->
        CompletableFuture.runAsync(
                () -> {
                  if (catalog instanceof JdbcAssistantCatalog jdbcCatalog) {
                    jdbcCatalog.refreshStartupCatalog();
                  } else {
                    catalog.refresh();
                  }
                })
            .whenComplete(
                (ignored, error) -> {
                  if (error == null) {
                    log.info("Assistant startup catalog refresh completed.");
                  } else {
                    log.warn("Assistant startup catalog refresh failed.", error);
                  }
                });
  }

  @Bean
  ApplicationRunner assistantMetamodelContractRefreshRunner(
      MetamodelContractIndexService contractIndex) {
    return args ->
        CompletableFuture.supplyAsync(contractIndex::refreshAll)
            .whenComplete(
                (count, error) -> {
                  if (error == null) {
                    log.info(
                        "Assistant metamodel contract index refreshed with {} records.", count);
                  } else {
                    log.warn("Assistant metamodel contract index refresh failed.", error);
                  }
                });
  }

  @Bean
  AssistantModelContextIndex assistantModelContextIndex(JdbcTemplate jdbc, ObjectMapper mapper) {
    return new JdbcAssistantModelContextIndex(jdbc, mapper);
  }

  private static EmbeddingSettings toEmbeddingSettings(AiProperties.Embeddings embeddings) {
    if (embeddings == null) {
      return resolveProvider(
          EmbeddingSettings.Provider.ONNX,
          new EmbeddingSettings(
              EmbeddingSettings.Provider.ONNX, null, null, null, null, false, -1, true));
    }
    EmbeddingSettings.Provider provider =
        embeddings.provider() == AiProperties.EmbeddingProvider.HASH
            ? EmbeddingSettings.Provider.HASH
            : EmbeddingSettings.Provider.ONNX;
    return resolveProvider(
        provider,
        new EmbeddingSettings(
            provider,
            embeddings.modelResource(),
            embeddings.tokenizerResource(),
            embeddings.modelOutputName(),
            embeddings.cacheDirectory(),
            embeddings.disableCaching(),
            embeddings.gpuDeviceId(),
            embeddings.fallbackToHash()));
  }

  private static EmbeddingSettings resolveProvider(
      EmbeddingSettings.Provider configured, EmbeddingSettings settings) {
    if (configured == EmbeddingSettings.Provider.ONNX
        && !LocalEmbeddingService.onnxRuntimeAvailable()) {
      log.warn(
          "ONNX embeddings are configured but the native runtime is not on the classpath. Using"
              + " deterministic hash embeddings for catalog retrieval. Build with the backend"
              + " onnx-embeddings Maven profile or set modless.ai.embeddings.provider=HASH.");
      return new EmbeddingSettings(
          EmbeddingSettings.Provider.HASH,
          settings.modelResource(),
          settings.tokenizerResource(),
          settings.modelOutputName(),
          settings.cacheDirectory(),
          settings.disableCaching(),
          settings.gpuDeviceId(),
          true);
    }
    return settings;
  }
}
