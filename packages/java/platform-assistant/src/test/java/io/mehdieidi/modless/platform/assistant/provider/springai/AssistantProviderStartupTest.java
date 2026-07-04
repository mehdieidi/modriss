package io.mehdieidi.modless.platform.assistant.provider.springai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.application.AssistantHardeningService;
import io.mehdieidi.modless.platform.assistant.application.AssistantPromptGuard;
import io.mehdieidi.modless.platform.assistant.config.AiProperties;
import io.mehdieidi.modless.platform.assistant.delta.DeltaCompiler;
import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.provider.ProxyAvailability;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.assistant.tools.AssistantToolService;
import io.mehdieidi.modless.platform.model.application.ModelService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AssistantProviderStartupTest {

  @Test
  void providerBeansInitializeWithoutConfiguredApiKeys() {
    AiProperties properties =
        new AiProperties(
            false,
            null,
            null,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            null,
            null,
            null,
            new AiProperties.Proxy(false, AiProperties.ProxyType.DIRECT, null, null, null),
            null,
            null,
            null,
            null,
            0,
            0,
            0,
            0,
            true,
            true);
    ProxyAvailability proxyAvailability = new ProxyAvailability(properties);
    AssistantPromptGuard promptGuard = new AssistantPromptGuard(properties);
    AssistantToolService tools =
        new AssistantToolService(
            mock(AssistantCatalog.class),
            new AssistantPatchCompiler(),
            new DeltaCompiler(new AssistantMetamodelSchemaService()),
            new AssistantMetamodelSchemaService(),
            mock(ModelService.class),
            new ObjectMapper());
    AssistantHardeningService hardening = new AssistantHardeningService(properties, null);

    assertDoesNotThrow(
        () ->
            new OpenAiCompatibleAssistantModelProvider(
                properties,
                proxyAvailability,
                promptGuard,
                tools,
                hardening,
                RestClient.builder()));
    assertDoesNotThrow(
        () ->
            new GeminiAssistantModelProvider(
                properties, proxyAvailability, promptGuard, tools, hardening));
  }

  @Test
  void geminiProviderUsesConfiguredRoleDefaultAtStartup() {
    AiProperties properties =
        new AiProperties(
            false,
            "gemini",
            Duration.ofSeconds(5),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            null,
            null,
            null,
            new AiProperties.Proxy(false, AiProperties.ProxyType.DIRECT, null, null, null),
            null,
            new AiProperties.Gemini(""),
            new AiProperties.Models(null, "gemini-2.0-flash", null),
            null,
            0,
            0,
            0,
            0,
            true,
            true);
    ProxyAvailability proxyAvailability = new ProxyAvailability(properties);
    AssistantToolService tools =
        new AssistantToolService(
            mock(AssistantCatalog.class),
            new AssistantPatchCompiler(),
            new DeltaCompiler(new AssistantMetamodelSchemaService()),
            new AssistantMetamodelSchemaService(),
            mock(ModelService.class),
            new ObjectMapper());

    assertDoesNotThrow(
        () ->
            new GeminiAssistantModelProvider(
                properties,
                proxyAvailability,
                new AssistantPromptGuard(properties),
                tools,
                new AssistantHardeningService(properties, null)));
  }

  @Test
  void geminiProviderDoesNotRegisterSpringAiTools() {
    AiProperties properties =
        new AiProperties(
            false,
            "gemini",
            Duration.ofSeconds(5),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            null,
            null,
            null,
            new AiProperties.Proxy(false, AiProperties.ProxyType.DIRECT, null, null, null),
            null,
            new AiProperties.Gemini(""),
            null,
            null,
            0,
            0,
            0,
            0,
            true,
            true);
    AssistantToolService tools =
        new AssistantToolService(
            mock(AssistantCatalog.class),
            new AssistantPatchCompiler(),
            new DeltaCompiler(new AssistantMetamodelSchemaService()),
            new AssistantMetamodelSchemaService(),
            mock(ModelService.class),
            new ObjectMapper());
    GeminiAssistantModelProvider gemini =
        new GeminiAssistantModelProvider(
            properties,
            new ProxyAvailability(properties),
            new AssistantPromptGuard(properties),
            tools,
            new AssistantHardeningService(properties, null));

    assertFalse(gemini.registerTools(AssistantModelRole.RESPONDER));
    assertFalse(gemini.registerTools(AssistantModelRole.PLANNER));
  }

  @Test
  void openAiCompatibleProviderRegistersToolsForResponderButNotStructuredPlanner() {
    AiProperties properties =
        new AiProperties(
            false,
            "openai",
            Duration.ofSeconds(5),
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            null,
            null,
            null,
            new AiProperties.Proxy(false, AiProperties.ProxyType.DIRECT, null, null, null),
            null,
            null,
            null,
            null,
            0,
            0,
            0,
            0,
            true,
            true);
    AssistantToolService tools =
        new AssistantToolService(
            mock(AssistantCatalog.class),
            new AssistantPatchCompiler(),
            new DeltaCompiler(new AssistantMetamodelSchemaService()),
            new AssistantMetamodelSchemaService(),
            mock(ModelService.class),
            new ObjectMapper());
    OpenAiCompatibleAssistantModelProvider openai =
        new OpenAiCompatibleAssistantModelProvider(
            properties,
            new ProxyAvailability(properties),
            new AssistantPromptGuard(properties),
            tools,
            new AssistantHardeningService(properties, null),
            RestClient.builder());

    assertTrue(openai.registerTools(AssistantModelRole.RESPONDER));
    assertFalse(openai.registerTools(AssistantModelRole.PLANNER));
  }
}
