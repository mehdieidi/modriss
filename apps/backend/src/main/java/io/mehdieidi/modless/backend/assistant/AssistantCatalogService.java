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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Builds and queries compact metamodel and EVL catalogs for assistant retrieval.
 */
@Service
public class AssistantCatalogService {

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
     * @param jdbc       JDBC access
     * @param embeddings local embedding service
     */
    @Autowired
    public AssistantCatalogService(JdbcTemplate jdbc,
            LocalAssistantEmbeddingService embeddings) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
    }

    /**
     * Rebuilds catalogs during application startup.
     */
    @PostConstruct
    public void initialize() {
        refresh();
    }

    /**
     * Reindexes the local metamodel and EVL files when content hashes change.
     */
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
    public List<AssistantModelProvider.ContextSnippet> search(String query, String level,
            int limit) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        String normalizedLevel = level == null ? "" : level.toUpperCase(Locale.ROOT);
        List<AssistantModelProvider.ContextSnippet> exact = jdbc.query("""
                SELECT source, title, content FROM assistant_retrieval_documents
                WHERE (lower(title) = lower(?) OR lower(source) = lower(?))
                  AND (? = '' OR metadata->>'level' = ?)
                ORDER BY updated_at DESC
                LIMIT ?
                """, (rs, row) -> snippet(rs.getString("source"), rs.getString("title"),
                rs.getString("content")), normalized, normalized, normalizedLevel,
                normalizedLevel, limit);
        if (!exact.isEmpty()) {
            return exact;
        }
        String fuzzyQuery = java.util.Arrays.stream(
                        normalized.toLowerCase(Locale.ROOT).split("[^a-z0-9_]+"))
                .filter(word -> word.length() > 2)
                .distinct()
                .limit(8)
                .collect(java.util.stream.Collectors.joining(" | "));
        if (fuzzyQuery.isBlank()) {
            return List.of();
        }
        String queryVector = embeddings.vectorLiteral(normalized);
        return jdbc.query("""
                SELECT source, title, content FROM assistant_retrieval_documents
                WHERE (? = '' OR metadata->>'level' = ?)
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
                """, (rs, row) -> snippet(rs.getString("source"), rs.getString("title"),
                rs.getString("content")), normalizedLevel, normalizedLevel, fuzzyQuery, fuzzyQuery,
                queryVector, limit);
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
            String existing = jdbc.query("""
                    SELECT source_hash FROM assistant_retrieval_documents
                    WHERE source = ? LIMIT 1
                    """, rs -> rs.next() ? rs.getString(1) : null, source);
            if (hash.equals(existing)) {
                return;
            }
            jdbc.update("DELETE FROM assistant_retrieval_documents WHERE source = ?", source);
            List<Document> documents = path.toString().toLowerCase(Locale.ROOT).endsWith(".evl")
                    ? parseEvl(path, source, hash) : parseMetamodel(path, source, hash);
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
        String currentClass = null;
        for (int lineNumber = 0; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber);
            String trimmed = line.trim();
            if (trimmed.startsWith("class ")) {
                currentClass = trimmed.substring(6).split("[\\s\\{]", 2)[0].trim();
                documents.add(document(source, hash, "classifier", currentClass, trimmed,
                        Map.of("kind", "class", "line", lineNumber + 1)));
            } else if (currentClass != null && trimmed.startsWith("val ")
                    || currentClass != null && trimmed.startsWith("attr ")
                    || currentClass != null && trimmed.startsWith("ref ")) {
                documents.add(document(source, hash, "feature",
                        currentClass + "." + featureName(trimmed), trimmed,
                        Map.of("owner", currentClass, "kind", featureKind(trimmed),
                                "line", lineNumber + 1)));
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
                documents.add(document(source, hash, "classifier", name,
                        kind + " " + name, Map.of("kind", kind)));
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
                    String content = "owner " + name + " feature " + featureName
                            + " kind " + featureKind + " type " + type
                            + " multiplicity " + lower + ".." + upper;
                    documents.add(document(source, hash, "feature", name + "." + featureName,
                            content, Map.of("owner", name, "kind", featureKind,
                                    "lowerBound", lower, "upperBound", upper, "type", type)));
                }
            }
        }
        return documents;
    }

    private List<Document> parseEvl(Path path, String source, String hash) throws Exception {
        List<Document> documents = new ArrayList<>();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        String context = "";
        String constraintKind = "constraint";
        for (int lineNumber = 0; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber);
            String trimmed = line.trim();
            if (trimmed.startsWith("context ")) {
                context = trimmed.substring(8).trim();
            } else if (trimmed.startsWith("constraint ") || trimmed.startsWith("critique ")) {
                constraintKind = trimmed.startsWith("critique ") ? "optional" : "mandatory";
                String keyword = trimmed.startsWith("critique ") ? "critique " : "constraint ";
                String name = trimmed.substring(keyword.length()).split("[\\s\\{]", 2)[0].trim();
                documents.add(document(source, hash, "constraint", name,
                        "context " + context + " " + constraintKind + " constraint " + name,
                        Map.of("context", context, "constraintKind", constraintKind,
                                "line", lineNumber + 1)));
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

    private void upsert(Document document) {
        jdbc.update("""
                        INSERT INTO assistant_retrieval_documents
                          (id, scope, source, source_hash, title, content, metadata, embedding,
                           updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector, ?)
                        ON CONFLICT (scope, source, source_hash, title) DO UPDATE SET
                          title = EXCLUDED.title, content = EXCLUDED.content,
                          metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding,
                          updated_at = EXCLUDED.updated_at
                """, document.id(), document.scope(), document.source(), document.sourceHash(),
                document.title(), document.content(), document.metadataJson(),
                embeddings.vectorLiteral(document.title() + "\n" + document.content()),
                Timestamp.from(Instant.now()));
    }

    private Document document(String source, String hash, String scope, String title,
            String content, Map<String, Object> metadata) {
        Map<String, Object> enriched = new java.util.LinkedHashMap<>(metadata);
        enriched.put("level", level(source));
        return new Document(java.util.UUID.randomUUID().toString(), scope, source, hash, title,
                content, enriched);
    }

    private String level(String source) {
        String normalized = source.toLowerCase(Locale.ROOT).replace('\\', '/');
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

    private AssistantModelProvider.ContextSnippet snippet(String source, String title,
            String content) {
        return new AssistantModelProvider.ContextSnippet(source, title, content);
    }

    private String hash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = Files.readAllBytes(path);
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    private record Document(String id, String scope, String source, String sourceHash,
                            String title, String content, Map<String, Object> metadata) {

        String metadataJson() {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                        metadata);
            } catch (Exception ex) {
                return "{}";
            }
        }
    }
}
