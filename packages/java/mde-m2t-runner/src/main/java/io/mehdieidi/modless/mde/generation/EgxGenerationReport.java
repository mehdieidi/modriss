package io.mehdieidi.modless.mde.generation;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record EgxGenerationReport(
        GenerationStatus status,
        Path moduleFile,
        Path outputDirectory,
        Instant startedAt,
        Instant finishedAt,
        Duration duration,
        List<GenerationDiagnostic> diagnostics,
        List<Path> generatedFiles,
        String standardOutput,
        String warningOutput,
        String errorOutput) {

    public EgxGenerationReport {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(moduleFile, "moduleFile");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(finishedAt, "finishedAt");
        Objects.requireNonNull(duration, "duration");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        generatedFiles = generatedFiles == null ? List.of() : List.copyOf(generatedFiles);
        standardOutput = standardOutput == null ? "" : standardOutput;
        warningOutput = warningOutput == null ? "" : warningOutput;
        errorOutput = errorOutput == null ? "" : errorOutput;
    }

    public boolean succeeded() {
        return status == GenerationStatus.SUCCEEDED;
    }
}
