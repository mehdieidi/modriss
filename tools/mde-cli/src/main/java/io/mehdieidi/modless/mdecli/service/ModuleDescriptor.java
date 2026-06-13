package io.mehdieidi.modless.mdecli.service;

import java.nio.file.Path;
import java.util.List;

/**
 * Describes an Emfatic module and its declared Ecore imports.
 *
 * @param sourceFile source Emfatic file
 * @param importedEcoreFiles imported Ecore paths as declared in the source
 */
public record ModuleDescriptor(Path sourceFile, List<String> importedEcoreFiles) {}
