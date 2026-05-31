package io.mehdieidi.modless.backend.api;

import io.mehdieidi.modless.platform.core.model.MdeJobRecord;
import io.mehdieidi.modless.platform.core.model.MdeJobStatus;
import io.mehdieidi.modless.platform.core.service.MdeJobService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transformations")
public class TransformationController {

    private final MdeJobService jobs;
    private final AuthSupport auth;

    public TransformationController(MdeJobService jobs, AuthSupport auth) {
        this.jobs = jobs;
        this.auth = auth;
    }

    @PostMapping("/cim-to-pim")
    ResponseEntity<JobResponse> cimToPim(@RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody TransformRequest request) {
        MdeJobRecord job = jobs.submitCimToPim(auth.user(token), request.sourceModelId(),
                request.expectedRevision());
        return ResponseEntity.accepted().body(new JobResponse(job.id(), job.status()));
    }

    @PostMapping("/pim-to-psm")
    ResponseEntity<JobResponse> pimToPsm(@RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody TransformRequest request) {
        MdeJobRecord job = jobs.submitPimToPsm(auth.user(token), request.sourceModelId(),
                request.expectedRevision());
        return ResponseEntity.accepted().body(new JobResponse(job.id(), job.status()));
    }

    @PostMapping("/psm-to-artifact")
    ResponseEntity<JobResponse> psmToArtifact(@RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody TransformRequest request) {
        MdeJobRecord job = jobs.submitPsmToArtifact(auth.user(token), request.sourceModelId(),
                request.expectedRevision());
        return ResponseEntity.accepted().body(new JobResponse(job.id(), job.status()));
    }

    @GetMapping("/jobs/{id}")
    MdeJobRecord job(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("id") String id) {
        return jobs.get(auth.user(token), id);
    }

    @PostMapping("/jobs/{id}/cancel")
    MdeJobRecord cancelJob(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("id") String id) {
        return jobs.cancel(auth.user(token), id);
    }

    public record TransformRequest(@NotBlank String sourceModelId, Long expectedRevision) {

    }

    public record JobResponse(String id, MdeJobStatus status) {

    }
}
