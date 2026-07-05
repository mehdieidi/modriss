package io.mehdieidi.modless.platform.assistant.application;

import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.Locale;

/** Classifies ModelDelta failures for repair vs terminal provider rejection. */
final class ModelDeltaFailureClassifier {

  private ModelDeltaFailureClassifier() {}

  static boolean isProviderFailure(PlatformException failure) {
    if (failure == null) {
      return false;
    }
    if (failure.status() == 502) {
      return true;
    }
    String message = failure.getMessage();
    if (message == null) {
      return false;
    }
    String normalized = message.toLowerCase(Locale.ROOT);
    return normalized.contains("schema parsing") || normalized.contains("rejected by schema");
  }

  static boolean isCompileFailure(PlatformException failure) {
    if (failure == null) {
      return false;
    }
    if (isProviderFailure(failure)) {
      return true;
    }
    if (failure.status() != 422) {
      return false;
    }
    String message = failure.getMessage();
    if (message == null || message.isBlank()) {
      return false;
    }
    String normalized = message.toLowerCase(Locale.ROOT);
    return normalized.contains("modeldelta")
        || normalized.contains("not grounded in the metamodel")
        || normalized.contains("requires localid")
        || normalized.contains("containment")
        || normalized.contains("unknown or cyclic owner")
        || normalized.contains("unknown metamodel element type")
        || normalized.contains("unknown metamodel type");
  }
}
