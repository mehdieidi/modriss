package io.mehdieidi.varka.platform.transformation.synchronization;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/** Untouched raw output of the last accepted ETL generation. */
public record GeneratedBaseline(
    TransformationDirection direction,
    String projectId,
    String sourceModelId,
    long sourceRevision,
    String sourceFingerprint,
    String targetModelId,
    long baselineVersion,
    String transformationFingerprint,
    Instant createdAt,
    JsonNode rawGeneratedModel,
    byte[] rawGeneratedXmi) {

  public GeneratedBaseline {
    rawGeneratedXmi = rawGeneratedXmi == null ? new byte[0] : rawGeneratedXmi.clone();
  }

  @Override
  public byte[] rawGeneratedXmi() {
    return rawGeneratedXmi.clone();
  }
}
