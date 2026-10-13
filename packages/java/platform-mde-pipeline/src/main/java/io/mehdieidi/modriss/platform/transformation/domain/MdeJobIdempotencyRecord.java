package io.mehdieidi.modriss.platform.transformation.domain;

/** Index entry binding an async MDE idempotency key to the original job and fingerprint. */
public record MdeJobIdempotencyRecord(
    String scopeHash, String jobId, String projectId, String fingerprint) {}
