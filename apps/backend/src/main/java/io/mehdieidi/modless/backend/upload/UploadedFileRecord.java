package io.mehdieidi.modless.backend.upload;

import io.mehdieidi.modless.platform.kernel.ModelLevel;
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
