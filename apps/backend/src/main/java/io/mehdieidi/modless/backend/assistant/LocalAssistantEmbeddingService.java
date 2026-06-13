package io.mehdieidi.modless.backend.assistant;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Local embedding service for PGvector retrieval.
 *
 * <p>ONNX mode uses Spring AI Transformers when the native runtime and tokenizer dependencies are
 * available. Hash mode keeps tests and constrained local environments deterministic.
 */
@Service
public class LocalAssistantEmbeddingService {

  static final int DIMENSIONS = 384;

  private final AiProperties.Embeddings properties;
  private volatile Object onnxModel;
  private volatile boolean onnxUnavailable;

  public LocalAssistantEmbeddingService() {
    this(
        new AiProperties.Embeddings(
            AiProperties.EmbeddingProvider.HASH, null, null, null, null, false, -1, true));
  }

  public LocalAssistantEmbeddingService(AiProperties properties) {
    this(properties == null ? null : properties.embeddings());
  }

  LocalAssistantEmbeddingService(AiProperties.Embeddings properties) {
    this.properties =
        properties == null
            ? new AiProperties.Embeddings(
                AiProperties.EmbeddingProvider.ONNX, null, null, null, null, false, -1, true)
            : properties;
  }

  /**
   * Embeds text as a PGvector literal.
   *
   * @param text text to embed
   * @return vector literal
   */
  public String vectorLiteral(String text) {
    float[] vector = embed(text);
    StringBuilder builder = new StringBuilder("[");
    for (int index = 0; index < vector.length; index++) {
      if (index > 0) {
        builder.append(',');
      }
      builder.append(vector[index]);
    }
    return builder.append(']').toString();
  }

  /**
   * Embeds text as a normalized vector.
   *
   * @param text text to embed
   * @return normalized vector
   */
  public float[] embed(String text) {
    if (properties.provider() == AiProperties.EmbeddingProvider.ONNX && !onnxUnavailable) {
      try {
        return onnxEmbed(text);
      } catch (RuntimeException ex) {
        onnxUnavailable = true;
        if (!properties.fallbackToHash()) {
          throw ex;
        }
      }
    }
    return hashEmbed(text);
  }

  private float[] onnxEmbed(String text) {
    try {
      Object model = onnxModel();
      Method embed = model.getClass().getMethod("embed", String.class);
      return (float[]) embed.invoke(model, text == null ? "" : text);
    } catch (Exception | LinkageError ex) {
      throw new IllegalStateException("Spring AI ONNX embeddings are unavailable.", ex);
    }
  }

  private Object onnxModel() throws Exception {
    Object current = onnxModel;
    if (current != null) {
      return current;
    }
    synchronized (this) {
      if (onnxModel != null) {
        return onnxModel;
      }
      Class<?> modelClass =
          Class.forName("org.springframework.ai.transformers.TransformersEmbeddingModel");
      Object model = modelClass.getConstructor().newInstance();
      invokeSetter(model, "setModelResource", properties.modelResource());
      invokeSetter(model, "setTokenizerResource", properties.tokenizerResource());
      invokeSetter(model, "setModelOutputName", properties.modelOutputName());
      invokeSetter(model, "setResourceCacheDirectory", properties.cacheDirectory());
      modelClass
          .getMethod("setDisableCaching", boolean.class)
          .invoke(model, properties.disableCaching());
      if (properties.gpuDeviceId() >= 0) {
        modelClass.getMethod("setGpuDeviceId", int.class).invoke(model, properties.gpuDeviceId());
      }
      modelClass.getMethod("afterPropertiesSet").invoke(model);
      onnxModel = model;
      return model;
    }
  }

  private void invokeSetter(Object target, String methodName, String value) throws Exception {
    if (value != null && !value.isBlank()) {
      target.getClass().getMethod(methodName, String.class).invoke(target, value);
    }
  }

  private float[] hashEmbed(String text) {
    float[] vector = new float[DIMENSIONS];
    String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
    for (String token : normalized.split("[^a-z0-9_]+")) {
      if (token.length() < 2) {
        continue;
      }
      int hash = hash(token);
      int index = Math.floorMod(hash, DIMENSIONS);
      vector[index] += (hash & 1) == 0 ? 1.0f : -1.0f;
    }
    normalize(vector);
    return vector;
  }

  private int hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return java.nio.ByteBuffer.wrap(digest).getInt();
    } catch (Exception ex) {
      return token.hashCode();
    }
  }

  private void normalize(float[] vector) {
    double sum = 0.0;
    for (float value : vector) {
      sum += value * value;
    }
    if (sum == 0.0) {
      return;
    }
    float norm = (float) Math.sqrt(sum);
    for (int index = 0; index < vector.length; index++) {
      vector[index] = vector[index] / norm;
    }
  }
}
