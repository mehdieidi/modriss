package io.mehdieidi.modriss.platform.transformation.domain;

/** Lifecycle status for an asynchronous MDE job. */
public enum MdeJobStatus {
  /** Job has been accepted but has not started executing. */
  QUEUED,
  /** Job is currently executing. */
  RUNNING,
  /** Job completed successfully. */
  SUCCEEDED,
  /** Job completed with an error. */
  FAILED,
  /** Job was cancelled before successful completion. */
  CANCELLED
}
