package io.mehdieidi.modriss.mdecli.service;

import java.util.List;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;

/** Repairs compiler-produced Ecore details that require explicit serialization defaults. */
public final class EcoreModelNormalizer {

  /**
   * Normalizes all package roots recursively.
   *
   * @param roots Ecore roots to normalize in place
   */
  public void normalize(List<EObject> roots) {
    for (EObject root : roots) {
      if (root instanceof EPackage ePackage) {
        normalizePackage(ePackage);
      }
    }
  }

  /**
   * Normalizes classifiers and nested packages.
   *
   * @param ePackage package to normalize
   */
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

  /**
   * Assigns {@code EString} to attributes whose type was omitted by the compiler.
   *
   * @param eClass class to normalize
   */
  private void normalizeClass(EClass eClass) {
    eClass.getEStructuralFeatures().stream()
        .filter(EAttribute.class::isInstance)
        .map(EAttribute.class::cast)
        .filter(attribute -> attribute.getEType() == null)
        .forEach(attribute -> attribute.setEType(EcorePackage.eINSTANCE.getEString()));
  }
}
