package io.mehdieidi.modriss.mdecli.service;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;

/** Combines and deep-copies Ecore resource roots while preserving cross-references. */
public final class EcoreCombiner {

  /**
   * Copies all root objects from the supplied resources into one list.
   *
   * @param resources compiled Ecore resources
   * @return independent copied roots
   */
  public List<EObject> combine(List<Resource> resources) {
    List<EObject> roots = new ArrayList<>();
    for (Resource resource : resources) {
      roots.addAll(resource.getContents());
    }
    return copy(roots);
  }

  /**
   * Deep-copies roots and rewrites references to copied targets.
   *
   * @param roots source roots
   * @return independent copied roots
   */
  public List<EObject> copy(List<EObject> roots) {
    EcoreUtil.Copier copier = new EcoreUtil.Copier(true, true);
    List<EObject> copies = new ArrayList<>(copier.copyAll(roots));
    copier.copyReferences();
    return copies;
  }
}
