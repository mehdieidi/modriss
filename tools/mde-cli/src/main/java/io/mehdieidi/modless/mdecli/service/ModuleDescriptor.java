package io.mehdieidi.modless.mdecli.service;

import java.nio.file.Path;
import java.util.List;

public record ModuleDescriptor(Path sourceFile, List<String> importedEcoreFiles) {

}
