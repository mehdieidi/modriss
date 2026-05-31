package io.mehdieidi.modless.platform.core.model;

import java.time.Instant;

public record StagedImportRecord(
        String token,
        String userId,
        String projectId,
        ModelLevel level,
        String xmiPath,
        long sizeBytes,
        Instant createdAt,
        Instant expiresAt) {

}
