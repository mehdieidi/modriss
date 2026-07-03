package io.mehdieidi.modless.platform.assistant.application;

import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.assistant.domain.AssistantWorkflowState;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.MessageRecord;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.spi.AssistantChatMemory;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMemoryStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSettings;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

/** Durable assistant memory operations and non-blocking rolling summary maintenance. */
public class AssistantMemoryService {

  private final AssistantMemoryStore memory;
  private final AssistantChatMemory chatMemory;
  private final AssistantSettings settings;
  private final AssistantModelProvider provider;
  private final Executor summaryExecutor;

  public AssistantMemoryService(
      AssistantMemoryStore memory,
      AssistantChatMemory chatMemory,
      AssistantSettings settings,
      AssistantModelProvider provider) {
    this(memory, chatMemory, settings, provider, ForkJoinPool.commonPool());
  }

  AssistantMemoryService(
      AssistantMemoryStore memory,
      AssistantChatMemory chatMemory,
      AssistantSettings settings,
      AssistantModelProvider provider,
      Executor summaryExecutor) {
    this.memory = memory;
    this.chatMemory = chatMemory;
    this.settings = settings;
    this.provider = provider;
    this.summaryExecutor = summaryExecutor == null ? Runnable::run : summaryExecutor;
  }

  public void appendAssistantAndRefreshSummary(
      String threadId, String message, AssistantWorkflowState workflowState) {
    memory.appendMessage(
        threadId,
        "ASSISTANT",
        message,
        Map.of("workflowState", workflowState == null ? "" : workflowState.name()));
    chatMemory.appendAssistant(threadId, message);
    summaryExecutor.execute(() -> refreshRollingSummary(threadId));
  }

  void refreshRollingSummary(String threadId) {
    List<MessageRecord> recent =
        memory.recentMessages(threadId, settings.hardening().recentMessageWindow());
    if (recent.isEmpty()) {
      return;
    }
    String conversation =
        recent.stream()
            .sorted(java.util.Comparator.comparing(MessageRecord::createdAt))
            .map(message -> message.role() + ": " + message.content())
            .collect(Collectors.joining("\n"));
    String previousSummary = memory.summary(threadId).orElse("");
    String summary = summarizeConversation(previousSummary, conversation);
    memory.updateSummary(threadId, summary, recent.get(0).id());
  }

  private String summarizeConversation(String previousSummary, String conversation) {
    if (!settings.enabled() || provider == null || !provider.available()) {
      return tailTruncate(conversation, 3000);
    }
    try {
      String prompt =
          """
          Compress this modeling assistant thread into a concise rolling summary. Preserve the
          domain name, modeling scope, key decisions, and any pending work. Do not invent facts.

          Previous summary:
          """
              + (previousSummary == null || previousSummary.isBlank() ? "(none)" : previousSummary)
              + "\n\nRecent messages:\n"
              + tailTruncate(conversation, 6000);
      AssistantModelProvider.AssistantReply reply =
          provider.complete(
              new AssistantModelProvider.AssistantPrompt(
                  AssistantModelRole.SUMMARIZER,
                  "You summarize modeling conversations for later turns.",
                  prompt,
                  List.of()));
      String content = reply.content() == null ? "" : reply.content().trim();
      if (content.isBlank()) {
        return tailTruncate(conversation, 3000);
      }
      return content.length() > 3000 ? content.substring(0, 3000) : content;
    } catch (RuntimeException ex) {
      return tailTruncate(conversation, 3000);
    }
  }

  private String tailTruncate(String value, int maxChars) {
    if (value == null || value.length() <= maxChars) {
      return value == null ? "" : value;
    }
    return value.substring(value.length() - maxChars);
  }
}
