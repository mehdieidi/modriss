package io.mehdieidi.varka.platform.export.application;

import io.mehdieidi.varka.platform.artifact.domain.ArtifactRecord;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.domain.ModelRecord;
import io.mehdieidi.varka.platform.project.application.ProjectService;
import io.mehdieidi.varka.platform.project.domain.ProjectRecord;
import io.mehdieidi.varka.platform.storage.api.PlatformStore;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobRecord;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Packages a complete project repository and generated artifact files as a ZIP archive. */
public final class ProjectArchiveService {

  private final PlatformStore store;
  private final ProjectService projects;

  public ProjectArchiveService(PlatformStore store, ProjectService projects) {
    this.store = store;
    this.projects = projects;
  }

  public byte[] zip(UserRecord user, String projectId) {
    ProjectRecord project = projects.get(user, projectId);
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bytes)) {
      addStoredProjectRecords(zip, project);
      addExpandedArtifactFiles(zip, project.id());
      zip.finish();
      return bytes.toByteArray();
    } catch (IOException ex) {
      throw new PlatformException(500, "Could not package project.");
    }
  }

  private void addStoredProjectRecords(ZipOutputStream zip, ProjectRecord project)
      throws IOException {
    addJsonEntry(zip, "varka-project/project.json", project);
    for (ModelLevel level : ModelLevel.values()) {
      for (ModelRecord model :
          store.list(
              Path.of("projects", project.id(), "models", level.apiName()), ModelRecord.class)) {
        String root = "varka-project/models/" + level.apiName() + "/" + model.id();
        addJsonEntry(zip, root + ".json", model);
        store
            .readBytes(
                Path.of("projects", project.id(), "models", level.apiName(), model.id() + ".xmi"))
            .ifPresent(bytes -> addFileEntryUnchecked(zip, root + ".xmi", bytes));
      }
    }
    for (ArtifactRecord artifact :
        store.list(Path.of("projects", project.id(), "artifacts"), ArtifactRecord.class)) {
      addJsonEntry(zip, "varka-project/artifacts/" + artifact.id() + ".json", artifact);
    }
    for (MdeJobRecord job :
        store.list(Path.of("projects", project.id(), "mde-jobs"), MdeJobRecord.class)) {
      addJsonEntry(zip, "varka-project/mde-jobs/" + job.id() + ".json", job);
    }
  }

  private void addExpandedArtifactFiles(ZipOutputStream zip, String projectId) throws IOException {
    for (ArtifactRecord artifact :
        store.list(Path.of("projects", projectId, "artifacts"), ArtifactRecord.class)) {
      String artifactRoot =
          "artifacts/"
              + archiveSegment(artifact.name(), artifact.id())
              + "-"
              + archiveSegment(shortId(artifact.id()), artifact.id());
      Map<String, String> artifactFiles = artifact.files() == null ? Map.of() : artifact.files();
      for (Map.Entry<String, String> file : artifactFiles.entrySet()) {
        String normalizedPath = normalizeArtifactPath(file.getKey());
        addFileEntry(
            zip,
            artifactRoot + "/" + normalizedPath,
            String.valueOf(file.getValue() == null ? "" : file.getValue())
                .getBytes(StandardCharsets.UTF_8));
      }
    }
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
    String normalized = name.replace('\\', '/').replaceAll("/+", "/");
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
    String normalized =
        String.valueOf(path == null ? "" : path).replace('\\', '/').replaceAll("/+", "/");
    if (normalized.startsWith("/")
        || normalized.contains("../")
        || normalized.equals("..")
        || normalized.isBlank()) {
      throw new PlatformException(400, "Invalid artifact file path.");
    }
    return normalized;
  }
}
