package io.mehdieidi.modriss.mde.generation;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Request object describing a single EGX generation run.
 *
 * @param moduleFile EGX entry module to parse and execute
 * @param templateRoot root directory for EGL templates
 * @param outputDirectory directory that receives generated files
 * @param models source model configurations
 * @param failIfOutputDirectoryIsNotEmpty whether a non-empty output directory should fail
 * @param captureOutput whether standard, warning, and error streams are captured
 */
public record EgxGenerationRequest(
    Path moduleFile,
    Path templateRoot,
    Path outputDirectory,
    List<GenerationModelConfiguration> models,
    boolean failIfOutputDirectoryIsNotEmpty,
    boolean captureOutput) {

  /** Applies the default template root and defensively copies model configurations. */
  public EgxGenerationRequest {
    Objects.requireNonNull(moduleFile, "moduleFile");
    templateRoot =
        templateRoot == null
            ? moduleFile.toAbsolutePath().getParent().resolve("templates")
            : templateRoot;
    Objects.requireNonNull(outputDirectory, "outputDirectory");
    models = models == null ? List.of() : List.copyOf(models);
  }
}
