package io.mehdieidi.varka.platform.transformation.synchronization;

import java.util.Set;
import org.eclipse.emf.ecore.EStructuralFeature;

/** Central, extensible default ownership policy for serialized EMF features. */
public final class SemanticMergePolicy {

  private static final Set<String> GENERATOR_OWNED =
      Set.of(
          "generatedFrom",
          "generatedByTransformation",
          "traceId",
          "sourceElementId",
          "targetElementId");
  private static final Set<String> IGNORED =
      Set.of("incomingTraces", "outgoingTraces", "_sourceXmiBase64", "_sourceXmiToken");

  public MergeFeaturePolicy policyFor(String featureName) {
    if ("id".equals(featureName)) {
      return MergeFeaturePolicy.IMMUTABLE;
    }
    if (GENERATOR_OWNED.contains(featureName)) {
      return MergeFeaturePolicy.GENERATOR_OWNED;
    }
    if (IGNORED.contains(featureName)) {
      return MergeFeaturePolicy.IGNORE;
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
