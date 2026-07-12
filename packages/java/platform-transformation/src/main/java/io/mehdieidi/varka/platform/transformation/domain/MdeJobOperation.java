package io.mehdieidi.varka.platform.transformation.domain;

/** Operation type executed by an asynchronous MDE job. */
public enum MdeJobOperation {
  /** Transform a CIM model into a PIM model. */
  CIM_TO_PIM,
  /** Transform a PIM model into an AWS PSM model. */
  PIM_TO_PSM,
  /** Generate artifacts from an AWS PSM model. */
  PSM_TO_ARTIFACT,
  /** Reserved validation operation value. */
  VALIDATE
}
