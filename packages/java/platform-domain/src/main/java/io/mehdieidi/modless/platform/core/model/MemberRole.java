package io.mehdieidi.modless.platform.core.model;

/**
 * Project membership role.
 */
public enum MemberRole {
    /**
     * Full control over the project and membership.
     */
    OWNER,
    /**
     * Can edit project content but not perform owner-only actions.
     */
    EDITOR,
    /**
     * Can read project content without editing.
     */
    VIEWER
}
