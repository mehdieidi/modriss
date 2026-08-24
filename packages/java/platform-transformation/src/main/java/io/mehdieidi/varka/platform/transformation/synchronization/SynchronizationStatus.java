package io.mehdieidi.varka.platform.transformation.synchronization;

/** Outcome of a generated-model synchronization attempt. */
public enum SynchronizationStatus {
  APPLIED,
  CONFLICTS,
  BOOTSTRAP_REQUIRED,
  VALIDATION_FAILED,
  STALE_SESSION
}
