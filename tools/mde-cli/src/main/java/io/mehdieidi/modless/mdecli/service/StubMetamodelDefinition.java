package io.mehdieidi.modless.mdecli.service;

import java.nio.file.Path;
import java.util.List;

public record StubMetamodelDefinition(
        Path sourceFile,
        String packageName,
        String namespaceUri,
        String namespacePrefix,
        List<StubClassifierDefinition> classifiers) {

}
