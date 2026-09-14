package io.mehdieidi.modriss.mde.etl;

/** Severity assigned to diagnostics produced while preparing or executing ETL. */
public enum DiagnosticSeverity {
  /** Informational message that does not indicate a failure. */
  INFO,
  /** Recoverable condition that should be surfaced to operators. */
  WARNING,
  /** Failure condition that prevents a successful ETL report. */
  ERROR
}
