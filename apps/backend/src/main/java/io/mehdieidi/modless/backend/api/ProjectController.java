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

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projects;
    private final AuthSupport auth;

    public ProjectController(ProjectService projects, AuthSupport auth) {
        this.projects = projects;
        this.auth = auth;
    }

    @GetMapping
    List<ProjectRecord> list(@RequestHeader("X-Auth-Token") String token) {
        return projects.list(auth.user(token));
    }

    @PostMapping
    ProjectRecord create(@RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody SaveProjectRequest request) {
        return projects.create(auth.user(token), request.name(), request.description());
    }

    @GetMapping("/{id}")
    ProjectRecord get(@RequestHeader("X-Auth-Token") String token, @PathVariable("id") String id) {
        return projects.get(auth.user(token), id);
    }

    @PutMapping("/{id}")
    ProjectRecord update(@RequestHeader("X-Auth-Token") String token, @PathVariable("id") String id,
            @Valid @RequestBody SaveProjectRequest request) {
        return projects.update(auth.user(token), id, request.name(), request.description(),
                request.activeModelIds());
    }

    @DeleteMapping("/{id}")
    void delete(@RequestHeader("X-Auth-Token") String token, @PathVariable("id") String id) {
        projects.delete(auth.user(token), id);
    }

    @GetMapping("/{id}/members")
    List<ProjectMember> members(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("id") String id) {
        return projects.members(auth.user(token), id);
    }

    @PostMapping("/{id}/invite")
    ProjectMember invite(@RequestHeader("X-Auth-Token") String token, @PathVariable("id") String id,
            @Valid @RequestBody InviteRequest request) {
        UserRecord user = auth.user(token);
        return projects.invite(user, id, request.email(), request.role());
    }

    @DeleteMapping("/{id}/members/{userId}")
    void revoke(@RequestHeader("X-Auth-Token") String token, @PathVariable("id") String id,
            @PathVariable("userId") String userId) {
        projects.revoke(auth.user(token), id, userId);
    }

    public record SaveProjectRequest(@NotBlank String name, String description,
                                     Map<String, String> activeModelIds) {

    }

    public record InviteRequest(@NotBlank String email, MemberRole role) {

    }
}
