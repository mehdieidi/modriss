package io.mehdieidi.modless.platform.assistant.delta;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeltaCompilerTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final DeltaCompiler compiler = new DeltaCompiler(new AssistantMetamodelSchemaService());

  @Test
  void compilesModelDeltaToSemanticPatchIr() throws Exception {
    ModelDelta delta =
        new ModelDelta(
            AssistantTurnPlan.Intent.MUTATION,
            ModelDelta.Kind.MODEL_DELTA,
            "Create API slice",
            List.of(),
            List.of(
                new ModelDelta.Element(
                    "fn",
                    "Function",
                    JsonNodeFactory.instance.objectNode().put("name", "Book order"),
                    new ModelDelta.Placement("root", "functions"),
                    List.of(),
                    List.of())),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    SemanticModelPatch patch =
        compiler.compile(
            ModelLevel.PIM,
            mapper.readTree(
                """
                {"id":"root","eClass":"PIMModel","modelLevel":"PIM","functions":[]}
                """),
            Map.of(),
            delta);

    assertEquals(1, patch.operations().size());
    assertEquals(SemanticModelPatch.OperationType.ADD_ELEMENT, patch.operations().get(0).type());
    assertEquals("Function", patch.operations().get(0).elementType());
  }

  @Test
  void canonicalizesSingleRootContainmentWhenOnlyOneLegalFeatureExists() throws Exception {
    ModelDelta delta =
        new ModelDelta(
            AssistantTurnPlan.Intent.MUTATION,
            ModelDelta.Kind.MODEL_DELTA,
            "Create invalid API slice",
            List.of(),
            List.of(
                new ModelDelta.Element(
                    "fn",
                    "Function",
                    JsonNodeFactory.instance.objectNode().put("name", "Book order"),
                    new ModelDelta.Placement("root", "notAContainment"),
                    List.of(),
                    List.of())),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    SemanticModelPatch patch =
        compiler.compile(
            ModelLevel.PIM,
            mapper.readTree(
                """
                {"id":"root","eClass":"PIMModel","modelLevel":"PIM","functions":[]}
                """),
            Map.of(),
            delta);

    assertEquals("functions", patch.operations().get(0).referenceName());
  }

  @Test
  void canonicalizesCommonEventStormingReferenceNames() throws Exception {
    ModelDelta delta =
        new ModelDelta(
            AssistantTurnPlan.Intent.MUTATION,
            ModelDelta.Kind.MODEL_DELTA,
            "Connect command to event",
            List.of(),
            List.of(
                new ModelDelta.Element(
                    "cmd",
                    "Command",
                    JsonNodeFactory.instance.objectNode().put("name", "Register claim"),
                    new ModelDelta.Placement("root", "commands"),
                    List.of(),
                    List.of()),
                new ModelDelta.Element(
                    "evt",
                    "BusinessEvent",
                    JsonNodeFactory.instance.objectNode().put("name", "Claim registered"),
                    new ModelDelta.Placement("root", "events"),
                    List.of(),
                    List.of())),
            List.of(new ModelDelta.Reference("cmd", "emits", "evt")),
            List.of(),
            List.of(),
            List.of());

    SemanticModelPatch patch =
        compiler.compile(
            ModelLevel.CIM,
            mapper.readTree(
                """
                {"id":"root","eClass":"CIMModel","modelLevel":"CIM","commands":[],"events":[]}
                """),
            Map.of(),
            delta);

    SemanticModelPatch.Operation connection =
        patch.operations().stream()
            .filter(
                operation -> operation.type() == SemanticModelPatch.OperationType.CONNECT_ELEMENTS)
            .findFirst()
            .orElseThrow();

    assertEquals("expectedEvents", connection.referenceName());
  }

  @Test
  void canonicalizesCaseInsensitiveAttributeUpdates() throws Exception {
    ModelDelta delta =
        new ModelDelta(
            AssistantTurnPlan.Intent.MUTATION,
            ModelDelta.Kind.MODEL_DELTA,
            "Rename actor",
            List.of(),
            List.of(
                new ModelDelta.Element(
                    "patient",
                    "Actor",
                    JsonNodeFactory.instance.objectNode().put("name", "Patient"),
                    new ModelDelta.Placement("root", "actors"),
                    List.of(),
                    List.of())),
            List.of(),
            List.of(
                new ModelDelta.AttributeUpdate(
                    "patient", "Summary", JsonNodeFactory.instance.textNode("Books visits"))),
            List.of(),
            List.of());

    SemanticModelPatch patch =
        compiler.compile(
            ModelLevel.CIM,
            mapper.readTree(
                """
                {"id":"root","eClass":"CIMModel","modelLevel":"CIM","actors":[]}
                """),
            Map.of(),
            delta);

    SemanticModelPatch.Operation update =
        patch.operations().stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.SET_ATTRIBUTE)
            .findFirst()
            .orElseThrow();
    assertEquals("summary", update.referenceName());
    assertEquals("Books visits", update.attributes().asText());
  }

  @Test
  void canonicalizesAggregateEntityAliasToMembers() throws Exception {
    ModelDelta delta =
        new ModelDelta(
            AssistantTurnPlan.Intent.MUTATION,
            ModelDelta.Kind.MODEL_DELTA,
            "Link aggregate to entity",
            List.of(),
            List.of(
                new ModelDelta.Element(
                    "agg1",
                    "AggregateCandidate",
                    JsonNodeFactory.instance.objectNode().put("name", "Booking aggregate"),
                    new ModelDelta.Placement("root", "aggregates"),
                    List.of(),
                    List.of()),
                new ModelDelta.Element(
                    "ent1",
                    "DomainEntity",
                    JsonNodeFactory.instance.objectNode().put("name", "Appointment"),
                    new ModelDelta.Placement("root", "entities"),
                    List.of(),
                    List.of())),
            List.of(new ModelDelta.Reference("agg1", "entities", "ent1")),
            List.of(),
            List.of(),
            List.of());

    SemanticModelPatch patch =
        compiler.compile(
            ModelLevel.CIM,
            mapper.readTree(
                """
                {"id":"root","eClass":"CIMModel","modelLevel":"CIM","aggregates":[],"entities":[]}
                """),
            Map.of(),
            delta);

    SemanticModelPatch.Operation connection =
        patch.operations().stream()
            .filter(
                operation -> operation.type() == SemanticModelPatch.OperationType.CONNECT_ELEMENTS)
            .findFirst()
            .orElseThrow();

    assertEquals("members", connection.referenceName());
  }
}
