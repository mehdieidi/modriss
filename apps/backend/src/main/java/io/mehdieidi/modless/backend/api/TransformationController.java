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

@RestController
@RequestMapping("/api/transformations")
public class TransformationController {

    private final MdeJobService jobs;
    private final TransformationService transformations;
    private final AuthSupport auth;

    public TransformationController(MdeJobService jobs, TransformationService transformations,
            AuthSupport auth) {
        this.jobs = jobs;
        this.transformations = transformations;
        this.auth = auth;
    }

    @PostMapping("/cim-to-pim")
    ResponseEntity<TransformationResponse> cimToPim(@RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody TransformRequest request) {
        ModelRecord model = transformations.cimToPim(auth.user(token), request.sourceModelId(),
                request.expectedRevision());
        return ResponseEntity.ok(TransformationResponse.model(model));
    }

    @PostMapping("/pim-to-psm")
    ResponseEntity<TransformationResponse> pimToPsm(@RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody TransformRequest request) {
        ModelRecord model = transformations.pimToPsm(auth.user(token), request.sourceModelId(),
                request.expectedRevision());
        return ResponseEntity.ok(TransformationResponse.model(model));
    }

    @PostMapping("/psm-to-artifact")
    ResponseEntity<TransformationResponse> psmToArtifact(
            @RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody TransformRequest request) {
        ArtifactRecord artifact = transformations.psmToArtifact(auth.user(token),
                request.sourceModelId(),
                request.expectedRevision());
        return ResponseEntity.ok(TransformationResponse.artifact(artifact));
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

    public record TransformationResponse(
            boolean success,
            String status,
            String resultModelId,
            String resultArtifactId,
            ModelRecord model,
            ArtifactRecord artifact,
            List<String> diagnostics) {

        static TransformationResponse model(ModelRecord model) {
            return new TransformationResponse(true, "SUCCEEDED", model.id(), null, model, null,
                    List.of());
        }

        static TransformationResponse artifact(ArtifactRecord artifact) {
            return new TransformationResponse(true, "SUCCEEDED", null, artifact.id(), null,
                    artifact, List.of());
        }
    }
}
