package io.mehdieidi.varka.platform.assistant.metamodel;

import io.mehdieidi.varka.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider.ContextSnippet;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.embedding.EmbeddingModel;

/** Local lexical retrieval fallback; it never sends model or document text to a remote service. */
public final class LexicalRetrievalIndex {
  private final AssistantMetamodelSchemaService schemas;
  private final MetamodelKnowledgeService knowledge;
  private final Path repositoryRoot;
  private final EmbeddingModel embeddingModel;
  private final Map<ModelLevel, List<ContextSnippet>> localCorpus = new ConcurrentHashMap<>();
  private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();

  public LexicalRetrievalIndex(AssistantMetamodelSchemaService schemas) {
    this(schemas, new MetamodelKnowledgeService(schemas), null, null);
  }

  /** Allows deployment packaging/tests to choose the repository/document root explicitly. */
  public LexicalRetrievalIndex(AssistantMetamodelSchemaService schemas, Path repositoryRoot) {
    this(schemas, new MetamodelKnowledgeService(schemas), repositoryRoot, null);
  }

  /**
   * Uses an optional local embedding model; null always retains deterministic lexical retrieval.
   */
  public LexicalRetrievalIndex(
      AssistantMetamodelSchemaService schemas, Path repositoryRoot, EmbeddingModel embeddingModel) {
    this(schemas, new MetamodelKnowledgeService(schemas), repositoryRoot, embeddingModel);
  }

  /**
   * Creates retrieval over Ecore contracts and approved guidance.
   *
   * <p>Every retrieved EClass is expanded with its required containment closure before it is sent
   * to the provider. This is structural retrieval, not an intent heuristic: a model can therefore
   * construct an element such as {@code Function} together with the exact contract it requires.
   */
  public LexicalRetrievalIndex(
      AssistantMetamodelSchemaService schemas,
      MetamodelKnowledgeService knowledge,
      Path repositoryRoot,
      EmbeddingModel embeddingModel) {
    this.schemas = schemas;
    this.knowledge = knowledge == null ? new MetamodelKnowledgeService(schemas) : knowledge;
    this.repositoryRoot =
        repositoryRoot == null
            ? discoverRepositoryRoot(Path.of("").toAbsolutePath().normalize())
            : repositoryRoot.toAbsolutePath().normalize();
    this.embeddingModel = embeddingModel;
  }

  private static Path discoverRepositoryRoot(Path start) {
    for (Path current = start; current != null; current = current.getParent()) {
      if (Files.isDirectory(current.resolve("mde")) && Files.isDirectory(current.resolve("docs"))) {
        return current;
      }
    }
    return start;
  }

  public List<ContextSnippet> search(ModelLevel level, String query, int limit) {
    List<String> terms =
        Arrays.stream((query == null ? "" : query).toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
            .filter(term -> term.length() > 1)
            .distinct()
            .toList();
    List<ContextSnippet> candidates = new ArrayList<>(schemas.allPlanningContracts(level));
    candidates.addAll(localCorpus.computeIfAbsent(level, this::loadCorpus));
    float[] queryEmbedding =
        embeddingModel == null || query == null || query.isBlank()
            ? null
            : embeddingModel.embed(query);
    List<ContextSnippet> selected =
        candidates.stream()
            .map(
                snippet ->
                    new Scored(snippet, score(snippet, terms), similarity(queryEmbedding, snippet)))
            .sorted(
                Comparator.comparingDouble(Scored::rank)
                    .reversed()
                    .thenComparing(item -> item.snippet().title()))
            .filter(item -> terms.isEmpty() || item.score() > 0)
            .limit(Math.max(1, limit))
            .map(Scored::snippet)
            .toList();
    return withRequiredContainmentClosure(level, selected);
  }

  private List<ContextSnippet> withRequiredContainmentClosure(
      ModelLevel level, List<ContextSnippet> selected) {
    List<String> selectedTypes =
        selected.stream()
            .map(ContextSnippet::title)
            .filter(title -> knowledge.index().typeContract(level, title).isPresent())
            .toList();
    if (selectedTypes.isEmpty()) return selected;

    Map<String, ContextSnippet> result = new java.util.LinkedHashMap<>();
    for (ContextSnippet snippet : selected)
      result.put(snippet.source() + "\n" + snippet.title(), snippet);
    for (ContextSnippet contract : knowledge.contractClosure(level, selectedTypes)) {
      result.put("ecore-closure\n" + contract.title(), contract);
    }
    return List.copyOf(result.values());
  }

  private double similarity(float[] query, ContextSnippet snippet) {
    if (query == null) return 0D;
    String key = snippet.source() + "\n" + snippet.title() + "\n" + snippet.content();
    float[] candidate =
        embeddingCache.computeIfAbsent(
            key, ignored -> embeddingModel.embed(snippet.title() + "\n" + snippet.content()));
    double dot = 0D, queryNorm = 0D, candidateNorm = 0D;
    for (int index = 0; index < Math.min(query.length, candidate.length); index++) {
      dot += query[index] * candidate[index];
      queryNorm += query[index] * query[index];
      candidateNorm += candidate[index] * candidate[index];
    }
    return queryNorm == 0D || candidateNorm == 0D ? 0D : dot / Math.sqrt(queryNorm * candidateNorm);
  }

  /**
   * Loads approved, repository-owned guidance only. Missing files simply leave lexical Ecore
   * retrieval active, which keeps production startup offline and deterministic.
   */
  private List<ContextSnippet> loadCorpus(ModelLevel level) {
    List<Path> paths = new ArrayList<>();
    paths.add(repositoryRoot.resolve("docs/public-docs/docs/guides/modeling-workflow.md"));
    paths.add(
        repositoryRoot.resolve(
            "docs/public-docs/docs/guides/" + level.apiName() + "-modeling-methodology.md"));
    if (level == ModelLevel.CIM) {
      paths.add(
          repositoryRoot.resolve("mde/samples/document-to-cim/community-clinic-user-stories.md"));
      paths.add(
          repositoryRoot.resolve(
              "mde/samples/document-to-cim/marketplace-returns-event-storming.md"));
    }
    List<ContextSnippet> snippets = new ArrayList<>();
    for (Path path : paths) appendChunks(snippets, path);
    return List.copyOf(snippets);
  }

  private void appendChunks(List<ContextSnippet> target, Path path) {
    if (!Files.isRegularFile(path)) return;
    try {
      String text = Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
      if (text.isBlank()) return;
      int ordinal = 0;
      for (int start = 0; start < text.length(); start += 2200) {
        int end = Math.min(text.length(), start + 2200);
        if (end < text.length()) {
          int boundary = text.lastIndexOf('\n', end);
          if (boundary > start + 400) end = boundary;
        }
        target.add(
            new ContextSnippet(
                "local-guidance:" + repositoryRoot.relativize(path) + "#" + ordinal++,
                path.getFileName().toString(),
                text.substring(start, end)));
        start = end - 2200;
      }
    } catch (IOException ignored) {
      // Retrieval remains available from structural contracts when optional guidance is absent.
    }
  }

  private int score(ContextSnippet snippet, List<String> terms) {
    String text = (snippet.title() + " " + snippet.content()).toLowerCase(Locale.ROOT);
    return (int) terms.stream().filter(text::contains).count();
  }

  private record Scored(ContextSnippet snippet, int score, double semanticScore) {
    double rank() {
      return score + semanticScore;
    }
  }
}
