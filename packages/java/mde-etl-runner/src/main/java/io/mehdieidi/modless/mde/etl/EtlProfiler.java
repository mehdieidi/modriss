package io.mehdieidi.modless.mde.etl;

/**
 * Lightweight profiler exposed to ETL scripts through the Epsilon frame stack.
 */
public final class EtlProfiler {

    /**
     * Whether profiler output is enabled through the {@code modless.etlProfiler} flag.
     */
    private final boolean enabled;
    /**
     * Timestamp of the previous emitted marker.
     */
    private long lastNanos;

    /**
     * Creates a profiler using the JVM system property switch.
     */
    public EtlProfiler() {
        this.enabled = Boolean.getBoolean("modless.etlProfiler");
        this.lastNanos = System.nanoTime();
    }

    /**
     * Emits elapsed time since the previous marker when profiling is enabled.
     *
     * @param label human-readable marker label
     */
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
