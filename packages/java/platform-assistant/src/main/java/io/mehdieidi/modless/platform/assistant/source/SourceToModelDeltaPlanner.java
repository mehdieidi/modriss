package io.mehdieidi.modless.platform.assistant.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import java.util.List;

/** Builds source-evidence prompt context for downstream ModelDelta planning. */
public class SourceToModelDeltaPlanner {

  private final ObjectMapper mapper;
  private final SourceCoverageMatrix coverageMatrix;

  public SourceToModelDeltaPlanner(ObjectMapper mapper, SourceCoverageMatrix coverageMatrix) {
    this.mapper = mapper;
    this.coverageMatrix = coverageMatrix == null ? new SourceCoverageMatrix() : coverageMatrix;
  }

  public List<AssistantModelProvider.ContextSnippet> snippets(SourceEvidenceGraph graph) {
    if (graph == null) {
      return List.of();
    }
    try {
      return List.of(
          new AssistantModelProvider.ContextSnippet(
              "source-evidence-graph", "Source evidence graph", mapper.writeValueAsString(graph)),
          new AssistantModelProvider.ContextSnippet(
              "source-coverage-matrix",
              "Source coverage matrix",
              mapper.writeValueAsString(coverageMatrix.summarize(graph))));
    } catch (Exception ex) {
      return List.of(
          new AssistantModelProvider.ContextSnippet(
              "source-evidence-graph", "Source evidence graph", graph.toString()));
    }
  }
}
