package io.mehdieidi.modless.platform.assistant.delta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mehdieidi.modless.platform.assistant.agent.ModelingAgent;
import io.mehdieidi.modless.platform.assistant.application.AssistantValidationFeedbackResolver;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompleter;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.assistant.support.AssistantSettingsFixtures;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DeltaRepairServiceTest {

  @Test
  void replanWithSafeDefaultsBuildsRepairPromptThroughModelingAgent() {
    ModelingAgent modelingAgent = mock(ModelingAgent.class);
    AssistantTurnPlan expected =
        new AssistantTurnPlan(
            AssistantTurnPlan.Intent.MUTATION,
            AssistantTurnPlan.Kind.PATCH,
            "repaired",
            List.of(),
            new SemanticModelPatch(List.of()));
    when(modelingAgent.repair(eq(ModelLevel.PIM), any(), any(), any())).thenReturn(expected);
    DeltaRepairService service = service(modelingAgent);

    AssistantTurnPlan actual =
        service.replanWithSafeDefaults(
            ModelLevel.PIM,
            "Base system prompt.",
            "Create ordering",
            null,
            List.of(new AssistantModelProvider.ContextSnippet("schema", "Function", "contract")),
            new AssistantTurnPlan(
                AssistantTurnPlan.Intent.MUTATION,
                AssistantTurnPlan.Kind.ANSWER,
                "no patch",
                List.of(),
                new SemanticModelPatch(List.of())),
            List.of("Function.name is required"));

    assertEquals(expected, actual);
    ArgumentCaptor<AssistantModelProvider.AssistantPrompt> prompt =
        ArgumentCaptor.forClass(AssistantModelProvider.AssistantPrompt.class);
    verify(modelingAgent).repair(eq(ModelLevel.PIM), eq(null), eq(null), prompt.capture());
    assertTrue(prompt.getValue().system().contains("mandatory replanning pass"));
    assertTrue(prompt.getValue().system().contains("Function.name is required"));
    assertTrue(prompt.getValue().user().contains("Create ordering"));
  }

  @Test
  void validatorGuidedRepairIncludesFailedPlanAndFeedback() {
    ModelingAgent modelingAgent = mock(ModelingAgent.class);
    AssistantTurnPlan expected =
        new AssistantTurnPlan(
            AssistantTurnPlan.Intent.MUTATION,
            AssistantTurnPlan.Kind.PATCH,
            "fixed",
            List.of(),
            new SemanticModelPatch(List.of()));
    when(modelingAgent.repair(eq(ModelLevel.PIM), any(), any(), any())).thenReturn(expected);
    DeltaRepairService service = service(modelingAgent);
    SemanticModelPatch failedPatch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "fn",
                    "Function",
                    null,
                    null,
                    null)));

    AssistantTurnPlan actual =
        service.repair(
            ModelLevel.PIM,
            "System prompt.",
            "Create order flow",
            null,
            null,
            List.of(),
            new AssistantTurnPlan(
                AssistantTurnPlan.Intent.MUTATION,
                AssistantTurnPlan.Kind.PATCH,
                "failed",
                List.of(),
                failedPatch),
            List.of("Function must be contained by PIMModel.functions"),
            1);

    assertEquals(expected, actual);
    ArgumentCaptor<AssistantModelProvider.AssistantPrompt> prompt =
        ArgumentCaptor.forClass(AssistantModelProvider.AssistantPrompt.class);
    verify(modelingAgent).repair(eq(ModelLevel.PIM), eq(null), eq(null), prompt.capture());
    assertTrue(prompt.getValue().system().contains("validator-guided repair pass 1"));
    assertTrue(prompt.getValue().user().contains("Rejected semantic operations summary"));
    assertTrue(prompt.getValue().user().contains("focused ModelDelta patch"));
    assertTrue(prompt.getValue().user().contains("Function must be contained"));
  }

  private DeltaRepairService service(ModelingAgent modelingAgent) {
    AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
    AssistantCatalog catalog = mock(AssistantCatalog.class);
    when(catalog.search(anyString(), anyString(), anyInt())).thenReturn(List.of());
    when(catalog.describeType(anyString(), anyString(), anyInt())).thenReturn(List.of());
    when(catalog.canonicalEnumLiteral(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    return new DeltaRepairService(
        modelingAgent,
        new AssistantValidationFeedbackResolver(schemas),
        schemas,
        catalog,
        new AssistantPatchCompleter(schemas),
        AssistantSettingsFixtures.defaults());
  }
}
