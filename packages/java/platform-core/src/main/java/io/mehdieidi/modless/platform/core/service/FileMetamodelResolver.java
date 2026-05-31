package io.mehdieidi.modless.platform.core.service;

import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
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

public final class FileMetamodelResolver implements MetamodelResolver {

    private final MdeRuntimePaths paths;
    private final ConcurrentMap<ModelLevel, MetamodelDescriptor> cache = new ConcurrentHashMap<>();

    public FileMetamodelResolver(MdeRuntimePaths paths) {
        this.paths = paths;
    }

    @Override
    public MetamodelDescriptor resolve(ModelLevel level) {
        return cache.computeIfAbsent(level, this::loadDescriptor);
    }

    private MetamodelDescriptor loadDescriptor(ModelLevel level) {
        Path file = paths.metamodelFile(level).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            throw new PlatformException(500, "Metamodel file not found: " + file);
        }
        try {
            ResourceSet resourceSet = new ResourceSetImpl();
            resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
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
            return new MetamodelDescriptor(level, file.toUri(), file, List.copyOf(packages),
                    version(packages), sha256(file));
        } catch (PlatformException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not load metamodel: "
                    + (ex.getMessage() == null ? ex.getClass().getSimpleName()
                    : ex.getMessage()));
        }
    }

    private void collectPackages(EPackage ePackage, List<EPackage> packages) {
        packages.add(ePackage);
        ePackage.getESubpackages().forEach(child -> collectPackages(child, packages));
    }

    private String version(List<EPackage> packages) {
        return packages.stream()
                .map(EPackage::getNsURI)
                .filter(nsUri -> nsUri != null && !nsUri.isBlank())
                .findFirst()
                .orElse("unknown");
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
    }
}
