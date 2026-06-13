package io.mehdieidi.modless.backend.assistant;

import jakarta.annotation.PostConstruct;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Builds and queries compact metamodel and EVL catalogs for assistant retrieval. */
@Service
public class AssistantCatalogService {

  private static final String INDEX_FORMAT_VERSION = "2";

  private final JdbcTemplate jdbc;
  private final LocalAssistantEmbeddingService embeddings;

  /**
   * Creates the catalog service.
   *
   * @param jdbc JDBC access
   */
  public AssistantCatalogService(JdbcTemplate jdbc) {
    this(jdbc, new LocalAssistantEmbeddingService());
  }

  /**
   * Creates the catalog service.
   *
   * @param jdbc JDBC access
   * @param embeddings local embedding service
   */
  @Autowired
  public AssistantCatalogService(JdbcTemplate jdbc, LocalAssistantEmbeddingService embeddings) {
    this.jdbc = jdbc;
    this.embeddings = embeddings;
  }

  /** Rebuilds catalogs during application startup. */
  @PostConstruct
  public void initialize() {
    refresh();
  }

  /** Reindexes the local metamodel and EVL files when content hashes change. */
  public void refresh() {
    Path root = Path.of("mde");
    if (!Files.exists(root)) {
      return;
    }
    try {
      Files.walk(root)
          .filter(Files::isRegularFile)
          .filter(path -> matches(path, ".emf", ".ecore", ".evl"))
          .forEach(this::indexFile);
    } catch (Exception ex) {
      throw new IllegalStateException("Could not index assistant catalogs.", ex);
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
      List<Document> documents =
          path.toString().toLowerCase(Locale.ROOT).endsWith(".evl")
              ? parseEvl(path, source, hash)
              : parseMetamodel(path, source, hash);
      for (Document document : documents) {
        upsert(document);
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

  private List<Document> parseEvl(Path path, String source, String hash) throws Exception {
    List<Document> documents = new ArrayList<>();
    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
    String context = "";
    String currentRuleName = null;
    String currentRuleKind = null;
    StringBuilder currentRuleBody = null;
    int braceDepth = 0;
    for (int lineNumber = 0; lineNumber < lines.size(); lineNumber++) {
      String line = lines.get(lineNumber);
      String trimmed = line.trim();
      if (currentRuleName == null && trimmed.startsWith("context ")) {
        context = trimmed.substring(8).trim();
      } else if (currentRuleName == null
          && (trimmed.startsWith("constraint ") || trimmed.startsWith("critique "))) {
        currentRuleKind = trimmed.startsWith("critique ") ? "optional" : "mandatory";
        String keyword = trimmed.startsWith("critique ") ? "critique " : "constraint ";
        currentRuleName = trimmed.substring(keyword.length()).split("[\\s\\{]", 2)[0].trim();
        currentRuleBody = new StringBuilder();
        braceDepth = 0;
      }
      if (currentRuleName != null) {
        currentRuleBody.append(line).append('\n');
        braceDepth += count(line, '{') - count(line, '}');
        if (braceDepth <= 0 && trimmed.endsWith("}")) {
          documents.add(
              document(
                  source,
                  hash,
                  "constraint",
                  currentRuleName,
                  "context " + context + "\nkind " + currentRuleKind + "\n" + currentRuleBody,
                  Map.of(
                      "context",
                      context,
                      "constraintKind",
                      currentRuleKind,
                      "line",
                      lineNumber + 1)));
          currentRuleName = null;
          currentRuleKind = null;
          currentRuleBody = null;
        }
      }
    }
    return documents;
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

  private int count(String value, char needle) {
    int matches = 0;
    for (int index = 0; index < value.length(); index++) {
      if (value.charAt(index) == needle) {
        matches++;
      }
    }
    return matches;
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
    enriched.put("level", level(source));
    return new Document(
        java.util.UUID.randomUUID().toString(), scope, source, hash, title, content, enriched);
  }

  private String level(String source) {
    String normalized = source.toLowerCase(Locale.ROOT).replace('\\', '/');
    if (normalized.contains("/shared/")) {
      return "SHARED";
    }
    if (normalized.contains("/cim/")) {
      return "CIM";
    }
    if (normalized.contains("/pim/")) {
      return "PIM";
    }
    if (normalized.contains("/psm/")) {
      return "PSM";
    }
    return "";
  }

  private AssistantModelProvider.ContextSnippet snippet(
      String source, String title, String content) {
    return new AssistantModelProvider.ContextSnippet(source, title, content);
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
