package io.mehdieidi.modriss.platform.identity.domain;

import java.time.Instant;

/** Persisted, one-time email verification token with a hashed secret. */
public record EmailVerificationToken(
    String id, String userId, String secretHash, Instant expiresAt) {}
