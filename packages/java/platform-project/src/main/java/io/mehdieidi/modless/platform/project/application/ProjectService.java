package io.mehdieidi.modless.platform.project.application;

import io.mehdieidi.modless.platform.identity.application.AuthService;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import io.mehdieidi.modless.platform.project.domain.ProjectMember;
import io.mehdieidi.modless.platform.project.domain.ProjectRecord;
import io.mehdieidi.modless.platform.storage.api.PlatformStore;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Manages project metadata, membership, and access checks. */
public final class ProjectService {

  /** Reserved owner role label persisted for the project creator. */
  private static final String OWNER_ROLE = "OWNER";

  /** Maximum length accepted for user-entered role labels. */
  private static final int MAX_ROLE_LENGTH = 48;

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
            List.of(member(owner, OWNER_ROLE, now)),
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
    requireProjectId(projectId);
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
   * Marks a stored model as the project's active model for its level.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @param level model level
   * @param modelId stored model identifier
   * @return updated project
   */
  public ProjectRecord setActiveModel(
      UserRecord user, String projectId, ModelLevel level, String modelId) {
    if (level == null || modelId == null || modelId.isBlank()) {
      throw new PlatformException(400, "Model level and model id are required.");
    }
    ProjectRecord project = get(user, projectId);
    requireEditor(project, user.id());
    Map<String, String> activeModelIds =
        new LinkedHashMap<>(project.activeModelIds() == null ? Map.of() : project.activeModelIds());
    activeModelIds.put(level.apiName(), modelId);
    return update(user, projectId, project.name(), project.description(), activeModelIds);
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
   * @param role requested role label
   * @return added member
   */
  public ProjectMember invite(UserRecord inviter, String projectId, String email, String role) {
    ProjectRecord project = get(inviter, projectId);
    requireOwner(project, inviter.id(), "Only the project owner can invite people.");
    UserRecord invited = authService.findByEmail(email);
    if (invited == null) {
      throw new PlatformException(404, "No registered user exists for this email.");
    }
    if (project.ownerUserId().equals(invited.id())) {
      throw new PlatformException(400, "The project owner is already a member.");
    }
    Instant now = Instant.now();
    ProjectMember existing =
        safeMembers(project).stream()
            .filter(candidate -> candidate.userId().equals(invited.id()))
            .findFirst()
            .orElse(null);
    ProjectMember newMember =
        member(invited, requireRole(role), existing == null ? now : existing.addedAt());
    List<ProjectMember> members =
        new ArrayList<>(
            safeMembers(project).stream()
                .filter(member -> !member.userId().equals(invited.id()))
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
    requireOwner(project, requester.id(), "Only the project owner can revoke access.");
    if (project.ownerUserId().equals(userId)) {
      throw new PlatformException(400, "Project owner access cannot be revoked.");
    }
    if (safeMembers(project).stream().noneMatch(member -> member.userId().equals(userId))) {
      throw new PlatformException(404, "Project member not found.");
    }
    List<ProjectMember> members =
        safeMembers(project).stream().filter(member -> !member.userId().equals(userId)).toList();
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
   * Updates a non-owner project member's role label.
   *
   * @param requester requesting user
   * @param projectId project identifier
   * @param userId member user id to update
   * @param role replacement role label
   * @return updated member
   */
  public ProjectMember updateMemberRole(
      UserRecord requester, String projectId, String userId, String role) {
    ProjectRecord project = get(requester, projectId);
    requireOwner(project, requester.id(), "Only the project owner can change member roles.");
    if (project.ownerUserId().equals(userId)) {
      throw new PlatformException(400, "Project owner role cannot be changed.");
    }
    String normalizedRole = requireRole(role);
    ProjectMember updatedMember = null;
    List<ProjectMember> members = new ArrayList<>();
    for (ProjectMember member : safeMembers(project)) {
      if (member.userId().equals(userId)) {
        updatedMember =
            new ProjectMember(
                member.userId(),
                member.email(),
                member.displayName(),
                normalizedRole,
                member.addedAt());
        members.add(updatedMember);
      } else {
        members.add(member);
      }
    }
    if (updatedMember == null) {
      throw new PlatformException(404, "Project member not found.");
    }
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
    return updatedMember;
  }

  /**
   * Requires the user to have project membership.
   *
   * @param project project record
   * @param userId requesting user identifier
   */
  public void requireEditor(ProjectRecord project, String userId) {
    ProjectMember member =
        safeMembers(project).stream()
            .filter(candidate -> candidate.userId().equals(userId))
            .findFirst()
            .orElse(null);
    if (member == null) {
      throw new PlatformException(403, "Project membership is required.");
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
    if (project.ownerUserId() != null && project.ownerUserId().equals(userId)) {
      return true;
    }
    return safeMembers(project).stream().anyMatch(member -> member.userId().equals(userId));
  }

  /**
   * Creates a membership snapshot from a user record.
   *
   * @param user user record
   * @param role assigned role
   * @param now membership timestamp
   * @return project member record
   */
  private ProjectMember member(UserRecord user, String role, Instant now) {
    return new ProjectMember(user.id(), user.email(), user.displayName(), role, now);
  }

  private void requireOwner(ProjectRecord project, String userId, String message) {
    if (!project.ownerUserId().equals(userId)) {
      throw new PlatformException(403, message);
    }
  }

  private List<ProjectMember> safeMembers(ProjectRecord project) {
    return project.members() == null ? List.of() : project.members();
  }

  private String requireRole(String role) {
    if (role == null || role.trim().isEmpty()) {
      throw new PlatformException(400, "Project role is required.");
    }
    String normalized = role.trim().replaceAll("\\s+", " ");
    if (normalized.length() > MAX_ROLE_LENGTH) {
      throw new PlatformException(400, "Project role must be 48 characters or fewer.");
    }
    if (OWNER_ROLE.equalsIgnoreCase(normalized)) {
      throw new PlatformException(400, "The owner role is reserved for the project owner.");
    }
    return normalized;
  }

  /**
   * Returns the repository path for a project record.
   *
   * @param projectId project identifier
   * @return repository-relative path
   */
  private Path projectPath(String projectId) {
    requireProjectId(projectId);
    return Path.of("projects", projectId, "project.json");
  }

  private String requireProjectId(String projectId) {
    if (projectId == null || projectId.isBlank()) {
      throw new PlatformException(400, "Project id is required.");
    }
    return projectId.trim();
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
