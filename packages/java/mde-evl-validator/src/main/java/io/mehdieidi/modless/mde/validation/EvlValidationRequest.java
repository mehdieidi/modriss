package io.mehdieidi.modless.mde.validation;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record EvlValidationRequest(
        Path evlRoot,
        List<Path> moduleFiles,
        List<EvlModelConfiguration> models,
        boolean captureOutput) {

    public EvlValidationRequest {
        Objects.requireNonNull(evlRoot, "evlRoot");
        moduleFiles = moduleFiles == null ? List.of() : List.copyOf(moduleFiles);
        models = models == null ? List.of() : List.copyOf(models);
    }

    public static EvlValidationRequest forRoot(
            Path evlRoot, List<EvlModelConfiguration> models, boolean captureOutput) {
        return new EvlValidationRequest(evlRoot, List.of(), models, captureOutput);
    }
}
