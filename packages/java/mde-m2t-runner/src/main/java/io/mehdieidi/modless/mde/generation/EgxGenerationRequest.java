package io.mehdieidi.modless.mde.generation;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record EgxGenerationRequest(
        Path moduleFile,
        Path templateRoot,
        Path outputDirectory,
        List<GenerationModelConfiguration> models,
        boolean failIfOutputDirectoryIsNotEmpty,
        boolean captureOutput) {

    public EgxGenerationRequest {
        Objects.requireNonNull(moduleFile, "moduleFile");
        templateRoot = templateRoot == null ? moduleFile.toAbsolutePath().getParent()
                                              .resolve("templates") : templateRoot;
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        models = models == null ? List.of() : List.copyOf(models);
    }
}
