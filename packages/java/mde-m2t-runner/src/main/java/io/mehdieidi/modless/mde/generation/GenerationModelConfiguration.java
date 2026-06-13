package io.mehdieidi.modless.mde.generation;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Describes how an EMF source model is exposed to an EGX module.
 *
 * @param name primary Epsilon model name
 * @param aliases alternate names visible to EGX/EGL modules
 * @param modelFile source model file
 * @param metamodelFiles Ecore metamodel files used to type the model
 * @param validate whether EMF validation is enabled while loading
 */
public record GenerationModelConfiguration(
    String name,
    List<String> aliases,
    Path modelFile,
    List<Path> metamodelFiles,
    boolean validate) {

  /** Validates required paths and normalizes collection fields. */
  public GenerationModelConfiguration {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Model name is required.");
    }
    Objects.requireNonNull(modelFile, "modelFile");
    aliases = aliases == null ? List.of() : List.copyOf(aliases);
    metamodelFiles = metamodelFiles == null ? List.of() : List.copyOf(metamodelFiles);
  }
}
