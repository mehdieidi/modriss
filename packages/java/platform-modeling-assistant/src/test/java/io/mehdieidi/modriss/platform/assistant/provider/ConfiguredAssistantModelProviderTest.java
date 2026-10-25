package io.mehdieidi.modriss.platform.assistant.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mehdieidi.modriss.platform.assistant.config.AiProperties;
import io.mehdieidi.modriss.platform.assistant.provider.springai.GeminiAssistantModelProvider;
import io.mehdieidi.modriss.platform.assistant.provider.springai.OpenAiCompatibleAssistantModelProvider;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import org.junit.jupiter.api.Test;

class ConfiguredAssistantModelProviderTest {

  @Test
  void conversationsReachTheConfiguredTextTransportWithoutToolOrStructuredCalls() {
    var properties = mock(AiProperties.class);
    when(properties.providerKind()).thenReturn(AiProperties.Provider.OPENAI);
    var openai = mock(OpenAiCompatibleAssistantModelProvider.class);
    var gemini = mock(GeminiAssistantModelProvider.class);
    var prompt = new AssistantModelProvider.AssistantPrompt("Assess", "Project", null);
    var reply =
        new AssistantModelProvider.AssistantReply("What latency is required?", "openai", "test");
    when(openai.completeText(prompt)).thenReturn(reply);
    var configured = new ConfiguredAssistantModelProvider(properties, openai, gemini);
    assertEquals(reply, configured.completeText(prompt));
    verify(openai, never()).completeStructured(any());
    verify(openai, never()).completeWithTools(any());
    org.mockito.Mockito.verifyNoInteractions(gemini);
  }

  @Test
  void neverFallsThroughToAnUnselectedProvider() {
    AiProperties properties =
        new AiProperties(
            true, null, "openai", null, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, null, null, null,
            null, null, null, 0, 0, 0, 0, true, 0, 0, 0, 0, 0, 0, 0, true, true, false, null, 0,
            true, true);
    OpenAiCompatibleAssistantModelProvider openai =
        mock(OpenAiCompatibleAssistantModelProvider.class);
    GeminiAssistantModelProvider gemini = mock(GeminiAssistantModelProvider.class);
    when(openai.available()).thenReturn(true);
    when(openai.complete(any())).thenThrow(new PlatformException(503, "OpenAI unavailable"));
    ConfiguredAssistantModelProvider provider =
        new ConfiguredAssistantModelProvider(properties, openai, gemini);

    PlatformException failure =
        assertThrows(
            PlatformException.class,
            () -> provider.complete(new AssistantModelProvider.AssistantPrompt("", "", null)));

    assertEquals("OpenAI unavailable", failure.getMessage());
    verify(gemini, never()).complete(any());
  }
}
