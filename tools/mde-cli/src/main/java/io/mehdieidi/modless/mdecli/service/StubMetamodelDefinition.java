package io.mehdieidi.modless.mdecli.service;

import java.nio.file.Path;
import java.util.List;

/**
 * Minimal metamodel metadata used to generate a bootstrap Ecore resource.
 *
 * @param sourceFile      source Emfatic file
 * @param packageName     Ecore package name
 * @param namespaceUri    package namespace URI
 * @param namespacePrefix package namespace prefix
 * @param classifiers     declared classifiers
 */
public record StubMetamodelDefinition(
        Path sourceFile,
        String packageName,
        String namespaceUri,
        String namespacePrefix,
        List<StubClassifierDefinition> classifiers) {

}
