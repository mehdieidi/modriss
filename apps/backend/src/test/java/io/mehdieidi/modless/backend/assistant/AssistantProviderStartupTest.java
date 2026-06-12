package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AssistantProviderStartupTest {

    @Test
    void providerBeansInitializeWithoutConfiguredApiKeys() {
        AiProperties properties = new AiProperties(false, null, null, null, 0, 0, null, null,
                new AiProperties.Proxy(false, AiProperties.ProxyType.DIRECT, null, null, null),
                null, null, null);
        ProxyAvailability proxyAvailability = new ProxyAvailability(properties);
        AssistantPromptGuard promptGuard = new AssistantPromptGuard();
        AssistantToolService tools = new AssistantToolService(mock(AssistantCatalogService.class),
                new AssistantPatchCompiler(), new ObjectMapper());
        AssistantHardeningService hardening = new AssistantHardeningService(properties, null);

        assertDoesNotThrow(() -> new OpenAiCompatibleAssistantModelProvider(properties,
                proxyAvailability, promptGuard, tools, hardening, RestClient.builder()));
        assertDoesNotThrow(() -> new GeminiAssistantModelProvider(properties, proxyAvailability,
                promptGuard, tools, hardening));
    }

    @Test
    void geminiProviderUsesConfiguredRoleDefaultAtStartup() {
        AiProperties properties = new AiProperties(false, null, "gemini", Duration.ofSeconds(5),
                0, 0, null, null,
                new AiProperties.Proxy(false, AiProperties.ProxyType.DIRECT, null, null, null),
                null, new AiProperties.Gemini(""), new AiProperties.Models(null,
                "gemini-2.0-flash", null));
        ProxyAvailability proxyAvailability = new ProxyAvailability(properties);
        AssistantToolService tools = new AssistantToolService(mock(AssistantCatalogService.class),
                new AssistantPatchCompiler(), new ObjectMapper());

        assertDoesNotThrow(() -> new GeminiAssistantModelProvider(properties, proxyAvailability,
                new AssistantPromptGuard(), tools,
                new AssistantHardeningService(properties, null)));
    }

    @Test
    void geminiProviderDoesNotRegisterSpringAiTools() {
        AiProperties properties = new AiProperties(false, null, "gemini", Duration.ofSeconds(5),
                0, 0, null, null,
                new AiProperties.Proxy(false, AiProperties.ProxyType.DIRECT, null, null, null),
                null, new AiProperties.Gemini(""), null);
        AssistantToolService tools = new AssistantToolService(mock(AssistantCatalogService.class),
                new AssistantPatchCompiler(), new ObjectMapper());
        GeminiAssistantModelProvider gemini = new GeminiAssistantModelProvider(properties,
                new ProxyAvailability(properties), new AssistantPromptGuard(), tools,
                new AssistantHardeningService(properties, null));

        assertFalse(gemini.registerTools(AssistantModelRole.RESPONDER));
        assertFalse(gemini.registerTools(AssistantModelRole.PLANNER));
    }

    @Test
    void openAiCompatibleProviderRegistersSpringAiTools() {
        AiProperties properties = new AiProperties(false, null, "openai", Duration.ofSeconds(5),
                0, 0, null, null,
                new AiProperties.Proxy(false, AiProperties.ProxyType.DIRECT, null, null, null),
                null, null, null);
        AssistantToolService tools = new AssistantToolService(mock(AssistantCatalogService.class),
                new AssistantPatchCompiler(), new ObjectMapper());
        OpenAiCompatibleAssistantModelProvider openai =
                new OpenAiCompatibleAssistantModelProvider(properties,
                        new ProxyAvailability(properties), new AssistantPromptGuard(), tools,
                        new AssistantHardeningService(properties, null), RestClient.builder());

        assertTrue(openai.registerTools(AssistantModelRole.RESPONDER));
        assertTrue(openai.registerTools(AssistantModelRole.PLANNER));
    }
}
