package io.mehdieidi.modless.platform.assistant.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceToModelDeltaPlannerTest {

  @Test
  void emitsEvidenceGraphAndCoverageMatrixSnippets() {
    SourceEvidenceGraph graph =
        new SourceEvidenceGraph(
            "workshop",
            List.of(
                new SourceEvidenceGraph.SourceFact(
                    "fact-1",
                    "chunk-1",
                    "SOURCE_NOTE",
                    "Customer requests refund",
                    List.of("Command"))),
            List.of(
                new SourceEvidenceGraph.CoverageEntry(
                    "chunk-1", SourceChunk.CoverageState.COMPRESSED, "Compressed")),
            List.of());

    List<AssistantModelProvider.ContextSnippet> snippets =
        new SourceToModelDeltaPlanner(new ObjectMapper(), new SourceCoverageMatrix())
            .snippets(graph);

    assertEquals(2, snippets.size());
    assertEquals("source-evidence-graph", snippets.get(0).source());
    assertTrue(snippets.get(0).content().contains("Customer requests refund"));
    assertEquals("source-coverage-matrix", snippets.get(1).source());
    assertTrue(snippets.get(1).content().contains("compressedChunks"));
    assertTrue(snippets.get(1).content().contains("chunk-1"));
  }
}
