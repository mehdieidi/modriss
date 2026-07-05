package io.mehdieidi.modless.platform.assistant.source;

import java.util.List;

/** Source-understanding boundary for untrusted attachment text. */
public class SourceUnderstandingService {

  private final SourceChunker chunker;
  private final SourceEvidenceExtractor extractor;
  private final SourceEvidenceMerger merger;

  public SourceUnderstandingService(SourceChunker chunker) {
    this(chunker, new SourceEvidenceExtractor(), new SourceEvidenceMerger());
  }

  public SourceUnderstandingService(
      SourceChunker chunker, SourceEvidenceExtractor extractor, SourceEvidenceMerger merger) {
    this.chunker = chunker == null ? new SourceChunker() : chunker;
    this.extractor = extractor == null ? new SourceEvidenceExtractor() : extractor;
    this.merger = merger == null ? new SourceEvidenceMerger() : merger;
  }

  /**
   * Builds a conservative evidence graph. LLM extraction/merge plugs in behind this boundary; this
   * fallback never treats source text as executable instructions.
   */
  public SourceEvidenceGraph understand(
      String sourceId, String sourceName, String content, int maxTokens, int maxChunks) {
    return understand(sourceId, sourceName, content, maxTokens, maxChunks, null);
  }

  /** Builds an evidence graph with optional per-chunk progress callbacks for realtime coverage. */
  public SourceEvidenceGraph understand(
      String sourceId,
      String sourceName,
      String content,
      int maxTokens,
      int maxChunks,
      SourceChunkProgressListener listener) {
    List<SourceChunk> chunks = chunker.chunk(sourceName, content, maxTokens, maxChunks);
    if (chunks.isEmpty()) {
      return new SourceEvidenceGraph(sourceId, List.of(), List.of(), List.of());
    }
    List<SourceEvidenceGraph> graphs = new java.util.ArrayList<>();
    for (int index = 0; index < chunks.size(); index++) {
      SourceChunk chunk = chunks.get(index);
      if (listener != null) {
        listener.onChunkStarting(index + 1, chunks.size(), chunk);
      }
      graphs.add(extractor.extract(sourceId, chunk));
      if (listener != null) {
        listener.onChunkProcessed(index + 1, chunks.size(), merger.merge(sourceId, graphs));
      }
    }
    return merger.merge(sourceId, graphs);
  }
}
