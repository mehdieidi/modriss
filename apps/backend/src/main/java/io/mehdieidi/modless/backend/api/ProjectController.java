package io.mehdieidi.modless.backend.api;

import io.mehdieidi.modless.platform.core.model.MemberRole;
import io.mehdieidi.modless.platform.core.model.ProjectMember;
import io.mehdieidi.modless.platform.core.model.ProjectRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.service.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides authenticated project lifecycle and membership endpoints.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projects;
    private final AuthSupport auth;

    /**
     * Creates the project controller.
     *
     * @param projects project service
     * @param auth     controller authentication support
     */
    public ProjectController(ProjectService projects, AuthSupport auth) {
        this.projects = projects;
        this.auth = auth;
    }

    /**
     * Lists projects accessible to the current user.
     *
     * @param token session token
     * @return accessible projects
     */
    @GetMapping
    List<ProjectRecord> list(@RequestHeader("X-Auth-Token") String token) {
        return projects.list(auth.user(token));
    }

    /**
     * Creates a project owned by the current user.
     *
     * @param token   session token
     * @param request project details
     * @return created project
     */
    @PostMapping
    ProjectRecord create(@RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody SaveProjectRequest request) {
        return projects.create(auth.user(token), request.name(), request.description());
    }

    /**
     * Returns an accessible project by identifier.
     *
     * @param token session token
     * @param id    project identifier
     * @return requested project
     */
    @GetMapping("/{id}")
    ProjectRecord get(@RequestHeader("X-Auth-Token") String token, @PathVariable("id") String id) {
        return projects.get(auth.user(token), id);
    }

    /**
     * Updates an existing project.
     *
     * @param token   session token
     * @param id      project identifier
     * @param request replacement project details
     * @return updated project
     */
    @PutMapping("/{id}")
    ProjectRecord update(@RequestHeader("X-Auth-Token") String token, @PathVariable("id") String id,
            @Valid @RequestBody SaveProjectRequest request) {
        return projects.update(auth.user(token), id, request.name(), request.description(),
                request.activeModelIds());
    }

    /**
     * Deletes a project.
     *
     * @param token session token
     * @param id    project identifier
     */
    @DeleteMapping("/{id}")
    void delete(@RequestHeader("X-Auth-Token") String token, @PathVariable("id") String id) {
        projects.delete(auth.user(token), id);
    }

    /**
     * Lists project members.
     *
     * @param token session token
     * @param id    project identifier
     * @return project members
     */
    @GetMapping("/{id}/members")
    List<ProjectMember> members(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("id") String id) {
        return projects.members(auth.user(token), id);
    }

    /**
     * Invites a user to a project with the requested role.
     *
     * @param token   session token
     * @param id      project identifier
     * @param request invitee and role
     * @return created project membership
     */
    @PostMapping("/{id}/invite")
    ProjectMember invite(@RequestHeader("X-Auth-Token") String token, @PathVariable("id") String id,
            @Valid @RequestBody InviteRequest request) {
        UserRecord user = auth.user(token);
        return projects.invite(user, id, request.email(), request.role());
    }

    /**
     * Revokes a user's project membership.
     *
     * @param token  session token
     * @param id     project identifier
     * @param userId member user identifier
     */
    @DeleteMapping("/{id}/members/{userId}")
    void revoke(@RequestHeader("X-Auth-Token") String token, @PathVariable("id") String id,
            @PathVariable("userId") String userId) {
        projects.revoke(auth.user(token), id, userId);
    }

    /**
     * Project create or update payload.
     *
     * @param name           project name
     * @param description    project description
     * @param activeModelIds active model identifiers keyed by model level
     */
    public record SaveProjectRequest(@NotBlank String name, String description,
                                     Map<String, String> activeModelIds) {

    }

    /**
     * Project invitation payload.
     *
     * @param email invitee email
     * @param role  requested membership role
     */
    public record InviteRequest(@NotBlank String email, MemberRole role) {

    }
}
