package io.mehdieidi.modriss.mde.etl;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Request object describing a single ETL module execution.
 *
 * @param moduleFile ETL entry module to parse and execute
 * @param workingDirectory directory used by the ETL runtime
 * @param models source and target model configurations
 * @param overwriteOutputs whether existing output files may be replaced
 * @param captureOutput whether standard, warning, and error streams are captured
 */
public record EtlExecutionRequest(
    Path moduleFile,
    Path workingDirectory,
    List<EtlModelConfiguration> models,
    boolean overwriteOutputs,
    boolean captureOutput) {

  /** Applies the default working directory and defensively copies model configurations. */
  public EtlExecutionRequest {
    Objects.requireNonNull(moduleFile, "moduleFile");
    workingDirectory =
        workingDirectory == null ? moduleFile.toAbsolutePath().getParent() : workingDirectory;
    models = models == null ? List.of() : List.copyOf(models);
  }
}
