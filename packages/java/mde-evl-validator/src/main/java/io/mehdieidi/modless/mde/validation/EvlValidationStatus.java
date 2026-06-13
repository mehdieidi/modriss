package io.mehdieidi.modless.mde.validation;

/** Terminal status for an EVL validation report. */
public enum EvlValidationStatus {
  /** Validation completed without error diagnostics. */
  SUCCEEDED,
  /** Validation stopped with request, module, or execution errors. */
  FAILED
}
