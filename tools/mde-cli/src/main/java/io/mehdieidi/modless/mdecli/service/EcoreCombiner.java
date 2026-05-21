package io.mehdieidi.modless.mdecli.service;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;

public final class EcoreCombiner {

    public List<EObject> combine(List<Resource> resources) {
        List<EObject> roots = new ArrayList<>();
        for (Resource resource : resources) {
            roots.addAll(resource.getContents());
        }
        return copy(roots);
    }

    public List<EObject> copy(List<EObject> roots) {
        EcoreUtil.Copier copier = new EcoreUtil.Copier(true, true);
        List<EObject> copies = new ArrayList<>(copier.copyAll(roots));
        copier.copyReferences();
        return copies;
    }
}
