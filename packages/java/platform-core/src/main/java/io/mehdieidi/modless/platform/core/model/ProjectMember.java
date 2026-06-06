package io.mehdieidi.modless.platform.core.model;

import java.time.Instant;

/**
 * User membership entry embedded in a project record.
 *
 * @param userId      member user identifier
 * @param email       member email snapshot
 * @param displayName member display-name snapshot
 * @param role        project role
 * @param addedAt     membership timestamp
 */
public record ProjectMember(
        String userId,
        String email,
        String displayName,
        MemberRole role,
        Instant addedAt) {

}
