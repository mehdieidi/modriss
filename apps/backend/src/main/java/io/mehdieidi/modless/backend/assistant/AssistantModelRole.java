package io.mehdieidi.modless.backend.assistant;

/**
 * Logical assistant model roles.
 */
public enum AssistantModelRole {
    /**
     * Decomposes user intent into bounded workflow steps.
     */
    PLANNER,
    /**
     * Produces user-facing assistant responses.
     */
    RESPONDER,
    /**
     * Compresses conversation history.
     */
    SUMMARIZER
}
