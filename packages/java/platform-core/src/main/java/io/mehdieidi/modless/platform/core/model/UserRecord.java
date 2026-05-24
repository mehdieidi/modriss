package io.mehdieidi.modless.platform.core.model;

import java.time.Instant;

public record UserRecord(
        String id,
        String email,
        String displayName,
        String passwordHash,
        String salt,
        Instant createdAt,
        Instant updatedAt) {

}
