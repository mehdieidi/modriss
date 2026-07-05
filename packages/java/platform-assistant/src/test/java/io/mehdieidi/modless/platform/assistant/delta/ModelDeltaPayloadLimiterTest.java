package io.mehdieidi.modless.platform.assistant.delta;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelDeltaPayloadLimiterTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void limitsElementsAndDropsDependentMutations() {
    ModelDelta delta =
        new ModelDelta(
            AssistantTurnPlan.Intent.MUTATION,
            ModelDelta.Kind.MODEL_DELTA,
            "batch",
            List.of(),
            List.of(
                element("e1", "Actor"),
                element("e2", "Actor"),
                element("e3", "Actor")),
            List.of(new ModelDelta.Reference("e1", "interactsWith", "e3")),
            List.of(),
            List.of(),
            List.of());

    ModelDelta limited = ModelDeltaPayloadLimiter.limit(delta, 2);

    assertEquals(2, limited.elements().size());
    assertEquals(0, limited.references().size());
  }

  private ModelDelta.Element element(String id, String type) {
    return new ModelDelta.Element(
        id,
        type,
        mapper.createObjectNode().put("name", id),
        new ModelDelta.Placement("cim", "actors"),
        List.of(),
        List.of());
  }
}
