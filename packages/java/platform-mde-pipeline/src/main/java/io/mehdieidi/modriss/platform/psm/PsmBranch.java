package io.mehdieidi.modriss.platform.psm;

import java.time.Instant;

/** A separately editable PSM snapshot, independent of the project's AWS model pointer. */
public record PsmBranch(
    String id,
    String projectId,
    String createdBy,
    String packageId,
    String name,
    String sourcePimId,
    long sourcePimRevision,
    String sourcePimHash,
    String xmi,
    long revision,
    Instant createdAt,
    Instant updatedAt,
    tools.jackson.databind.JsonNode modelJson) {
  public PsmBranch {
    // JSON null and a missing legacy snapshot both require the XMI fallback.
    if (modelJson != null && modelJson.isNull()) {
      modelJson = null;
    }
  }

  public PsmBranch(
      String id,
      String projectId,
      String createdBy,
      String packageId,
      String name,
      String sourcePimId,
      long sourcePimRevision,
      String sourcePimHash,
      String xmi,
      long revision,
      Instant createdAt,
      Instant updatedAt) {
    this(
        id,
        projectId,
        createdBy,
        packageId,
        name,
        sourcePimId,
        sourcePimRevision,
        sourcePimHash,
        xmi,
        revision,
        createdAt,
        updatedAt,
        null);
  }
}
