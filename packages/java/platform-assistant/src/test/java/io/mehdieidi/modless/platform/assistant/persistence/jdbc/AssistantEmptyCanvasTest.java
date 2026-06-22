package io.mehdieidi.modless.platform.assistant.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.model.application.ModelService;
import io.mehdieidi.modless.platform.modeling.config.ModelingConfigService;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantEmptyCanvasTest {

  @Test
  void starterModelIsEmptyCanvas() {
    var baseModel = new ModelingConfigService().starterModel(ModelLevel.PIM, "Orders");
    var contexts = new JdbcAssistantModelContextIndex();
    var context =
        contexts.transientSnapshot(
            "project",
            ModelLevel.PIM,
            "Orders",
            0L,
            baseModel,
            new ModelService.ValidationResult(true, List.of()));
    assertTrue(
        contexts.isEmptyCanvas(context),
        () ->
            "Expected empty canvas but found elements: "
                + context.elements().stream()
                    .map(element -> element.type() + ":" + element.id())
                    .toList());
  }
}
