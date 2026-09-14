package io.mehdieidi.modriss.platform.modeling.metamodel;

import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.modeling.runtime.MdeRuntimePaths;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;

/**
 * Loads Ecore metamodel descriptors from repository filesystem paths and caches them by model
 * level.
 */
public final class FileMetamodelResolver implements MetamodelResolver {

  /** Runtime paths used to locate metamodel files. */
  private final MdeRuntimePaths paths;

  /** Thread-safe descriptor cache by model level. */
  private final ConcurrentMap<ModelLevel, MetamodelDescriptor> cache = new ConcurrentHashMap<>();

  /**
   * Creates a file-backed metamodel resolver.
   *
   * @param paths runtime path resolver
   */
  public FileMetamodelResolver(MdeRuntimePaths paths) {
    this.paths = paths;
  }

  /**
   * Resolves and caches the descriptor for the requested level.
   *
   * @param level model level
   * @return loaded metamodel descriptor
   */
  @Override
  public MetamodelDescriptor resolve(ModelLevel level) {
    return cache.computeIfAbsent(level, this::loadDescriptor);
  }

  /**
   * Loads and hashes the metamodel descriptor for a model level.
   *
   * @param level model level
   * @return loaded descriptor
   */
  private MetamodelDescriptor loadDescriptor(ModelLevel level) {
    Path file = paths.metamodelFile(level).toAbsolutePath().normalize();
    if (!Files.isRegularFile(file)) {
      throw new PlatformException(500, "Metamodel file not found: " + file);
    }
    try {
      ResourceSet resourceSet = new ResourceSetImpl();
      resourceSet
          .getResourceFactoryRegistry()
          .getExtensionToFactoryMap()
          .put("ecore", new EcoreResourceFactoryImpl());
      resourceSet.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
      Resource resource = resourceSet.createResource(URI.createFileURI(file.toString()));
      try (InputStream input = Files.newInputStream(file)) {
        resource.load(input, Map.of());
      }
      EcoreUtil.resolveAll(resourceSet);
      List<EPackage> packages = new ArrayList<>();
      resource.getContents().stream()
          .filter(EPackage.class::isInstance)
          .map(EPackage.class::cast)
          .forEach(root -> collectPackages(root, packages));
      if (packages.isEmpty()) {
        throw new PlatformException(500, "Metamodel has no EPackage content: " + file);
      }
      return new MetamodelDescriptor(
          level, file.toUri(), file, List.copyOf(packages), version(packages), sha256(file));
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(
          500,
          "Could not load metamodel: "
              + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
    }
  }

  /**
   * Recursively appends an EPackage and its subpackages.
   *
   * @param ePackage package to collect
   * @param packages mutable package sink
   */
  private void collectPackages(EPackage ePackage, List<EPackage> packages) {
    packages.add(ePackage);
    ePackage.getESubpackages().forEach(child -> collectPackages(child, packages));
  }

  /**
   * Selects a version string from the first non-blank namespace URI.
   *
   * @param packages loaded packages
   * @return namespace/version string or {@code unknown}
   */
  private String version(List<EPackage> packages) {
    return packages.stream()
        .map(EPackage::getNsURI)
        .filter(nsUri -> nsUri != null && !nsUri.isBlank())
        .findFirst()
        .orElse("unknown");
  }

  /**
   * Computes a SHA-256 hash for a file.
   *
   * @param path file to hash
   * @return lowercase hexadecimal digest
   * @throws Exception when hashing or reading fails
   */
  private String sha256(Path path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
  }
}
