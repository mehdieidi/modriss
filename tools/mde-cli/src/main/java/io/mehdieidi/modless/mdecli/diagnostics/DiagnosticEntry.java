package io.mehdieidi.modless.mdecli.diagnostics;

/**
 * One structured compiler or resource diagnostic.
 *
 * @param severity diagnostic severity
 * @param message diagnostic message
 * @param location source location description
 * @param line one-based source line, or a negative value when unavailable
 * @param column one-based source column, or a negative value when unavailable
 * @param hint actionable resolution hint
 * @param sourceExcerpt relevant source excerpt
 */
public record DiagnosticEntry(
    Severity severity,
    String message,
    String location,
    int line,
    int column,
    String hint,
    String sourceExcerpt) {

  /** Severity of a conversion diagnostic. */
  public enum Severity {
    ERROR,
    WARNING,
    INFO
  }
}
