package io.mehdieidi.modless.mde.validation;

import java.util.List;
import java.util.Objects;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.epsilon.emc.emf.InMemoryEmfModel;
import org.eclipse.epsilon.eol.models.IModel;

/**
 * EVL model configuration for an in-memory EMF resource.
 *
 * @param name primary Epsilon model name
 * @param aliases alternate names visible to EVL modules
 * @param resource EMF resource to expose
 * @param metamodelPackages packages used to type the model
 * @param expand whether the Epsilon model should expand all contents
 * @param cachingEnabled whether Epsilon model caching is enabled
 * @param validate whether to run EMF structural validation before EVL execution
 */
public record ResourceEvlModelConfiguration(
    String name,
    List<String> aliases,
    Resource resource,
    List<EPackage> metamodelPackages,
    boolean expand,
    boolean cachingEnabled,
    boolean validate)
    implements EvlModelConfiguration {

  /** Validates required fields and defensively copies collection fields. */
  public ResourceEvlModelConfiguration {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(resource, "resource");
    aliases = aliases == null ? List.of() : List.copyOf(aliases);
    metamodelPackages = metamodelPackages == null ? List.of() : List.copyOf(metamodelPackages);
  }

  /**
   * Creates an in-memory model configuration without aliases and with validation enabled.
   *
   * @param name primary Epsilon model name
   * @param resource EMF resource to expose
   * @param metamodelPackages packages used to type the model
   * @param expand whether the Epsilon model should expand all contents
   * @param cachingEnabled whether Epsilon model caching is enabled
   */
  public ResourceEvlModelConfiguration(
      String name,
      Resource resource,
      List<EPackage> metamodelPackages,
      boolean expand,
      boolean cachingEnabled) {
    this(name, List.of(), resource, metamodelPackages, expand, cachingEnabled, true);
  }

  /**
   * Creates a read-only in-memory model configuration without aliases.
   *
   * @param name primary Epsilon model name
   * @param resource EMF resource to expose
   * @param metamodelPackages packages used to type the model
   * @return read-only in-memory model configuration
   */
  public static ResourceEvlModelConfiguration readOnly(
      String name, Resource resource, List<EPackage> metamodelPackages) {
    return readOnly(name, List.of(), resource, metamodelPackages);
  }

  /**
   * Creates a read-only in-memory model configuration with aliases.
   *
   * @param name primary Epsilon model name
   * @param aliases alternate model aliases
   * @param resource EMF resource to expose
   * @param metamodelPackages packages used to type the model
   * @return read-only in-memory model configuration
   */
  public static ResourceEvlModelConfiguration readOnly(
      String name, List<String> aliases, Resource resource, List<EPackage> metamodelPackages) {
    return new ResourceEvlModelConfiguration(
        name, aliases, resource, metamodelPackages, true, true, true);
  }

  /**
   * Loads the in-memory EMF resource into Epsilon.
   *
   * @return loaded Epsilon model
   */
  @Override
  public IModel load() {
    InMemoryEmfModel model =
        new InMemoryEmfModel(name, resource, metamodelPackages, expand, cachingEnabled);
    model.getAliases().addAll(aliases);
    return model;
  }

  /**
   * Runs structural validation against the in-memory resource when enabled.
   *
   * @param model loaded Epsilon model
   * @return structural diagnostics, or an empty list when disabled
   */
  @Override
  public List<EvlDiagnostic> validateLoadedModel(IModel model) {
    if (!validate) {
      return List.of();
    }
    return EvlModelResourceDiagnostics.validate(resource, null);
  }
}
