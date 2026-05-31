package io.mehdieidi.modless.platform.core.service;

import java.nio.file.Path;
import java.time.Duration;

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

    private static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration DEFAULT_JOB_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration DEFAULT_IMPORT_TTL = Duration.ofHours(2);
    private static final int DEFAULT_MAX_CAPTURED_OUTPUT_BYTES = 1024 * 1024;
    private static final int DEFAULT_MAX_CONCURRENT_JOBS = 2;
    private static final int DEFAULT_QUEUE_CAPACITY = 32;
    private static final long DEFAULT_MAX_MODEL_UPLOAD_BYTES = 20L * 1024L * 1024L;
    private static final int DEFAULT_MAX_GENERATED_FILES = 2_000;
    private static final long DEFAULT_MAX_GENERATED_FILE_BYTES = 5L * 1024L * 1024L;
    private static final long DEFAULT_MAX_GENERATED_ARTIFACT_BYTES = 50L * 1024L * 1024L;

    public MdeRuntimeOptions {
        executionTimeout = positiveDuration(executionTimeout, DEFAULT_EXECUTION_TIMEOUT);
        maxCapturedOutputBytes = positiveInt(maxCapturedOutputBytes,
                DEFAULT_MAX_CAPTURED_OUTPUT_BYTES);
        maxConcurrentJobs = positiveInt(maxConcurrentJobs, DEFAULT_MAX_CONCURRENT_JOBS);
        queueCapacity = positiveInt(queueCapacity, DEFAULT_QUEUE_CAPACITY);
        jobTimeout = positiveDuration(jobTimeout, DEFAULT_JOB_TIMEOUT);
        maxModelUploadBytes = positiveLong(maxModelUploadBytes, DEFAULT_MAX_MODEL_UPLOAD_BYTES);
        maxGeneratedFiles = positiveInt(maxGeneratedFiles, DEFAULT_MAX_GENERATED_FILES);
        maxGeneratedFileBytes = positiveLong(maxGeneratedFileBytes,
                DEFAULT_MAX_GENERATED_FILE_BYTES);
        maxGeneratedArtifactBytes = positiveLong(maxGeneratedArtifactBytes,
                DEFAULT_MAX_GENERATED_ARTIFACT_BYTES);
        stagedImportTtl = positiveDuration(stagedImportTtl, DEFAULT_IMPORT_TTL);
    }

    public static MdeRuntimeOptions defaults() {
        return new MdeRuntimeOptions(null, null, null, null, null, null, 0, 0, 0, null, 0,
                0, 0, 0, null);
    }

    private static Duration positiveDuration(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private static int positiveInt(int value, int fallback) {
        return value <= 0 ? fallback : value;
    }

    private static long positiveLong(long value, long fallback) {
        return value <= 0 ? fallback : value;
    }
}
