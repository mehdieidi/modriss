package io.mehdieidi.modless.mde.validation;

import java.nio.file.Path;
import java.util.Objects;

public record EvlDiagnostic(
        ValidationSeverity severity,
        ValidationPhase phase,
        Path file,
        int line,
        int column,
        String reason,
        String whatWentWrong,
        String howToFix,
        String exceptionType) {

    public EvlDiagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(phase, "phase");
        reason = reason == null ? "" : reason;
        whatWentWrong = whatWentWrong == null ? "" : whatWentWrong;
        howToFix = howToFix == null ? "" : howToFix;
        exceptionType = exceptionType == null ? "" : exceptionType;
    }

    public static EvlDiagnostic error(
            ValidationPhase phase,
            Path file,
            int line,
            int column,
            String reason,
            String whatWentWrong,
            String howToFix,
            Throwable cause) {
        return new EvlDiagnostic(
                ValidationSeverity.ERROR,
                phase,
                file,
                line,
                column,
                reason,
                whatWentWrong,
                howToFix,
                cause == null ? "" : cause.getClass().getName());
    }
}
