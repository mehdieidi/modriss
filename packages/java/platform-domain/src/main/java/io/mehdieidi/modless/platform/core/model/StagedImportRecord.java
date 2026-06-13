package io.mehdieidi.modless.platform.core.model;

import java.time.Instant;

/**
 * Temporary record for uploaded XMI that is staged until a model create/update commits it.
 *
 * @param token staging token supplied by the client
 * @param userId user that staged the import
 * @param projectId project that owns the staged import
 * @param level model level for the staged XMI
 * @param xmiPath repository-relative staged XMI path
 * @param sizeBytes staged payload size
 * @param createdAt creation timestamp
 * @param expiresAt expiration timestamp
 */
public record StagedImportRecord(
    String token,
    String userId,
    String projectId,
    ModelLevel level,
    String xmiPath,
    long sizeBytes,
    Instant createdAt,
    Instant expiresAt) {}
