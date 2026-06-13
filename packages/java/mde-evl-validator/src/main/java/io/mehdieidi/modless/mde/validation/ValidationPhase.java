package io.mehdieidi.modless.mde.validation;

/** Lifecycle phase associated with an EVL diagnostic. */
public enum ValidationPhase {
  /** Request-level checks before module discovery. */
  REQUEST_VALIDATION,
  /** Discovery of EVL entry modules. */
  MODULE_DISCOVERY,
  /** Parsing EVL modules and imports. */
  PARSE,
  /** Loading and structurally validating EMF models. */
  MODEL_LOADING,
  /** Executing EVL constraints, critiques, guards, messages, and fixes. */
  EXECUTION,
  /** Unexpected failure outside the known phase buckets. */
  UNEXPECTED
}
