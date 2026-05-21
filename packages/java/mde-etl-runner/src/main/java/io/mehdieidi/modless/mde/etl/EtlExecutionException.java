package io.mehdieidi.modless.mde.etl;

public final class EtlExecutionException extends Exception {

    private final EtlExecutionReport report;

    public EtlExecutionException(String message, EtlExecutionReport report) {
        super(message);
        this.report = report;
    }

    public EtlExecutionException(String message, EtlExecutionReport report, Throwable cause) {
        super(message, cause);
        this.report = report;
    }

    public EtlExecutionReport getReport() {
        return report;
    }
}
