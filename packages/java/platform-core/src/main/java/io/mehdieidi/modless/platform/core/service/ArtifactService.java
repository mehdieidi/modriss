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
import io.mehdieidi.modless.platform.core.repository.JsonFileStore;
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

public final class ArtifactService {

    private final JsonFileStore store;
    private final ProjectService projectService;

    public ArtifactService(JsonFileStore store, ProjectService projectService) {
        this.store = store;
        this.projectService = projectService;
    }

    public List<ArtifactRecord> list(UserRecord user, String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return List.of();
        }
        projectService.get(user, projectId);
        Path dir = store.resolve(Path.of("projects", projectId, "artifacts"));
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readSummary)
                    .filter(artifact -> artifact != null)
                    .sorted(Comparator.comparing(ArtifactRecord::updatedAt).reversed())
                    .toList();
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not list artifacts.");
        }
    }

    public ArtifactRecord get(UserRecord user, String id) {
        ArtifactRecord artifact = find(id);
        projectService.get(user, artifact.projectId());
        return repairLegacyMetadata(artifact);
    }

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

    public String readFile(UserRecord user, String artifactId, String path) {
        ArtifactRecord artifact = get(user, artifactId);
        String normalized = normalizePath(path);
        if (!artifact.files().containsKey(normalized)) {
            throw new PlatformException(404, "Artifact file not found.");
        }
        return artifact.files().get(normalized);
    }

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

    public ArtifactRecord find(String id) {
        var index = store.read(artifactIndexPath(id), ArtifactIndexRecord.class);
        if (index.isPresent()) {
            return store.require(artifactPath(index.get().projectId(), id), ArtifactRecord.class,
                    "Artifact not found.");
        }
        return findLegacyAndIndex(id);
    }

    public JsonNode filesNode(Map<String, String> files) {
        return store.objectMapper().valueToTree(files);
    }

    private Path artifactPath(String projectId, String id) {
        return Path.of("projects", projectId, "artifacts", id + ".json");
    }

    private Path artifactIndexPath(String id) {
        return Path.of("indexes", "artifacts", id + ".json");
    }

    private void writeArtifactIndex(ArtifactRecord artifact) {
        store.write(artifactIndexPath(artifact.id()),
                new ArtifactIndexRecord(artifact.id(), artifact.projectId()));
    }

    private ArtifactRecord findLegacyAndIndex(String id) {
        try (Stream<Path> projectDirs = Files.list(store.resolve(Path.of("projects")))) {
            ArtifactRecord artifact = projectDirs.map(project -> store.read(Path.of("projects",
                                    project.getFileName().toString(), "artifacts", id + ".json"),
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

    private Instant parseInstant(String value, Instant fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Instant.parse(value);
        } catch (Exception ex) {
            return fallback;
        }
    }

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

    private String normalizePath(String path) {
        String normalized = String.valueOf(path == null ? "" : path).replace('\\', '/');
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.contains("..")) {
            throw new PlatformException(400, "Invalid artifact file path.");
        }
        return normalized;
    }
}
