package io.mehdieidi.modless.mde.validation;

import java.util.List;
import java.util.Objects;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.epsilon.emc.emf.InMemoryEmfModel;
import org.eclipse.epsilon.eol.models.IModel;

public record ResourceEvlModelConfiguration(
        String name,
        Resource resource,
        List<EPackage> metamodelPackages,
        boolean expand,
        boolean cachingEnabled) implements EvlModelConfiguration {

    public ResourceEvlModelConfiguration {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(resource, "resource");
        metamodelPackages = metamodelPackages == null ? List.of() : List.copyOf(metamodelPackages);
    }

    public static ResourceEvlModelConfiguration readOnly(
            String name, Resource resource, List<EPackage> metamodelPackages) {
        return new ResourceEvlModelConfiguration(name, resource, metamodelPackages, true, true);
    }

    @Override
    public IModel load() {
        return new InMemoryEmfModel(name, resource, metamodelPackages, expand, cachingEnabled);
    }
}
