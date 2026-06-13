package io.mehdieidi.modless.platform.core.service;

import io.mehdieidi.modless.platform.core.model.ModelLevel;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.ecore.EPackage;

/**
 * Loaded metamodel metadata used by import/export and validation services.
 *
 * @param level model level described by the metamodel
 * @param uri metamodel file URI
 * @param file metamodel file path
 * @param packages root and nested EPackages loaded from the file
 * @param version namespace/version string derived from the loaded packages
 * @param sha256 SHA-256 hash of the metamodel file
 */
public record MetamodelDescriptor(
    ModelLevel level, URI uri, Path file, List<EPackage> packages, String version, String sha256) {}
