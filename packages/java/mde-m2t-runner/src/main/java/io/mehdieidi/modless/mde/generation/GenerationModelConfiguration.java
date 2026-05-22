package io.mehdieidi.modless.mde.generation;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record GenerationModelConfiguration(
        String name,
        List<String> aliases,
        Path modelFile,
        List<Path> metamodelFiles,
        boolean validate) {

    public GenerationModelConfiguration {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Model name is required.");
        }
        Objects.requireNonNull(modelFile, "modelFile");
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        metamodelFiles = metamodelFiles == null ? List.of() : List.copyOf(metamodelFiles);
    }
}
