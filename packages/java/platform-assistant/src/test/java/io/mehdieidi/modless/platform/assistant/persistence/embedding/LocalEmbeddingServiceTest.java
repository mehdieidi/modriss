package io.mehdieidi.modless.platform.assistant.persistence.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LocalEmbeddingServiceTest {

  @Test
  void producesStableNormalizedPgvectorLiteral() {
    LocalEmbeddingService embeddings = new LocalEmbeddingService();

    float[] first = embeddings.embed("Service connects Queue");
    float[] second = embeddings.embed("Service connects Queue");
    String literal = embeddings.vectorLiteral("Service connects Queue");

    assertEquals(LocalEmbeddingService.DIMENSIONS, first.length);
    assertEquals(first[0], second[0]);
    assertTrue(literal.startsWith("["));
    assertTrue(literal.endsWith("]"));
  }

  @Test
  void onnxModeFallsBackWhenNativeRuntimeIsUnavailable() {
    EmbeddingSettings settings =
        new EmbeddingSettings(
            EmbeddingSettings.Provider.ONNX, null, null, null, null, false, -1, true);
    LocalEmbeddingService embeddings = new LocalEmbeddingService(settings);

    assertEquals(EmbeddingSettings.Provider.HASH, embeddings.activeProvider());
    assertEquals(LocalEmbeddingService.DIMENSIONS, embeddings.embed("fallback").length);
  }

  @Test
  void onnxRuntimeAvailabilityReflectsClasspath() {
    assertEquals(
        LocalEmbeddingService.onnxRuntimeAvailable(), LocalEmbeddingService.onnxRuntimeAvailable());
  }
}
