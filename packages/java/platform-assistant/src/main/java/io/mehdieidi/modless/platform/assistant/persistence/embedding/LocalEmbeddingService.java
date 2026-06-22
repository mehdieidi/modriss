package io.mehdieidi.modless.platform.assistant.persistence.embedding;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local embedding service for PGvector retrieval.
 *
 * <p>ONNX mode uses Spring AI Transformers when the native runtime and tokenizer dependencies are
 * available. Hash mode keeps tests and constrained local environments deterministic.
 */
public class LocalEmbeddingService {

  static final int DIMENSIONS = 384;

  private static final Logger log = LoggerFactory.getLogger(LocalEmbeddingService.class);

  private final EmbeddingSettings settings;
  private volatile Object onnxModel;
  private volatile boolean onnxUnavailable;

  public LocalEmbeddingService() {
    this(
        new EmbeddingSettings(
            EmbeddingSettings.Provider.HASH, null, null, null, null, false, -1, true));
  }

  public LocalEmbeddingService(EmbeddingSettings settings) {
    this.settings =
        settings == null
            ? new EmbeddingSettings(
                EmbeddingSettings.Provider.ONNX, null, null, null, null, false, -1, true)
            : settings;
    if (this.settings.provider() == EmbeddingSettings.Provider.ONNX && !onnxRuntimeAvailable()) {
      onnxUnavailable = true;
    }
  }

  /**
   * Returns whether the ONNX runtime and Spring AI transformers classes are loadable.
   *
   * @return true when ONNX embeddings can be attempted
   */
  public static boolean onnxRuntimeAvailable() {
    try {
      Class.forName("ai.onnxruntime.OrtException");
      Class.forName("org.springframework.ai.transformers.TransformersEmbeddingModel");
      return true;
    } catch (ClassNotFoundException | LinkageError ex) {
      return false;
    }
  }

  /**
   * Returns the embedding provider actively serving requests.
   *
   * @return ONNX when the runtime model is active, otherwise HASH
   */
  public EmbeddingSettings.Provider activeProvider() {
    if (settings.provider() == EmbeddingSettings.Provider.ONNX && !onnxUnavailable) {
      return EmbeddingSettings.Provider.ONNX;
    }
    return EmbeddingSettings.Provider.HASH;
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
    if (settings.provider() == EmbeddingSettings.Provider.ONNX && !onnxUnavailable) {
      try {
        return onnxEmbed(text);
      } catch (RuntimeException ex) {
        onnxUnavailable = true;
        if (!settings.fallbackToHash()) {
          throw ex;
        }
        log.warn(
            "ONNX embeddings failed; falling back to deterministic hash vectors for retrieval.",
            ex);
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
      invokeSetter(model, "setModelResource", settings.modelResource());
      invokeSetter(model, "setTokenizerResource", settings.tokenizerResource());
      invokeSetter(model, "setModelOutputName", settings.modelOutputName());
      invokeSetter(model, "setResourceCacheDirectory", settings.cacheDirectory());
      modelClass
          .getMethod("setDisableCaching", boolean.class)
          .invoke(model, settings.disableCaching());
      if (settings.gpuDeviceId() >= 0) {
        modelClass.getMethod("setGpuDeviceId", int.class).invoke(model, settings.gpuDeviceId());
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
