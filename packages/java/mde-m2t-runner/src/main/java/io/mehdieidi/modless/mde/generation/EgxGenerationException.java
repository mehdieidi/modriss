package io.mehdieidi.modless.mde.generation;

/**
 * Exception raised when EGX generation cannot produce a successful report.
 */
public final class EgxGenerationException extends Exception {

    /**
     * Structured report describing the failed generation run.
     */
    private final EgxGenerationReport report;

    /**
     * Creates an exception with a failed generation report.
     *
     * @param message exception message
     * @param report  failed generation report
     */
    public EgxGenerationException(String message, EgxGenerationReport report) {
        super(message);
        this.report = report;
    }

    /**
     * Creates an exception with a failed generation report and root cause.
     *
     * @param message exception message
     * @param report  failed generation report
     * @param cause   root cause
     */
    public EgxGenerationException(String message, EgxGenerationReport report, Throwable cause) {
        super(message, cause);
        this.report = report;
    }

    /**
     * Returns the structured report captured at the point of failure.
     *
     * @return failed generation report
     */
    public EgxGenerationReport getReport() {
        return report;
    }
}
