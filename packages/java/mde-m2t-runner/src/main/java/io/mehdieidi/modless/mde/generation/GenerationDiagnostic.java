package io.mehdieidi.modless.mde.generation;

import java.nio.file.Path;

/**
 * Structured diagnostic emitted by the EGX generation runner.
 *
 * @param severity      diagnostic severity
 * @param phase         generation phase that produced the diagnostic
 * @param file          file associated with the diagnostic, when known
 * @param line          one-based source line, or {@code -1} when unavailable
 * @param column        one-based source column, or {@code -1} when unavailable
 * @param reason        concise source-reported reason
 * @param whatWentWrong human-readable failure explanation
 * @param howToFix      operator guidance for resolving the diagnostic
 * @param exceptionType fully qualified exception class name, when available
 */
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

    /**
     * Creates an error diagnostic with the exception type extracted from the supplied cause.
     *
     * @param phase         phase that failed
     * @param file          file associated with the failure
     * @param line          source line, or {@code -1}
     * @param column        source column, or {@code -1}
     * @param reason        concise failure reason
     * @param whatWentWrong user-facing failure explanation
     * @param howToFix      user-facing remediation guidance
     * @param cause         exception that caused the diagnostic, when available
     * @return populated error diagnostic
     */
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
