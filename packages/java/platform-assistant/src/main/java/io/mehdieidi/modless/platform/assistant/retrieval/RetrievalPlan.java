package io.mehdieidi.modless.platform.assistant.retrieval;

import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;

/** Structured retrieval request produced by intent/task analysis. */
public record RetrievalPlan(
    ModelLevel level,
    List<String> concepts,
    List<String> candidateTypes,
    boolean includeMethodology,
    boolean includeSelectedNeighborhood,
    String taskSummary,
    List<String> mustIncludeContractsFor) {

  public RetrievalPlan(
      ModelLevel level,
      List<String> concepts,
      List<String> candidateTypes,
      boolean includeMethodology,
      boolean includeSelectedNeighborhood) {
    this(
        level,
        concepts,
        candidateTypes,
        includeMethodology,
        includeSelectedNeighborhood,
        "",
        List.of());
  }

  public RetrievalPlan {
    concepts = concepts == null ? List.of() : List.copyOf(concepts);
    candidateTypes = candidateTypes == null ? List.of() : List.copyOf(candidateTypes);
    taskSummary = taskSummary == null ? "" : taskSummary.trim();
    mustIncludeContractsFor =
        mustIncludeContractsFor == null ? List.of() : List.copyOf(mustIncludeContractsFor);
  }
}
