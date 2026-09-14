package io.mehdieidi.modriss.backend.api;

import io.mehdieidi.modriss.platform.model.domain.ModelRecord;
import io.mehdieidi.modriss.platform.transformation.synchronization.ConflictResolution;
import io.mehdieidi.modriss.platform.transformation.synchronization.ModelConflict;
import io.mehdieidi.modriss.platform.transformation.synchronization.SynchronizationSession;
import io.mehdieidi.modriss.platform.transformation.synchronization.TransformationDirection;
import io.mehdieidi.modriss.platform.transformation.synchronization.TransformationSynchronizationCoordinator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST contract for inspecting, resolving, finalizing, and cancelling pending model merges. */
@RestController
@RequestMapping("/api/synchronizations")
public class SynchronizationController {

  private final TransformationSynchronizationCoordinator synchronization;
  private final AuthSupport auth;

  public SynchronizationController(
      TransformationSynchronizationCoordinator synchronization, AuthSupport auth) {
    this.synchronization = synchronization;
    this.auth = auth;
  }

  @GetMapping("/{projectId}/{sessionId}")
  SynchronizationSessionResponse get(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String sessionId) {
    return SynchronizationSessionResponse.from(
        synchronization.getSession(auth.user(token), projectId, sessionId));
  }

  @PostMapping("/{projectId}/{sessionId}/resolutions")
  SynchronizationSessionResponse resolve(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String sessionId,
      @Valid @RequestBody ResolutionRequest request) {
    return SynchronizationSessionResponse.from(
        synchronization.resolve(
            auth.user(token), projectId, sessionId, request.conflictId(), request.resolution()));
  }

  /** Stores all submitted choices with one durable write for large conflict sessions. */
  @PostMapping("/{projectId}/{sessionId}/resolutions/batch")
  SynchronizationSessionResponse resolveBatch(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String sessionId,
      @Valid @RequestBody BatchResolutionRequest request) {
    return SynchronizationSessionResponse.from(
        synchronization.resolveAll(auth.user(token), projectId, sessionId, request.resolutions()));
  }

  @PostMapping("/{projectId}/{sessionId}/finalize")
  ModelRecord finalizeSession(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String sessionId) {
    return synchronization.finalizeSession(auth.user(token), projectId, sessionId);
  }

  @DeleteMapping("/{projectId}/{sessionId}")
  ResponseEntity<Void> cancel(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String sessionId) {
    synchronization.cancel(auth.user(token), projectId, sessionId);
    return ResponseEntity.noContent().build();
  }

  public record ResolutionRequest(
      @NotBlank String conflictId, @NotNull ConflictResolution resolution) {}

  public record BatchResolutionRequest(
      @NotEmpty Map<@NotBlank String, @NotNull ConflictResolution> resolutions) {}

  /** Public pending-session view; raw merge resources remain durable server-side state. */
  public record SynchronizationSessionResponse(
      String id,
      TransformationDirection direction,
      String projectId,
      String sourceModelId,
      String targetModelId,
      long workingRevision,
      long baselineVersion,
      long newSourceRevision,
      Instant createdAt,
      List<ModelConflict> conflicts,
      Map<String, ConflictResolution> resolutions) {

    static SynchronizationSessionResponse from(SynchronizationSession session) {
      return new SynchronizationSessionResponse(
          session.id(),
          session.direction(),
          session.projectId(),
          session.sourceModelId(),
          session.targetModelId(),
          session.workingRevision(),
          session.baselineVersion(),
          session.newSourceRevision(),
          session.createdAt(),
          session.conflicts(),
          session.resolutions());
    }
  }
}
