package io.mehdieidi.modriss.platform.transformation.synchronization;

import org.eclipse.emf.ecore.EStructuralFeature;

/** Central, extensible default ownership policy for serialized EMF features. */
public final class SemanticMergePolicy {

  public MergeFeaturePolicy policyFor(String featureName) {
    if ("id".equals(featureName)) {
      return MergeFeaturePolicy.IMMUTABLE;
    }
    return MergeFeaturePolicy.THREE_WAY;
  }

  /** Returns the default policy for a concrete Ecore feature. */
  public MergeFeaturePolicy policyFor(EStructuralFeature feature) {
    if (feature == null) {
      return MergeFeaturePolicy.THREE_WAY;
    }
    if (feature.isDerived() || feature.isTransient() || feature.isVolatile()) {
      return MergeFeaturePolicy.IGNORE;
    }
    return policyFor(feature.getName());
  }
}
