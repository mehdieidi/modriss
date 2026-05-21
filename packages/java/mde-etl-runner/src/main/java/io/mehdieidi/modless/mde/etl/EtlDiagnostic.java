package io.mehdieidi.modless.mde.etl;

import java.nio.file.Path;
import java.util.Objects;

public record EtlDiagnostic(
        DiagnosticSeverity severity,
        ExecutionPhase phase,
        Path file,
        int line,
        int column,
        String reason,
        String whatWentWrong,
        String howToFix,
        String exceptionType) {

    public EtlDiagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(phase, "phase");
        reason = reason == null ? "" : reason;
        whatWentWrong = whatWentWrong == null ? "" : whatWentWrong;
        howToFix = howToFix == null ? "" : howToFix;
        exceptionType = exceptionType == null ? "" : exceptionType;
    }

    public static EtlDiagnostic error(
            ExecutionPhase phase,
            Path file,
            int line,
            int column,
            String reason,
            String whatWentWrong,
            String howToFix,
            Throwable cause) {
        return new EtlDiagnostic(
                DiagnosticSeverity.ERROR,
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
