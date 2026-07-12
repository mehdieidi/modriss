package io.mehdieidi.varka.platform.transformation.domain;

import io.mehdieidi.varka.platform.kernel.ModelLevel;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Persisted state for an asynchronous MDE transformation or generation job.
 *
 * @param id job identifier
 * @param projectId owning project identifier
 * @param userId submitting user identifier
 * @param sourceModelId source model identifier
 * @param sourceLevel source model level
 * @param sourceRevision revision observed when the job was submitted
 * @param sourceModelHash hash of the source payload observed at submission
 * @param operation operation to execute
 * @param status current job status
 * @param progressPercent coarse progress value from 0 to 100
 * @param resultModelId generated model identifier, when applicable
 * @param resultArtifactId generated artifact identifier, when applicable
 * @param diagnostics user-facing job diagnostics
 * @param validationResult stored validation result payload, when applicable
 * @param timings phase and lifecycle timings in milliseconds
 * @param idempotencyKey optional caller-supplied idempotency key
 * @param fingerprint request fingerprint bound to the idempotency key
 * @param createdAt submission timestamp
 * @param startedAt execution start timestamp, when started
 * @param finishedAt completion timestamp, when terminal
 */
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
    Object validationResult,
    Map<String, Long> timings,
    String idempotencyKey,
    String fingerprint,
    Instant createdAt,
    Instant startedAt,
    Instant finishedAt) {

  /** Defensively copies diagnostics and treats a missing list as empty. */
  public MdeJobRecord {
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    timings = timings == null ? Map.of() : Map.copyOf(timings);
  }

  /**
   * Backward-compatible constructor for stored records created before timing and idempotency were
   * introduced.
   */
  public MdeJobRecord(
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
    this(
        id,
        projectId,
        userId,
        sourceModelId,
        sourceLevel,
        sourceRevision,
        sourceModelHash,
        operation,
        status,
        progressPercent,
        resultModelId,
        resultArtifactId,
        diagnostics,
        null,
        Map.of(),
        null,
        null,
        createdAt,
        startedAt,
        finishedAt);
  }
}
