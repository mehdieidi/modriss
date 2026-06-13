package io.mehdieidi.modless.backend.api;

import io.mehdieidi.modless.platform.core.model.ArtifactRecord;
import io.mehdieidi.modless.platform.core.model.MdeJobRecord;
import io.mehdieidi.modless.platform.core.model.MdeJobStatus;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.service.MdeJobService;
import io.mehdieidi.modless.platform.core.service.TransformationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes model transformation, artifact generation, and MDE job endpoints. */
@RestController
@RequestMapping("/api/transformations")
public class TransformationController {

  private final MdeJobService jobs;
  private final TransformationService transformations;
  private final AuthSupport auth;

  /**
   * Creates the transformation controller.
   *
   * @param jobs MDE job service
   * @param transformations transformation service
   * @param auth controller authentication support
   */
  public TransformationController(
      MdeJobService jobs, TransformationService transformations, AuthSupport auth) {
    this.jobs = jobs;
    this.transformations = transformations;
    this.auth = auth;
  }

  /**
   * Transforms a CIM into a PIM.
   *
   * @param token session token
   * @param request source model and expected revision
   * @return transformation result
   */
  @PostMapping("/cim-to-pim")
  ResponseEntity<TransformationResponse> cimToPim(
      @RequestHeader("X-Auth-Token") String token, @Valid @RequestBody TransformRequest request) {
    ModelRecord model =
        transformations.cimToPim(
            auth.user(token), request.sourceModelId(), request.expectedRevision());
    return ResponseEntity.ok(TransformationResponse.model(model));
  }

  /**
   * Transforms a PIM into a PSM.
   *
   * @param token session token
   * @param request source model and expected revision
   * @return transformation result
   */
  @PostMapping("/pim-to-psm")
  ResponseEntity<TransformationResponse> pimToPsm(
      @RequestHeader("X-Auth-Token") String token, @Valid @RequestBody TransformRequest request) {
    ModelRecord model =
        transformations.pimToPsm(
            auth.user(token), request.sourceModelId(), request.expectedRevision());
    return ResponseEntity.ok(TransformationResponse.model(model));
  }

  /**
   * Generates an artifact from a PSM.
   *
   * @param token session token
   * @param request source model and expected revision
   * @return generation result
   */
  @PostMapping("/psm-to-artifact")
  ResponseEntity<TransformationResponse> psmToArtifact(
      @RequestHeader("X-Auth-Token") String token, @Valid @RequestBody TransformRequest request) {
    ArtifactRecord artifact =
        transformations.psmToArtifact(
            auth.user(token), request.sourceModelId(), request.expectedRevision());
    return ResponseEntity.ok(TransformationResponse.artifact(artifact));
  }

  /**
   * Returns an MDE job by identifier.
   *
   * @param token session token
   * @param id job identifier
   * @return requested job
   */
  @GetMapping("/jobs/{id}")
  MdeJobRecord job(@RequestHeader("X-Auth-Token") String token, @PathVariable("id") String id) {
    return jobs.get(auth.user(token), id);
  }

  /**
   * Requests cancellation of an MDE job.
   *
   * @param token session token
   * @param id job identifier
   * @return updated job
   */
  @PostMapping("/jobs/{id}/cancel")
  MdeJobRecord cancelJob(
      @RequestHeader("X-Auth-Token") String token, @PathVariable("id") String id) {
    return jobs.cancel(auth.user(token), id);
  }

  /**
   * Transformation request payload.
   *
   * @param sourceModelId source model identifier
   * @param expectedRevision expected source model revision
   */
  public record TransformRequest(@NotBlank String sourceModelId, Long expectedRevision) {}

  /**
   * Minimal MDE job representation.
   *
   * @param id job identifier
   * @param status current job status
   */
  public record JobResponse(String id, MdeJobStatus status) {}

  /**
   * Synchronous transformation response containing either a model or an artifact.
   *
   * @param success whether the operation succeeded
   * @param status operation status name
   * @param resultModelId generated model identifier, when applicable
   * @param resultArtifactId generated artifact identifier, when applicable
   * @param model generated model, when applicable
   * @param artifact generated artifact, when applicable
   * @param diagnostics transformation diagnostics
   */
  public record TransformationResponse(
      boolean success,
      String status,
      String resultModelId,
      String resultArtifactId,
      ModelRecord model,
      ArtifactRecord artifact,
      List<String> diagnostics) {

    /**
     * Creates a successful model transformation response.
     *
     * @param model generated model
     * @return successful response
     */
    static TransformationResponse model(ModelRecord model) {
      return new TransformationResponse(
          true, "SUCCEEDED", model.id(), null, model, null, List.of());
    }

    /**
     * Creates a successful artifact generation response.
     *
     * @param artifact generated artifact
     * @return successful response
     */
    static TransformationResponse artifact(ArtifactRecord artifact) {
      return new TransformationResponse(
          true, "SUCCEEDED", null, artifact.id(), null, artifact, List.of());
    }
  }
}
