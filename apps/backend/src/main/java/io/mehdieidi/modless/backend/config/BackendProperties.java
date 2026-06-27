package io.mehdieidi.modless.backend.config;

import io.mehdieidi.modless.platform.modeling.runtime.MdeRuntimeOptions;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration for backend storage, sessions, CORS, and MDE runtimes.
 *
 * @param storageRoot root directory for persisted backend data
 * @param sessionTtl lifetime of authenticated sessions
 * @param allowedOrigins origins permitted by the backend CORS policy
 * @param mde model-driven engineering runtime settings
 */
@ConfigurationProperties(prefix = "modless")
public record BackendProperties(
    Path storageRoot, Duration sessionTtl, List<String> allowedOrigins, Mde mde, Upload upload) {

  /** Applies safe defaults and immutable collection semantics to bound properties. */
  public BackendProperties {
    storageRoot = storageRoot == null ? Path.of("storage") : storageRoot;
    sessionTtl = sessionTtl == null ? Duration.ofDays(30) : sessionTtl;
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    mde =
        mde == null
            ? new Mde(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null)
            : mde;
    upload = upload == null ? new Upload(null, null, null) : upload;
  }

  /**
   * Converts a nullable integer setting to the runtime's disabled value.
   *
   * @param value configured value, or {@code null}
   * @return the configured value, or zero when absent
   */
  private static int integer(Integer value) {
    return value == null ? 0 : value;
  }

  /**
   * Converts a nullable long setting to the runtime's disabled value.
   *
   * @param value configured value, or {@code null}
   * @return the configured value, or zero when absent
   */
  private static long longValue(Long value) {
    return value == null ? 0 : value;
  }

  /**
   * Converts nested backend properties into the platform runtime options.
   *
   * @return normalized MDE runtime options
   */
  public MdeRuntimeOptions mdeRuntimeOptions() {
    return new MdeRuntimeOptions(
        mde.repositoryRoot(),
        mde.validationRoot(),
        mde.transformationRoot(),
        mde.generationRoot(),
        mde.metamodelRoot(),
        mde.executionTimeout(),
        integer(mde.maxCapturedOutputBytes()),
        integer(mde.maxConcurrentJobs()),
        integer(mde.queueCapacity()),
        mde.jobTimeout(),
        longValue(mde.maxModelUploadBytes()),
        integer(mde.maxGeneratedFiles()),
        longValue(mde.maxGeneratedFileBytes()),
        longValue(mde.maxGeneratedArtifactBytes()),
        mde.stagedImportTtl());
  }

  /**
   * Configures runtime paths, limits, concurrency, and timeouts for MDE operations.
   *
   * @param repositoryRoot root containing MDE repositories
   * @param validationRoot validation workspace root
   * @param transformationRoot transformation workspace root
   * @param generationRoot generated-artifact workspace root
   * @param metamodelRoot metamodel repository root
   * @param executionTimeout external process timeout
   * @param maxCapturedOutputBytes maximum captured process output size
   * @param maxConcurrentJobs maximum number of concurrently executing jobs
   * @param queueCapacity maximum queued job count
   * @param jobTimeout overall job timeout
   * @param maxModelUploadBytes maximum accepted model upload size
   * @param maxGeneratedFiles maximum files in a generated artifact
   * @param maxGeneratedFileBytes maximum size of one generated file
   * @param maxGeneratedArtifactBytes maximum total generated artifact size
   * @param stagedImportTtl lifetime of staged imports
   */
  public record Mde(
      Path repositoryRoot,
      Path validationRoot,
      Path transformationRoot,
      Path generationRoot,
      Path metamodelRoot,
      Duration executionTimeout,
      Integer maxCapturedOutputBytes,
      Integer maxConcurrentJobs,
      Integer queueCapacity,
      Duration jobTimeout,
      Long maxModelUploadBytes,
      Integer maxGeneratedFiles,
      Long maxGeneratedFileBytes,
      Long maxGeneratedArtifactBytes,
      Duration stagedImportTtl) {}

  /**
   * Configures generic user uploads.
   *
   * @param root upload storage root, relative to {@link #storageRoot()} when not absolute
   * @param maxFileBytes maximum accepted upload size
   * @param maxTextChars maximum text included in assistant prompts
   */
  public record Upload(Path root, Long maxFileBytes, Integer maxTextChars) {

    /** Applies safe defaults for upload limits. */
    public Upload {
      maxFileBytes = maxFileBytes == null ? 1_048_576L : maxFileBytes;
      maxTextChars = maxTextChars == null ? 120_000 : maxTextChars;
    }
  }
}
