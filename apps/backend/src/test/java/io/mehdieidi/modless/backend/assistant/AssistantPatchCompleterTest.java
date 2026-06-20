package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssistantPatchCompleterTest {

  private final AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
  private final AssistantPatchCompleter completer = new AssistantPatchCompleter(schemas);
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void addsMissingFunctionContractForFunction() throws Exception {
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "function-1",
                    "Function",
                    mapper.readTree("{\"name\":\"Dispense item\"}"),
                    null,
                    null)));

    SemanticModelPatch completed = completer.complete(ModelLevel.PIM, patch, Map.of());

    assertEquals(2, completed.operations().size());
    SemanticModelPatch.Operation contract =
        completed.operations().stream()
            .filter(operation -> "FunctionContract".equals(operation.elementType()))
            .findFirst()
            .orElseThrow();
    assertEquals("function-1", contract.sourceElementId());
    assertEquals("contract", contract.referenceName());
    assertTrue(contract.attributes().has("name"));
  }

  @Test
  void repairsMissingContractForExistingFunctionId() {
    String functionId = "68ae9350-f377-409e-bfad-12dbc945f0b6";
    SemanticModelPatch patch = new SemanticModelPatch(List.of());
    List<AssistantValidationFeedbackResolver.MissingRequiredFeature> missing =
        List.of(
            new AssistantValidationFeedbackResolver.MissingRequiredFeature(functionId, "contract"));

    SemanticModelPatch repaired =
        completer.repairFromValidationFeedback(
            ModelLevel.PIM, patch, Map.of(functionId, "Function"), missing);

    assertEquals(1, repaired.operations().size());
    assertEquals(functionId, repaired.operations().get(0).sourceElementId());
    assertEquals("contract", repaired.operations().get(0).referenceName());
    assertEquals("FunctionContract", repaired.operations().get(0).elementType());
  }
}
