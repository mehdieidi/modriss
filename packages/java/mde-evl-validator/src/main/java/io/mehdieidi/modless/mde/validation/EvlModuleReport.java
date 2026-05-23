package io.mehdieidi.modless.mde.validation;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record EvlModuleReport(
        Path moduleFile,
        Duration duration,
        List<EvlConstraintViolation> violations,
        List<EvlDiagnostic> diagnostics) {

    public EvlModuleReport {
        Objects.requireNonNull(moduleFile, "moduleFile");
        Objects.requireNonNull(duration, "duration");
        violations = violations == null ? List.of() : List.copyOf(violations);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
