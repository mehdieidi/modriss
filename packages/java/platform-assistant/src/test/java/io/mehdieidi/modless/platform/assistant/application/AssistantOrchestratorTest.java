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
import static org.mockito.Mockito.atLeastOnce;
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
import org.mockito.ArgumentCaptor;

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
    when(provider.planMutationTurn(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AgentLoopResult(
                new AssistantTurnPlan(
                    AssistantTurnPlan.Kind.ANSWER,
                    "Recovered thread.",
                    List.of(),
                    new SemanticModelPatch(List.of())),
                0,
                1));

    orchestrator.handleMessage(user, "session", request("Explain what this model does"));

    verify(memory)
        .createThread(
            eq("session"), eq(user), eq("project"), eq(ModelLevel.PIM), eq("Orders"), any(), any());
    verify(memory).appendMessage(eq("session"), eq("USER"), anyString(), any());
  }

  @Test
  void llmDecisionControlsIntentWithoutKeywordRouting() {
    when(provider.planMutationTurn(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AgentLoopResult(
                new AssistantTurnPlan(
                    AssistantTurnPlan.Kind.ANSWER,
                    "This is an explanation only.",
                    List.of(),
                    new SemanticModelPatch(List.of())),
                0,
                1));

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
                    AssistantTurnPlan.Intent.MUTATION,
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

    assertEquals(
        AssistantWorkflowState.APPLIED, response.workflowState(), response.assistantMessage());
    assertNotNull(response.proposal());
    assertEquals(true, response.proposal().validation().mandatoryPassed());
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
  void mutationRouteReplansAnswerOnlyResultIntoPatch() throws Exception {
    when(provider.planMutationTurn(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AgentLoopResult(
                new AssistantTurnPlan(
                    AssistantTurnPlan.Intent.MUTATION,
                    AssistantTurnPlan.Kind.ANSWER,
                    "I completed the analysis.",
                    List.of(),
                    new SemanticModelPatch(List.of())),
                1,
                1));
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

    assertEquals(
        AssistantWorkflowState.APPLIED, response.workflowState(), response.assistantMessage());
    assertNotNull(response.proposal());
    verify(provider, times(1)).planTurn(any());
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
  void usesRetrievalContextForAttachmentTurns() throws Exception {
    String attachment = "As a clinic receptionist, I register patients.\n".repeat(3000);
    var attributes =
        new ObjectMapper()
            .readTree(
                "{\"name\":\"Register patient\",\"businessOperationRef\":\"register-patient\"}");
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "register-patient",
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
                    "Prepared retrieval patch.",
                    List.of(),
                    patch),
                0,
                1));

    orchestrator.handleMessage(
        user,
        "session",
        new AssistantOrchestrator.AssistantTurnRequest(
            "Create a CIM model from this user story document",
            null,
            null,
            "pim",
            List.of(),
            null,
            "clinic-user-stories.md",
            attachment));

    ArgumentCaptor<AssistantModelProvider.AssistantPrompt> prompt =
        ArgumentCaptor.forClass(AssistantModelProvider.AssistantPrompt.class);
    verify(provider).planMutationTurn(prompt.capture(), any());
    assertTrue(
        prompt.getValue().snippets().stream()
            .anyMatch(
                snippet ->
                    snippet.source().equals("user-attachment")
                        && attachment.startsWith(snippet.content().replace("\n[truncated]", ""))));
    assertTrue(
        prompt.getValue().snippets().stream()
            .noneMatch(snippet -> snippet.source().startsWith("full-context")));
  }

  @Test
  void cimAttachmentTurnsRunSourceAnalysisBeforePlanning() {
    AssistantSessionStore.AssistantSession cimSession =
        new AssistantSessionStore.AssistantSession(
            "cim-session",
            "user",
            "project",
            ModelLevel.CIM,
            "Clinic CIM",
            Instant.now(),
            Instant.now());
    when(sessions.require("cim-session", "user")).thenReturn(cimSession);
    when(provider.analyzeSource(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AssistantReply(
                "Commands: Register patient. Events: Patient registered. Entities: Patient.",
                "mock",
                "mock-model"));
    when(provider.planMutationTurn(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AgentLoopResult(
                new AssistantTurnPlan(
                    AssistantTurnPlan.Kind.ANSWER,
                    "Analyzed source.",
                    List.of(),
                    new SemanticModelPatch(List.of())),
                0,
                1));

    orchestrator.handleMessage(
        user,
        "cim-session",
        new AssistantOrchestrator.AssistantTurnRequest(
            "Analyze the attached event storming notes",
            null,
            null,
            "cim",
            List.of(),
            null,
            "event-storming.md",
            "User commands Register patient, then Patient registered event occurs."));

    ArgumentCaptor<AssistantModelProvider.AssistantPrompt> analysisPrompt =
        ArgumentCaptor.forClass(AssistantModelProvider.AssistantPrompt.class);
    verify(provider).analyzeSource(analysisPrompt.capture(), any());
    assertTrue(
        analysisPrompt.getValue().snippets().stream()
            .anyMatch(snippet -> snippet.source().equals("user-attachment")));
    assertTrue(analysisPrompt.getValue().user().contains("event-storming.md"));
    assertTrue(
        analysisPrompt
            .getValue()
            .user()
            .contains("User commands Register patient, then Patient registered event occurs."));

    ArgumentCaptor<AssistantModelProvider.AssistantPrompt> planPrompt =
        ArgumentCaptor.forClass(AssistantModelProvider.AssistantPrompt.class);
    verify(provider).planMutationTurn(planPrompt.capture(), any());
    assertTrue(
        planPrompt.getValue().snippets().stream()
            .anyMatch(
                snippet ->
                    snippet.source().equals("source-analysis")
                        && snippet.content().contains("Register patient")));
    assertTrue(
        planPrompt.getValue().snippets().stream()
            .anyMatch(
                snippet ->
                    snippet.source().equals("user-attachment")
                        && snippet.content().contains("Patient registered event")));
  }

  @Test
  void explicitCimCreationAnswerIsReplannedAsPatch() throws Exception {
    AssistantSessionStore.AssistantSession cimSession =
        new AssistantSessionStore.AssistantSession(
            "cim-session",
            "user",
            "project",
            ModelLevel.CIM,
            "Clinic CIM",
            Instant.now(),
            Instant.now());
    when(sessions.require("cim-session", "user")).thenReturn(cimSession);
    JsonNode cimModel =
        new ObjectMapper()
            .readTree(
                """
                {
                  "id": "cim-root",
                  "eClass": "CIMModel",
                  "modelLevel": "CIM",
                  "name": "Clinic CIM",
                  "goals": [],
                  "diagram": {"elements": [], "relationships": []}
                }
                """);
    ModelRecord createdModel =
        new ModelRecord(
            "cim-model-1",
            "project",
            ModelLevel.CIM,
            "Clinic CIM",
            cimModel,
            "v1",
            "hash",
            1L,
            null,
            "CURRENT",
            Instant.now(),
            Instant.now());
    when(models.create(eq(user), eq(ModelLevel.CIM), eq("project"), anyString(), any()))
        .thenReturn(createdModel);
    when(models.patch(eq(user), eq(ModelLevel.CIM), eq("cim-model-1"), anyString(), any(), eq(1L)))
        .thenReturn(
            new ModelRecord(
                "cim-model-1",
                "project",
                ModelLevel.CIM,
                "Clinic CIM",
                cimModel,
                "v1",
                "hash",
                2L,
                null,
                "CURRENT",
                Instant.now(),
                Instant.now()));
    when(provider.analyzeSource(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AssistantReply(
                "Goals: improve clinic intake.", "mock", "mock-model"));
    when(provider.planMutationTurn(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AgentLoopResult(
                new AssistantTurnPlan(
                    AssistantTurnPlan.Intent.INFORMATION,
                    AssistantTurnPlan.Kind.ANSWER,
                    "I completed the model analysis.",
                    List.of(),
                    new SemanticModelPatch(List.of())),
                0,
                1));
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "goal-1",
                    "BusinessGoal",
                    JsonNodeFactory.instance
                        .objectNode()
                        .put("name", "Improve clinic intake")
                        .put("description", "Reduce manual intake work."),
                    null,
                    null)));
    when(provider.planTurn(any()))
        .thenReturn(
            new AssistantTurnPlan(
                AssistantTurnPlan.Intent.MUTATION,
                AssistantTurnPlan.Kind.PATCH,
                "Prepared CIM model.",
                List.of(),
                patch));

    AssistantOrchestrator.AssistantTurnResponse response =
        orchestrator.handleMessage(
            user,
            "cim-session",
            new AssistantOrchestrator.AssistantTurnRequest(
                "Build a CIM model for patient appointment registration",
                null,
                null,
                "cim",
                List.of(),
                null));

    assertEquals(AssistantWorkflowState.APPLIED, response.workflowState());
    verify(provider).planTurn(any());
  }

  @Test
  void rejectsShallowValidPatchForFullCimDocumentAndRepairsForCoverage() throws Exception {
    AssistantSessionStore.AssistantSession cimSession =
        new AssistantSessionStore.AssistantSession(
            "cim-session",
            "user",
            "project",
            ModelLevel.CIM,
            "Clinic CIM",
            Instant.now(),
            Instant.now());
    when(sessions.require("cim-session", "user")).thenReturn(cimSession);
    JsonNode cimModel =
        new ObjectMapper()
            .readTree(
                """
                {
                  "id": "cim-root",
                  "eClass": "CIMModel",
                  "modelLevel": "CIM",
                  "name": "Clinic CIM",
                  "goals": [],
                  "diagram": {"elements": [], "relationships": []}
                }
                """);
    ModelRecord createdModel =
        new ModelRecord(
            "cim-model-1",
            "project",
            ModelLevel.CIM,
            "Clinic CIM",
            cimModel,
            "v1",
            "hash",
            1L,
            null,
            "CURRENT",
            Instant.now(),
            Instant.now());
    when(models.create(eq(user), eq(ModelLevel.CIM), eq("project"), anyString(), any()))
        .thenReturn(createdModel);
    when(models.patch(eq(user), eq(ModelLevel.CIM), eq("cim-model-1"), anyString(), any(), eq(1L)))
        .thenReturn(
            new ModelRecord(
                "cim-model-1",
                "project",
                ModelLevel.CIM,
                "Clinic CIM",
                cimModel,
                "v1",
                "hash",
                2L,
                null,
                "CURRENT",
                Instant.now(),
                Instant.now()));
    when(provider.analyzeSource(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AssistantReply(
                "Clinic appointment source contains many stories, acceptance criteria, commands, "
                    + "events, policies, risks, assumptions, and domain data.",
                "mock",
                "mock-model"));
    when(provider.planMutationTurn(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AgentLoopResult(
                new AssistantTurnPlan(
                    AssistantTurnPlan.Intent.MUTATION,
                    AssistantTurnPlan.Kind.PATCH,
                    "Prepared shallow CIM.",
                    List.of(),
                    shallowCimPatch()),
                0,
                1));
    when(provider.planTurn(any()))
        .thenReturn(
            new AssistantTurnPlan(
                AssistantTurnPlan.Intent.MUTATION,
                AssistantTurnPlan.Kind.PATCH,
                "Prepared full CIM.",
                List.of(),
                fullCimCoveragePatch()));

    AssistantOrchestrator.AssistantTurnResponse response =
        orchestrator.handleMessage(
            user,
            "cim-session",
            new AssistantOrchestrator.AssistantTurnRequest(
                "Build a full CIM model from the attached user story document",
                null,
                null,
                "cim",
                List.of(),
                null,
                "clinic-user-stories.md",
                fullCimSourceDocument()));

    assertEquals(
        AssistantWorkflowState.APPLIED, response.workflowState(), response.assistantMessage());
    assertNotNull(response.proposal());
    assertTrue(response.proposal().patch().operations().size() >= 70);
    verify(provider).planTurn(any());
  }

  @Test
  void acceptsPatchesLargerThanLegacyOperationLimit() {
    ObjectMapper mapper = new ObjectMapper();
    List<SemanticModelPatch.Operation> operations = new java.util.ArrayList<>();
    for (int index = 0; index < 120; index++) {
      operations.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.ADD_ELEMENT,
              java.util.UUID.randomUUID().toString(),
              "Function",
              mapper.createObjectNode().put("name", "Function " + index),
              null,
              null));
    }
    SemanticModelPatch largePatch = new SemanticModelPatch(operations);
    when(provider.planMutationTurn(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AgentLoopResult(
                new AssistantTurnPlan(
                    AssistantTurnPlan.Intent.MUTATION,
                    AssistantTurnPlan.Kind.PATCH,
                    "Prepared a large model.",
                    List.of(),
                    largePatch),
                0,
                1));

    var response =
        orchestrator.handleMessage(user, "session", request("Create the complete operations map"));

    assertEquals(AssistantWorkflowState.APPLIED, response.workflowState());
    assertNotNull(response.proposal());
    assertTrue(response.proposal().patch().operations().size() > 96);
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
    verify(models).patch(eq(user), eq(ModelLevel.PIM), eq("model-1"), anyString(), any(), eq(1L));
    verify(memory).markProposalApplied(anyString(), eq("model-1"), eq(2L));
  }

  @Test
  void streamsPreviewModelBeforeCommittedModelUpdate() {
    ObjectMapper mapper = new ObjectMapper();
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "8c17f60a-cabe-46f1-aa60-17f70f669991",
                    "Function",
                    mapper.createObjectNode().put("name", "Create order"),
                    null,
                    null)));
    when(provider.planMutationTurn(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AgentLoopResult(
                new AssistantTurnPlan(
                    AssistantTurnPlan.Intent.MUTATION,
                    AssistantTurnPlan.Kind.PATCH,
                    "Created an order function.",
                    List.of(),
                    patch),
                1,
                2));

    orchestrator.handleMessage(user, "session", request("Create an order function"));

    ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(realtime, atLeastOnce())
        .publish(eq("session"), typeCaptor.capture(), payloadCaptor.capture());
    List<String> eventTypes = typeCaptor.getAllValues();
    assertTrue(eventTypes.contains("assistant.model.preview"));
    assertTrue(eventTypes.contains("model.updated"));
    int previewIndex = eventTypes.indexOf("assistant.model.preview");
    int commitIndex = eventTypes.indexOf("model.updated");
    assertTrue(previewIndex < commitIndex);
    Object previewPayload = payloadCaptor.getAllValues().get(previewIndex);
    assertTrue(previewPayload instanceof Map<?, ?>);
    assertTrue(((Map<?, ?>) previewPayload).containsKey("model"));
    assertEquals("draft", ((Map<?, ?>) previewPayload).get("phase"));
    assertTrue(
        payloadCaptor.getAllValues().stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .anyMatch(payload -> "validated".equals(payload.get("phase"))));
  }

  @Test
  void replacesDuplicatePlannerUuidIdsWithBackendGeneratedIds() {
    String plannerId = "a1b2c3d4-e5f6-4789-abcd-ef1234567890";
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    plannerId,
                    "Function",
                    JsonNodeFactory.instance.objectNode().put("name", "Book appointment"),
                    null,
                    null),
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    plannerId,
                    "Function",
                    JsonNodeFactory.instance.objectNode().put("name", "Cancel appointment"),
                    null,
                    null)));
    when(provider.planMutationTurn(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AgentLoopResult(
                new AssistantTurnPlan(
                    AssistantTurnPlan.Intent.MUTATION,
                    AssistantTurnPlan.Kind.PATCH,
                    "Created appointment functions.",
                    List.of(),
                    patch),
                0,
                1));

    AssistantOrchestrator.AssistantTurnResponse response =
        orchestrator.handleMessage(user, "session", request("Create appointment functions"));

    List<String> ids =
        response.proposal().patch().operations().stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .map(SemanticModelPatch.Operation::targetElementId)
            .toList();
    assertTrue(ids.size() >= 2);
    assertEquals(ids.size(), ids.stream().distinct().count());
    assertTrue(ids.stream().noneMatch(plannerId::equals));
    assertTrue(
        ids.stream().allMatch(id -> java.util.UUID.fromString(id).toString().equalsIgnoreCase(id)));
  }

  private AssistantOrchestrator.AssistantTurnRequest request(String message) {
    return new AssistantOrchestrator.AssistantTurnRequest(
        message, null, null, "pim", List.of(), null);
  }

  private SemanticModelPatch shallowCimPatch() {
    return new SemanticModelPatch(
        List.of(
            add("goal-1", "BusinessGoal", "Improve clinic intake"),
            add("actor-1", "Actor", "Patient"),
            add("entity-1", "DomainEntity", "Appointment"),
            add("command-1", "Command", "Book appointment"),
            add("event-1", "BusinessEvent", "Appointment booked")));
  }

  private SemanticModelPatch fullCimCoveragePatch() {
    List<SemanticModelPatch.Operation> operations = new java.util.ArrayList<>();
    operations.add(add("goal-1", "BusinessGoal", "Reduce phone scheduling traffic"));
    operations.add(add("stakeholder-1", "Stakeholder", "Clinic Operations"));
    operations.add(add("actor-1", "Actor", "Patient"));
    operations.add(add("actor-2", "Actor", "Scheduler"));
    operations.add(add("role-1", "Role", "Appointment requester"));
    operations.add(add("req-1", "Requirement", "Search available appointments"));
    operations.add(add("entity-1", "DomainEntity", "Appointment"));
    operations.add(add("entity-2", "DomainEntity", "Appointment Slot"));
    operations.add(add("aggregate-1", "AggregateCandidate", "Appointment booking"));
    operations.add(add("info-1", "InformationItem", "Patient identity"));
    operations.add(add("info-2", "InformationItem", "Visit reason"));
    operations.add(add("command-1", "Command", "Search appointments"));
    operations.add(add("command-2", "Command", "Book appointment"));
    operations.add(add("query-1", "Query", "Available appointment search"));
    operations.add(add("event-1", "BusinessEvent", "Appointment searched"));
    operations.add(add("event-2", "BusinessEvent", "Appointment booked"));
    operations.add(add("policy-1", "Policy", "Appointment hold expires after ten minutes"));
    operations.add(add("process-1", "BusinessProcess", "Book appointment journey"));
    operations.add(add("risk-1", "Risk", "Protected health information exposure"));
    operations.add(add("assumption-1", "Assumption", "Clinic policies are available online"));
    operations.add(add("hotspot-1", "Hotspot", "Provider schedule conflict resolution"));
    for (int index = 2; index <= 30; index++) {
      operations.add(add("req-" + index, "Requirement", "Clinic story requirement " + index));
    }
    for (int index = 3; index <= 12; index++) {
      operations.add(add("entity-" + index, "DomainEntity", "Clinic domain entity " + index));
    }
    for (int index = 3; index <= 9; index++) {
      operations.add(add("info-" + index, "InformationItem", "Clinic information item " + index));
    }
    for (int index = 3; index <= 8; index++) {
      operations.add(add("command-" + index, "Command", "Clinic command " + index));
      operations.add(add("event-" + index, "BusinessEvent", "Clinic event " + index));
    }
    operations.add(connect("actor-1", "role-1", "Actor", "playsRoles"));
    operations.add(connect("stakeholder-1", "goal-1", "Stakeholder", "ownsGoals"));
    operations.add(connect("stakeholder-1", "req-1", "Stakeholder", "providesRequirements"));
    operations.add(connect("req-1", "goal-1", "Requirement", "supportsGoals"));
    operations.add(connect("command-1", "actor-1", "Command", "issuedBy"));
    operations.add(connect("command-2", "actor-1", "Command", "issuedBy"));
    operations.add(connect("query-1", "actor-1", "Query", "issuedBy"));
    operations.add(connect("query-1", "entity-1", "Query", "reads"));
    operations.add(connect("command-2", "event-2", "Command", "expectedEvents"));
    operations.add(connect("policy-1", "command-2", "Policy", "guards"));
    operations.add(connect("policy-1", "event-2", "Policy", "triggeredBy"));
    operations.add(connect("process-1", "command-2", "BusinessProcess", "triggeringCommand"));
    return new SemanticModelPatch(operations);
  }

  private SemanticModelPatch.Operation add(String id, String type, String name) {
    return new SemanticModelPatch.Operation(
        SemanticModelPatch.OperationType.ADD_ELEMENT,
        id,
        type,
        JsonNodeFactory.instance.objectNode().put("name", name).put("description", name),
        null,
        null);
  }

  private SemanticModelPatch.Operation connect(
      String sourceId, String targetId, String sourceType, String referenceName) {
    return new SemanticModelPatch.Operation(
        SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
        targetId,
        sourceType,
        null,
        sourceId,
        referenceName);
  }

  private String fullCimSourceDocument() {
    return """
    # Community Clinic Appointment Portal - User Story Requirements

    The Community Clinic Network wants a patient-facing appointment portal for primary-care
    visits, vaccinations, lab follow-ups, and telehealth consultations. The system must reduce
    phone traffic, protect patient information, and keep clinic staff in control of provider
    schedules.

    ## Goals
    - Patients can find and book appointments without calling the clinic.
    - Clinic schedulers can manage capacity, blocked times, and provider availability.
    - Providers can review appointment context before the visit.
    - Compliance staff can audit access to protected health information.

    ## User Stories
    ### US-01 Search Available Appointments
    As a patient, I want to search available appointment slots by clinic, visit reason,
    provider, and date range so that I can choose a time that fits my needs.
    Acceptance criteria:
    - Search results show clinic location, provider name, visit type, earliest start time, and
      whether telehealth is available.
    - Patients can filter by language preference and accessibility needs.
    - Slots already held or booked are not returned.

    ### US-02 Book Appointment
    As a patient, I want to book a selected slot so that the clinic reserves the time for me.
    Acceptance criteria:
    - The portal captures patient identity, visit reason, contact preference, and insurance.
    - The selected slot is held for 10 minutes during confirmation.
    - A booking confirmation is created only when the patient accepts clinic policies.
    - The patient receives a confirmation notification.

    ### US-03 Manage Provider Availability
    As a clinic scheduler, I want to manage provider availability, blocked time, and capacity.
    Acceptance criteria:
    - Schedulers can publish provider templates, close blocks, and override capacity.
    - Patients cannot book blocked or over-capacity slots.
    - Schedule changes emit events for notifications and audit.

    ### US-04 Review Appointment Context
    As a provider, I want appointment context before the visit.
    Acceptance criteria:
    - Providers see visit reason, patient preferences, telehealth flag, and lab follow-up data.
    - Context hides information the provider is not authorized to view.

    ### US-05 Audit PHI Access
    As a compliance officer, I want to audit protected health information access.
    Acceptance criteria:
    - Every read of patient identity, appointment context, and contact details is logged.
    - Risk: privacy exposure if audit events are missing.
    - Assumption: clinic identity provider supplies staff roles.
    - Hotspot: provider schedule conflicts need domain policy review.
    """;
  }
}
