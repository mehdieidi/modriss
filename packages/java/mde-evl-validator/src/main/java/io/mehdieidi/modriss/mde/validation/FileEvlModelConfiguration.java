package io.mehdieidi.modriss.mde.validation;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.eclipse.emf.common.util.URI;
import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.emc.emf.EmfModel;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.models.IModel;
import org.eclipse.epsilon.eol.models.Model;

/**
 * EVL model configuration for a read-only file-backed EMF model.
 *
 * @param name primary Epsilon model name
 * @param aliases alternate names visible to EVL modules
 * @param modelFile XMI model file to load
 * @param metamodelFiles Ecore files used to type the model
 * @param validate whether to run EMF structural validation after loading
 */
public record FileEvlModelConfiguration(
    String name, List<String> aliases, Path modelFile, List<Path> metamodelFiles, boolean validate)
    implements EvlModelConfiguration {

  /** Validates required fields and defensively copies collection fields. */
  public FileEvlModelConfiguration {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(modelFile, "modelFile");
    aliases = aliases == null ? List.of() : List.copyOf(aliases);
    metamodelFiles = metamodelFiles == null ? List.of() : List.copyOf(metamodelFiles);
  }

  /**
   * Creates a read-only file-backed model configuration with structural validation enabled.
   *
   * @param name primary Epsilon model name
   * @param aliases alternate model aliases
   * @param modelFile model file to load
   * @param metamodelFiles metamodel files for the model
   * @return file-backed EVL model configuration
   */
  public static FileEvlModelConfiguration readOnly(
      String name, List<String> aliases, Path modelFile, List<Path> metamodelFiles) {
    return new FileEvlModelConfiguration(name, aliases, modelFile, metamodelFiles, true);
  }

  /**
   * Loads the configured EMF model into Epsilon.
   *
   * @return loaded Epsilon model
   * @throws EolModelLoadingException when Epsilon cannot load the model
   */
  @Override
  public IModel load() throws EolModelLoadingException {
    EmfModel model = new EmfModel();
    StringProperties properties = new StringProperties();
    properties.put(Model.PROPERTY_NAME, name);
    if (!aliases.isEmpty()) {
      properties.put(Model.PROPERTY_ALIASES, String.join(",", aliases));
    }
    properties.put(Model.PROPERTY_READONLOAD, "true");
    properties.put(Model.PROPERTY_STOREONDISPOSAL, "false");
    properties.put(Model.PROPERTY_READONLY, "true");
    properties.put(EmfModel.PROPERTY_MODEL_URI, fileUri(modelFile));
    properties.put(EmfModel.PROPERTY_FILE_BASED_METAMODEL_URI, joinFileUris(metamodelFiles));
    properties.put(
        EmfModel.PROPERTY_REUSE_UNMODIFIED_FILE_BASED_METAMODELS, Boolean.FALSE.toString());
    properties.put(EmfModel.PROPERTY_VALIDATE, "false");
    model.load(properties);
    return model;
  }

  /**
   * Runs structural validation against the loaded EMF resource when enabled.
   *
   * @param model loaded Epsilon model
   * @return structural diagnostics, or an empty list when disabled
   */
  @Override
  public List<EvlDiagnostic> validateLoadedModel(IModel model) {
    if (!validate || !(model instanceof EmfModel emfModel)) {
      return List.of();
    }
    return EvlModelResourceDiagnostics.validate(emfModel.getResource(), modelFile);
  }

  /**
   * Joins metamodel paths as comma-separated file URIs for Epsilon.
   *
   * @param paths metamodel paths
   * @return comma-separated file URI list
   */
  private String joinFileUris(List<Path> paths) {
    return paths.stream().map(this::fileUri).reduce((left, right) -> left + "," + right).orElse("");
  }

  /**
   * Converts a filesystem path to an EMF file URI.
   *
   * @param path filesystem path
   * @return normalized file URI string
   */
  private String fileUri(Path path) {
    return URI.createFileURI(path.toAbsolutePath().normalize().toString()).toString();
  }
}
