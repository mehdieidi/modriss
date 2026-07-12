package io.mehdieidi.varka.mde.etl;

/** Lifecycle phase associated with an ETL diagnostic. */
public enum ExecutionPhase {
  /** Request validation before touching outputs or Epsilon runtime state. */
  VALIDATION,
  /** ETL module parsing and import resolution. */
  PARSE,
  /** Loading source or target EMF models. */
  MODEL_LOADING,
  /** Executing ETL rules. */
  EXECUTION,
  /** Persisting writable target models. */
  MODEL_STORING,
  /** Unexpected failure outside the known phase buckets. */
  UNEXPECTED
}
