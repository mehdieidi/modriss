package io.mehdieidi.modless.platform.core.service;

import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ArtifactRecord;
import io.mehdieidi.modless.platform.core.model.MdeJobRecord;
import io.mehdieidi.modless.platform.core.model.MemberRole;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.model.ProjectMember;
import io.mehdieidi.modless.platform.core.model.ProjectRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.repository.PlatformStore;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Manages project metadata, membership, and access checks. */
public final class ProjectService {

  /** File repository used for project records. */
  private final PlatformStore store;

  /** Authentication service used to resolve invited users. */
  private final AuthService authService;

  /**
   * Creates a project service.
   *
   * @param store backing JSON repository
   * @param authService authentication service for user lookup
   */
  public ProjectService(PlatformStore store, AuthService authService) {
    this.store = store;
    this.authService = authService;
  }

  /**
   * Lists projects visible to a user.
   *
   * @param user requesting user
   * @return visible projects ordered by last update
   */
  public List<ProjectRecord> list(UserRecord user) {
    try {
      return store.list(Path.of("projects"), ProjectRecord.class).stream()
          .filter(project -> project != null && canRead(project, user.id()))
          .sorted(
              Comparator.comparing(
                      ProjectRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                  .reversed())
          .toList();
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not list stored data.");
    }
  }

  /**
   * Creates a project owned by the supplied user.
   *
   * @param owner project owner
   * @param name project name
   * @param description project description
   * @return created project
   */
  public ProjectRecord create(UserRecord owner, String name, String description) {
    Instant now = Instant.now();
    ProjectRecord project =
        new ProjectRecord(
            UUID.randomUUID().toString(),
            requireName(name),
            description == null ? "" : description.trim(),
            owner.id(),
            new LinkedHashMap<>(),
            List.of(member(owner, MemberRole.OWNER, now)),
            now,
            now);
    store.write(projectPath(project.id()), project);
    return project;
  }

  /**
   * Loads a project and verifies read access.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @return project record
   */
  public ProjectRecord get(UserRecord user, String projectId) {
    ProjectRecord project =
        store.require(projectPath(projectId), ProjectRecord.class, "Project not found.");
    if (!canRead(project, user.id())) {
      throw new PlatformException(403, "You do not have access to this project.");
    }
    return project;
  }

  /**
   * Updates project metadata and active model pointers.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @param name replacement project name
   * @param description replacement project description
   * @param activeModelIds active model id mapping
   * @return updated project
   */
  public ProjectRecord update(
      UserRecord user,
      String projectId,
      String name,
      String description,
      Map<String, String> activeModelIds) {
    ProjectRecord project = get(user, projectId);
    requireEditor(project, user.id());
    ProjectRecord updated =
        new ProjectRecord(
            project.id(),
            requireName(name),
            description == null ? "" : description.trim(),
            project.ownerUserId(),
            activeModelIds == null ? Map.of() : new LinkedHashMap<>(activeModelIds),
            project.members(),
            project.createdAt(),
            Instant.now());
    store.write(projectPath(project.id()), updated);
    return updated;
  }

  /**
   * Deletes a project and all files under its project directory.
   *
   * @param user requesting user
   * @param projectId project identifier
   */
  public void delete(UserRecord user, String projectId) {
    ProjectRecord project = get(user, projectId);
    if (!project.ownerUserId().equals(user.id())) {
      throw new PlatformException(403, "Only the project owner can delete a project.");
    }
    store.deleteTree(Path.of("projects", projectId));
  }

  /**
   * Packages the complete project repository and expanded generated artifact files as ZIP.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @return ZIP archive bytes
   */
  public byte[] zip(UserRecord user, String projectId) {
    ProjectRecord project = get(user, projectId);
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

  /**
   * Lists project members visible to a user with project access.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @return project members
   */
  public List<ProjectMember> members(UserRecord user, String projectId) {
    return get(user, projectId).members();
  }

  /**
   * Adds or updates a project membership for an existing user.
   *
   * @param inviter requesting user
   * @param projectId project identifier
   * @param email invited user's email
   * @param role requested role; owner requests are downgraded to editor
   * @return added member
   */
  public ProjectMember invite(UserRecord inviter, String projectId, String email, MemberRole role) {
    ProjectRecord project = get(inviter, projectId);
    requireEditor(project, inviter.id());
    UserRecord invited = authService.findByEmail(email);
    if (invited == null) {
      throw new PlatformException(404, "No registered user exists for this email.");
    }
    Instant now = Instant.now();
    ProjectMember newMember =
        member(invited, role == null || role == MemberRole.OWNER ? MemberRole.EDITOR : role, now);
    List<ProjectMember> members =
        new ArrayList<>(
            project.members().stream()
                .filter(existing -> !existing.userId().equals(invited.id()))
                .toList());
    members.add(newMember);
    store.write(
        projectPath(project.id()),
        new ProjectRecord(
            project.id(),
            project.name(),
            project.description(),
            project.ownerUserId(),
            project.activeModelIds(),
            members,
            project.createdAt(),
            now));
    return newMember;
  }

  /**
   * Removes a non-owner project member.
   *
   * @param requester requesting user
   * @param projectId project identifier
   * @param userId member user id to remove
   */
  public void revoke(UserRecord requester, String projectId, String userId) {
    ProjectRecord project = get(requester, projectId);
    if (!project.ownerUserId().equals(requester.id())) {
      throw new PlatformException(403, "Only the project owner can revoke access.");
    }
    if (project.ownerUserId().equals(userId)) {
      throw new PlatformException(400, "Project owner access cannot be revoked.");
    }
    List<ProjectMember> members =
        project.members().stream().filter(member -> !member.userId().equals(userId)).toList();
    store.write(
        projectPath(project.id()),
        new ProjectRecord(
            project.id(),
            project.name(),
            project.description(),
            project.ownerUserId(),
            project.activeModelIds(),
            members,
            project.createdAt(),
            Instant.now()));
  }

  /**
   * Requires the user to have owner or editor access to a project.
   *
   * @param project project record
   * @param userId requesting user identifier
   */
  public void requireEditor(ProjectRecord project, String userId) {
    ProjectMember member =
        project.members().stream()
            .filter(candidate -> candidate.userId().equals(userId))
            .findFirst()
            .orElse(null);
    if (member == null || member.role() == MemberRole.VIEWER) {
      throw new PlatformException(403, "Editor access is required.");
    }
  }

  /**
   * Checks whether a project is readable by a user.
   *
   * @param project project record
   * @param userId user identifier
   * @return {@code true} when the user is a member
   */
  private boolean canRead(ProjectRecord project, String userId) {
    if (project.members() == null) {
      return false;
    }
    return project.members().stream().anyMatch(member -> member.userId().equals(userId));
  }

  /**
   * Creates a membership snapshot from a user record.
   *
   * @param user user record
   * @param role assigned role
   * @param now membership timestamp
   * @return project member record
   */
  private ProjectMember member(UserRecord user, MemberRole role, Instant now) {
    return new ProjectMember(user.id(), user.email(), user.displayName(), role, now);
  }

  /**
   * Returns the repository path for a project record.
   *
   * @param projectId project identifier
   * @return repository-relative path
   */
  private Path projectPath(String projectId) {
    return Path.of("projects", projectId, "project.json");
  }

  /**
   * Adds stored project metadata, model records, and artifact records to the ZIP.
   *
   * @param zip target archive
   * @param projectDir resolved project repository directory
   * @throws IOException when a file cannot be read or written to the archive
   */
  private void addStoredProjectRecords(ZipOutputStream zip, ProjectRecord project)
      throws IOException {
    addJsonEntry(zip, "modless-project/project.json", project);
    for (ModelLevel level : ModelLevel.values()) {
      for (ModelRecord model :
          store.list(
              Path.of("projects", project.id(), "models", level.apiName()), ModelRecord.class)) {
        String root = "modless-project/models/" + level.apiName() + "/" + model.id();
        addJsonEntry(zip, root + ".json", model);
        store
            .readBytes(
                Path.of("projects", project.id(), "models", level.apiName(), model.id() + ".xmi"))
            .ifPresent(bytes -> addFileEntryUnchecked(zip, root + ".xmi", bytes));
      }
    }
    for (ArtifactRecord artifact :
        store.list(Path.of("projects", project.id(), "artifacts"), ArtifactRecord.class)) {
      addJsonEntry(zip, "modless-project/artifacts/" + artifact.id() + ".json", artifact);
    }
    for (MdeJobRecord job :
        store.list(Path.of("projects", project.id(), "mde-jobs"), MdeJobRecord.class)) {
      addJsonEntry(zip, "modless-project/mde-jobs/" + job.id() + ".json", job);
    }
  }

  /**
   * Adds generated artifact file trees from each artifact record to the ZIP.
   *
   * @param zip target archive
   * @param projectDir resolved project repository directory
   * @throws IOException when an artifact file cannot be read or written to the archive
   */
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

  /**
   * Writes one normalized file entry into the ZIP archive.
   *
   * @param zip target archive
   * @param name archive entry name
   * @param bytes file bytes
   * @throws IOException when the entry cannot be written
   */
  private void addFileEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
    String normalized = name.replace('\\', '/').replaceAll("/+", "/");
    zip.putNextEntry(new ZipEntry(normalized));
    zip.write(bytes == null ? new byte[0] : bytes);
    zip.closeEntry();
  }

  /**
   * Converts an arbitrary label into a safe archive path segment.
   *
   * @param label preferred label
   * @param fallback fallback value
   * @return path-safe archive segment
   */
  private String archiveSegment(String label, String fallback) {
    String value = label == null || label.isBlank() ? fallback : label;
    String sanitized = value.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
    return sanitized.isBlank() ? "artifact" : sanitized;
  }

  /**
   * Returns a compact identifier suffix for disambiguating archive paths.
   *
   * @param id full identifier
   * @return first eight identifier characters, or the full nonblank identifier if shorter
   */
  private String shortId(String id) {
    String value = String.valueOf(id == null ? "" : id).trim();
    return value.length() <= 8 ? value : value.substring(0, 8);
  }

  /**
   * Normalizes and validates generated artifact paths before adding them to a ZIP.
   *
   * @param path artifact-relative path
   * @return normalized relative path
   */
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

  /**
   * Validates and trims a project name.
   *
   * @param name project name
   * @return trimmed name
   */
  private String requireName(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new PlatformException(400, "Project name is required.");
    }
    return name.trim();
  }
}
