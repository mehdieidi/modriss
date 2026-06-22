package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.mehdieidi.modless.platform.assistant.application.AssistantValidationFeedbackResolver;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantValidationFeedbackResolverTest {

  private final AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
  private final AssistantValidationFeedbackResolver resolver =
      new AssistantValidationFeedbackResolver(schemas);

  @Test
  void resolvesFunctionFromRequiredContractFeedback() {
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "function-1",
                    "Function",
                    JsonNodeFactory.instance.objectNode().put("name", "Restock"),
                    null,
                    null)));

    List<String> feedback =
        List.of("EVL_MODEL_LOADING: required feature 'contract' of 'Function@functions.0'");

    List<String> types = resolver.resolveTypes(ModelLevel.PIM, feedback, patch);

    assertTrue(types.contains("Function"));
    List<AssistantModelProvider.ContextSnippet> contracts =
        resolver.contractsForFeedback(ModelLevel.PIM, feedback, patch, 4);
    assertEquals("Function", contracts.get(0).title());
    assertTrue(contracts.get(0).content().contains("contract -> FunctionContract required"));
  }

  @Test
  void parsesElementIdFromRequiredFeatureFeedback() {
    List<String> feedback =
        List.of(
            "EVL_MODEL_LOADING: The required feature 'contract' of "
                + "'org.eclipse.emf.ecore.impl.DynamicEObjectImpl/Function@abc"
                + "{memory:/export-pim.xmi#68ae9350-f377-409e-bfad-12dbc945f0b6}' must be set");

    List<AssistantValidationFeedbackResolver.MissingRequiredFeature> missing =
        resolver.missingRequiredFeatures(feedback);

    assertEquals(1, missing.size());
    assertEquals("68ae9350-f377-409e-bfad-12dbc945f0b6", missing.get(0).ownerElementId());
    assertEquals("contract", missing.get(0).featureName());
  }
}
