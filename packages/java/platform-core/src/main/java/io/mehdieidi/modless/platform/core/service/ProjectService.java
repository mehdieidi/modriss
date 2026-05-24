package io.mehdieidi.modless.platform.core.service;

import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.MemberRole;
import io.mehdieidi.modless.platform.core.model.ProjectMember;
import io.mehdieidi.modless.platform.core.model.ProjectRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.repository.JsonFileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class ProjectService {

    private final JsonFileStore store;
    private final AuthService authService;

    public ProjectService(JsonFileStore store, AuthService authService) {
        this.store = store;
        this.authService = authService;
    }

    public List<ProjectRecord> list(UserRecord user) {
        try (Stream<Path> projects = Files.list(store.resolve(Path.of("projects")))) {
            return projects.map(path -> store.read(
                            Path.of("projects", path.getFileName().toString(), "project.json"),
                            ProjectRecord.class).orElse(null))
                    .filter(project -> project != null && canRead(project, user.id()))
                    .sorted(Comparator.comparing(ProjectRecord::updatedAt).reversed())
                    .toList();
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not list projects.");
        }
    }

    public ProjectRecord create(UserRecord owner, String name, String description) {
        Instant now = Instant.now();
        ProjectRecord project = new ProjectRecord(UUID.randomUUID().toString(), requireName(name),
                description == null ? "" : description.trim(), owner.id(), new LinkedHashMap<>(),
                List.of(member(owner, MemberRole.OWNER, now)), now, now);
        store.write(projectPath(project.id()), project);
        return project;
    }

    public ProjectRecord get(UserRecord user, String projectId) {
        ProjectRecord project = store.require(projectPath(projectId), ProjectRecord.class,
                "Project not found.");
        if (!canRead(project, user.id())) {
            throw new PlatformException(403, "You do not have access to this project.");
        }
        return project;
    }

    public ProjectRecord update(UserRecord user, String projectId, String name, String description,
            Map<String, String> activeModelIds) {
        ProjectRecord project = get(user, projectId);
        requireEditor(project, user.id());
        ProjectRecord updated = new ProjectRecord(project.id(), requireName(name),
                description == null ? "" : description.trim(), project.ownerUserId(),
                activeModelIds == null ? Map.of() : new LinkedHashMap<>(activeModelIds),
                project.members(), project.createdAt(), Instant.now());
        store.write(projectPath(project.id()), updated);
        return updated;
    }

    public void delete(UserRecord user, String projectId) {
        ProjectRecord project = get(user, projectId);
        if (!project.ownerUserId().equals(user.id())) {
            throw new PlatformException(403, "Only the project owner can delete a project.");
        }
        deleteRecursively(store.resolve(Path.of("projects", projectId)));
    }

    public List<ProjectMember> members(UserRecord user, String projectId) {
        return get(user, projectId).members();
    }

    public ProjectMember invite(UserRecord inviter, String projectId, String email,
            MemberRole role) {
        ProjectRecord project = get(inviter, projectId);
        requireEditor(project, inviter.id());
        UserRecord invited = authService.findByEmail(email);
        if (invited == null) {
            throw new PlatformException(404, "No registered user exists for this email.");
        }
        Instant now = Instant.now();
        ProjectMember newMember = member(invited,
                role == null || role == MemberRole.OWNER ? MemberRole.EDITOR : role, now);
        List<ProjectMember> members = new ArrayList<>(project.members().stream()
                .filter(existing -> !existing.userId().equals(invited.id()))
                .toList());
        members.add(newMember);
        store.write(projectPath(project.id()),
                new ProjectRecord(project.id(), project.name(), project.description(),
                        project.ownerUserId(), project.activeModelIds(), members,
                        project.createdAt(), now));
        return newMember;
    }

    public void revoke(UserRecord requester, String projectId, String userId) {
        ProjectRecord project = get(requester, projectId);
        if (!project.ownerUserId().equals(requester.id())) {
            throw new PlatformException(403, "Only the project owner can revoke access.");
        }
        if (project.ownerUserId().equals(userId)) {
            throw new PlatformException(400, "Project owner access cannot be revoked.");
        }
        List<ProjectMember> members = project.members().stream()
                .filter(member -> !member.userId().equals(userId))
                .toList();
        store.write(projectPath(project.id()),
                new ProjectRecord(project.id(), project.name(), project.description(),
                        project.ownerUserId(), project.activeModelIds(), members,
                        project.createdAt(), Instant.now()));
    }

    public void requireEditor(ProjectRecord project, String userId) {
        ProjectMember member = project.members().stream()
                .filter(candidate -> candidate.userId().equals(userId))
                .findFirst()
                .orElse(null);
        if (member == null || member.role() == MemberRole.VIEWER) {
            throw new PlatformException(403, "Editor access is required.");
        }
    }

    private boolean canRead(ProjectRecord project, String userId) {
        return project.members().stream().anyMatch(member -> member.userId().equals(userId));
    }

    private ProjectMember member(UserRecord user, MemberRole role, Instant now) {
        return new ProjectMember(user.id(), user.email(), user.displayName(), role, now);
    }

    private Path projectPath(String projectId) {
        return Path.of("projects", projectId, "project.json");
    }

    private String requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new PlatformException(400, "Project name is required.");
        }
        return name.trim();
    }

    private void deleteRecursively(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ex) {
                    throw new PlatformException(500, "Could not delete project.");
                }
            });
        } catch (Exception ex) {
            if (ex instanceof PlatformException platformException) {
                throw platformException;
            }
            throw new PlatformException(500, "Could not delete project.");
        }
    }
}
