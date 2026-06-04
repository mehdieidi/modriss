package io.mehdieidi.modless.mde.validation;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record EvlModuleReport(
        Path moduleFile,
        Duration duration,
        Duration parseDuration,
        Duration modelLoadDuration,
        Duration structuralValidationDuration,
        Duration evlExecuteDuration,
        Duration violationMappingDuration,
        Duration disposeDuration,
        List<EvlConstraintViolation> violations,
        List<EvlDiagnostic> diagnostics) {

    public EvlModuleReport {
        Objects.requireNonNull(moduleFile, "moduleFile");
        Objects.requireNonNull(duration, "duration");
        parseDuration = parseDuration == null ? Duration.ZERO : parseDuration;
        modelLoadDuration = modelLoadDuration == null ? Duration.ZERO : modelLoadDuration;
        structuralValidationDuration = structuralValidationDuration == null
                ? Duration.ZERO : structuralValidationDuration;
        evlExecuteDuration = evlExecuteDuration == null ? Duration.ZERO : evlExecuteDuration;
        violationMappingDuration = violationMappingDuration == null
                ? Duration.ZERO : violationMappingDuration;
        disposeDuration = disposeDuration == null ? Duration.ZERO : disposeDuration;
        violations = violations == null ? List.of() : List.copyOf(violations);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public EvlModuleReport(
            Path moduleFile,
            Duration duration,
            List<EvlConstraintViolation> violations,
            List<EvlDiagnostic> diagnostics) {
        this(moduleFile, duration, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO,
                Duration.ZERO, Duration.ZERO, violations, diagnostics);
    }
}
