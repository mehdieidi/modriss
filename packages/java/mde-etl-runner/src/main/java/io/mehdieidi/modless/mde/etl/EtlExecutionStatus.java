package io.mehdieidi.modless.mde.etl;

/** Terminal status for an ETL execution report. */
public enum EtlExecutionStatus {
  /** ETL completed without error diagnostics. */
  SUCCEEDED,
  /** ETL stopped before producing a valid target model. */
  FAILED
}
