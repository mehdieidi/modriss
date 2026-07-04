package io.mehdieidi.modless.platform.assistant.source;

/** Callback for per-chunk source evidence extraction progress. */
@FunctionalInterface
public interface SourceChunkProgressListener {

  /**
   * Invoked after each chunk is extracted and merged into the running graph.
   *
   * @param chunkIndex one-based index of the chunk just processed
   * @param totalChunks total chunks in the source document
   * @param partialGraph merged graph including all chunks processed so far
   */
  void onChunkProcessed(int chunkIndex, int totalChunks, SourceEvidenceGraph partialGraph);
}
