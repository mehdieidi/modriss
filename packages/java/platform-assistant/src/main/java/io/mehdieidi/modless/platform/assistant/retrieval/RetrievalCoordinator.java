package io.mehdieidi.modless.platform.assistant.retrieval;

import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.retrieval.MetamodelRetrievalDocumentBuilder.RetrievalDocument;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMetamodelContractStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMetamodelContractStore.ContractSearchHit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Coordinates semantic retrieval with deterministic metamodel contract expansion. */
public class RetrievalCoordinator {

  private final MetamodelKnowledgeService metamodels;
  private final AssistantCatalog catalogs;
  private final AssistantMetamodelContractStore contractStore;
  private final String embeddingProvider;
  private final MetamodelRetrievalDocumentBuilder documentBuilder;
  private final RetrievalReranker reranker;
  private final LlmContractReranker llmReranker;
  private final ConcurrentHashMap<String, RetrievalResult> retrievalCache =
      new ConcurrentHashMap<>();
  private final AtomicLong retrievalCacheHits = new AtomicLong();
  private final AtomicLong retrievalCacheMisses = new AtomicLong();

  public RetrievalCoordinator(
      MetamodelKnowledgeService metamodels, AssistantCatalog catalogs, String embeddingProvider) {
    this(metamodels, catalogs, AssistantMetamodelContractStore.noop(), embeddingProvider, null);
  }

  public RetrievalCoordinator(
      MetamodelKnowledgeService metamodels,
      AssistantCatalog catalogs,
      AssistantMetamodelContractStore contractStore,
      String embeddingProvider,
      AssistantModelProvider provider) {
    this.metamodels = metamodels;
    this.catalogs = catalogs;
    this.contractStore =
        contractStore == null ? AssistantMetamodelContractStore.noop() : contractStore;
    this.embeddingProvider = embeddingProvider == null ? "" : embeddingProvider;
    this.documentBuilder = new MetamodelRetrievalDocumentBuilder(metamodels);
    this.reranker = new RetrievalReranker();
    this.llmReranker = new LlmContractReranker(provider);
  }

  /** Retrieves context for a structured plan and always includes required contract closure. */
  public RetrievalResult retrieve(RetrievalPlan plan, int methodologyLimit) {
    String cacheKey = retrievalCacheKey(plan);
    RetrievalResult cached = retrievalCache.get(cacheKey);
    if (cached != null) {
      retrievalCacheHits.incrementAndGet();
      return cached;
    }
    retrievalCacheMisses.incrementAndGet();
    RetrievalResult result = retrieveUncached(plan, methodologyLimit);
    retrievalCache.put(cacheKey, result);
    return result;
  }

  /** Clears cached retrieval results after metamodel refresh. */
  public void clearCache() {
    retrievalCache.clear();
  }

  public long retrievalCacheHits() {
    return retrievalCacheHits.get();
  }

  public long retrievalCacheMisses() {
    return retrievalCacheMisses.get();
  }

  private RetrievalResult retrieveUncached(RetrievalPlan plan, int methodologyLimit) {
    List<AssistantModelProvider.ContextSnippet> snippets = new ArrayList<>();
    snippets.addAll(metamodels.contractClosure(plan.level(), plan.candidateTypes()));
    snippets.addAll(hybridContractSnippets(plan, Math.max(8, methodologyLimit * 4)));
    List<RetrievalDocument> metamodelDocuments =
        reranker.rerank(
            plan, documentBuilder.documents(plan.level()), Math.max(4, methodologyLimit * 2));
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
    List<String> missingContracts = missingContracts(plan, selectedContracts);
    List<String> warnings = new ArrayList<>();
    if ("HASH".equalsIgnoreCase(embeddingProvider)) {
      warnings.add("embeddingProvider=HASH; retrieval quality is degraded.");
    }
    if (!missingContracts.isEmpty()) {
      warnings.add("missingContracts=" + String.join(",", missingContracts));
    }
    return new RetrievalResult(
        List.copyOf(snippets),
        new RetrievalDiagnostics(
            plan.concepts(),
            List.copyOf(selectedContracts),
            missingContracts,
            embeddingProvider,
            List.copyOf(warnings)));
  }

  private String retrievalCacheKey(RetrievalPlan plan) {
    return plan.level().name()
        + "|"
        + metamodels.metamodelHash(plan.level())
        + "|"
        + String.join(",", plan.concepts())
        + "|"
        + String.join(",", plan.candidateTypes())
        + "|"
        + plan.includeMethodology()
        + "|"
        + plan.includeSelectedNeighborhood()
        + "|"
        + plan.taskSummary()
        + "|"
        + String.join(",", plan.mustIncludeContractsFor());
  }

  private List<AssistantModelProvider.ContextSnippet> hybridContractSnippets(
      RetrievalPlan plan, int limit) {
    LinkedHashSet<String> queries = new LinkedHashSet<>();
    queries.add(plan.taskSummary());
    queries.addAll(plan.concepts());
    queries.addAll(plan.candidateTypes());
    List<ContractSearchHit> hits = new ArrayList<>();
    for (String query : queries) {
      if (query == null || query.isBlank()) {
        continue;
      }
      hits.addAll(contractStore.search(plan.level(), query, limit));
    }
    List<ContractSearchHit> ranked = llmReranker.rerank(plan, dedupeHits(hits), limit);
    return ranked.stream()
        .map(
            hit ->
                new AssistantModelProvider.ContextSnippet(
                    "metamodel-contract-db", hit.title(), hit.content()))
        .toList();
  }

  private List<ContractSearchHit> dedupeHits(List<ContractSearchHit> hits) {
    LinkedHashMap<String, ContractSearchHit> unique = new LinkedHashMap<>();
    for (ContractSearchHit hit : hits == null ? List.<ContractSearchHit>of() : hits) {
      unique.putIfAbsent(hit.eClass() + "|" + hit.feature() + "|" + hit.contractKind(), hit);
    }
    return List.copyOf(unique.values());
  }

  private List<String> missingContracts(RetrievalPlan plan, LinkedHashSet<String> selected) {
    if (plan.mustIncludeContractsFor() == null || plan.mustIncludeContractsFor().isEmpty()) {
      return List.of();
    }
    String haystack = String.join("\n", selected).toLowerCase();
    List<String> missing = new ArrayList<>();
    for (String required : plan.mustIncludeContractsFor()) {
      if (required != null && !required.isBlank() && !haystack.contains(required.toLowerCase())) {
        missing.add(required);
      }
    }
    return List.copyOf(missing);
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
