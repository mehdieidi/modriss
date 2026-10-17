package io.mehdieidi.modriss.platform.psm;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/** Immutable, project-owned language and executable package. Existing branches pin its id. */
public record PsmPlatformPackage(
    String id,
    String projectId,
    String uploadedBy,
    String name,
    String description,
    byte[] ecore,
    byte[] transformationZip,
    byte[] generationZip,
    byte[] validationZip,
    String transformationEntry,
    String generationEntry,
    JsonNode schema,
    Instant createdAt,
    JsonNode visualSyntax) {
  public PsmPlatformPackage(
      String id,
      String projectId,
      String uploadedBy,
      String name,
      String description,
      byte[] ecore,
      byte[] transformationZip,
      byte[] generationZip,
      byte[] validationZip,
      String transformationEntry,
      String generationEntry,
      JsonNode schema,
      Instant createdAt) {
    this(
        id,
        projectId,
        uploadedBy,
        name,
        description,
        ecore,
        transformationZip,
        generationZip,
        validationZip,
        transformationEntry,
        generationEntry,
        schema,
        createdAt,
        null);
  }
}
