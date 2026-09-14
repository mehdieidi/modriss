package io.mehdieidi.modriss.backend.config;

import io.mehdieidi.modriss.platform.assistant.persistence.jdbc.JdbcAssistantMemoryStore;
import io.mehdieidi.modriss.platform.assistant.persistence.jdbc.JdbcAssistantTurnStore;
import io.mehdieidi.modriss.platform.assistant.persistence.memory.SpringAiJdbcChatMemory;
import io.mehdieidi.modriss.platform.assistant.spi.AssistantChatMemory;
import io.mehdieidi.modriss.platform.assistant.spi.AssistantMemoryStore;
import io.mehdieidi.modriss.platform.assistant.turn.AssistantTurnStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

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

  @Bean
  AssistantTurnStore assistantTurnStore(JdbcTemplate jdbc, ObjectMapper mapper) {
    return new JdbcAssistantTurnStore(jdbc, mapper);
  }
}
