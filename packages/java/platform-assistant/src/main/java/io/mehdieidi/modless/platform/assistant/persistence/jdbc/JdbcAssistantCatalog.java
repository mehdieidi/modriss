package io.mehdieidi.modless.platform.assistant.persistence.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.persistence.embedding.LocalEmbeddingService;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.modeling.runtime.MdeRuntimePaths;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/** Builds and queries compact metamodel and methodology catalogs for assistant retrieval. */
public class JdbcAssistantCatalog implements AssistantCatalog {

  private static final String INDEX_FORMAT_VERSION = "3";

  private final JdbcTemplate jdbc;
  private final LocalEmbeddingService embeddings;
  private final MdeRuntimePaths mdePaths;
  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * Creates the catalog service.
   *
   * @param jdbc JDBC access
   */
  public JdbcAssistantCatalog(JdbcTemplate jdbc) {
    this(jdbc, new LocalEmbeddingService(), null);
  }

  /**
   * Creates the catalog service.
   *
   * @param jdbc JDBC access
   * @param embeddings local embedding service
   */
  public JdbcAssistantCatalog(
      JdbcTemplate jdbc, LocalEmbeddingService embeddings, MdeRuntimePaths mdePaths) {
    this.jdbc = jdbc;
    this.embeddings = embeddings;
    this.mdePaths = mdePaths == null ? new MdeRuntimePaths(null) : mdePaths;
  }

  /** Reindexes local metamodel and methodology files when content hashes change. */
  @Override
  public void refresh() {
    try {
      removeEvlCatalogDocuments();
      indexMetamodelCatalog();
      indexMethodologyCatalog();
    } catch (Exception ex) {
      throw new IllegalStateException("Could not index assistant catalogs.", ex);
    }
  }

  /** Warms startup-critical catalog knowledge without rewriting an existing metamodel cache. */
  public void refreshStartupCatalog() {
    try {
      removeEvlCatalogDocuments();
      if (!hasMetamodelCatalog()) {
        indexMetamodelCatalog();
      }
      indexMethodologyCatalog();
    } catch (Exception ex) {
      throw new IllegalStateException("Could not index assistant startup catalogs.", ex);
    }
  }

  private boolean hasMetamodelCatalog() {
    if (jdbc == null) {
      return false;
    }
    Integer count =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM assistant_retrieval_documents
            WHERE scope IN ('package', 'classifier', 'feature', 'enum')
            """,
            Integer.class);
    return count != null && count > 0;
  }

  private void indexMetamodelCatalog() throws Exception {
    indexTree(mdePaths.repositoryRoot().resolve("mde"), path -> matches(path, ".emf", ".ecore"));
  }

  private void removeEvlCatalogDocuments() {
    if (jdbc == null) {
      return;
    }
    jdbc.update(
        """
        DELETE FROM assistant_retrieval_documents
        WHERE scope = 'constraint'
           OR lower(title) LIKE '%evl%'
           OR lower(content) LIKE '%evl%'
           OR lower(title) LIKE '%semantic validation%'
           OR lower(content) LIKE '%semantic validation%'
        """);
  }

  private void indexMethodologyCatalog() throws Exception {
    indexTree(
        mdePaths.repositoryRoot().resolve("mde"),
        path -> isMethodologyJson(path) || isMethodologyMarkdown(path));
    indexTree(
        mdePaths
            .repositoryRoot()
            .resolve("docs")
            .resolve("public-docs")
            .resolve("docs")
            .resolve("guides"),
        this::isMethodologyMarkdown);
  }

  private void indexTree(Path root, java.util.function.Predicate<Path> include) throws Exception {
    if (!Files.exists(root)) {
      return;
    }
    try (var stream = Files.walk(root)) {
      stream.filter(Files::isRegularFile).filter(include).forEach(this::indexFile);
    }
  }

  /**
   * Searches catalogs using exact symbol lookup first and fuzzy matching second.
   *
   * @param query search text
   * @param level optional model level
   * @param limit maximum number of results
   * @return catalog snippets
   */
  @Override
  public List<AssistantModelProvider.ContextSnippet> search(String query, String level, int limit) {
    String normalized = query == null ? "" : query.trim();
    if (jdbc == null || normalized.isBlank()) {
      return List.of();
    }
    String normalizedLevel = level == null ? "" : level.toUpperCase(Locale.ROOT);
    List<AssistantModelProvider.ContextSnippet> exact =
        jdbc.query(
            """
            SELECT source, title, content FROM assistant_retrieval_documents
            WHERE (lower(title) = lower(?) OR lower(source) = lower(?))
              AND (? = '' OR metadata->>'level' = ? OR metadata->>'level' = 'SHARED')
            ORDER BY updated_at DESC
            LIMIT ?
            """,
            (rs, row) ->
                snippet(rs.getString("source"), rs.getString("title"), rs.getString("content")),
            normalized,
            normalized,
            normalizedLevel,
            normalizedLevel,
            limit);
    if (!exact.isEmpty()) {
      return exact;
    }
    String fuzzyQuery =
        java.util.Arrays.stream(normalized.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+"))
            .filter(word -> word.length() > 2)
            .distinct()
            .limit(8)
            .collect(java.util.stream.Collectors.joining(" | "));
    if (fuzzyQuery.isBlank()) {
      return List.of();
    }
    String queryVector = embeddings.vectorLiteral(normalized);
    return jdbc.query(
        """
        SELECT source, title, content FROM assistant_retrieval_documents
        WHERE (? = '' OR metadata->>'level' = ? OR metadata->>'level' = 'SHARED')
        ORDER BY
          CASE
            WHEN to_tsvector('simple', title || ' ' || content)
              @@ to_tsquery('simple', ?)
            THEN ts_rank(
            to_tsvector('simple', title || ' ' || content),
            to_tsquery('simple', ?))
            ELSE 0
          END DESC,
          embedding <=> ?::vector ASC,
          updated_at DESC
        LIMIT ?
        """,
        (rs, row) ->
            snippet(rs.getString("source"), rs.getString("title"), rs.getString("content")),
        normalizedLevel,
        normalizedLevel,
        fuzzyQuery,
        fuzzyQuery,
        queryVector,
        limit);
  }

  /**
   * Returns a compact classifier definition with mandatory features first.
   *
   * @param type metamodel classifier name
   * @param level modeling level
   * @param limit maximum classifier and feature snippets
   * @return classifier and feature snippets
   */
  @Override
  public List<AssistantModelProvider.ContextSnippet> describeType(
      String type, String level, int limit) {
    String normalizedType = type == null ? "" : type.trim();
    if (jdbc == null || normalizedType.isBlank()) {
      return List.of();
    }
    String normalizedLevel = level == null ? "" : level.toUpperCase(Locale.ROOT);
    return jdbc.query(
        """
        SELECT source, title, content FROM assistant_retrieval_documents
        WHERE (lower(title) = lower(?) OR lower(metadata->>'owner') = lower(?))
          AND (? = '' OR metadata->>'level' = ? OR metadata->>'level' = 'SHARED')
        ORDER BY
          CASE
            WHEN lower(title) = lower(?) THEN 0
            WHEN metadata->>'lowerBound' = '1' THEN 1
            ELSE 2
          END,
          title
        LIMIT ?
        """,
        (rs, row) ->
            snippet(rs.getString("source"), rs.getString("title"), rs.getString("content")),
        normalizedType,
        normalizedType,
        normalizedLevel,
        normalizedLevel,
        normalizedType,
        Math.max(1, limit));
  }

  /**
   * Canonicalizes a textual enum value using the indexed metamodel literals.
   *
   * @param ownerType classifier that owns the enum-typed feature
   * @param featureName feature name
   * @param value proposed textual value
   * @param level modeling level
   * @return canonical enum literal when the feature and value are known
   */
  @Override
  public Optional<String> canonicalEnumLiteral(
      String ownerType, String featureName, String value, String level) {
    if (jdbc == null
        || ownerType == null
        || ownerType.isBlank()
        || featureName == null
        || featureName.isBlank()
        || value == null
        || value.isBlank()) {
      return Optional.empty();
    }
    String normalizedLevel = level == null ? "" : level.toUpperCase(Locale.ROOT);
    List<String> contents =
        jdbc.query(
            """
            SELECT enum_doc.content
            FROM assistant_retrieval_documents feature_doc
            JOIN assistant_retrieval_documents enum_doc
              ON lower(enum_doc.title) =
                 lower(regexp_replace(feature_doc.metadata->>'type', '^.*/', '')
                       || '.literals')
            WHERE lower(feature_doc.title) = lower(?)
              AND (? = '' OR feature_doc.metadata->>'level' = ?
                   OR feature_doc.metadata->>'level' = 'SHARED')
              AND (? = '' OR enum_doc.metadata->>'level' = ?
                   OR enum_doc.metadata->>'level' = 'SHARED')
            LIMIT 4
            """,
            (rs, row) -> rs.getString("content"),
            ownerType + "." + featureName,
            normalizedLevel,
            normalizedLevel,
            normalizedLevel,
            normalizedLevel);
    return contents.stream()
        .map(content -> canonicalEnumLiteral(content, value))
        .flatMap(Optional::stream)
        .findFirst();
  }

  Optional<String> canonicalEnumLiteral(String enumDescription, String value) {
    if (enumDescription == null || value == null) {
      return Optional.empty();
    }
    int marker = enumDescription.indexOf("allowed literals ");
    if (marker < 0) {
      return Optional.empty();
    }
    String requested = value.trim();
    return java.util.Arrays.stream(
            enumDescription.substring(marker + "allowed literals ".length()).split(","))
        .map(String::trim)
        .filter(literal -> literal.equalsIgnoreCase(requested))
        .findFirst();
  }

  private boolean matches(Path path, String... suffixes) {
    String value = path.getFileName().toString().toLowerCase(Locale.ROOT);
    for (String suffix : suffixes) {
      if (value.endsWith(suffix)) {
        return true;
      }
    }
    return false;
  }

  private boolean isMethodologyJson(Path path) {
    String normalized = path.toString().toLowerCase(Locale.ROOT).replace('\\', '/');
    return normalized.contains("/mde/methodology/process-definitions/")
        && normalized.endsWith(".json");
  }

  private boolean isMethodologyMarkdown(Path path) {
    String normalized = path.toString().toLowerCase(Locale.ROOT).replace('\\', '/');
    if (!normalized.endsWith(".md")) {
      return false;
    }
    return normalized.contains("/mde/methodology/")
        || normalized.endsWith("cim-modeling-methodology.md")
        || normalized.endsWith("pim-modeling-methodology.md")
        || normalized.endsWith("psm-modeling-methodology.md")
        || normalized.endsWith("end-to-end-modeling-methodology.md")
        || normalized.endsWith("modeling-workflow.md");
  }

  private void indexFile(Path path) {
    try {
      String source = path.toString().replace('\\', '/');
      String hash = hash(path);
      String existing =
          jdbc.query(
              """
              SELECT source_hash FROM assistant_retrieval_documents
              WHERE source = ? LIMIT 1
              """,
              rs -> rs.next() ? rs.getString(1) : null,
              source);
      if (hash.equals(existing)) {
        return;
      }
      jdbc.update("DELETE FROM assistant_retrieval_documents WHERE source = ?", source);
      List<Document> documents;
      if (isMethodologyJson(path)) {
        documents = parseMethodologyJson(path, source, hash);
      } else if (isMethodologyMarkdown(path)) {
        documents = parseMethodologyMarkdown(path, source, hash);
      } else {
        documents = parseMetamodel(path, source, hash);
      }
      for (Document document : documents) {
        Document sanitized = sanitizeEvlValidationReferences(document);
        if (sanitized.content().isBlank() || sanitized.title().isBlank()) {
          continue;
        }
        upsert(sanitized);
      }
    } catch (Exception ex) {
      throw new IllegalStateException("Could not index " + path, ex);
    }
  }

  private List<Document> parseMetamodel(Path path, String source, String hash) throws Exception {
    if (path.toString().toLowerCase(Locale.ROOT).endsWith(".ecore")) {
      return parseEcore(path, source, hash);
    }
    return parseEmfatic(path, source, hash);
  }

  private List<Document> parseEmfatic(Path path, String source, String hash) throws Exception {
    List<Document> documents = new ArrayList<>();
    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
    String currentPackage = "";
    String currentClass = null;
    for (int lineNumber = 0; lineNumber < lines.size(); lineNumber++) {
      String line = lines.get(lineNumber);
      String trimmed = line.trim();
      if (trimmed.startsWith("package ")) {
        currentPackage = trimmed.substring(8).split("[;\\s]", 2)[0].trim();
        documents.add(
            document(
                source,
                hash,
                "package",
                currentPackage,
                trimmed,
                Map.of("kind", "package", "line", lineNumber + 1)));
      }
      if (trimmed.startsWith("class ")) {
        currentClass = trimmed.substring(6).split("[\\s\\{]", 2)[0].trim();
        documents.add(
            document(
                source,
                hash,
                "classifier",
                currentPackage.isBlank() ? currentClass : currentPackage + "." + currentClass,
                trimmed,
                Map.of("kind", "class", "package", currentPackage, "line", lineNumber + 1)));
      } else if (currentClass != null && trimmed.startsWith("val ")
          || currentClass != null && trimmed.startsWith("attr ")
          || currentClass != null && trimmed.startsWith("ref ")) {
        documents.add(
            document(
                source,
                hash,
                "feature",
                currentClass + "." + featureName(trimmed),
                trimmed,
                Map.of(
                    "owner", currentClass, "kind", featureKind(trimmed), "line", lineNumber + 1)));
      }
    }
    return documents;
  }

  private List<Document> parseEcore(Path path, String source, String hash) throws Exception {
    List<Document> documents = new ArrayList<>();
    var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(false);
    var builder = factory.newDocumentBuilder();
    try (InputStream input = Files.newInputStream(path)) {
      org.w3c.dom.Document document = builder.parse(input);
      var root = document.getDocumentElement();
      if (root != null) {
        String packageName =
            blankToDefault(root.getAttribute("name"), path.getFileName().toString());
        documents.add(
            document(
                source,
                hash,
                "package",
                packageName,
                root.getTagName() + " " + packageName,
                Map.of(
                    "kind",
                    "package",
                    "nsURI",
                    root.getAttribute("nsURI"),
                    "nsPrefix",
                    root.getAttribute("nsPrefix"))));
      }
      var classifiers = document.getElementsByTagName("eClassifiers");
      for (int index = 0; index < classifiers.getLength(); index++) {
        var node = classifiers.item(index);
        if (!(node instanceof org.w3c.dom.Element element)) {
          continue;
        }
        String kind = element.getAttribute("xsi:type");
        String name = element.getAttribute("name");
        if (name == null || name.isBlank()) {
          continue;
        }
        String superTypes = element.getAttribute("eSuperTypes");
        StringBuilder classifierContent = new StringBuilder(kind + " " + name);
        if (superTypes != null && !superTypes.isBlank()) {
          classifierContent.append(" supers ").append(superTypes);
        }
        documents.add(
            document(
                source,
                hash,
                "classifier",
                name,
                classifierContent.toString(),
                Map.of(
                    "kind",
                    kind,
                    "abstract",
                    element.getAttribute("abstract"),
                    "interface",
                    element.getAttribute("interface"),
                    "eSuperTypes",
                    superTypes)));
        if ("ecore:EEnum".equals(kind)) {
          List<String> literals = new ArrayList<>();
          var literalNodes = element.getElementsByTagName("eLiterals");
          for (int literalIndex = 0; literalIndex < literalNodes.getLength(); literalIndex++) {
            if (literalNodes.item(literalIndex) instanceof org.w3c.dom.Element literal) {
              String literalName = literal.getAttribute("name");
              if (literalName != null && !literalName.isBlank()) {
                literals.add(literalName);
              }
            }
          }
          documents.add(
              document(
                  source,
                  hash,
                  "enum",
                  name + ".literals",
                  "enum " + name + " allowed literals " + String.join(", ", literals),
                  Map.of("owner", name, "kind", "enum-literals")));
        }
        var features = element.getElementsByTagName("eStructuralFeatures");
        for (int featureIndex = 0; featureIndex < features.getLength(); featureIndex++) {
          var featureNode = features.item(featureIndex);
          if (!(featureNode instanceof org.w3c.dom.Element feature)) {
            continue;
          }
          String featureName = feature.getAttribute("name");
          if (featureName == null || featureName.isBlank()) {
            continue;
          }
          String featureKind = feature.getAttribute("xsi:type");
          String lower = blankToDefault(feature.getAttribute("lowerBound"), "0");
          String upper = blankToDefault(feature.getAttribute("upperBound"), "1");
          String type = feature.getAttribute("eType");
          String containment = feature.getAttribute("containment");
          String derived = feature.getAttribute("derived");
          String ordered = feature.getAttribute("ordered");
          String unique = feature.getAttribute("unique");
          String content =
              "owner "
                  + name
                  + " feature "
                  + featureName
                  + " kind "
                  + featureKind
                  + " type "
                  + type
                  + " multiplicity "
                  + lower
                  + ".."
                  + upper
                  + " containment "
                  + containment
                  + " derived "
                  + derived
                  + " ordered "
                  + ordered
                  + " unique "
                  + unique;
          documents.add(
              document(
                  source,
                  hash,
                  "feature",
                  name + "." + featureName,
                  content,
                  Map.of(
                      "owner",
                      name,
                      "kind",
                      featureKind,
                      "lowerBound",
                      lower,
                      "upperBound",
                      upper,
                      "type",
                      type)));
        }
      }
    }
    return documents;
  }

  private List<Document> parseMethodologyJson(Path path, String source, String hash)
      throws Exception {
    JsonNode root = mapper.readTree(path.toFile());
    String level = root.path("level").asText(level(source).toLowerCase(Locale.ROOT));
    String displayName =
        blankToDefault(root.path("displayName").asText(""), level.toUpperCase(Locale.ROOT));
    List<Document> documents = new ArrayList<>();
    documents.add(
        document(
            source,
            hash,
            "methodology",
            displayName + " methodology process",
            compactProcessOverview(root),
            Map.of("kind", "methodology-process", "level", level.toUpperCase(Locale.ROOT))));
    for (JsonNode phase : iterable(root.path("phases"))) {
      documents.add(
          document(
              source,
              hash,
              "methodology",
              displayName + " phase " + phase.path("name").asText(phase.path("id").asText()),
              compactPhase(phase),
              Map.of(
                  "kind",
                  "methodology-phase",
                  "phaseId",
                  phase.path("id").asText(""),
                  "level",
                  level.toUpperCase(Locale.ROOT))));
      collectStageDocuments(
          documents, source, hash, displayName, level, phase, phase.path("id").asText(""));
    }
    for (JsonNode workflow : iterable(root.path("changeManagement").path("workflows"))) {
      documents.add(
          document(
              source,
              hash,
              "methodology",
              displayName + " change workflow " + workflow.path("name").asText(""),
              "Change workflow: "
                  + workflow.path("name").asText("")
                  + "\nTrigger: "
                  + workflow.path("trigger").asText("")
                  + "\nSteps: "
                  + joinStrings(workflow.path("steps")),
              Map.of("kind", "methodology-change", "level", level.toUpperCase(Locale.ROOT))));
    }
    return documents;
  }

  private void collectStageDocuments(
      List<Document> documents,
      String source,
      String hash,
      String displayName,
      String level,
      JsonNode owner,
      String phaseId) {
    for (JsonNode stage :
        iterable(
            owner.path("stages").isMissingNode()
                ? owner.path("subStages")
                : owner.path("stages"))) {
      documents.add(
          document(
              source,
              hash,
              "methodology",
              displayName + " stage " + stage.path("name").asText(stage.path("id").asText()),
              compactStage(stage),
              Map.of(
                  "kind",
                  "methodology-stage",
                  "phaseId",
                  phaseId,
                  "stageId",
                  stage.path("id").asText(""),
                  "level",
                  level.toUpperCase(Locale.ROOT))));
      collectStageDocuments(documents, source, hash, displayName, level, stage, phaseId);
    }
  }

  private List<Document> parseMethodologyMarkdown(Path path, String source, String hash)
      throws Exception {
    String text = Files.readString(path, StandardCharsets.UTF_8).trim();
    if (text.isBlank()) {
      return List.of();
    }
    String title =
        java.util.Arrays.stream(text.split("\\R", 2))
            .findFirst()
            .orElse(path.getFileName().toString())
            .replaceFirst("^#+\\s*", "")
            .trim();
    if (title.isBlank()) {
      title = path.getFileName().toString();
    }
    String content = text.length() > 5000 ? text.substring(0, 5000) + "\n[truncated]" : text;
    return List.of(
        document(source, hash, "methodology", title, content, Map.of("kind", "methodology-guide")));
  }

  private String compactProcessOverview(JsonNode root) {
    return "Process "
        + root.path("processId").asText("")
        + " for "
        + root.path("displayName").asText(root.path("level").asText(""))
        + "\nPhases: "
        + joinNames(root.path("phases"))
        + "\nEngine: "
        + root.path("processEngine").path("description").asText("")
        + "\nGuidelines: "
        + joinGuidelines(root.path("guidelines"));
  }

  private String compactPhase(JsonNode phase) {
    return "Phase "
        + phase.path("name").asText("")
        + "\nObjective: "
        + phase.path("objective").asText("")
        + "\nStages: "
        + joinNames(phase.path("stages"))
        + "\nPrimary role: "
        + phase.path("primaryRole").asText("");
  }

  private String compactStage(JsonNode stage) {
    List<String> tasks = new ArrayList<>();
    for (JsonNode task : iterable(stage.path("tasks"))) {
      tasks.add(
          task.path("name").asText("")
              + " steps="
              + joinStrings(task.path("steps"))
              + " paletteFocus="
              + joinStrings(task.path("paletteFocus"))
              + " exit="
              + joinStrings(task.path("exitCriteria")));
    }
    return "Stage "
        + stage.path("name").asText("")
        + "\nObjective: "
        + stage.path("objective").asText("")
        + "\nTasks: "
        + String.join(" | ", tasks);
  }

  private Iterable<JsonNode> iterable(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    return node;
  }

  private String joinNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    for (JsonNode item : iterable(node)) {
      String name = item.path("name").asText(item.path("id").asText(""));
      if (!name.isBlank()) {
        names.add(name);
      }
    }
    return String.join(", ", names);
  }

  private String joinStrings(JsonNode node) {
    List<String> values = new ArrayList<>();
    for (JsonNode item : iterable(node)) {
      if (item.isTextual()) {
        values.add(item.asText());
      } else if (item.hasNonNull("name")) {
        values.add(item.path("name").asText());
      } else if (item.hasNonNull("id")) {
        values.add(item.path("id").asText());
      }
    }
    return String.join("; ", values);
  }

  private String joinGuidelines(JsonNode node) {
    List<String> values = new ArrayList<>();
    for (JsonNode item : iterable(node)) {
      String name = item.path("name").asText("");
      String text = item.path("text").asText("");
      if (!name.isBlank() || !text.isBlank()) {
        values.add((name + " " + text).trim());
      }
    }
    return String.join(" | ", values);
  }

  private String featureName(String trimmed) {
    String[] parts = trimmed.split("\\s+");
    return parts.length > 1 ? parts[1].replaceAll("[;:]", "") : "feature";
  }

  private String featureKind(String trimmed) {
    if (trimmed.startsWith("attr ")) {
      return "attribute";
    }
    if (trimmed.startsWith("ref ")) {
      return "reference";
    }
    return "containment";
  }

  private String blankToDefault(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private void upsert(Document document) {
    jdbc.update(
        """
                INSERT INTO assistant_retrieval_documents
                  (id, scope, source, source_hash, title, content, metadata, embedding,
                   updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector, ?)
                ON CONFLICT (scope, source, source_hash, title) DO UPDATE SET
                  title = EXCLUDED.title, content = EXCLUDED.content,
                  metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding,
                  updated_at = EXCLUDED.updated_at
        """,
        document.id(),
        document.scope(),
        document.source(),
        document.sourceHash(),
        document.title(),
        document.content(),
        document.metadataJson(),
        embeddings.vectorLiteral(document.title() + "\n" + document.content()),
        Timestamp.from(Instant.now()));
  }

  private Document document(
      String source,
      String hash,
      String scope,
      String title,
      String content,
      Map<String, Object> metadata) {
    Map<String, Object> enriched = new java.util.LinkedHashMap<>(metadata);
    enriched.putIfAbsent("level", level(source));
    return new Document(
        java.util.UUID.randomUUID().toString(), scope, source, hash, title, content, enriched);
  }

  private String level(String source) {
    String normalized = source.toLowerCase(Locale.ROOT).replace('\\', '/');
    if (normalized.contains("/shared/")) {
      return "SHARED";
    }
    if (normalized.contains("/cim/")
        || normalized.endsWith("/cim.json")
        || normalized.contains("cim-modeling-methodology")) {
      return "CIM";
    }
    if (normalized.contains("/pim/")
        || normalized.endsWith("/pim.json")
        || normalized.contains("pim-modeling-methodology")) {
      return "PIM";
    }
    if (normalized.contains("/psm/")
        || normalized.endsWith("/psm.json")
        || normalized.contains("psm-modeling-methodology")) {
      return "PSM";
    }
    return "";
  }

  private AssistantModelProvider.ContextSnippet snippet(
      String source, String title, String content) {
    return new AssistantModelProvider.ContextSnippet(source, title, content);
  }

  private Document sanitizeEvlValidationReferences(Document document) {
    String title = removeEvlSegments(document.title()).trim();
    String content = removeEvlSegments(document.content()).trim();
    return new Document(
        document.id(),
        document.scope(),
        document.source(),
        document.sourceHash(),
        title,
        content,
        document.metadata());
  }

  private String removeEvlSegments(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String withoutLines =
        java.util.Arrays.stream(value.split("\\R"))
            .map(this::removeEvlInlineSegments)
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .collect(java.util.stream.Collectors.joining("\n"));
    return withoutLines
        .replaceAll("(?i)\\bsemantic validation\\b", "")
        .replaceAll("(?i)\\bevl\\b", "")
        .replaceAll("\\s{2,}", " ")
        .trim();
  }

  private String removeEvlInlineSegments(String line) {
    if (line == null || line.isBlank()) {
      return "";
    }
    String byPipe = removeDelimitedEvlSegments(line, "\\|", " | ");
    String bySemicolon = removeDelimitedEvlSegments(byPipe, ";", "; ");
    return containsEvlReference(bySemicolon) ? "" : bySemicolon;
  }

  private String removeDelimitedEvlSegments(String value, String delimiterRegex, String joiner) {
    List<String> kept =
        java.util.Arrays.stream(value.split(delimiterRegex))
            .map(String::trim)
            .filter(part -> !part.isBlank())
            .filter(part -> !containsEvlReference(part))
            .toList();
    return String.join(joiner, kept);
  }

  private boolean containsEvlReference(String value) {
    String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
    return normalized.contains("evl") || normalized.contains("semantic validation");
  }

  private String hash(Path path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update(INDEX_FORMAT_VERSION.getBytes(StandardCharsets.UTF_8));
    byte[] bytes = Files.readAllBytes(path);
    return HexFormat.of().formatHex(digest.digest(bytes));
  }

  private record Document(
      String id,
      String scope,
      String source,
      String sourceHash,
      String title,
      String content,
      Map<String, Object> metadata) {

    String metadataJson() {
      try {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata);
      } catch (Exception ex) {
        return "{}";
      }
    }
  }
}
