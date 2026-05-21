package io.mehdieidi.modless.mde.etl;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record EtlExecutionRequest(
        Path moduleFile,
        Path workingDirectory,
        List<EtlModelConfiguration> models,
        boolean overwriteOutputs,
        boolean captureOutput) {

    public EtlExecutionRequest {
        Objects.requireNonNull(moduleFile, "moduleFile");
        workingDirectory = workingDirectory == null ? moduleFile.toAbsolutePath().getParent()
                : workingDirectory;
        models = models == null ? List.of() : List.copyOf(models);
    }
}
