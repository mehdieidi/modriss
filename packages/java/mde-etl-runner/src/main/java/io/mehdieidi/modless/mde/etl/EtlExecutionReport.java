package io.mehdieidi.modless.mde.etl;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record EtlExecutionReport(
        EtlExecutionStatus status,
        Path moduleFile,
        Instant startedAt,
        Instant finishedAt,
        Duration duration,
        List<EtlDiagnostic> diagnostics,
        String standardOutput,
        String warningOutput,
        String errorOutput) {

    public EtlExecutionReport {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(moduleFile, "moduleFile");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(finishedAt, "finishedAt");
        Objects.requireNonNull(duration, "duration");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        standardOutput = standardOutput == null ? "" : standardOutput;
        warningOutput = warningOutput == null ? "" : warningOutput;
        errorOutput = errorOutput == null ? "" : errorOutput;
    }

    public boolean succeeded() {
        return status == EtlExecutionStatus.SUCCEEDED;
    }
}
