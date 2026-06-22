package io.mehdieidi.modless.platform.assistant.domain;

/** Explicit assistant workflow states surfaced to clients and audit records. */
public enum AssistantWorkflowState {
  /** Request was answered without a mutation proposal. */
  EXPLAINED,
  /** A proposal was drafted and is waiting for user approval. */
  PROPOSED,
  /** A user-approved proposal was applied after approval-time validation. */
  APPLIED,
  /** A stored proposal was rejected. */
  REJECTED,
  /** A previously applied proposal was undone. */
  UNDONE,
  /** The assistant needs a bounded user choice before continuing. */
  WAITING_FOR_CHOICE,
  /** Provider execution failed without changing the model. */
  FAILED
}
