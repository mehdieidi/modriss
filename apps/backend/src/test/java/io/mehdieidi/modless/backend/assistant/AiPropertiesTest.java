package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiPropertiesTest {

  @Test
  void appliesConservativeDefaults() {
    AiProperties properties =
        new AiProperties(false, null, null, null, 0, 0, null, null, null, null, null, null);

    assertFalse(properties.enabled());
    assertEquals(AiProperties.RolloutMode.EXPLAIN_ONLY, properties.mode());
    assertEquals("openai", properties.provider());
    assertEquals("127.0.0.1", properties.proxy().host());
    assertEquals(2081, properties.proxy().port());
    assertEquals("https://api.openai.com", properties.openaiCompatible().baseUrl());
    assertEquals("", properties.gemini().apiKey());
    assertEquals(30, properties.hardening().perUserRequestsPerWindow());
    assertEquals(AiProperties.EmbeddingProvider.ONNX, properties.embeddings().provider());
    assertTrue(properties.embeddings().fallbackToHash());
    assertEquals("gpt-4o-mini", properties.modelFor(AssistantModelRole.RESPONDER));
  }

  @Test
  void appliesSocksDefaultPort() {
    AiProperties.Proxy proxy =
        new AiProperties.Proxy(true, AiProperties.ProxyType.SOCKS, null, null, null);

    assertEquals(2082, proxy.port());
  }

  @Test
  void stripsVersionSegmentFromOpenAiCompatibleBaseUrl() {
    AiProperties.OpenAiCompatible openai =
        new AiProperties.OpenAiCompatible("https://example.test/openai/v1/", "key");

    assertEquals("https://example.test/openai", openai.baseUrl());
  }

  @Test
  void appliesGeminiProviderDefaultModel() {
    AiProperties properties =
        new AiProperties(
            false,
            null,
            "gemini",
            null,
            0,
            0,
            null,
            null,
            null,
            null,
            new AiProperties.Gemini("key"),
            null);

    assertEquals(AiProperties.Provider.GEMINI, properties.providerKind());
    assertEquals("gemini-2.0-flash", properties.modelFor(AssistantModelRole.RESPONDER));
    assertEquals("key", properties.gemini().apiKey());
  }
}
