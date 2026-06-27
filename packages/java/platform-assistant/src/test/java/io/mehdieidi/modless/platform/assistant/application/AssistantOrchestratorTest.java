package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.mehdieidi.modless.platform.assistant.domain.AssistantChoice;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.AssistantWorkflowState;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.domain.memory.AssistantMemoryRecords.ThreadRecord;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompleter;
import io.mehdieidi.modless.platform.assistant.persistence.jdbc.JdbcAssistantModelContextIndex;
import io.mehdieidi.modless.platform.assistant.planning.AssistantClarificationGate;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.assistant.spi.AssistantChatMemory;
import io.mehdieidi.modless.platform.assistant.spi.AssistantMemoryStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantRealtimePublisher;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSettings;
import io.mehdieidi.modless.platform.assistant.spi.AssistantToolBridge;
import io.mehdieidi.modless.platform.assistant.support.AssistantSettingsFixtures;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.model.application.ModelService;
import io.mehdieidi.modless.platform.model.domain.ModelRecord;
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
  private final AssistantMemoryStore memory = mock(AssistantMemoryStore.class);
  private final AssistantChatMemory chatMemory = mock(AssistantChatMemory.class);
  private final AssistantCatalog catalogs = mock(AssistantCatalog.class);
  private final JdbcAssistantModelContextIndex contexts = new JdbcAssistantModelContextIndex();
  private final AssistantRealtimePublisher realtime = mock(AssistantRealtimePublisher.class);
  private final ModelService models = mock(ModelService.class);
  private final ProjectService projects = mock(ProjectService.class);
  private final UserRecord user =
      new UserRecord("user", "user@example.com", "User", "", "", Instant.now(), Instant.now());
  private final AssistantSessionStore.AssistantSession session =
      new AssistantSessionStore.AssistantSession(
          "session", "user", "project", ModelLevel.PIM, "Orders", Instant.now(), Instant.now());
  private final AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
  private final AssistantToolBridge tools = mock(AssistantToolBridge.class);
  private final io.mehdieidi.modless.platform.assistant.spi.AssistantMetrics metrics =
      mock(io.mehdieidi.modless.platform.assistant.spi.AssistantMetrics.class);
  private final AssistantPatchCompleter patchCompleter = new AssistantPatchCompleter(schemas);
  private final AssistantValidationFeedbackResolver feedbackResolver =
      new AssistantValidationFeedbackResolver(schemas);
  private final AssistantClarificationGate clarificationGate = new AssistantClarificationGate();
  private AssistantOrchestrator orchestrator;

  @BeforeEach
  void setUp() throws Exception {
    AssistantSettings properties = AssistantSettingsFixtures.defaults();
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
    when(models.validateStructural(any(ModelLevel.class), any()))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    when(models.validate(any(ModelLevel.class), any()))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    JsonNode emptyPimModel =
        new ObjectMapper()
            .readTree(
                """
                {
                  "id": "root",
                  "eClass": "PIMModel",
                  "modelLevel": "PIM",
                  "name": "Orders",
                  "functions": [],
                  "diagram": {"elements": [], "relationships": []}
                }
                """);
    ModelRecord createdModel =
        new ModelRecord(
            "model-1",
            "project",
            ModelLevel.PIM,
            "Orders",
            emptyPimModel,
            "v1",
            "hash",
            1L,
            null,
            "CURRENT",
            Instant.now(),
            Instant.now());
    when(models.create(eq(user), eq(ModelLevel.PIM), eq("project"), anyString(), any()))
        .thenReturn(createdModel);
    when(models.patch(eq(user), eq(ModelLevel.PIM), eq("model-1"), anyString(), any(), eq(1L)))
        .thenAnswer(
            invocation ->
                new ModelRecord(
                    createdModel.id(),
                    createdModel.projectId(),
                    createdModel.level(),
                    createdModel.name(),
                    createdModel.modelJson(),
                    createdModel.metamodelVersion(),
                    createdModel.metamodelHash(),
                    2L,
                    createdModel.sourceXmiHash(),
                    createdModel.migrationState(),
                    createdModel.createdAt(),
                    Instant.now()));
    when(catalogs.search(anyString(), anyString(), anyInt())).thenReturn(List.of());
    when(catalogs.describeType(anyString(), anyString(), anyInt())).thenReturn(List.of());
    when(memory.summary(anyString())).thenReturn(Optional.empty());
    when(memory.recentMessages(anyString(), anyInt())).thenReturn(List.of());
    when(memory.userMessageCount(anyString())).thenReturn(0);
    when(memory.requireThread(anyString()))
        .thenReturn(
            new ThreadRecord(
                "session",
                "user",
                "project",
                ModelLevel.PIM,
                "Orders",
                null,
                null,
                Instant.now(),
                Instant.now()));
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
            tools,
            metrics,
            new ObjectMapper(),
            realtime,
            new AssistantHardeningService(properties, null),
            models,
            projects);
  }

  @Test
  void recreatesMissingDurableThreadBeforePersistingMessages() {
    when(memory.requireThread("session")).thenReturn(null);
    when(provider.planTurn(any()))
        .thenReturn(
            new AssistantTurnPlan(
                AssistantTurnPlan.Kind.ANSWER,
                "Recovered thread.",
                List.of(),
                new SemanticModelPatch(List.of())));

    orchestrator.handleMessage(user, "session", request("Explain what this model does"));

    verify(memory)
        .createThread(
            eq("session"), eq(user), eq("project"), eq(ModelLevel.PIM), eq("Orders"), any(), any());
    verify(memory).appendMessage(eq("session"), eq("USER"), anyString(), any());
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

    var response =
        orchestrator.handleMessage(user, "session", request("Explain what this model does"));

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
    when(provider.planMutationTurn(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AgentLoopResult(
                new AssistantTurnPlan(
                    AssistantTurnPlan.Intent.INFORMATION,
                    AssistantTurnPlan.Kind.CLARIFICATION,
                    "I need one decision.",
                    List.of(question),
                    new SemanticModelPatch(List.of())),
                3,
                4));

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
    when(provider.planMutationTurn(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AgentLoopResult(
                new AssistantTurnPlan(
                    AssistantTurnPlan.Kind.PATCH, "Prepared the function.", List.of(), patch),
                3,
                4));

    var response = orchestrator.handleMessage(user, "session", request("model submission"));

    assertEquals(AssistantWorkflowState.APPLIED, response.workflowState());
    assertNotNull(response.proposal());
    assertEquals(true, response.proposal().validation().mandatoryPassed());
    assertEquals(false, response.proposal().approvalRequired());
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
    when(provider.planMutationTurn(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AgentLoopResult(
                new AssistantTurnPlan(AssistantTurnPlan.Kind.PATCH, "", List.of(), invalid), 3, 4));
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
    when(provider.planMutationTurn(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AgentLoopResult(
                new AssistantTurnPlan(AssistantTurnPlan.Kind.PATCH, "", List.of(), invalid), 3, 4));
    when(provider.planTurn(any()))
        .thenReturn(
            new AssistantTurnPlan(AssistantTurnPlan.Kind.PATCH, "", List.of(), invalid),
            new AssistantTurnPlan(AssistantTurnPlan.Kind.PATCH, "", List.of(), invalid),
            new AssistantTurnPlan(AssistantTurnPlan.Kind.PATCH, "Prepared.", List.of(), valid));

    var response = orchestrator.handleMessage(user, "session", request("model submission"));

    assertEquals(AssistantWorkflowState.APPLIED, response.workflowState());
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
    when(provider.planMutationTurn(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AgentLoopResult(
                new AssistantTurnPlan(
                    AssistantTurnPlan.Intent.MUTATION,
                    AssistantTurnPlan.Kind.CLARIFICATION,
                    "I need architectural decisions.",
                    List.of(architectureQuestion),
                    new SemanticModelPatch(List.of())),
                3,
                4));
    when(provider.planTurn(any()))
        .thenReturn(
            new AssistantTurnPlan(
                AssistantTurnPlan.Intent.MUTATION,
                AssistantTurnPlan.Kind.PATCH,
                "Prepared vending machine backend.",
                List.of(),
                patch));

    var response =
        orchestrator.handleMessage(
            user, "session", request("Create a serverless model for vending machine backend"));

    assertEquals(AssistantWorkflowState.APPLIED, response.workflowState());
    assertNotNull(response.proposal());
    verify(provider, times(1)).planMutationTurn(any(), any());
    verify(provider, times(1)).planTurn(any());
  }

  @Test
  void creationPromptPreservesLlmPatch() throws Exception {
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
    when(provider.planMutationTurn(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AgentLoopResult(
                new AssistantTurnPlan(
                    AssistantTurnPlan.Intent.MUTATION,
                    AssistantTurnPlan.Kind.PATCH,
                    "Prepared the vending machine backend.",
                    List.of(),
                    patch),
                3,
                4));

    var response =
        orchestrator.handleMessage(
            user, "session", request("Create a serverless model for vending machine backend"));

    assertEquals(AssistantWorkflowState.APPLIED, response.workflowState());
    assertNotNull(response.proposal());
    assertTrue(
        response.proposal().patch().operations().size() < 40,
        () ->
            "Expected the LLM patch to be preserved, but got "
                + response.proposal().patch().operations().size()
                + " operations");
    verify(provider, times(1)).planMutationTurn(any(), any());
  }

  @Test
  void autoAppliesLowRiskSingleAttributeEdit() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode modelJson =
        mapper.readTree(
            """
            {
              "id": "root",
              "eClass": "PIMModel",
              "modelLevel": "PIM",
              "name": "Orders",
              "diagram": {
                "elements": [{"id": "fn-1", "eClass": "Function", "name": "Old"}],
                "relationships": []
              }
            }
            """);
    ModelRecord model =
        new ModelRecord(
            "model-1",
            "project",
            ModelLevel.PIM,
            "Orders",
            modelJson,
            "v1",
            "hash",
            1L,
            null,
            "CURRENT",
            Instant.now(),
            Instant.now());
    when(projects.get(user, "project"))
        .thenReturn(
            new ProjectRecord(
                "project",
                "Project",
                "",
                "user",
                Map.of("pim", "model-1"),
                List.of(),
                Instant.now(),
                Instant.now()));
    when(models.get(user, ModelLevel.PIM, "model-1")).thenReturn(model);
    when(models.patch(any(), any(), anyString(), anyString(), any(), anyLong()))
        .thenAnswer(
            invocation ->
                new ModelRecord(
                    model.id(),
                    model.projectId(),
                    model.level(),
                    model.name(),
                    model.modelJson(),
                    model.metamodelVersion(),
                    model.metamodelHash(),
                    2L,
                    model.sourceXmiHash(),
                    model.migrationState(),
                    model.createdAt(),
                    Instant.now()));

    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.SET_ATTRIBUTE,
                    "fn-1",
                    "Function",
                    mapper.readTree("\"New\""),
                    null,
                    "name")));
    when(provider.planMutationTurn(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AgentLoopResult(
                new AssistantTurnPlan(
                    AssistantTurnPlan.Kind.PATCH, "Renamed the function.", List.of(), patch),
                2,
                3));

    var response =
        orchestrator.handleMessage(
            user,
            "session",
            new AssistantOrchestrator.AssistantTurnRequest(
                "Rename the function to New", "model-1", 1L, "pim", List.of("fn-1"), null));

    assertEquals(AssistantWorkflowState.APPLIED, response.workflowState());
    assertNotNull(response.proposal());
    assertEquals(false, response.proposal().approvalRequired());
    verify(models).patch(eq(user), eq(ModelLevel.PIM), eq("model-1"), anyString(), any(), eq(1L));
    verify(memory).markProposalApplied(anyString(), eq("model-1"), eq(2L));
  }

  private AssistantOrchestrator.AssistantTurnRequest request(String message) {
    return new AssistantOrchestrator.AssistantTurnRequest(
        message, null, null, "pim", List.of(), null);
  }
}
