package io.mehdieidi.varka.platform.project.domain;

import java.time.Instant;

/**
 * User membership entry embedded in a project record.
 *
 * @param userId member user identifier
 * @param email member email snapshot
 * @param displayName member display-name snapshot
 * @param role project role label
 * @param addedAt membership timestamp
 */
public record ProjectMember(
    String userId, String email, String displayName, String role, Instant addedAt) {}
