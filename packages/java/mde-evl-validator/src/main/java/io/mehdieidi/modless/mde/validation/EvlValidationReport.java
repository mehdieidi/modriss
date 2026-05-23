package io.mehdieidi.modless.mde.validation;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record EvlValidationReport(
        EvlValidationStatus status,
        Path evlRoot,
        Instant startedAt,
        Instant finishedAt,
        Duration duration,
        List<EvlModuleReport> moduleReports,
        List<EvlConstraintViolation> violations,
        List<EvlDiagnostic> diagnostics,
        String stdout,
        String warnings,
        String stderr) {

    public EvlValidationReport {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(evlRoot, "evlRoot");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(finishedAt, "finishedAt");
        Objects.requireNonNull(duration, "duration");
        moduleReports = moduleReports == null ? List.of() : List.copyOf(moduleReports);
        violations = violations == null ? List.of() : List.copyOf(violations);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        stdout = stdout == null ? "" : stdout;
        warnings = warnings == null ? "" : warnings;
        stderr = stderr == null ? "" : stderr;
    }

    public boolean hasMandatoryViolations() {
        return violations.stream().anyMatch(v -> v.kind() == EvlConstraintKind.MANDATORY);
    }
}
