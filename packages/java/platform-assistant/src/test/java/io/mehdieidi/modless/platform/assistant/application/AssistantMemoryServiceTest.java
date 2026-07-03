package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mehdieidi.modless.platform.assistant.domain.AssistantWorkflowState;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.MessageRecord;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.spi.AssistantChatMemory;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMemoryStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.modless.platform.assistant.support.AssistantSettingsFixtures;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssistantMemoryServiceTest {

  private final AssistantMemoryStore memory = mock(AssistantMemoryStore.class);
  private final AssistantChatMemory chatMemory = mock(AssistantChatMemory.class);
  private final AssistantModelProvider provider = mock(AssistantModelProvider.class);
  private final AssistantSettings settings = AssistantSettingsFixtures.defaults();

  @Test
  void appendsAssistantMessageImmediatelyAndRefreshesSummaryViaExecutor() {
    when(provider.available()).thenReturn(true);
    when(provider.complete(any()))
        .thenReturn(new AssistantModelProvider.AssistantReply("Concise summary", "test", "model"));
    when(memory.recentMessages(eq("thread"), anyInt()))
        .thenReturn(
            List.of(
                new MessageRecord(
                    "m1",
                    "thread",
                    "USER",
                    "Create an order model",
                    Map.of(),
                    Instant.parse("2026-07-03T10:00:00Z")),
                new MessageRecord(
                    "m2",
                    "thread",
                    "ASSISTANT",
                    "Created it",
                    Map.of(),
                    Instant.parse("2026-07-03T10:00:01Z"))));
    when(memory.summary("thread")).thenReturn(Optional.of("Previous"));

    AssistantMemoryService service =
        new AssistantMemoryService(memory, chatMemory, settings, provider, Runnable::run);

    service.appendAssistantAndRefreshSummary(
        "thread", "Created it", AssistantWorkflowState.APPLIED);

    verify(memory).appendMessage(eq("thread"), eq("ASSISTANT"), eq("Created it"), anyMap());
    verify(chatMemory).appendAssistant("thread", "Created it");
    verify(memory).updateSummary("thread", "Concise summary", "m1");
  }

  @Test
  void fallsBackToTailSummaryWhenProviderUnavailable() {
    when(provider.available()).thenReturn(false);
    when(memory.recentMessages(eq("thread"), anyInt()))
        .thenReturn(
            List.of(
                new MessageRecord(
                    "m1", "thread", "USER", "Short conversation", Map.of(), Instant.now())));
    when(memory.summary("thread")).thenReturn(Optional.empty());
    AssistantMemoryService service =
        new AssistantMemoryService(memory, chatMemory, settings, provider, Runnable::run);

    service.appendAssistantAndRefreshSummary("thread", "Answer", AssistantWorkflowState.EXPLAINED);

    verify(memory).updateSummary("thread", "USER: Short conversation", "m1");
    assertEquals(false, provider.available());
  }
}
