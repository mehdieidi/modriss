package io.mehdieidi.modless.platform.identity.domain;

import java.time.Instant;

/**
 * Persisted platform user account.
 *
 * @param id user identifier
 * @param email normalized email address
 * @param displayName display name
 * @param passwordHash password hash
 * @param salt password salt
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 */
public record UserRecord(
    String id,
    String email,
    String displayName,
    String passwordHash,
    String salt,
    Instant createdAt,
    Instant updatedAt) {}
