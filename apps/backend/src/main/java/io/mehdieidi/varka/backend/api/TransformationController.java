package io.mehdieidi.varka.backend.api;

import io.mehdieidi.varka.platform.transformation.application.MdeJobService;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobRecord;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger LOGGER = LoggerFactory.getLogger(TransformationController.class);

  private final MdeJobService jobs;
  private final AuthSupport auth;
  private final io.mehdieidi.varka.backend.observability.VarkaMetrics metrics;

  /**
   * Creates the transformation controller.
   *
   * @param jobs MDE job service
   * @param auth controller authentication support
   * @param metrics application metrics recorder
   */
  public TransformationController(
      MdeJobService jobs,
      AuthSupport auth,
      io.mehdieidi.varka.backend.observability.VarkaMetrics metrics) {
    this.jobs = jobs;
    this.auth = auth;
    this.metrics = metrics;
  }

  /**
   * Transforms a CIM into a PIM.
   *
   * @param token session token
   * @param request source model and expected revision
   * @return transformation result
   */
  @PostMapping("/cim-to-pim")
  ResponseEntity<JobResponse> cimToPim(
      @RequestHeader("X-Auth-Token") String token,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody TransformRequest request) {
    long started = System.nanoTime();
    MdeJobRecord job =
        jobs.submitCimToPim(
            auth.user(token), request.sourceModelId(), request.expectedRevision(), idempotencyKey);
    return accepted(job, "cim-to-pim", started);
  }

  /**
   * Transforms a PIM into a PSM.
   *
   * @param token session token
   * @param request source model and expected revision
   * @return transformation result
   */
  @PostMapping("/pim-to-psm")
  ResponseEntity<JobResponse> pimToPsm(
      @RequestHeader("X-Auth-Token") String token,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody TransformRequest request) {
    long started = System.nanoTime();
    MdeJobRecord job =
        jobs.submitPimToPsm(
            auth.user(token), request.sourceModelId(), request.expectedRevision(), idempotencyKey);
    return accepted(job, "pim-to-psm", started);
  }

  /**
   * Generates an artifact from a PSM.
   *
   * @param token session token
   * @param request source model and expected revision
   * @return generation result
   */
  @PostMapping("/psm-to-artifact")
  ResponseEntity<JobResponse> psmToArtifact(
      @RequestHeader("X-Auth-Token") String token,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody TransformRequest request) {
    long started = System.nanoTime();
    MdeJobRecord job =
        jobs.submitPsmToArtifact(
            auth.user(token), request.sourceModelId(), request.expectedRevision(), idempotencyKey);
    return accepted(job, "psm-to-artifact", started);
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

  private ResponseEntity<JobResponse> accepted(MdeJobRecord job, String route, long startedNanos) {
    metrics.recordMdeJobSubmitted();
    if (job.status() == MdeJobStatus.FAILED) {
      metrics.recordMdeJobFailed();
    }
    long controllerMs = millisSince(startedNanos);
    jobs.appendTimings(job.id(), Map.of("controller.acceptMs", controllerMs));
    LOGGER.info(
        "mde_request_accepted route={} jobId={} projectId={} operation={} controllerMs={} "
            + "status={}",
        route,
        job.id(),
        job.projectId(),
        job.operation(),
        controllerMs,
        job.status());
    return ResponseEntity.accepted()
        .location(URI.create("/api/transformations/jobs/" + job.id()))
        .body(new JobResponse(job.id(), job.status()));
  }

  private long millisSince(long startedNanos) {
    return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
  }
}
