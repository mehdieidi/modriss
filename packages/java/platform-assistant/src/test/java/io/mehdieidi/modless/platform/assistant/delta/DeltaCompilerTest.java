package io.mehdieidi.modless.platform.assistant.delta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
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
  void rejectsUnknownFeatureBeforeApply() throws Exception {
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

    assertThrows(
        PlatformException.class,
        () ->
            compiler.compile(
                ModelLevel.PIM,
                mapper.readTree(
                    """
                    {"id":"root","eClass":"PIMModel","modelLevel":"PIM","functions":[]}
                    """),
                Map.of(),
                delta));
  }
}
