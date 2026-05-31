package io.mehdieidi.modless.platform.core.model;

import java.time.Instant;
import java.util.List;

public record MdeJobRecord(
        String id,
        String projectId,
        String userId,
        String sourceModelId,
        ModelLevel sourceLevel,
        long sourceRevision,
        String sourceModelHash,
        MdeJobOperation operation,
        MdeJobStatus status,
        int progressPercent,
        String resultModelId,
        String resultArtifactId,
        List<String> diagnostics,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {

    public MdeJobRecord {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
