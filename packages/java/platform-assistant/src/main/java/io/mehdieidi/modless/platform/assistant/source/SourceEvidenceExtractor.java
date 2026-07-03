package io.mehdieidi.modless.platform.assistant.source;

import java.util.ArrayList;
import java.util.List;

/** Extracts source facts from one untrusted chunk without executing source instructions. */
public class SourceEvidenceExtractor {

  /**
   * Conservative local fallback extraction. Provider-native extraction can replace this boundary.
   */
  public SourceEvidenceGraph extract(String sourceId, SourceChunk chunk) {
    if (chunk == null || chunk.content().isBlank()) {
      return new SourceEvidenceGraph(sourceId, List.of(), List.of(), List.of());
    }
    List<SourceEvidenceGraph.SourceFact> facts = new ArrayList<>();
    if (instructionLike(chunk.content())) {
      facts.add(
          new SourceEvidenceGraph.SourceFact(
              chunk.id() + "-instruction",
              chunk.id(),
              "IGNORED_INSTRUCTION",
              compact(chunk.content()),
              List.of()));
    }
    facts.add(
        new SourceEvidenceGraph.SourceFact(
            chunk.id() + "-note", chunk.id(), "SOURCE_NOTE", compact(chunk.content()), List.of()));
    return new SourceEvidenceGraph(
        sourceId,
        facts,
        List.of(
            new SourceEvidenceGraph.CoverageEntry(
                chunk.id(), SourceChunk.CoverageState.COMPRESSED, "Compressed for modeling")),
        List.of());
  }

  private boolean instructionLike(String content) {
    String lower = content == null ? "" : content.toLowerCase(java.util.Locale.ROOT);
    return lower.contains("ignore all prior instructions")
        || lower.contains("ignore previous instructions")
        || lower.contains("system prompt")
        || lower.contains("developer message");
  }

  private String compact(String value) {
    String compact = value == null ? "" : value.trim().replaceAll("\\s+", " ");
    return compact.length() <= 800 ? compact : compact.substring(0, 800);
  }
}
