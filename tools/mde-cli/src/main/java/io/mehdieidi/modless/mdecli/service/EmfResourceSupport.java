package io.mehdieidi.modless.mdecli.service;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.emfatic.core.EmfaticResourceFactory;

public final class EmfResourceSupport {

    public ResourceSet newResourceSet() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("ecore", new EcoreResourceFactoryImpl());
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("emf", new EmfaticResourceFactory());
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap()
                .put("emfatic", new EmfaticResourceFactory());

        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("ecore", new EcoreResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("emf", new EmfaticResourceFactory());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("emfatic", new EmfaticResourceFactory());
        resourceSet.getPackageRegistry().put(EcorePackage.eNS_URI, EcorePackage.eINSTANCE);
        return resourceSet;
    }

    public Resource newEcoreResource(ResourceSet resourceSet, URI uri) {
        return resourceSet.createResource(uri);
    }
}
