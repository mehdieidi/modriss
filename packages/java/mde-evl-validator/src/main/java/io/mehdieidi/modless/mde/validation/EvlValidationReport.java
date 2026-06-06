package io.mehdieidi.modless.mde.validation;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable summary of an EVL validation run across one or more modules.
 *
 * @param status                  final validation status
 * @param evlRoot                 entry file or directory used for module discovery
 * @param startedAt               wall-clock start time
 * @param finishedAt              wall-clock finish time
 * @param duration                total wall-clock duration
 * @param moduleDiscoveryDuration time spent discovering modules
 * @param moduleReports           per-module validation reports
 * @param violations              flattened violations from all modules
 * @param diagnostics             flattened diagnostics from all modules and request checks
 * @param stdout                  captured standard output
 * @param warnings                captured warning output
 * @param stderr                  captured error output
 */
public record EvlValidationReport(
        EvlValidationStatus status,
        Path evlRoot,
        Instant startedAt,
        Instant finishedAt,
        Duration duration,
        Duration moduleDiscoveryDuration,
        List<EvlModuleReport> moduleReports,
        List<EvlConstraintViolation> violations,
        List<EvlDiagnostic> diagnostics,
        String stdout,
        String warnings,
        String stderr) {

    /**
     * Normalizes nullable optional fields and defensively copies collections.
     */
    public EvlValidationReport {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(evlRoot, "evlRoot");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(finishedAt, "finishedAt");
        Objects.requireNonNull(duration, "duration");
        moduleDiscoveryDuration = moduleDiscoveryDuration == null
                ? Duration.ZERO : moduleDiscoveryDuration;
        moduleReports = moduleReports == null ? List.of() : List.copyOf(moduleReports);
        violations = violations == null ? List.of() : List.copyOf(violations);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        stdout = stdout == null ? "" : stdout;
        warnings = warnings == null ? "" : warnings;
        stderr = stderr == null ? "" : stderr;
    }

    /**
     * Indicates whether any mandatory EVL constraint failed.
     *
     * @return {@code true} when at least one mandatory violation exists
     */
    public boolean hasMandatoryViolations() {
        return violations.stream().anyMatch(v -> v.kind() == EvlConstraintKind.MANDATORY);
    }

    /**
     * Returns the total report duration.
     *
     * @return validation duration
     */
    public Duration totalDuration() {
        return duration;
    }
}
