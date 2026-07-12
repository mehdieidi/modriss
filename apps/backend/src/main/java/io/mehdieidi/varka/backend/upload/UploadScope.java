package io.mehdieidi.varka.backend.upload;

import io.mehdieidi.varka.platform.kernel.ModelLevel;

/** Ownership scope for an uploaded file. */
public record UploadScope(String userId, String projectId, ModelLevel level, String sessionId) {

  /** Normalizes scope values. */
  public UploadScope {
    userId = normalize(userId, "user");
    projectId = normalize(projectId, "project");
    if (level == null) {
      throw new IllegalArgumentException("Upload level is required.");
    }
    sessionId = normalize(sessionId, "session");
  }

  private static String normalize(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Upload " + name + " is required.");
    }
    return value.trim();
  }
}
