package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.model.application.ModelService;
import io.mehdieidi.modless.platform.project.application.ProjectService;
import io.mehdieidi.modless.platform.project.domain.ProjectRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssistantOrchestratorTest {

  private final AssistantModelProvider provider = mock(AssistantModelProvider.class);
  private final AssistantSessionStore sessions = mock(AssistantSessionStore.class);
  private final AssistantMemoryRepository memory = mock(AssistantMemoryRepository.class);
  private final SpringAiChatMemoryService chatMemory = mock(SpringAiChatMemoryService.class);
  private final AssistantCatalogService catalogs = mock(AssistantCatalogService.class);
  private final AssistantModelContextIndexService contexts =
      mock(AssistantModelContextIndexService.class);
  private final AssistantRealtimeHub realtime = mock(AssistantRealtimeHub.class);
  private final ModelService models = mock(ModelService.class);
  private final ProjectService projects = mock(ProjectService.class);
  private final UserRecord user =
      new UserRecord("user", "user@example.com", "User", "", "", Instant.now(), Instant.now());
  private final AssistantSessionStore.AssistantSession session =
      new AssistantSessionStore.AssistantSession(
          "session", "user", "project", ModelLevel.PIM, "Orders", Instant.now(), Instant.now());
  private final AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
  private final AssistantPatchCompleter patchCompleter = new AssistantPatchCompleter(schemas);
  private final AssistantValidationFeedbackResolver feedbackResolver =
      new AssistantValidationFeedbackResolver(schemas);
  private final AssistantClarificationGate clarificationGate = new AssistantClarificationGate();
  private AssistantOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    AiProperties properties =
        new AiProperties(
            true, null, null, null, 64, 6, 6000, 0, 0, 0, null, null, null, null, null, null, null);
    when(sessions.require("session", "user")).thenReturn(session);
    when(projects.get(user, "project"))
        .thenReturn(
            new ProjectRecord(
                "project",
                "Project",
                "",
                "user",
                Map.of(),
                List.of(),
                Instant.now(),
                Instant.now()));
    when(models.validate(any(ModelLevel.class), any()))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    var context =
        new AssistantModelContextIndexService.AssistantModelContext(
            null,
            "project",
            ModelLevel.PIM,
            "Orders",
            0L,
            List.of(),
            List.of(),
            Map.of(),
            List.of());
    when(contexts.transientSnapshot(anyString(), any(), anyString(), anyLong(), any(), any()))
        .thenReturn(context);
    when(contexts.summarize(context)).thenReturn("No saved elements.");
    when(catalogs.search(anyString(), anyString(), anyInt())).thenReturn(List.of());
    when(catalogs.describeType(anyString(), anyString(), anyInt())).thenReturn(List.of());
    when(memory.summary(anyString())).thenReturn(Optional.empty());
    when(memory.recentMessages(anyString(), anyInt())).thenReturn(List.of());
    when(chatMemory.recent(anyString(), anyInt())).thenReturn(List.of());
    orchestrator =
        new AssistantOrchestrator(
            properties,
            provider,
            sessions,
            memory,
            chatMemory,
            catalogs,
            contexts,
            new AssistantPatchCompiler(),
            patchCompleter,
            feedbackResolver,
            clarificationGate,
            schemas,
            realtime,
            new AssistantHardeningService(properties, null),
            models,
            projects);
  }

  @Test
  void llmDecisionControlsIntentWithoutKeywordRouting() {
    when(provider.planTurn(any()))
        .thenReturn(
            new AssistantTurnPlan(
                AssistantTurnPlan.Kind.ANSWER,
                "This is an explanation only.",
                List.of(),
                new SemanticModelPatch(List.of())));

    var response = orchestrator.handleMessage(user, "session", request("delete everything"));

    assertEquals(AssistantWorkflowState.EXPLAINED, response.workflowState());
    assertNull(response.proposal());
  }

  @Test
  void persistsStructuredClarificationInsteadOfGuessing() {
    AssistantChoice question =
        new AssistantChoice(
            "delivery",
            "Which delivery guarantee is required?",
            AssistantChoice.SelectionMode.SINGLE,
            List.of(
                new AssistantChoice.Option("once", "At least once", "Allow deduplication."),
                new AssistantChoice.Option("exact", "Effectively once", "Require idempotency.")),
            true);
    when(provider.planTurn(any()))
        .thenReturn(
            new AssistantTurnPlan(
                AssistantTurnPlan.Intent.INFORMATION,
                AssistantTurnPlan.Kind.CLARIFICATION,
                "I need one decision.",
                List.of(question),
                new SemanticModelPatch(List.of())));

    var response = orchestrator.handleMessage(user, "session", request("design the workflow"));

    assertEquals(AssistantWorkflowState.WAITING_FOR_CHOICE, response.workflowState());
    assertEquals(1, response.choices().size());
    verify(memory).savePendingInteraction(anyString(), any(), any());
  }

  @Test
  void onlyValidatedPatchBecomesReviewableProposal() throws Exception {
    var attributes =
        new ObjectMapper()
            .readTree("{\"name\":\"Submit order\",\"businessOperationRef\":\"submit-order\"}");
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "submit-order",
                    "Function",
                    attributes,
                    null,
                    null)));
    when(provider.planTurn(any()))
        .thenReturn(
            new AssistantTurnPlan(
                AssistantTurnPlan.Kind.PATCH, "Prepared the function.", List.of(), patch));

    var response = orchestrator.handleMessage(user, "session", request("model submission"));

    assertEquals(AssistantWorkflowState.PROPOSED, response.workflowState());
    assertNotNull(response.proposal());
    assertEquals(true, response.proposal().validation().mandatoryPassed());
    assertEquals(true, response.proposal().approvalRequired());
    java.util.UUID.fromString(response.proposal().patch().operations().get(0).targetElementId());
    assertEquals(
        response.proposal().patch().operations().get(0).targetElementId(),
        response
            .proposal()
            .patch()
            .operations()
            .get(0)
            .attributes()
            .path("businessOperationRef")
            .asText());
    verify(memory).saveProposal(anyString(), anyString(), any(), anyLong(), any(), anyString());
  }

  @Test
  void invalidPatchIsRepairedToClarificationAndNeverSavedAsProposal() {
    SemanticModelPatch invalid =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "invented",
                    "NotInTheMetamodel",
                    JsonNodeFactory.instance.objectNode(),
                    null,
                    null)));
    AssistantChoice question =
        new AssistantChoice(
            "scope",
            "Which formal concern should be modeled first?",
            List.of(new AssistantChoice.Option("core", "Core flow", "Start with the core flow.")));
    when(provider.planTurn(any()))
        .thenReturn(
            new AssistantTurnPlan(AssistantTurnPlan.Kind.PATCH, "", List.of(), invalid),
            new AssistantTurnPlan(
                AssistantTurnPlan.Kind.CLARIFICATION,
                "The type was not grounded.",
                List.of(question),
                new SemanticModelPatch(List.of())))
        .thenReturn(
            new AssistantTurnPlan(
                AssistantTurnPlan.Kind.CLARIFICATION,
                "The type was not grounded.",
                List.of(question),
                new SemanticModelPatch(List.of())));

    var response = orchestrator.handleMessage(user, "session", request("make it"));

    assertEquals(AssistantWorkflowState.WAITING_FOR_CHOICE, response.workflowState());
    assertNull(response.proposal());
  }

  @Test
  void retriesValidationWithFreshFeedbackUntilAProposalIsValid() throws Exception {
    SemanticModelPatch invalid =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "bad-id",
                    "NotInTheMetamodel",
                    JsonNodeFactory.instance.objectNode(),
                    null,
                    null)));
    var attributes = new ObjectMapper().readTree("{\"name\":\"Submit order\"}");
    SemanticModelPatch valid =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "b92ec7c2-8875-4eb6-bb3e-70993dcf20bf",
                    "Function",
                    attributes,
                    null,
                    null)));
    when(provider.planTurn(any()))
        .thenReturn(
            new AssistantTurnPlan(AssistantTurnPlan.Kind.PATCH, "", List.of(), invalid),
            new AssistantTurnPlan(AssistantTurnPlan.Kind.PATCH, "", List.of(), invalid),
            new AssistantTurnPlan(AssistantTurnPlan.Kind.PATCH, "Prepared.", List.of(), valid));

    var response = orchestrator.handleMessage(user, "session", request("model submission"));

    assertEquals(AssistantWorkflowState.PROPOSED, response.workflowState());
    assertNotNull(response.proposal());
    verify(provider, times(3)).planTurn(any());
  }

  @Test
  void defersArchitectureClarificationAndProducesProposal() throws Exception {
    AssistantChoice architectureQuestion =
        new AssistantChoice(
            "interaction-style",
            "Primary Interaction Style: REST APIs or event-driven updates?",
            AssistantChoice.SelectionMode.SINGLE,
            List.of(
                new AssistantChoice.Option("api", "APIFIRSTSERVERLESS", "REST APIs."),
                new AssistantChoice.Option("events", "EVENTDRIVENSERVERLESS", "Events.")),
            true);
    var attributes = new ObjectMapper().readTree("{\"name\":\"Dispense item\"}");
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "b92ec7c2-8875-4eb6-bb3e-70993dcf20bf",
                    "Function",
                    attributes,
                    null,
                    null)));
    when(provider.planTurn(any()))
        .thenReturn(
            new AssistantTurnPlan(
                AssistantTurnPlan.Intent.MUTATION,
                AssistantTurnPlan.Kind.CLARIFICATION,
                "I need architectural decisions.",
                List.of(architectureQuestion),
                new SemanticModelPatch(List.of())),
            new AssistantTurnPlan(
                AssistantTurnPlan.Intent.MUTATION,
                AssistantTurnPlan.Kind.PATCH,
                "Prepared vending machine backend.",
                List.of(),
                patch));

    var response =
        orchestrator.handleMessage(
            user, "session", request("Create a serverless model for vending machine backend"));

    assertEquals(AssistantWorkflowState.PROPOSED, response.workflowState());
    assertNotNull(response.proposal());
    verify(provider, times(2)).planTurn(any());
  }

  @Test
  void vendingMachinePromptProducesProposalAfterPatchCompletion() throws Exception {
    var attributes = new ObjectMapper().readTree("{\"name\":\"Dispense item\"}");
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "b92ec7c2-8875-4eb6-bb3e-70993dcf20bf",
                    "Function",
                    attributes,
                    null,
                    null)));
    when(provider.planTurn(any()))
        .thenReturn(
            new AssistantTurnPlan(
                AssistantTurnPlan.Intent.MUTATION,
                AssistantTurnPlan.Kind.PATCH,
                "Prepared the vending machine backend.",
                List.of(),
                patch));

    var response =
        orchestrator.handleMessage(
            user, "session", request("Create a serverless model for vending machine backend"));

    assertEquals(AssistantWorkflowState.PROPOSED, response.workflowState());
    assertNotNull(response.proposal());
    assertTrue(
        response.proposal().patch().operations().stream()
            .anyMatch(operation -> "FunctionContract".equals(operation.elementType())));
  }

  private AssistantOrchestrator.AssistantTurnRequest request(String message) {
    return new AssistantOrchestrator.AssistantTurnRequest(
        message, null, null, "pim", List.of(), null);
  }
}
