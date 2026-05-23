package io.mehdieidi.modless.mde.validation;

public class EvlValidationException extends Exception {

    private final EvlValidationReport report;

    public EvlValidationException(String message, EvlValidationReport report) {
        super(message);
        this.report = report;
    }

    public EvlValidationException(String message, EvlValidationReport report, Throwable cause) {
        super(message, cause);
        this.report = report;
    }

    public EvlValidationReport getReport() {
        return report;
    }
}
