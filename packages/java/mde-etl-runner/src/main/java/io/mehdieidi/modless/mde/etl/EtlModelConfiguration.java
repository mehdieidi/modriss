package io.mehdieidi.modless.mde.etl;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record EtlModelConfiguration(
        String name,
        List<String> aliases,
        Path modelFile,
        List<Path> metamodelFiles,
        boolean readOnLoad,
        boolean storeOnDisposal,
        boolean readOnly,
        boolean validate) {

    public EtlModelConfiguration {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Model name is required.");
        }
        Objects.requireNonNull(modelFile, "modelFile");
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        metamodelFiles = metamodelFiles == null ? List.of() : List.copyOf(metamodelFiles);
    }

    public static EtlModelConfiguration source(
            String name, List<String> aliases, Path modelFile, List<Path> metamodelFiles) {
        return new EtlModelConfiguration(name, aliases, modelFile, metamodelFiles, true, false,
                true, false);
    }

    public static EtlModelConfiguration target(
            String name, List<String> aliases, Path modelFile, List<Path> metamodelFiles,
            boolean readOnLoad) {
        return new EtlModelConfiguration(name, aliases, modelFile, metamodelFiles, readOnLoad,
                false, false, false);
    }
}
