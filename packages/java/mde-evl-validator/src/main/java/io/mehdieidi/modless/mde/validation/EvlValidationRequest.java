package io.mehdieidi.modless.mde.validation;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Request object describing EVL modules and models to validate.
 *
 * @param evlRoot entry module file or directory used for module discovery
 * @param moduleFiles explicit module files relative to {@code evlRoot}, when supplied
 * @param models model configurations to load for each module
 * @param captureOutput whether standard, warning, and error streams are captured
 */
public record EvlValidationRequest(
    Path evlRoot,
    List<Path> moduleFiles,
    List<EvlModelConfiguration> models,
    boolean captureOutput) {

  /** Validates the EVL root and defensively copies collection fields. */
  public EvlValidationRequest {
    Objects.requireNonNull(evlRoot, "evlRoot");
    moduleFiles = moduleFiles == null ? List.of() : List.copyOf(moduleFiles);
    models = models == null ? List.of() : List.copyOf(models);
  }

  /**
   * Creates a request that discovers modules from the supplied root.
   *
   * @param evlRoot entry file or directory
   * @param models model configurations to validate
   * @param captureOutput whether output streams should be captured
   * @return validation request using root-based module discovery
   */
  public static EvlValidationRequest forRoot(
      Path evlRoot, List<EvlModelConfiguration> models, boolean captureOutput) {
    return new EvlValidationRequest(evlRoot, List.of(), models, captureOutput);
  }
}
