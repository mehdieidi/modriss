package io.mehdieidi.modless.backend.assistant;

/**
 * Explicit assistant workflow states surfaced to clients and audit records.
 */
public enum AssistantWorkflowState {
    /**
     * Request was answered without a mutation proposal.
     */
    EXPLAINED,
    /**
     * A proposal was drafted and is waiting for user approval.
     */
    PROPOSED,
    /**
     * A low-risk proposal was applied after validation.
     */
    APPLIED,
    /**
     * A stored proposal was rejected.
     */
    REJECTED,
    /**
     * A previously applied proposal was undone.
     */
    UNDONE,
    /**
     * The assistant needs a bounded user choice before continuing.
     */
    WAITING_FOR_CHOICE
}
