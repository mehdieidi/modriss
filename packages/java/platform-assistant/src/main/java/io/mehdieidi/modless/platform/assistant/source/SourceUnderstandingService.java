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
    List<SourceChunk> chunks = chunker.chunk(sourceName, content, maxTokens, maxChunks);
    List<SourceEvidenceGraph> graphs =
        chunks.stream().map(chunk -> extractor.extract(sourceId, chunk)).toList();
    return merger.merge(sourceId, graphs);
  }
}
