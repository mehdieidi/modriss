package io.mehdieidi.modless.mde.validation;

/**
 * Severity assigned to EVL diagnostics.
 */
public enum ValidationSeverity {
    /**
     * Failure condition that should fail validation.
     */
    ERROR,
    /**
     * Recoverable condition that should be reported to callers.
     */
    WARNING
}
