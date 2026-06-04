package io.mehdieidi.modless.mde.etl;

public final class EtlProfiler {

    private final boolean enabled;
    private long lastNanos;

    public EtlProfiler() {
        this.enabled = Boolean.getBoolean("modless.etlProfiler");
        this.lastNanos = System.nanoTime();
    }

    public void mark(String label) {
        if (!enabled) {
            return;
        }
        long now = System.nanoTime();
        System.out.println("ETL_PROFILE " + label + " "
                + ((now - lastNanos) / 1_000_000L) + "ms");
        lastNanos = now;
    }
}
