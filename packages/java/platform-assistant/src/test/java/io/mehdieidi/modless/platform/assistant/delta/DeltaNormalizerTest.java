package io.mehdieidi.modless.platform.assistant.delta;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeltaNormalizerTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final DeltaNormalizer normalizer =
      new DeltaNormalizer(new AssistantMetamodelSchemaService());

  @Test
  void canonicalizesTypeEnumAndRootPlacementWithoutDomainSynthesis() {
    ModelDelta delta =
        new ModelDelta(
            AssistantTurnPlan.Intent.MUTATION,
            ModelDelta.Kind.MODEL_DELTA,
            "Add actor",
            List.of(),
            List.of(
                new ModelDelta.Element(
                    "new actor!",
                    "actor",
                    mapper.createObjectNode().put("name", "Customer").put("id", "bad"),
                    null,
                    List.of(),
                    List.of("chunk-1-note"))),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    ModelDelta normalized = normalizer.normalize(ModelLevel.CIM, delta);

    ModelDelta.Element element = normalized.elements().get(0);
    assertEquals("Actor", element.eClass());
    assertEquals("new_actor_", element.localId());
    assertEquals("root", element.placement().ownerId());
    assertEquals("actors", element.placement().referenceName());
    assertEquals("", element.attributes().path("id").asText(""));
    assertEquals("Customer", element.attributes().path("name").asText());
  }
}
