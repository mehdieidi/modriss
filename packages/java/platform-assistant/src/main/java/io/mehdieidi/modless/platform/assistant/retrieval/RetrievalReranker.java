package io.mehdieidi.modless.platform.assistant.retrieval;

import io.mehdieidi.modless.platform.assistant.retrieval.MetamodelRetrievalDocumentBuilder.RetrievalDocument;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Deterministic reranker for structured retrieval-plan concepts and Ecore contract documents. */
public class RetrievalReranker {

  public List<RetrievalDocument> rerank(
      RetrievalPlan plan, List<RetrievalDocument> documents, int limit) {
    int bounded = Math.max(0, limit);
    if (bounded == 0 || documents == null || documents.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> requestedTypes =
        new LinkedHashSet<>(plan == null ? List.of() : plan.candidateTypes());
    LinkedHashSet<String> concepts =
        new LinkedHashSet<>(plan == null ? List.of() : plan.concepts());
    return documents.stream()
        .sorted(
            Comparator.comparingInt(
                    (RetrievalDocument document) -> score(document, requestedTypes, concepts))
                .reversed()
                .thenComparing(RetrievalDocument::kind)
                .thenComparing(RetrievalDocument::title))
        .limit(bounded)
        .toList();
  }

  private int score(
      RetrievalDocument document,
      LinkedHashSet<String> requestedTypes,
      LinkedHashSet<String> concepts) {
    int score = 0;
    if (document == null) {
      return score;
    }
    if (requestedTypes.contains(document.eClass())) {
      score += 100;
    }
    for (String dependency : document.dependencies()) {
      if (requestedTypes.contains(dependency)) {
        score += 35;
      }
    }
    if ("level-overview".equals(document.kind())) {
      score += 5;
    }
    String searchable =
        (document.title() + "\n" + document.content() + "\n" + document.kind())
            .toLowerCase(Locale.ROOT);
    for (String concept : concepts) {
      String normalized = concept == null ? "" : concept.toLowerCase(Locale.ROOT).trim();
      if (!normalized.isBlank() && searchable.contains(normalized)) {
        score += 10;
      }
    }
    return score;
  }
}
