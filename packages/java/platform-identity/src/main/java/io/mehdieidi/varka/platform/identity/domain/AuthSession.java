package io.mehdieidi.varka.platform.identity.domain;

import java.time.Instant;

/**
 * Persisted authentication session issued to a user.
 *
 * @param token bearer token used to load the session
 * @param userId authenticated user identifier
 * @param createdAt creation timestamp
 * @param expiresAt expiration timestamp
 */
public record AuthSession(String token, String userId, Instant createdAt, Instant expiresAt) {}
