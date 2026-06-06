package io.mehdieidi.modless.mde.etl;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Structured diagnostic emitted by the ETL runner for validation, parsing, model loading,
 * execution, persistence, or unexpected failures.
 *
 * @param severity      diagnostic severity
 * @param phase         execution phase that produced the diagnostic
 * @param file          file associated with the diagnostic, when known
 * @param line          one-based source line, or {@code -1} when unavailable
 * @param column        one-based source column, or {@code -1} when unavailable
 * @param reason        concise machine-readable or source-reported reason
 * @param whatWentWrong human-readable explanation of the failure mode
 * @param howToFix      operator guidance for resolving the diagnostic
 * @param exceptionType fully qualified exception class name, when available
 */
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

    /**
     * Normalizes nullable textual fields and enforces required severity and phase values.
     */
    public EtlDiagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(phase, "phase");
        reason = reason == null ? "" : reason;
        whatWentWrong = whatWentWrong == null ? "" : whatWentWrong;
        howToFix = howToFix == null ? "" : howToFix;
        exceptionType = exceptionType == null ? "" : exceptionType;
    }

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
