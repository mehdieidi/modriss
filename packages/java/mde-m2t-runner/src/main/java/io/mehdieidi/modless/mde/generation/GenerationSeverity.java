package io.mehdieidi.modless.mde.generation;

/**
 * Severity assigned to EGX generation diagnostics.
 */
public enum GenerationSeverity {
    /**
     * Failure condition that should fail generation.
     */
    ERROR,
    /**
     * Recoverable condition that should be reported to callers.
     */
    WARNING
}
