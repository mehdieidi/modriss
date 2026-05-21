package io.mehdieidi.modless.mdecli.service;

import java.util.List;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;

public final class EcoreModelNormalizer {

  public void normalize(List<EObject> roots) {
    for (EObject root : roots) {
      if (root instanceof EPackage ePackage) {
        normalizePackage(ePackage);
      }
    }
  }

  private void normalizePackage(EPackage ePackage) {
    for (EClassifier classifier : ePackage.getEClassifiers()) {
      if (classifier instanceof EClass eClass) {
        normalizeClass(eClass);
      }
    }

    for (EPackage subpackage : ePackage.getESubpackages()) {
      normalizePackage(subpackage);
    }
  }

  private void normalizeClass(EClass eClass) {
    eClass.getEStructuralFeatures().stream()
        .filter(EAttribute.class::isInstance)
        .map(EAttribute.class::cast)
        .filter(attribute -> attribute.getEType() == null)
        .forEach(attribute -> attribute.setEType(EcorePackage.eINSTANCE.getEString()));
  }
}
