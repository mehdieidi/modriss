package io.mehdieidi.modriss.platform.artifact.application;

import io.mehdieidi.modriss.platform.artifact.domain.ArtifactIndexRecord;
import io.mehdieidi.modriss.platform.artifact.domain.ArtifactRecord;
import io.mehdieidi.modriss.platform.artifact.storage.ArtifactStorage;
import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.project.application.ProjectService;
import io.mehdieidi.modriss.platform.project.domain.ProjectRecord;
import io.mehdieidi.modriss.platform.storage.api.PlatformStore;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Manages generated artifact bundles and their editable file contents. */
public final class ArtifactService {

  /** File repository used for artifact records and indexes. */
  private final PlatformStore store;

  /** Project service used for access and editor checks. */
  private final ProjectService projectService;

  /**
   * Creates an artifact service.
   *
   * @param store backing JSON repository
   * @param projectService project access service
   */
  public ArtifactService(PlatformStore store, ProjectService projectService) {
    this.store = store;
    this.projectService = projectService;
  }

  /**
   * Lists artifact summaries for a project visible to the user.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @return artifact summaries ordered by last update
   */
  public List<ArtifactRecord> list(UserRecord user, String projectId) {
    if (projectId == null || projectId.isBlank()) {
      return List.of();
    }
    projectService.get(user, projectId);
    return store.list(Path.of("projects", projectId, "artifacts"), ArtifactRecord.class).stream()
        .sorted(Comparator.comparing(ArtifactRecord::updatedAt).reversed())
        .toList();
  }

  /**
   * Lists artifact metadata and file paths without transferring file contents.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @return accessible artifact summaries ordered by last update
   */
  public List<ArtifactRecord> listSummaries(UserRecord user, String projectId) {
    if (projectId == null || projectId.isBlank()) {
      return List.of();
    }
    projectService.get(user, projectId);
    if (store instanceof ArtifactStorage optimized) {
      return optimized.listSummaries(projectId).stream()
          .sorted(Comparator.comparing(ArtifactRecord::updatedAt).reversed())
          .toList();
    }
    return list(user, projectId).stream()
        .map(
            artifact ->
                new ArtifactRecord(
                    artifact.id(),
                    artifact.projectId(),
                    artifact.name(),
                    artifact.modelJson(),
                    artifact.files().keySet().stream()
                        .collect(
                            java.util.stream.Collectors.toMap(
                                path -> path,
                                path -> "",
                                (left, right) -> left,
                                LinkedHashMap::new)),
                    artifact.createdAt(),
                    artifact.updatedAt()))
        .sorted(Comparator.comparing(ArtifactRecord::updatedAt).reversed())
        .toList();
  }

  /**
   * Loads an artifact after verifying project access.
   *
   * @param user requesting user
   * @param id artifact identifier
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
   * @param user requesting user
   * @param projectId project identifier
   * @param name artifact display name
   * @param files artifact files keyed by path
   * @return created artifact
   */
  public ArtifactRecord create(
      UserRecord user, String projectId, String name, Map<String, String> files) {
    return create(user, projectId, name, null, files);
  }

  /**
   * Creates an artifact bundle under a project with caller-provided metadata.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @param name artifact display name
   * @param metadata additional artifact metadata, or {@code null}
   * @param files artifact files keyed by path
   * @return created artifact
   */
  public ArtifactRecord create(
      UserRecord user,
      String projectId,
      String name,
      ObjectNode metadata,
      Map<String, String> files) {
    ProjectRecord project = projectService.get(user, projectId);
    projectService.requireEditor(project, user.id());
    Instant now = Instant.now();
    String id = UUID.randomUUID().toString();
    Map<String, String> normalizedFiles = normalizeFiles(files);
    ObjectNode modelJson =
        metadata == null ? store.objectMapper().createObjectNode() : metadata.deepCopy();
    modelJson.put("name", name);
    modelJson.put("fileCount", normalizedFiles.size());
    ArtifactRecord artifact =
        new ArtifactRecord(id, projectId, name, modelJson, normalizedFiles, now, now);
    store.write(artifactPath(projectId, id), artifact);
    writeArtifactIndex(artifact);
    return artifact;
  }

  /**
   * Reads one file from an artifact bundle.
   *
   * @param user requesting user
   * @param artifactId artifact identifier
   * @param path artifact-relative file path
   * @return file content
   */
  public String readFile(UserRecord user, String artifactId, String path) {
    String normalized = normalizePath(path);
    if (store instanceof ArtifactStorage optimized) {
      var index = store.read(artifactIndexPath(artifactId), ArtifactIndexRecord.class);
      if (index.isEmpty()) {
        ArtifactRecord artifact = get(user, artifactId);
        if (!artifact.files().containsKey(normalized)) {
          throw new PlatformException(404, "Artifact file not found.");
        }
        return artifact.files().get(normalized);
      }
      projectService.get(user, index.get().projectId());
      return optimized
          .readFile(artifactId, normalized)
          .orElseThrow(() -> new PlatformException(404, "Artifact file not found."));
    }
    ArtifactRecord artifact = get(user, artifactId);
    if (!artifact.files().containsKey(normalized)) {
      throw new PlatformException(404, "Artifact file not found.");
    }
    return artifact.files().get(normalized);
  }

  /**
   * Replaces or adds one file in an artifact bundle.
   *
   * @param user requesting user
   * @param artifactId artifact identifier
   * @param path artifact-relative file path
   * @param content replacement content
   * @return updated artifact
   */
  public ArtifactRecord updateFile(
      UserRecord user, String artifactId, String path, String content) {
    ArtifactRecord artifact = get(user, artifactId);
    ProjectRecord project = projectService.get(user, artifact.projectId());
    projectService.requireEditor(project, user.id());
    Map<String, String> files = new LinkedHashMap<>(artifact.files());
    files.put(normalizePath(path), content == null ? "" : content);
    ObjectNode modelJson =
        artifact.modelJson() != null && artifact.modelJson().isObject()
            ? ((ObjectNode) artifact.modelJson()).deepCopy()
            : store.objectMapper().createObjectNode();
    modelJson.remove("files");
    modelJson.put("name", artifact.name());
    modelJson.put("fileCount", files.size());
    ArtifactRecord updated =
        new ArtifactRecord(
            artifact.id(),
            artifact.projectId(),
            artifact.name(),
            modelJson,
            files,
            artifact.createdAt(),
            Instant.now());
    store.write(artifactPath(artifact.projectId(), artifact.id()), updated);
    writeArtifactIndex(updated);
    return updated;
  }

  /**
   * Packages an artifact bundle as a UTF-8 zip archive.
   *
   * @param user requesting user
   * @param artifactId artifact identifier
   * @return zip archive bytes
   */
  public byte[] zip(UserRecord user, String artifactId) {
    ArtifactRecord artifact = get(user, artifactId);
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      if (store instanceof ArtifactStorage optimized) {
        for (ArtifactStorage.ArtifactFile entry : optimized.listFiles(artifactId)) {
          zip.putNextEntry(new ZipEntry(normalizePath(entry.path())));
          zip.write(
              String.valueOf(entry.content() == null ? "" : entry.content())
                  .getBytes(StandardCharsets.UTF_8));
          zip.closeEntry();
        }
      } else {
        for (Map.Entry<String, String> entry : artifact.files().entrySet()) {
          zip.putNextEntry(new ZipEntry(normalizePath(entry.getKey())));
          zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
          zip.closeEntry();
        }
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
      return store.require(
          artifactPath(index.get().projectId(), id), ArtifactRecord.class, "Artifact not found.");
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
   * @param id artifact identifier
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
    store.write(
        artifactIndexPath(artifact.id()),
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
      ArtifactRecord artifact =
          store.list(Path.of("projects"), ProjectRecord.class).stream()
              .map(
                  project ->
                      store
                          .read(
                              Path.of("projects", project.id(), "artifacts", id + ".json"),
                              ArtifactRecord.class)
                          .orElse(null))
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
   * Migrates legacy artifact metadata that incorrectly stored files inside {@code modelJson}.
   *
   * @param artifact artifact record to inspect
   * @return original or repaired artifact
   */
  private ArtifactRecord repairLegacyMetadata(ArtifactRecord artifact) {
    if (!artifact.modelJson().path("files").isObject()) {
      return artifact;
    }
    ObjectNode modelJson = ((ObjectNode) artifact.modelJson()).deepCopy();
    modelJson.remove("files");
    modelJson.put("name", artifact.name());
    modelJson.put("fileCount", artifact.files().size());
    ArtifactRecord repaired =
        new ArtifactRecord(
            artifact.id(),
            artifact.projectId(),
            artifact.name(),
            modelJson,
            artifact.files(),
            artifact.createdAt(),
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
    normalized = normalized.replaceAll("/+", "/");
    if (normalized.isBlank() || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
      throw new PlatformException(400, "Invalid artifact file path.");
    }
    for (String segment : normalized.split("/")) {
      if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
        throw new PlatformException(400, "Invalid artifact file path.");
      }
    }
    return normalized;
  }

  private Map<String, String> normalizeFiles(Map<String, String> files) {
    Map<String, String> normalized = new LinkedHashMap<>();
    (files == null ? Map.<String, String>of() : files)
        .forEach(
            (path, content) -> normalized.put(normalizePath(path), content == null ? "" : content));
    return normalized;
  }
}
