package io.mehdieidi.modriss.platform.transformation.synchronization;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Durable pending merge state used to resolve conflicts without touching canonical models. */
public record SynchronizationSession(
    String id,
    TransformationDirection direction,
    String projectId,
    String sourceModelId,
    String targetModelId,
    long workingRevision,
    String workingFingerprint,
    long baselineVersion,
    long newSourceRevision,
    String newSourceFingerprint,
    String transformationFingerprint,
    Instant createdAt,
    JsonNode pendingMergedWorking,
    byte[] rawBaseXmi,
    byte[] rawWorkingXmi,
    byte[] pendingMergedWorkingXmi,
    JsonNode rawNewGenerated,
    byte[] rawNewGeneratedXmi,
    List<ModelConflict> conflicts,
    Map<String, ConflictResolution> resolutions) {

  public SynchronizationSession {
    rawBaseXmi = rawBaseXmi == null ? new byte[0] : rawBaseXmi.clone();
    rawWorkingXmi = rawWorkingXmi == null ? new byte[0] : rawWorkingXmi.clone();
    pendingMergedWorkingXmi =
        pendingMergedWorkingXmi == null ? new byte[0] : pendingMergedWorkingXmi.clone();
    rawNewGeneratedXmi = rawNewGeneratedXmi == null ? new byte[0] : rawNewGeneratedXmi.clone();
    conflicts = List.copyOf(conflicts);
    resolutions = resolutions == null ? Map.of() : Map.copyOf(resolutions);
  }

  @Override
  public byte[] rawBaseXmi() {
    return rawBaseXmi.clone();
  }

  @Override
  public byte[] rawWorkingXmi() {
    return rawWorkingXmi.clone();
  }

  @Override
  public byte[] pendingMergedWorkingXmi() {
    return pendingMergedWorkingXmi.clone();
  }

  @Override
  public byte[] rawNewGeneratedXmi() {
    return rawNewGeneratedXmi.clone();
  }
}
