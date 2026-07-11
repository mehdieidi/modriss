package io.mehdieidi.modless.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.persistence.jdbc.JdbcAssistantMemoryStore;
import io.mehdieidi.modless.platform.assistant.persistence.memory.SpringAiJdbcChatMemory;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.assistant.spi.AssistantChatMemory;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMemoryStore;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** Persistence retained by the agentic runtime: conversation history and undo proposals only. */
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

  /** Compatibility adapter for provider construction; the agent uses deterministic Ecore tools. */
  @Bean
  AssistantCatalog assistantCatalog() {
    return new AssistantCatalog() {
      @Override
      public void refresh() {}

      @Override
      public List<AssistantModelProvider.ContextSnippet> search(
          String query, String level, int limit) {
        return List.of();
      }

      @Override
      public List<AssistantModelProvider.ContextSnippet> describeType(
          String typeName, String level, int limit) {
        return List.of();
      }

      @Override
      public Optional<String> canonicalEnumLiteral(
          String ownerType, String featureName, String value, String level) {
        return Optional.empty();
      }
    };
  }
}
