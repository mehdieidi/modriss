package io.mehdieidi.varka.mde.etl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable accumulator for ETL phase timing values in milliseconds. */
public final class EtlPhaseTiming {

  /** Time spent validating the request. */
  private long validationMs;

  /** Time spent preparing writable output paths. */
  private long prepareOutputsMs;

  /** Time spent parsing the ETL module. */
  private long parseMs;

  /** Time spent loading read-only source models. */
  private long sourceModelLoadMs;

  /** Time spent loading writable target models. */
  private long targetModelLoadMs;

  /** Time spent executing ETL rules. */
  private long etlExecuteMs;

  /** Time spent storing target models. */
  private long modelStoreMs;

  /** Time spent disposing models and runtime context. */
  private long disposeMs;

  /** Total elapsed execution time. */
  private long totalMs;

  /**
   * Returns request validation time.
   *
   * @return elapsed milliseconds
   */
  public long validationMs() {
    return validationMs;
  }

  /**
   * Returns output preparation time.
   *
   * @return elapsed milliseconds
   */
  public long prepareOutputsMs() {
    return prepareOutputsMs;
  }

  /**
   * Returns ETL parse time.
   *
   * @return elapsed milliseconds
   */
  public long parseMs() {
    return parseMs;
  }

  /**
   * Returns source model loading time.
   *
   * @return elapsed milliseconds
   */
  public long sourceModelLoadMs() {
    return sourceModelLoadMs;
  }

  /**
   * Returns target model loading time.
   *
   * @return elapsed milliseconds
   */
  public long targetModelLoadMs() {
    return targetModelLoadMs;
  }

  /**
   * Returns ETL execution time.
   *
   * @return elapsed milliseconds
   */
  public long etlExecuteMs() {
    return etlExecuteMs;
  }

  /**
   * Returns target model persistence time.
   *
   * @return elapsed milliseconds
   */
  public long modelStoreMs() {
    return modelStoreMs;
  }

  /**
   * Returns model/context disposal time.
   *
   * @return elapsed milliseconds
   */
  public long disposeMs() {
    return disposeMs;
  }

  /**
   * Returns total ETL runtime.
   *
   * @return elapsed milliseconds
   */
  public long totalMs() {
    return totalMs;
  }

  /**
   * Exposes timing values in report serialization order.
   *
   * @return unmodifiable timing map
   */
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

  /**
   * Adds request validation time.
   *
   * @param elapsedNanos elapsed nanoseconds
   */
  void addValidation(long elapsedNanos) {
    validationMs += millis(elapsedNanos);
  }

  /**
   * Adds output preparation time.
   *
   * @param elapsedNanos elapsed nanoseconds
   */
  void addPrepareOutputs(long elapsedNanos) {
    prepareOutputsMs += millis(elapsedNanos);
  }

  /**
   * Adds parse time.
   *
   * @param elapsedNanos elapsed nanoseconds
   */
  void addParse(long elapsedNanos) {
    parseMs += millis(elapsedNanos);
  }

  /**
   * Adds model loading time to the source or target bucket.
   *
   * @param source {@code true} for source/read-only model time
   * @param elapsedNanos elapsed nanoseconds
   */
  void addModelLoad(boolean source, long elapsedNanos) {
    if (source) {
      sourceModelLoadMs += millis(elapsedNanos);
    } else {
      targetModelLoadMs += millis(elapsedNanos);
    }
  }

  /**
   * Adds ETL execution time.
   *
   * @param elapsedNanos elapsed nanoseconds
   */
  void addExecute(long elapsedNanos) {
    etlExecuteMs += millis(elapsedNanos);
  }

  /**
   * Adds model store time.
   *
   * @param elapsedNanos elapsed nanoseconds
   */
  void addStore(long elapsedNanos) {
    modelStoreMs += millis(elapsedNanos);
  }

  /**
   * Adds runtime disposal time.
   *
   * @param elapsedNanos elapsed nanoseconds
   */
  void addDispose(long elapsedNanos) {
    disposeMs += millis(elapsedNanos);
  }

  /**
   * Replaces total runtime with the supplied elapsed time.
   *
   * @param elapsedNanos elapsed nanoseconds
   */
  void setTotal(long elapsedNanos) {
    totalMs = millis(elapsedNanos);
  }

  /**
   * Converts nanoseconds to a non-negative millisecond value.
   *
   * @param elapsedNanos elapsed nanoseconds
   * @return elapsed milliseconds
   */
  private long millis(long elapsedNanos) {
    return Math.max(0L, elapsedNanos / 1_000_000L);
  }
}
