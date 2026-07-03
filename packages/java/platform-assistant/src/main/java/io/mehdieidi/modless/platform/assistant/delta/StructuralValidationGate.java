package io.mehdieidi.modless.platform.assistant.delta;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.model.application.ModelService;

/** Final structural Ecore/EMF validation gate for assistant-generated model previews. */
public class StructuralValidationGate {

  private final ModelService models;

  public StructuralValidationGate(ModelService models) {
    this.models = models;
  }

  /** Runs structural validation only; EVL is intentionally outside the assistant apply gate. */
  public ModelService.ValidationResult validate(ModelLevel level, JsonNode modelJson) {
    return models.validateStructural(level, modelJson);
  }
}
