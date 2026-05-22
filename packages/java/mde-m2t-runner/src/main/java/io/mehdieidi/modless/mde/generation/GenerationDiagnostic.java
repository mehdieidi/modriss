package io.mehdieidi.modless.mde.generation;

import java.nio.file.Path;

public record GenerationDiagnostic(
        GenerationSeverity severity,
        GenerationPhase phase,
        Path file,
        int line,
        int column,
        String reason,
        String whatWentWrong,
        String howToFix,
        String exceptionType) {

    public static GenerationDiagnostic error(
            GenerationPhase phase,
            Path file,
            int line,
            int column,
            String reason,
            String whatWentWrong,
            String howToFix,
            Throwable cause) {
        return new GenerationDiagnostic(
                GenerationSeverity.ERROR,
                phase,
                file,
                line,
                column,
                reason == null ? "" : reason,
                whatWentWrong == null ? "" : whatWentWrong,
                howToFix == null ? "" : howToFix,
                cause == null ? "" : cause.getClass().getName());
    }
}
