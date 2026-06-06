package io.mehdieidi.modless.mde.generation;

/**
 * Terminal status for an EGX generation report.
 */
public enum GenerationStatus {
    /**
     * Generation completed without error diagnostics.
     */
    SUCCEEDED,
    /**
     * Generation stopped before producing a valid artifact tree.
     */
    FAILED
}
