package io.mehdieidi.modless.mdecli.service;

import io.mehdieidi.modless.mdecli.diagnostics.ConversionReport;

public final class ConversionException extends RuntimeException {

    private final ConversionReport report;
    private final boolean userFixable;

    public ConversionException(String message, ConversionReport report, boolean userFixable,
            Throwable cause) {
        super(message, cause);
        this.report = report;
        this.userFixable = userFixable;
    }

    public ConversionReport getReport() {
        return report;
    }

    public boolean isUserFixable() {
        return userFixable;
    }
}
