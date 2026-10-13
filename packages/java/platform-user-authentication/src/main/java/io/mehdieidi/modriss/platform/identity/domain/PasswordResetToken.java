package io.mehdieidi.modriss.platform.identity.domain;

import java.time.Instant;

/** Persisted, short-lived password reset secret. */
public record PasswordResetToken(String id, String userId, String secretHash, Instant expiresAt) {}
