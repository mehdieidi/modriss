package io.mehdieidi.modless.platform.assistant.persistence.embedding;

/** Local embedding runtime settings for catalog vector indexing. */
public record EmbeddingSettings(
    Provider provider,
    String modelResource,
    String tokenizerResource,
    String modelOutputName,
    String cacheDirectory,
    boolean disableCaching,
    int gpuDeviceId,
    boolean fallbackToHash) {

  public EmbeddingSettings {
    provider = provider == null ? Provider.ONNX : provider;
    modelResource = modelResource == null ? "" : modelResource.trim();
    tokenizerResource = tokenizerResource == null ? "" : tokenizerResource.trim();
    modelOutputName = modelOutputName == null ? "" : modelOutputName.trim();
    cacheDirectory = cacheDirectory == null ? "" : cacheDirectory.trim();
  }

  /** Embedding provider mode. */
  public enum Provider {
    ONNX,
    HASH
  }
}
