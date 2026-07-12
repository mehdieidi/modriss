package io.mehdieidi.varka.backend.upload;

import io.mehdieidi.varka.platform.kernel.ModelLevel;
import java.time.Instant;

/** Persisted metadata for a user upload. */
public record UploadedFileRecord(
    String id,
    String userId,
    String projectId,
    ModelLevel level,
    String sessionId,
    String originalFileName,
    String storedFileName,
    String contentType,
    long sizeBytes,
    Instant uploadedAt) {}
