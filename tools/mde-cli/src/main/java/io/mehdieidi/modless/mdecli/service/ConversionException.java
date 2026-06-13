package io.mehdieidi.modless.mdecli.service;

import io.mehdieidi.modless.mdecli.diagnostics.ConversionReport;

/** Signals a conversion failure while retaining its structured report and exit-code category. */
public final class ConversionException extends RuntimeException {

  private final ConversionReport report;
  private final boolean userFixable;

  /**
   * Creates a conversion failure.
   *
   * @param message failure message
   * @param report structured conversion report
   * @param userFixable whether correcting user input can resolve the failure
   * @param cause underlying cause, when available
   */
  public ConversionException(
      String message, ConversionReport report, boolean userFixable, Throwable cause) {
    super(message, cause);
    this.report = report;
    this.userFixable = userFixable;
  }

  /**
   * Returns the structured conversion report.
   *
   * @return conversion report
   */
  public ConversionReport getReport() {
    return report;
  }

  /**
   * Indicates whether this failure is expected to be correctable by the user.
   *
   * @return {@code true} for a user-fixable failure
   */
  public boolean isUserFixable() {
    return userFixable;
  }
}
