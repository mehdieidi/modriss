package io.mehdieidi.modless.mde.etl;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Describes how an EMF model is exposed to an ETL module.
 *
 * @param name primary Epsilon model name
 * @param aliases alternate names visible to the ETL module
 * @param modelFile file-backed model location
 * @param metamodelFiles Ecore metamodel files used to type the model
 * @param readOnLoad whether the existing model is read before execution
 * @param storeOnDisposal whether Epsilon should store the model during disposal
 * @param readOnly whether ETL may mutate the model
 * @param validate whether EMF model validation is enabled while loading
 */
public record EtlModelConfiguration(
    String name,
    List<String> aliases,
    Path modelFile,
    List<Path> metamodelFiles,
    boolean readOnLoad,
    boolean storeOnDisposal,
    boolean readOnly,
    boolean validate) {

  /** Validates required paths and normalizes collection fields. */
  public EtlModelConfiguration {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Model name is required.");
    }
    Objects.requireNonNull(modelFile, "modelFile");
    aliases = aliases == null ? List.of() : List.copyOf(aliases);
    metamodelFiles = metamodelFiles == null ? List.of() : List.copyOf(metamodelFiles);
  }

  /**
   * Creates a read-only source model configuration.
   *
   * @param name primary Epsilon model name
   * @param aliases alternate model aliases
   * @param modelFile source model file
   * @param metamodelFiles metamodel files for the source model
   * @return source model configuration
   */
  public static EtlModelConfiguration source(
      String name, List<String> aliases, Path modelFile, List<Path> metamodelFiles) {
    return new EtlModelConfiguration(
        name, aliases, modelFile, metamodelFiles, true, false, true, false);
  }

  /**
   * Creates a writable target model configuration.
   *
   * @param name primary Epsilon model name
   * @param aliases alternate model aliases
   * @param modelFile target model file
   * @param metamodelFiles metamodel files for the target model
   * @param readOnLoad whether to seed the target from an existing file
   * @return target model configuration
   */
  public static EtlModelConfiguration target(
      String name,
      List<String> aliases,
      Path modelFile,
      List<Path> metamodelFiles,
      boolean readOnLoad) {
    return new EtlModelConfiguration(
        name, aliases, modelFile, metamodelFiles, readOnLoad, false, false, false);
  }
}
