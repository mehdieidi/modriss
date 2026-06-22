package io.mehdieidi.modless.backend.assistant;

import io.mehdieidi.modless.platform.assistant.spi.AssistantChatMemory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.PostgresChatMemoryRepositoryDialect;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/** Spring AI chat memory facade for the assistant's recent conversation window. */
@Service
public class SpringAiChatMemoryService implements AssistantChatMemory {

  private final ChatMemoryRepository repository;
  private final Map<String, List<Message>> inMemory;

  public SpringAiChatMemoryService() {
    this.repository = null;
    this.inMemory = new ConcurrentHashMap<>();
  }

  @Autowired
  public SpringAiChatMemoryService(@Nullable JdbcTemplate jdbcTemplate) {
    this.repository =
        jdbcTemplate == null
            ? null
            : JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                .dialect(new PostgresChatMemoryRepositoryDialect())
                .build();
    this.inMemory = repository == null ? new ConcurrentHashMap<>() : null;
  }

  @Override
  public void appendUser(String conversationId, String content) {
    append(conversationId, new UserMessage(content == null ? "" : content));
  }

  @Override
  public void appendAssistant(String conversationId, String content) {
    append(conversationId, new AssistantMessage(content == null ? "" : content));
  }

  @Override
  public List<MemoryMessage> recent(String conversationId, int limit) {
    List<Message> messages = messages(conversationId);
    int fromIndex = Math.max(0, messages.size() - Math.max(0, limit));
    return messages.subList(fromIndex, messages.size()).stream()
        .map(message -> new MemoryMessage(message.getMessageType().name(), message.getText()))
        .toList();
  }

  @Override
  public void clear(String conversationId) {
    if (repository != null) {
      repository.deleteByConversationId(conversationId);
    } else {
      inMemory.remove(conversationId);
    }
  }

  private void append(String conversationId, Message message) {
    if (repository != null) {
      List<Message> messages = new ArrayList<>(repository.findByConversationId(conversationId));
      messages.add(message);
      repository.saveAll(conversationId, messages);
    } else {
      inMemory.computeIfAbsent(conversationId, ignored -> new ArrayList<>()).add(message);
    }
  }

  private List<Message> messages(String conversationId) {
    if (repository != null) {
      return repository.findByConversationId(conversationId);
    }
    return List.copyOf(inMemory.getOrDefault(conversationId, List.of()));
  }
}
