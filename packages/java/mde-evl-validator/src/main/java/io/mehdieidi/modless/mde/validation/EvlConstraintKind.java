package io.mehdieidi.modless.mde.validation;

/**
 * Type of EVL constraint violation.
 */
public enum EvlConstraintKind {
    /**
     * Constraint violation that should fail validation.
     */
    MANDATORY,
    /**
     * Critique violation that should be reported as optional guidance.
     */
    OPTIONAL
}
