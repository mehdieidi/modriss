package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LocalAssistantEmbeddingServiceTest {

  @Test
  void producesStableNormalizedPgvectorLiteral() {
    LocalAssistantEmbeddingService embeddings = new LocalAssistantEmbeddingService();

    float[] first = embeddings.embed("Service connects Queue");
    float[] second = embeddings.embed("Service connects Queue");
    String literal = embeddings.vectorLiteral("Service connects Queue");

    assertEquals(LocalAssistantEmbeddingService.DIMENSIONS, first.length);
    assertEquals(first[0], second[0]);
    assertTrue(literal.startsWith("["));
    assertTrue(literal.endsWith("]"));
  }

  @Test
  void onnxModeFallsBackWhenNativeRuntimeIsUnavailable() {
    AiProperties properties =
        new AiProperties(
            false,
            null,
            null,
            null,
            0,
            0,
            0,
            null,
            new AiProperties.Embeddings(
                AiProperties.EmbeddingProvider.ONNX, null, null, null, null, false, -1, true),
            null,
            null,
            null,
            null);
    LocalAssistantEmbeddingService embeddings = new LocalAssistantEmbeddingService(properties);

    assertEquals(LocalAssistantEmbeddingService.DIMENSIONS, embeddings.embed("fallback").length);
  }
}
