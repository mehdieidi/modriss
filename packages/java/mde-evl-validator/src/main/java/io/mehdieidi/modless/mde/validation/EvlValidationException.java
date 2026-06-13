package io.mehdieidi.modless.mde.validation;

/** Exception raised when EVL validation cannot produce a successful report. */
public class EvlValidationException extends Exception {

  /** Structured report describing the failed validation run. */
  private final EvlValidationReport report;

  /**
   * Creates an exception with a failed validation report.
   *
   * @param message exception message
   * @param report failed validation report
   */
  public EvlValidationException(String message, EvlValidationReport report) {
    super(message);
    this.report = report;
  }

  /**
   * Creates an exception with a failed validation report and root cause.
   *
   * @param message exception message
   * @param report failed validation report
   * @param cause root cause
   */
  public EvlValidationException(String message, EvlValidationReport report, Throwable cause) {
    super(message, cause);
    this.report = report;
  }

  /**
   * Returns the structured report captured at the point of failure.
   *
   * @return failed validation report
   */
  public EvlValidationReport getReport() {
    return report;
  }
}
