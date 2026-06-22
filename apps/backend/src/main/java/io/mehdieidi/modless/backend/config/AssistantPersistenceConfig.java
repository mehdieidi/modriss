package io.mehdieidi.modless.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.backend.assistant.AiProperties;
import io.mehdieidi.modless.platform.assistant.persistence.embedding.EmbeddingSettings;
import io.mehdieidi.modless.platform.assistant.persistence.embedding.LocalEmbeddingService;
import io.mehdieidi.modless.platform.assistant.persistence.jdbc.JdbcAssistantCatalog;
import io.mehdieidi.modless.platform.assistant.persistence.jdbc.JdbcAssistantMemoryStore;
import io.mehdieidi.modless.platform.assistant.persistence.jdbc.JdbcAssistantModelContextIndex;
import io.mehdieidi.modless.platform.assistant.persistence.memory.SpringAiJdbcChatMemory;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.assistant.spi.AssistantChatMemory;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMemoryStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantModelContextIndex;
import io.mehdieidi.modless.platform.modeling.runtime.MdeRuntimePaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Wires assistant persistence implementations from the platform-assistant feature package. */
@Configuration
public class AssistantPersistenceConfig {

  @Bean
  AssistantMemoryStore assistantMemoryStore(JdbcTemplate jdbc, ObjectMapper mapper) {
    return new JdbcAssistantMemoryStore(jdbc, mapper);
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
    JdbcAssistantCatalog catalog = new JdbcAssistantCatalog(jdbc, embeddings, mdePaths);
    catalog.refresh();
    return catalog;
  }

  @Bean
  AssistantModelContextIndex assistantModelContextIndex(JdbcTemplate jdbc, ObjectMapper mapper) {
    return new JdbcAssistantModelContextIndex(jdbc, mapper);
  }

  private static EmbeddingSettings toEmbeddingSettings(AiProperties.Embeddings embeddings) {
    if (embeddings == null) {
      return new EmbeddingSettings(
          EmbeddingSettings.Provider.ONNX, null, null, null, null, false, -1, true);
    }
    return new EmbeddingSettings(
        embeddings.provider() == AiProperties.EmbeddingProvider.HASH
            ? EmbeddingSettings.Provider.HASH
            : EmbeddingSettings.Provider.ONNX,
        embeddings.modelResource(),
        embeddings.tokenizerResource(),
        embeddings.modelOutputName(),
        embeddings.cacheDirectory(),
        embeddings.disableCaching(),
        embeddings.gpuDeviceId(),
        embeddings.fallbackToHash());
  }
}
