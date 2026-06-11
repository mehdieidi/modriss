package io.mehdieidi.modless.platform.core.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ArtifactIndexRecord;
import io.mehdieidi.modless.platform.core.model.ArtifactRecord;
import io.mehdieidi.modless.platform.core.model.ProjectRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.repository.PlatformStore;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Manages generated artifact bundles and their editable file contents.
 */
public final class ArtifactService {

    /**
     * File repository used for artifact records and indexes.
     */
    private final PlatformStore store;
    /**
     * Project service used for access and editor checks.
     */
    private final ProjectService projectService;

    /**
     * Creates an artifact service.
     *
     * @param store          backing JSON repository
     * @param projectService project access service
     */
    public ArtifactService(PlatformStore store, ProjectService projectService) {
        this.store = store;
        this.projectService = projectService;
    }

    /**
     * Lists artifact summaries for a project visible to the user.
     *
     * @param user      requesting user
     * @param projectId project identifier
     * @return artifact summaries ordered by last update
     */
    public List<ArtifactRecord> list(UserRecord user, String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return List.of();
        }
        projectService.get(user, projectId);
        return store.list(Path.of("projects", projectId, "artifacts"),
                        ArtifactRecord.class).stream()
                .sorted(Comparator.comparing(ArtifactRecord::updatedAt).reversed())
                .toList();
    }

    /**
     * Loads an artifact after verifying project access.
     *
     * @param user requesting user
     * @param id   artifact identifier
     * @return artifact record
     */
    public ArtifactRecord get(UserRecord user, String id) {
        ArtifactRecord artifact = find(id);
        projectService.get(user, artifact.projectId());
        return repairLegacyMetadata(artifact);
    }

    /**
     * Creates an artifact bundle under a project.
     *
     * @param user      requesting user
     * @param projectId project identifier
     * @param name      artifact display name
     * @param files     artifact files keyed by path
     * @return created artifact
     */
    public ArtifactRecord create(UserRecord user, String projectId, String name,
            Map<String, String> files) {
        ProjectRecord project = projectService.get(user, projectId);
        projectService.requireEditor(project, user.id());
        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        Map<String, String> normalizedFiles = new LinkedHashMap<>(files);
        ObjectNode modelJson = store.objectMapper().createObjectNode();
        modelJson.put("name", name);
        modelJson.put("fileCount", normalizedFiles.size());
        ArtifactRecord artifact = new ArtifactRecord(id, projectId, name, modelJson,
                normalizedFiles, now, now);
        store.write(artifactPath(projectId, id), artifact);
        writeArtifactIndex(artifact);
        return artifact;
    }

    /**
     * Reads one file from an artifact bundle.
     *
     * @param user       requesting user
     * @param artifactId artifact identifier
     * @param path       artifact-relative file path
     * @return file content
     */
    public String readFile(UserRecord user, String artifactId, String path) {
        ArtifactRecord artifact = get(user, artifactId);
        String normalized = normalizePath(path);
        if (!artifact.files().containsKey(normalized)) {
            throw new PlatformException(404, "Artifact file not found.");
        }
        return artifact.files().get(normalized);
    }

    /**
     * Replaces or adds one file in an artifact bundle.
     *
     * @param user       requesting user
     * @param artifactId artifact identifier
     * @param path       artifact-relative file path
     * @param content    replacement content
     * @return updated artifact
     */
    public ArtifactRecord updateFile(UserRecord user, String artifactId, String path,
            String content) {
        ArtifactRecord artifact = get(user, artifactId);
        ProjectRecord project = projectService.get(user, artifact.projectId());
        projectService.requireEditor(project, user.id());
        Map<String, String> files = new LinkedHashMap<>(artifact.files());
        files.put(normalizePath(path), content == null ? "" : content);
        ObjectNode modelJson = store.objectMapper().createObjectNode();
        modelJson.put("name", artifact.name());
        modelJson.put("fileCount", files.size());
        ArtifactRecord updated = new ArtifactRecord(artifact.id(), artifact.projectId(),
                artifact.name(), modelJson,
                files, artifact.createdAt(), Instant.now());
        store.write(artifactPath(artifact.projectId(), artifact.id()), updated);
        writeArtifactIndex(updated);
        return updated;
    }

    /**
     * Packages an artifact bundle as a UTF-8 zip archive.
     *
     * @param user       requesting user
     * @param artifactId artifact identifier
     * @return zip archive bytes
     */
    public byte[] zip(UserRecord user, String artifactId) {
        ArtifactRecord artifact = get(user, artifactId);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : artifact.files().entrySet()) {
                zip.putNextEntry(new ZipEntry(normalizePath(entry.getKey())));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not package artifact.");
        }
    }

    /**
     * Finds an artifact by id using the global index with legacy fallback.
     *
     * @param id artifact identifier
     * @return artifact record
     */
    public ArtifactRecord find(String id) {
        var index = store.read(artifactIndexPath(id), ArtifactIndexRecord.class);
        if (index.isPresent()) {
            return store.require(artifactPath(index.get().projectId(), id), ArtifactRecord.class,
                    "Artifact not found.");
        }
        return findLegacyAndIndex(id);
    }

    /**
     * Converts artifact file maps to JSON nodes for API responses.
     *
     * @param files artifact file map
     * @return JSON representation of the files map
     */
    public JsonNode filesNode(Map<String, String> files) {
        return store.objectMapper().valueToTree(files);
    }

    /**
     * Returns the repository path for an artifact record.
     *
     * @param projectId project identifier
     * @param id        artifact identifier
     * @return repository-relative path
     */
    private Path artifactPath(String projectId, String id) {
        return Path.of("projects", projectId, "artifacts", id + ".json");
    }

    /**
     * Returns the repository path for an artifact index record.
     *
     * @param id artifact identifier
     * @return repository-relative path
     */
    private Path artifactIndexPath(String id) {
        return Path.of("indexes", "artifacts", id + ".json");
    }

    /**
     * Writes the global artifact index entry.
     *
     * @param artifact artifact to index
     */
    private void writeArtifactIndex(ArtifactRecord artifact) {
        store.write(artifactIndexPath(artifact.id()),
                new ArtifactIndexRecord(artifact.id(), artifact.projectId()));
    }

    /**
     * Searches legacy per-project artifact locations and writes an index entry after a match is
     * found.
     *
     * @param id artifact identifier
     * @return artifact record
     */
    private ArtifactRecord findLegacyAndIndex(String id) {
        try {
            ArtifactRecord artifact = store.list(Path.of("projects"), ProjectRecord.class).stream()
                    .map(project -> store.read(Path.of("projects", project.id(), "artifacts",
                                    id + ".json"),
                            ArtifactRecord.class).orElse(null))
                    .filter(candidate -> candidate != null)
                    .findFirst()
                    .orElseThrow(() -> new PlatformException(404, "Artifact not found."));
            writeArtifactIndex(artifact);
            return artifact;
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not read artifact store.");
        }
    }

    /**
     * Reads only summary fields from an artifact JSON file without materializing the full file
     * map.
     *
     * @param path resolved artifact JSON path
     * @return summary record, or {@code null} when the file cannot be summarized
     */
    private ArtifactRecord readSummary(Path path) {
        try (JsonParser parser = store.objectMapper().getFactory().createParser(path.toFile())) {
            String id = null;
            String projectId = null;
            String name = null;
            Instant updatedAt = Files.getLastModifiedTime(path).toInstant();
            Instant createdAt = updatedAt;
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return null;
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName();
                parser.nextToken();
                if ("id".equals(field)) {
                    id = parser.getValueAsString();
                } else if ("projectId".equals(field)) {
                    projectId = parser.getValueAsString();
                } else if ("name".equals(field)) {
                    name = parser.getValueAsString();
                } else if ("createdAt".equals(field)) {
                    createdAt = parseInstant(parser.getValueAsString(), createdAt);
                } else if ("updatedAt".equals(field)) {
                    updatedAt = parseInstant(parser.getValueAsString(), updatedAt);
                } else if ("modelJson".equals(field) || "files".equals(field)) {
                    break;
                } else {
                    parser.skipChildren();
                }
            }
            if (id == null || projectId == null) {
                return null;
            }
            ObjectNode modelJson = store.objectMapper().createObjectNode();
            modelJson.put("name", name == null ? id : name);
            return new ArtifactRecord(id, projectId, name == null ? id : name, modelJson,
                    Map.of(), createdAt, updatedAt);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Parses an instant with a fallback for absent or legacy values.
     *
     * @param value    raw instant text
     * @param fallback fallback timestamp
     * @return parsed or fallback timestamp
     */
    private Instant parseInstant(String value, Instant fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Instant.parse(value);
        } catch (Exception ex) {
            return fallback;
        }
    }

    /**
     * Migrates legacy artifact metadata that incorrectly stored files inside {@code modelJson}.
     *
     * @param artifact artifact record to inspect
     * @return original or repaired artifact
     */
    private ArtifactRecord repairLegacyMetadata(ArtifactRecord artifact) {
        if (!artifact.modelJson().path("files").isObject()) {
            return artifact;
        }
        ObjectNode modelJson = store.objectMapper().createObjectNode();
        modelJson.put("name", artifact.name());
        modelJson.put("fileCount", artifact.files().size());
        ArtifactRecord repaired = new ArtifactRecord(artifact.id(), artifact.projectId(),
                artifact.name(), modelJson, artifact.files(), artifact.createdAt(),
                artifact.updatedAt());
        store.write(artifactPath(artifact.projectId(), artifact.id()), repaired);
        writeArtifactIndex(repaired);
        return repaired;
    }

    /**
     * Normalizes and validates an artifact-relative path.
     *
     * @param path raw path
     * @return normalized path using forward slashes
     */
    private String normalizePath(String path) {
        String normalized = String.valueOf(path == null ? "" : path).replace('\\', '/');
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.contains("..")) {
            throw new PlatformException(400, "Invalid artifact file path.");
        }
        return normalized;
    }
}
