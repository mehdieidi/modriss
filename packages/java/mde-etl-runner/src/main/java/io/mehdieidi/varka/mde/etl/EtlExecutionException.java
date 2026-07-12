package io.mehdieidi.varka.mde.etl;

/** Exception raised when ETL execution cannot produce a successful report. */
public final class EtlExecutionException extends Exception {

  /** Structured report describing the failed execution. */
  private final EtlExecutionReport report;

  /**
   * Creates an exception with a failure report.
   *
   * @param message exception message
   * @param report failed ETL report
   */
  public EtlExecutionException(String message, EtlExecutionReport report) {
    super(message);
    this.report = report;
  }

  /**
   * Creates an exception with a failure report and root cause.
   *
   * @param message exception message
   * @param report failed ETL report
   * @param cause root cause
   */
  public EtlExecutionException(String message, EtlExecutionReport report, Throwable cause) {
    super(message, cause);
    this.report = report;
  }

  /**
   * Returns the structured report captured at the point of failure.
   *
   * @return failed ETL report
   */
  public EtlExecutionReport getReport() {
    return report;
  }
}
