package io.mehdieidi.modless.mde.etl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EtlPhaseTiming {

    private long validationMs;
    private long prepareOutputsMs;
    private long parseMs;
    private long sourceModelLoadMs;
    private long targetModelLoadMs;
    private long etlExecuteMs;
    private long modelStoreMs;
    private long disposeMs;
    private long totalMs;

    public long validationMs() {
        return validationMs;
    }

    public long prepareOutputsMs() {
        return prepareOutputsMs;
    }

    public long parseMs() {
        return parseMs;
    }

    public long sourceModelLoadMs() {
        return sourceModelLoadMs;
    }

    public long targetModelLoadMs() {
        return targetModelLoadMs;
    }

    public long etlExecuteMs() {
        return etlExecuteMs;
    }

    public long modelStoreMs() {
        return modelStoreMs;
    }

    public long disposeMs() {
        return disposeMs;
    }

    public long totalMs() {
        return totalMs;
    }

    public Map<String, Long> asMap() {
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("validationMs", validationMs);
        values.put("prepareOutputsMs", prepareOutputsMs);
        values.put("parseMs", parseMs);
        values.put("sourceModelLoadMs", sourceModelLoadMs);
        values.put("targetModelLoadMs", targetModelLoadMs);
        values.put("etlExecuteMs", etlExecuteMs);
        values.put("modelStoreMs", modelStoreMs);
        values.put("disposeMs", disposeMs);
        values.put("totalMs", totalMs);
        return Collections.unmodifiableMap(values);
    }

    void addValidation(long elapsedNanos) {
        validationMs += millis(elapsedNanos);
    }

    void addPrepareOutputs(long elapsedNanos) {
        prepareOutputsMs += millis(elapsedNanos);
    }

    void addParse(long elapsedNanos) {
        parseMs += millis(elapsedNanos);
    }

    void addModelLoad(boolean source, long elapsedNanos) {
        if (source) {
            sourceModelLoadMs += millis(elapsedNanos);
        } else {
            targetModelLoadMs += millis(elapsedNanos);
        }
    }

    void addExecute(long elapsedNanos) {
        etlExecuteMs += millis(elapsedNanos);
    }

    void addStore(long elapsedNanos) {
        modelStoreMs += millis(elapsedNanos);
    }

    void addDispose(long elapsedNanos) {
        disposeMs += millis(elapsedNanos);
    }

    void setTotal(long elapsedNanos) {
        totalMs = millis(elapsedNanos);
    }

    private long millis(long elapsedNanos) {
        return Math.max(0L, elapsedNanos / 1_000_000L);
    }
}
