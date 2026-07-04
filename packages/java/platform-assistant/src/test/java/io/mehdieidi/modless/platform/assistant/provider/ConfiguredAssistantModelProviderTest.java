package io.mehdieidi.modless.platform.assistant.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mehdieidi.modless.platform.assistant.config.AiProperties;
import io.mehdieidi.modless.platform.assistant.provider.springai.GeminiAssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.provider.springai.OpenAiCompatibleAssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import org.junit.jupiter.api.Test;

class ConfiguredAssistantModelProviderTest {

  @Test
  void neverFallsThroughToAnUnselectedProvider() {
    AiProperties properties =
        new AiProperties(
            true, "openai", null, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, null, null, null, null,
            null, null, 0, 0, 0, 0, true);
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
            () ->
                provider.complete(new AssistantModelProvider.AssistantPrompt(null, "", "", null)));

    assertEquals("OpenAI unavailable", failure.getMessage());
    verify(gemini, never()).complete(any());
  }
}
