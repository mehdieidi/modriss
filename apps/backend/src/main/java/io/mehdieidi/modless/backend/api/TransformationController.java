package io.mehdieidi.modless.backend.api;

import io.mehdieidi.modless.platform.core.model.ArtifactRecord;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.service.TransformationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transformations")
public class TransformationController {

    private final TransformationService transformations;
    private final AuthSupport auth;

    public TransformationController(TransformationService transformations, AuthSupport auth) {
        this.transformations = transformations;
        this.auth = auth;
    }

    @PostMapping("/cim-to-pim")
    ModelRecord cimToPim(@RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody TransformRequest request) {
        return transformations.cimToPim(auth.user(token), request.sourceModelId());
    }

    @PostMapping("/pim-to-psm")
    ModelRecord pimToPsm(@RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody TransformRequest request) {
        return transformations.pimToPsm(auth.user(token), request.sourceModelId());
    }

    @PostMapping("/psm-to-artifact")
    ArtifactRecord psmToArtifact(@RequestHeader("X-Auth-Token") String token,
            @Valid @RequestBody TransformRequest request) {
        return transformations.psmToArtifact(auth.user(token), request.sourceModelId());
    }

    public record TransformRequest(@NotBlank String sourceModelId) {

    }
}
