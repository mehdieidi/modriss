package io.mehdieidi.varka.platform.assistant.spi;

import java.util.List;

/** Recent conversational memory used to enrich provider prompts. */
public interface AssistantChatMemory {

  /**
   * Appends a user message.
   *
   * @param conversationId conversation ID
   * @param content message text
   */
  void appendUser(String conversationId, String content);

  /**
   * Appends an assistant message.
   *
   * @param conversationId conversation ID
   * @param content message text
   */
  void appendAssistant(String conversationId, String content);

  /**
   * Returns recent messages in oldest-first order.
   *
   * @param conversationId conversation ID
   * @param limit maximum messages
   * @return recent messages
   */
  List<MemoryMessage> recent(String conversationId, int limit);

  /**
   * Clears one conversation.
   *
   * @param conversationId conversation ID
   */
  void clear(String conversationId);

  /**
   * Lightweight conversation message projection.
   *
   * @param role message role
   * @param content message content
   */
  record MemoryMessage(String role, String content) {}
}
