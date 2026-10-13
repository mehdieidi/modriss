package io.mehdieidi.modriss.platform.export.application;

import io.mehdieidi.modriss.platform.artifact.domain.ArtifactRecord;
import io.mehdieidi.modriss.platform.artifact.storage.ArtifactStorage;
import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.model.domain.ModelRecord;
import io.mehdieidi.modriss.platform.project.application.ProjectService;
import io.mehdieidi.modriss.platform.project.domain.ProjectRecord;
import io.mehdieidi.modriss.platform.storage.api.PlatformStore;
import io.mehdieidi.modriss.platform.transformation.domain.MdeJobRecord;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Packages a complete project repository and generated artifact files as a ZIP archive. */
public final class ProjectArchiveService {

  private final PlatformStore store;
  private final ProjectService projects;

  public ProjectArchiveService(PlatformStore store, ProjectService projects) {
    this.store = store;
    this.projects = projects;
  }

  public byte[] zip(UserRecord user, String projectId) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    writeZip(user, projectId, bytes);
    return bytes.toByteArray();
  }

  /** Writes a project archive directly to the response stream. */
  public void writeZip(UserRecord user, String projectId, OutputStream output) {
    ProjectRecord project = projects.get(user, projectId);
    List<ArtifactRecord> artifacts = artifactRecords(project.id());
    try (ZipOutputStream zip = new ZipOutputStream(output)) {
      zip.setLevel(Deflater.BEST_SPEED);
      addStoredProjectRecords(zip, project, artifacts);
      addExpandedArtifactFiles(zip, artifacts);
      zip.finish();
    } catch (IOException ex) {
      throw new PlatformException(500, "Could not package project.");
    }
  }

  private void addStoredProjectRecords(
      ZipOutputStream zip, ProjectRecord project, List<ArtifactRecord> artifacts)
      throws IOException {
    addJsonEntry(zip, "modriss-project/project.json", project);
    for (ModelLevel level : ModelLevel.values()) {
      for (ModelRecord model :
          store.list(
              Path.of("projects", project.id(), "models", level.apiName()), ModelRecord.class)) {
        String root = "modriss-project/models/" + level.apiName() + "/" + model.id();
        addJsonEntry(zip, root + ".json", model);
        store
            .readBytes(
                Path.of("projects", project.id(), "models", level.apiName(), model.id() + ".xmi"))
            .ifPresent(bytes -> addFileEntryUnchecked(zip, root + ".xmi", bytes));
      }
    }
    for (ArtifactRecord artifact : artifacts) {
      addJsonEntry(
          zip, "modriss-project/artifacts/" + artifact.id() + ".json", metadataOnly(artifact));
    }
    for (MdeJobRecord job :
        store.list(Path.of("projects", project.id(), "mde-jobs"), MdeJobRecord.class)) {
      addJsonEntry(zip, "modriss-project/mde-jobs/" + job.id() + ".json", job);
    }
  }

  private void addExpandedArtifactFiles(ZipOutputStream zip, List<ArtifactRecord> artifacts)
      throws IOException {
    for (ArtifactRecord artifact : artifacts) {
      String artifactRoot =
          "artifacts/"
              + archiveSegment(artifact.name(), artifact.id())
              + "-"
              + archiveSegment(shortId(artifact.id()), artifact.id());
      Iterable<ArtifactStorage.ArtifactFile> artifactFiles = artifactFiles(artifact);
      for (ArtifactStorage.ArtifactFile file : artifactFiles) {
        String normalizedPath = normalizeArtifactPath(file.path());
        addFileEntry(
            zip,
            artifactRoot + "/" + normalizedPath,
            String.valueOf(file.content() == null ? "" : file.content())
                .getBytes(StandardCharsets.UTF_8));
      }
    }
  }

  private List<ArtifactRecord> artifactRecords(String projectId) {
    if (store instanceof ArtifactStorage optimized) {
      return optimized.listSummaries(projectId);
    }
    return store.list(Path.of("projects", projectId, "artifacts"), ArtifactRecord.class);
  }

  private Iterable<ArtifactStorage.ArtifactFile> artifactFiles(ArtifactRecord artifact) {
    if (store instanceof ArtifactStorage optimized) {
      return optimized.listFiles(artifact.id());
    }
    Map<String, String> files = artifact.files() == null ? Map.of() : artifact.files();
    return files.entrySet().stream()
        .map(entry -> new ArtifactStorage.ArtifactFile(entry.getKey(), entry.getValue()))
        .toList();
  }

  private ArtifactRecord metadataOnly(ArtifactRecord artifact) {
    JsonNode modelJson = artifact.modelJson();
    if (modelJson != null && modelJson.isObject()) {
      modelJson = ((ObjectNode) modelJson).deepCopy();
      ((ObjectNode) modelJson).remove("files");
    }
    return new ArtifactRecord(
        artifact.id(),
        artifact.projectId(),
        artifact.name(),
        modelJson,
        Map.of(),
        artifact.createdAt(),
        artifact.updatedAt());
  }

  private void addJsonEntry(ZipOutputStream zip, String name, Object value) throws IOException {
    addFileEntry(zip, name, store.objectMapper().writeValueAsBytes(value));
  }

  private void addFileEntryUnchecked(ZipOutputStream zip, String name, byte[] bytes) {
    try {
      addFileEntry(zip, name, bytes);
    } catch (IOException ex) {
      throw new PlatformException(500, "Could not package project.");
    }
  }

  private void addFileEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
    String normalized = normalizeZipEntryName(name);
    zip.putNextEntry(new ZipEntry(normalized));
    zip.write(bytes == null ? new byte[0] : bytes);
    zip.closeEntry();
  }

  private String archiveSegment(String label, String fallback) {
    String value = label == null || label.isBlank() ? fallback : label;
    String sanitized = value.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
    return sanitized.isBlank() ? "artifact" : sanitized;
  }

  private String shortId(String id) {
    String value = String.valueOf(id == null ? "" : id).trim();
    return value.length() <= 8 ? value : value.substring(0, 8);
  }

  private String normalizeArtifactPath(String path) {
    String normalized = normalizeZipEntryName(String.valueOf(path == null ? "" : path));
    if (normalized.isBlank()) {
      throw new PlatformException(400, "Invalid artifact file path.");
    }
    return normalized;
  }

  private String normalizeZipEntryName(String path) {
    String normalized = String.valueOf(path == null ? "" : path).replace('\\', '/');
    normalized = normalized.replaceAll("/+", "/");
    if (normalized.isBlank() || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
      throw new PlatformException(400, "Invalid archive file path.");
    }
    for (String segment : normalized.split("/")) {
      if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
        throw new PlatformException(400, "Invalid archive file path.");
      }
    }
    return normalized;
  }
}
