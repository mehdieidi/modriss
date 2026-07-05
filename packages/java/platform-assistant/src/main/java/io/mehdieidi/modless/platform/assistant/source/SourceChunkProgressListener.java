package io.mehdieidi.modless.platform.assistant.source;

/** Callback for per-chunk source evidence extraction progress. */
public interface SourceChunkProgressListener {

  /**
   * Invoked immediately before provider extraction starts for a chunk.
   *
   * @param chunkIndex one-based chunk index about to be processed
   * @param totalChunks total chunks in the source document
   * @param chunk chunk metadata and content
   */
  default void onChunkStarting(int chunkIndex, int totalChunks, SourceChunk chunk) {}

  /**
   * Invoked after each chunk is extracted and merged into the running graph.
   *
   * @param chunkIndex one-based index of the chunk just processed
   * @param totalChunks total chunks in the source document
   * @param partialGraph merged graph including all chunks processed so far
   */
  void onChunkProcessed(int chunkIndex, int totalChunks, SourceEvidenceGraph partialGraph);
}
