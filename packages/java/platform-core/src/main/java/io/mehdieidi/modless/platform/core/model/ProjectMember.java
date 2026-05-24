package io.mehdieidi.modless.platform.core.model;

import java.time.Instant;

public record ProjectMember(
        String userId,
        String email,
        String displayName,
        MemberRole role,
        Instant addedAt) {

}
