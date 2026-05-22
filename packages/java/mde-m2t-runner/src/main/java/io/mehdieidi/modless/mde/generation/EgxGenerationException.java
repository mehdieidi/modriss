package io.mehdieidi.modless.mde.generation;

public final class EgxGenerationException extends Exception {

    private final EgxGenerationReport report;

    public EgxGenerationException(String message, EgxGenerationReport report) {
        super(message);
        this.report = report;
    }

    public EgxGenerationException(String message, EgxGenerationReport report, Throwable cause) {
        super(message, cause);
        this.report = report;
    }

    public EgxGenerationReport getReport() {
        return report;
    }
}
