package io.mehdieidi.modless.mde.validation;

import java.util.List;
import java.util.Objects;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.epsilon.emc.emf.InMemoryEmfModel;
import org.eclipse.epsilon.eol.models.IModel;

public record ResourceEvlModelConfiguration(
        String name,
        List<String> aliases,
        Resource resource,
        List<EPackage> metamodelPackages,
        boolean expand,
        boolean cachingEnabled,
        boolean validate) implements EvlModelConfiguration {

    public ResourceEvlModelConfiguration {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(resource, "resource");
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        metamodelPackages = metamodelPackages == null ? List.of() : List.copyOf(metamodelPackages);
    }

    public ResourceEvlModelConfiguration(
            String name,
            Resource resource,
            List<EPackage> metamodelPackages,
            boolean expand,
            boolean cachingEnabled) {
        this(name, List.of(), resource, metamodelPackages, expand, cachingEnabled, true);
    }

    public static ResourceEvlModelConfiguration readOnly(
            String name, Resource resource, List<EPackage> metamodelPackages) {
        return readOnly(name, List.of(), resource, metamodelPackages);
    }

    public static ResourceEvlModelConfiguration readOnly(
            String name, List<String> aliases, Resource resource,
            List<EPackage> metamodelPackages) {
        return new ResourceEvlModelConfiguration(name, aliases, resource, metamodelPackages, true,
                true, true);
    }

    @Override
    public IModel load() {
        InMemoryEmfModel model = new InMemoryEmfModel(name, resource, metamodelPackages, expand,
                cachingEnabled);
        model.getAliases().addAll(aliases);
        return model;
    }

    @Override
    public List<EvlDiagnostic> validateLoadedModel(IModel model) {
        if (!validate) {
            return List.of();
        }
        return EvlModelResourceDiagnostics.validate(resource, null);
    }
}
