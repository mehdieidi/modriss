package io.mehdieidi.modless.platform.assistant.source;

import java.util.List;

/** Merged source evidence extracted from untrusted source material. */
public record SourceEvidenceGraph(
    String sourceId, List<SourceFact> facts, List<CoverageEntry> coverage, List<String> gaps) {

  public SourceEvidenceGraph {
    sourceId = sourceId == null ? "" : sourceId.trim();
    facts = facts == null ? List.of() : List.copyOf(facts);
    coverage = coverage == null ? List.of() : List.copyOf(coverage);
    gaps = gaps == null ? List.of() : List.copyOf(gaps);
  }

  /** One source-backed modeling fact. */
  public record SourceFact(
      String id, String chunkId, String kind, String summary, List<String> suggestedTypes) {
    public SourceFact {
      id = id == null ? "" : id.trim();
      chunkId = chunkId == null ? "" : chunkId.trim();
      kind = kind == null ? "" : kind.trim();
      summary = summary == null ? "" : summary.trim();
      suggestedTypes = suggestedTypes == null ? List.of() : List.copyOf(suggestedTypes);
    }
  }

  /** Coverage state for one chunk. */
  public record CoverageEntry(String chunkId, SourceChunk.CoverageState state, String note) {
    public CoverageEntry {
      chunkId = chunkId == null ? "" : chunkId.trim();
      state = state == null ? SourceChunk.CoverageState.COMPRESSED : state;
      note = note == null ? "" : note.trim();
    }
  }
}
