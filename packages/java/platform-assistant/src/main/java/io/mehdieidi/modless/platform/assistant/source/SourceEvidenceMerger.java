package io.mehdieidi.modless.platform.assistant.source;

import java.util.LinkedHashMap;
import java.util.List;

/** Merges chunk evidence and deduplicates facts by stable ID/summary. */
public class SourceEvidenceMerger {

  public SourceEvidenceGraph merge(String sourceId, List<SourceEvidenceGraph> graphs) {
    LinkedHashMap<String, SourceEvidenceGraph.SourceFact> facts = new LinkedHashMap<>();
    LinkedHashMap<String, SourceEvidenceGraph.CoverageEntry> coverage = new LinkedHashMap<>();
    java.util.ArrayList<String> gaps = new java.util.ArrayList<>();
    for (SourceEvidenceGraph graph : graphs == null ? List.<SourceEvidenceGraph>of() : graphs) {
      for (SourceEvidenceGraph.SourceFact fact : graph.facts()) {
        String key =
            fact.id().isBlank()
                ? fact.kind() + ":" + fact.summary().toLowerCase(java.util.Locale.ROOT)
                : fact.id();
        facts.putIfAbsent(key, fact);
      }
      for (SourceEvidenceGraph.CoverageEntry entry : graph.coverage()) {
        coverage.putIfAbsent(entry.chunkId(), entry);
      }
      gaps.addAll(graph.gaps());
    }
    return new SourceEvidenceGraph(
        sourceId, List.copyOf(facts.values()), List.copyOf(coverage.values()), List.copyOf(gaps));
  }
}
