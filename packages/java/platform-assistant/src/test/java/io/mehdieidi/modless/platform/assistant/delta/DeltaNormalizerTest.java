package io.mehdieidi.modless.platform.assistant.delta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @Test
  void promotesMisplacedServiceFunctionOwnershipToRootContainmentPlusReference() {
    ModelDelta delta =
        new ModelDelta(
            AssistantTurnPlan.Intent.MUTATION,
            ModelDelta.Kind.MODEL_DELTA,
            "Add vending function",
            List.of(),
            List.of(
                new ModelDelta.Element(
                    "svc",
                    "ServerlessService",
                    mapper.createObjectNode().put("name", "Vending"),
                    new ModelDelta.Placement("root", "services"),
                    List.of(),
                    List.of()),
                new ModelDelta.Element(
                    "fn",
                    "Function",
                    mapper.createObjectNode().put("name", "Dispense"),
                    new ModelDelta.Placement("svc", "ownsFunctions"),
                    List.of(),
                    List.of())),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    ModelDelta normalized = normalizer.normalize(ModelLevel.PIM, delta);

    ModelDelta.Element function =
        normalized.elements().stream()
            .filter(element -> "Function".equals(element.eClass()))
            .findFirst()
            .orElseThrow();
    assertEquals("root", function.placement().ownerId());
    assertEquals("functions", function.placement().referenceName());
    assertTrue(
        normalized.references().stream()
            .anyMatch(
                reference ->
                    "svc".equals(reference.sourceId())
                        && "ownsFunctions".equals(reference.referenceName())
                        && "fn".equals(reference.targetId())));
  }

  @Test
  void canonicalizesFunctionPublishesEventsReferenceAndDropsInvalidPolicyLinks() throws Exception {
    ModelDelta delta =
        new ModelDelta(
            io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan.Intent.MUTATION,
            ModelDelta.Kind.MODEL_DELTA,
            "test",
            List.of(),
            List.of(
                new ModelDelta.Element(
                    "fn",
                    "Function",
                    mapper.createObjectNode().put("name", "Dispense"),
                    new ModelDelta.Placement("root", "functions"),
                    List.of(new ModelDelta.Reference("fn", "publishesEvents", "evt")),
                    List.of()),
                new ModelDelta.Element(
                    "evt",
                    "EventType",
                    mapper.createObjectNode().put("name", "Product dispensed"),
                    new ModelDelta.Placement("root", "eventTypes"),
                    List.of(),
                    List.of()),
                new ModelDelta.Element(
                    "rule",
                    "BusinessRule",
                    mapper.createObjectNode().put("name", "Max qty"),
                    new ModelDelta.Placement("root", "businessRules"),
                    List.of(),
                    List.of()),
                new ModelDelta.Element(
                    "svc",
                    "ServerlessService",
                    mapper.createObjectNode().put("name", "Vending"),
                    new ModelDelta.Placement("root", "services"),
                    List.of(),
                    List.of())),
            List.of(new ModelDelta.Reference("svc", "constrainedBy", "rule")),
            List.of(),
            List.of(),
            List.of());

    ModelDelta normalized = normalizer.normalize(ModelLevel.PIM, delta);

    assertTrue(
        normalized.elements().stream()
            .filter(element -> "fn".equals(element.localId()))
            .flatMap(element -> element.references().stream())
            .anyMatch(
                reference ->
                    "publishes".equals(reference.referenceName())
                        && "evt".equals(reference.targetId())));
    assertTrue(
        normalized.references().stream()
            .noneMatch(reference -> "constrainedBy".equals(reference.referenceName())));
  }
}
