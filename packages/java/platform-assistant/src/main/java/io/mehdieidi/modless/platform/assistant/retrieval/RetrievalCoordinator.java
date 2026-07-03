package io.mehdieidi.modless.platform.assistant.retrieval;

import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.retrieval.MetamodelRetrievalDocumentBuilder.RetrievalDocument;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

/** Coordinates semantic retrieval with deterministic metamodel contract expansion. */
public class RetrievalCoordinator {

  private final MetamodelKnowledgeService metamodels;
  private final AssistantCatalog catalogs;
  private final String embeddingProvider;
  private final MetamodelRetrievalDocumentBuilder documentBuilder;
  private final RetrievalReranker reranker;

  public RetrievalCoordinator(
      MetamodelKnowledgeService metamodels, AssistantCatalog catalogs, String embeddingProvider) {
    this.metamodels = metamodels;
    this.catalogs = catalogs;
    this.embeddingProvider = embeddingProvider == null ? "" : embeddingProvider;
    this.documentBuilder = new MetamodelRetrievalDocumentBuilder(metamodels);
    this.reranker = new RetrievalReranker();
  }

  /** Retrieves context for a structured plan and always includes required contract closure. */
  public RetrievalResult retrieve(RetrievalPlan plan, int methodologyLimit) {
    List<AssistantModelProvider.ContextSnippet> snippets = new ArrayList<>();
    snippets.addAll(metamodels.contractClosure(plan.level(), plan.candidateTypes()));
    List<RetrievalDocument> metamodelDocuments =
        reranker.rerank(
            plan, documentBuilder.documents(plan.level()), Math.max(8, methodologyLimit * 4));
    metamodelDocuments.stream().map(documentBuilder::snippet).forEach(snippets::add);
    if (plan.includeMethodology()) {
      for (String concept : plan.concepts()) {
        snippets.addAll(catalogs.search(concept, plan.level().name(), methodologyLimit));
      }
    }
    snippets = dedupe(snippets);
    LinkedHashSet<String> selectedContracts = new LinkedHashSet<>();
    snippets.stream()
        .filter(snippet -> snippet.source().contains("metamodel"))
        .map(AssistantModelProvider.ContextSnippet::title)
        .forEach(selectedContracts::add);
    List<String> warnings =
        "HASH".equalsIgnoreCase(embeddingProvider)
            ? List.of("embeddingProvider=HASH; retrieval quality is degraded.")
            : List.of();
    return new RetrievalResult(
        List.copyOf(snippets),
        new RetrievalDiagnostics(
            plan.concepts(),
            List.copyOf(selectedContracts),
            List.of(),
            embeddingProvider,
            warnings));
  }

  private List<AssistantModelProvider.ContextSnippet> dedupe(
      List<AssistantModelProvider.ContextSnippet> snippets) {
    LinkedHashMap<String, AssistantModelProvider.ContextSnippet> unique = new LinkedHashMap<>();
    for (AssistantModelProvider.ContextSnippet snippet :
        snippets == null ? List.<AssistantModelProvider.ContextSnippet>of() : snippets) {
      unique.putIfAbsent(snippet.source() + "|" + snippet.title(), snippet);
    }
    return List.copyOf(unique.values());
  }

  /** Retrieval output plus diagnostics. */
  public record RetrievalResult(
      List<AssistantModelProvider.ContextSnippet> snippets, RetrievalDiagnostics diagnostics) {
    public RetrievalResult {
      snippets = snippets == null ? List.of() : List.copyOf(snippets);
    }
  }
}
