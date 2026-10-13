package io.mehdieidi.modriss.platform.transformation.synchronization;

import java.util.List;
import tools.jackson.databind.JsonNode;

/** Serializable, transport-safe description of a three-way model conflict. */
public record ModelConflict(
    String conflictId,
    TransformationDirection transformationDirection,
    String elementId,
    String elementEClass,
    String elementName,
    String featureName,
    String differenceKind,
    String jsonPointer,
    JsonNode baseValue,
    JsonNode workingValue,
    JsonNode generatedValue,
    String description,
    List<ConflictResolution> availableResolutions) {

  public ModelConflict {
    availableResolutions = List.copyOf(availableResolutions);
  }
}
