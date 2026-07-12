package io.mehdieidi.varka.platform.modeling.runtime;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Runtime limits and asset roots used by MDE validation, transformation, and generation services.
 *
 * @param repositoryRoot explicit repository root, or {@code null} for discovery
 * @param validationRoot explicit validation asset root
 * @param transformationRoot explicit transformation asset root
 * @param generationRoot explicit generation asset root
 * @param metamodelRoot explicit metamodel asset root
 * @param executionTimeout timeout for individual Epsilon executions
 * @param maxCapturedOutputBytes output capture limit per stream
 * @param maxConcurrentJobs asynchronous MDE job worker count
 * @param queueCapacity asynchronous MDE job queue capacity
 * @param jobTimeout reserved job-level timeout
 * @param maxModelUploadBytes maximum uploaded or transformed model bytes
 * @param maxGeneratedFiles maximum number of generated artifact files
 * @param maxGeneratedFileBytes maximum bytes per generated artifact file
 * @param maxGeneratedArtifactBytes maximum total generated artifact bytes
 * @param stagedImportTtl time-to-live for staged XMI uploads
 */
public record MdeRuntimeOptions(
    Path repositoryRoot,
    Path validationRoot,
    Path transformationRoot,
    Path generationRoot,
    Path metamodelRoot,
    Duration executionTimeout,
    int maxCapturedOutputBytes,
    int maxConcurrentJobs,
    int queueCapacity,
    Duration jobTimeout,
    long maxModelUploadBytes,
    int maxGeneratedFiles,
    long maxGeneratedFileBytes,
    long maxGeneratedArtifactBytes,
    Duration stagedImportTtl) {

  /** Default Epsilon execution timeout. */
  private static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofMinutes(5);

  /** Default asynchronous job timeout. */
  private static final Duration DEFAULT_JOB_TIMEOUT = Duration.ofMinutes(10);

  /** Default staged import time-to-live. */
  private static final Duration DEFAULT_IMPORT_TTL = Duration.ofHours(2);

  /** Default output capture limit per stream. */
  private static final int DEFAULT_MAX_CAPTURED_OUTPUT_BYTES = 1024 * 1024;

  /** Default asynchronous job worker count. */
  private static final int DEFAULT_MAX_CONCURRENT_JOBS = 2;

  /** Default asynchronous job queue capacity. */
  private static final int DEFAULT_QUEUE_CAPACITY = 32;

  /** Default uploaded model size limit. */
  private static final long DEFAULT_MAX_MODEL_UPLOAD_BYTES = 20L * 1024L * 1024L;

  /** Default generated file count limit. */
  private static final int DEFAULT_MAX_GENERATED_FILES = 2_000;

  /** Default per-file generated artifact size limit. */
  private static final long DEFAULT_MAX_GENERATED_FILE_BYTES = 5L * 1024L * 1024L;

  /** Default aggregate generated artifact size limit. */
  private static final long DEFAULT_MAX_GENERATED_ARTIFACT_BYTES = 50L * 1024L * 1024L;

  /** Applies defaults for missing, zero, or negative runtime limits. */
  public MdeRuntimeOptions {
    executionTimeout = positiveDuration(executionTimeout, DEFAULT_EXECUTION_TIMEOUT);
    maxCapturedOutputBytes = positiveInt(maxCapturedOutputBytes, DEFAULT_MAX_CAPTURED_OUTPUT_BYTES);
    maxConcurrentJobs = positiveInt(maxConcurrentJobs, DEFAULT_MAX_CONCURRENT_JOBS);
    queueCapacity = positiveInt(queueCapacity, DEFAULT_QUEUE_CAPACITY);
    jobTimeout = positiveDuration(jobTimeout, DEFAULT_JOB_TIMEOUT);
    maxModelUploadBytes = positiveLong(maxModelUploadBytes, DEFAULT_MAX_MODEL_UPLOAD_BYTES);
    maxGeneratedFiles = positiveInt(maxGeneratedFiles, DEFAULT_MAX_GENERATED_FILES);
    maxGeneratedFileBytes = positiveLong(maxGeneratedFileBytes, DEFAULT_MAX_GENERATED_FILE_BYTES);
    maxGeneratedArtifactBytes =
        positiveLong(maxGeneratedArtifactBytes, DEFAULT_MAX_GENERATED_ARTIFACT_BYTES);
    stagedImportTtl = positiveDuration(stagedImportTtl, DEFAULT_IMPORT_TTL);
  }

  /**
   * Returns runtime options with all defaults applied.
   *
   * @return default runtime options
   */
  public static MdeRuntimeOptions defaults() {
    return new MdeRuntimeOptions(
        null, null, null, null, null, null, 0, 0, 0, null, 0, 0, 0, 0, null);
  }

  /**
   * Returns a positive duration or a fallback.
   *
   * @param value candidate duration
   * @param fallback fallback duration
   * @return positive duration
   */
  private static Duration positiveDuration(Duration value, Duration fallback) {
    return value == null || value.isZero() || value.isNegative() ? fallback : value;
  }

  /**
   * Returns a positive integer or a fallback.
   *
   * @param value candidate value
   * @param fallback fallback value
   * @return positive integer
   */
  private static int positiveInt(int value, int fallback) {
    return value <= 0 ? fallback : value;
  }

  /**
   * Returns a positive long or a fallback.
   *
   * @param value candidate value
   * @param fallback fallback value
   * @return positive long
   */
  private static long positiveLong(long value, long fallback) {
    return value <= 0 ? fallback : value;
  }
}
