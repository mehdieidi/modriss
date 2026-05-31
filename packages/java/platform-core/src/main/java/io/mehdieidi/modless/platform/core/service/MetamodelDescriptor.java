package io.mehdieidi.modless.platform.core.service;

import io.mehdieidi.modless.platform.core.model.ModelLevel;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.ecore.EPackage;

public record MetamodelDescriptor(
        ModelLevel level,
        URI uri,
        Path file,
        List<EPackage> packages,
        String version,
        String sha256) {

}
