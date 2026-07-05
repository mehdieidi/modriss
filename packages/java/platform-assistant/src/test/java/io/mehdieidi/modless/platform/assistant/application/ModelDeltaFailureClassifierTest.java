package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.kernel.PlatformException;
import org.junit.jupiter.api.Test;

class ModelDeltaFailureClassifierTest {

  @Test
  void compileGroundingFailuresAreRepairableNotProviderFailures() {
    PlatformException failure =
        new PlatformException(
            422,
            "ModelDelta attribute update is not grounded in the metamodel: elementId=actor-1,"
                + " attributeName=title, elementType=Actor.");

    assertTrue(ModelDeltaFailureClassifier.isCompileFailure(failure));
    assertFalse(ModelDeltaFailureClassifier.isProviderFailure(failure));
  }

  @Test
  void schemaParsingFailuresAreProviderFailures() {
    PlatformException failure =
        new PlatformException(502, "AI assistant ModelDelta was rejected by schema parsing.");

    assertTrue(ModelDeltaFailureClassifier.isProviderFailure(failure));
    assertTrue(ModelDeltaFailureClassifier.isCompileFailure(failure));
  }
}
