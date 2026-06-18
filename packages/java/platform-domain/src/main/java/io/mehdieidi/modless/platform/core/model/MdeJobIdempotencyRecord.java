package io.mehdieidi.modless.platform.core.model;

/** Index entry binding an async MDE idempotency key to the original job and fingerprint. */
public record MdeJobIdempotencyRecord(
    String scopeHash, String jobId, String projectId, String fingerprint) {}
