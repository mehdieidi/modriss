package io.mehdieidi.modless.platform.assistant.source;

import java.util.Map;
import java.util.stream.Collectors;

/** Coverage view linking source chunks to extracted facts. */
public class SourceCoverageMatrix {

  public Coverage summarize(SourceEvidenceGraph graph) {
    if (graph == null) {
      return new Coverage(0, 0, 0, Map.of());
    }
    Map<String, Long> factsByChunk =
        graph.facts().stream()
            .collect(
                Collectors.groupingBy(
                    SourceEvidenceGraph.SourceFact::chunkId,
                    java.util.LinkedHashMap::new,
                    Collectors.counting()));
    long covered =
        graph.coverage().stream()
            .filter(entry -> entry.state() == SourceChunk.CoverageState.COVERED)
            .count();
    long compressed =
        graph.coverage().stream()
            .filter(entry -> entry.state() == SourceChunk.CoverageState.COMPRESSED)
            .count();
    long gaps =
        graph.coverage().stream()
                .filter(entry -> entry.state() == SourceChunk.CoverageState.NEEDS_CLARIFICATION)
                .count()
            + graph.gaps().size();
    return new Coverage((int) covered, (int) compressed, (int) gaps, factsByChunk);
  }

  public record Coverage(
      int coveredChunks, int compressedChunks, int gapCount, Map<String, Long> factsByChunk) {
    public Coverage {
      factsByChunk = factsByChunk == null ? Map.of() : Map.copyOf(factsByChunk);
    }
  }
}
