package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiPropertiesTest {

    @Test
    void appliesConservativeDefaults() {
        AiProperties properties = new AiProperties(false, null, null, null, 0, 0, null, null,
                null, null, null);

        assertFalse(properties.enabled());
        assertEquals(AiProperties.RolloutMode.EXPLAIN_ONLY, properties.mode());
        assertEquals("groq", properties.provider());
        assertEquals("127.0.0.1", properties.proxy().host());
        assertEquals(2081, properties.proxy().port());
        assertEquals("https://api.groq.com/openai", properties.groq().baseUrl());
        assertEquals(30, properties.hardening().perUserRequestsPerWindow());
        assertEquals(AiProperties.EmbeddingProvider.ONNX, properties.embeddings().provider());
        assertTrue(properties.embeddings().fallbackToHash());
        assertEquals("openai/gpt-oss-120b",
                properties.models().forRole(AssistantModelRole.RESPONDER));
    }

    @Test
    void appliesSocksDefaultPort() {
        AiProperties.Proxy proxy = new AiProperties.Proxy(true,
                AiProperties.ProxyType.SOCKS, null, null, null);

        assertEquals(2082, proxy.port());
    }

    @Test
    void stripsVersionSegmentFromOpenAiCompatibleBaseUrl() {
        AiProperties.Groq groq = new AiProperties.Groq(
                "https://api.groq.com/openai/v1/", "key");

        assertEquals("https://api.groq.com/openai", groq.baseUrl());
    }
}
