package io.mehdieidi.modriss.mde.generation;

/** Lifecycle phase associated with a generation diagnostic. */
public enum GenerationPhase {
  /** Request validation before Epsilon runtime setup. */
  VALIDATION,
  /** EGX module parsing and import resolution. */
  PARSE,
  /** Loading source EMF models. */
  MODEL_LOADING,
  /** Executing EGX rules and EGL templates. */
  EXECUTION,
  /** Unexpected failure outside the known phase buckets. */
  UNEXPECTED
}
