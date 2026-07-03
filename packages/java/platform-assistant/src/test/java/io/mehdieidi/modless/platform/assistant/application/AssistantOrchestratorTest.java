package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.mehdieidi.modless.platform.assistant.spi.AssistantSourceEvidenceStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantToolBridge;
import io.mehdieidi.modless.platform.assistant.support.AssistantSettingsFixtures;
import io.mehdieidi.modless.platform.identity.domain.UserRecord;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import io.mehdieidi.modless.platform.model.application.ModelLockService;
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
  private final AssistantSourceEvidenceStore sourceEvidenceStore =
      mock(AssistantSourceEvidenceStore.class);
  private final JdbcAssistantModelContextIndex contexts = new JdbcAssistantModelContextIndex();
  private final AssistantRealtimePublisher realtime = mock(AssistantRealtimePublisher.class);
  private final ModelService models = mock(ModelService.class);
  private final ModelLockService modelLocks = new ModelLockService();
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
  private AssistantOrchestrator orchestrator;

  @BeforeEach
  void setUp() throws Exception {
    AssistantSettings properties = AssistantSettingsFixtures.defaults();
    when(provider.metadata())
        .thenReturn(new AssistantModelProvider.AssistantProviderMetadata("test", "", ""));
    when(provider.available()).thenReturn(true);
    when(provider.complete(any()))
        .thenReturn(new AssistantModelProvider.AssistantReply("Summary", "test", "model"));
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
    when(models.modelLocks()).thenReturn(modelLocks);
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
    when(models.get(eq(user), eq(ModelLevel.PIM), eq("model-1"))).thenReturn(createdModel);
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
            sourceEvidenceStore,
            new AssistantPatchCompiler(),
            new AssistantPatchCompleter(schemas),
            new AssistantValidationFeedbackResolver(schemas),
            new AssistantClarificationGate(),
            schemas,
            tools,
            metrics,
            new ObjectMapper(),
            realtime,
            new AssistantHardeningService(properties, null),
            models,
            projects,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
  }

  @Test
  void modelDeltaAnswerCompletesWithoutProposal() {
    when(provider.completeStructured(any()))
        .thenReturn(
            new AssistantModelProvider.AssistantReply(
                """
                {
                  "intent": "INFORMATION",
                  "kind": "ANSWER",
                  "message": "The model is empty.",
                  "questions": [],
                  "elements": [],
                  "references": [],
                  "attributeUpdates": [],
                  "deletions": [],
                  "assumptions": []
                }
                """,
                "test",
                "model"));

    AssistantOrchestrator.AssistantTurnResponse response =
        orchestrator.handleMessage(user, "session", request("Explain what this model does"));

    assertEquals(AssistantWorkflowState.EXPLAINED, response.workflowState());
    assertNull(response.proposal());
    verify(provider).completeStructured(any());
  }

  @Test
  void modelDeltaMutationAppliesValidatedPatch() {
    when(provider.completeStructured(any()))
        .thenReturn(
            new AssistantModelProvider.AssistantReply(
                """
                {
                  "intent": "MUTATION",
                  "kind": "MODEL_DELTA",
                  "message": "Created the order function.",
                  "questions": [],
                  "elements": [
                    {
                      "localId": "create-order",
                      "eClass": "Function",
                      "attributes": {"name": "Create order"},
                      "placement": {"ownerId": "root", "referenceName": "functions"}
                    }
                  ],
                  "references": [],
                  "attributeUpdates": [],
                  "deletions": [],
                  "assumptions": []
                }
                """,
                "test",
                "model"));

    AssistantOrchestrator.AssistantTurnResponse response =
        orchestrator.handleMessage(user, "session", request("Create an order function"));

    assertEquals(
        AssistantWorkflowState.APPLIED, response.workflowState(), response.assistantMessage());
    assertNotNull(response.proposal());
    assertTrue(
        response.proposal().patch().operations().stream()
            .anyMatch(
                operation ->
                    operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT
                        && "Function".equals(operation.elementType())));
    verify(models).patch(eq(user), eq(ModelLevel.PIM), eq("model-1"), anyString(), any(), eq(1L));
  }

  @Test
  void modelDeltaClarificationPersistsPendingChoice() {
    when(provider.completeStructured(any()))
        .thenReturn(
            new AssistantModelProvider.AssistantReply(
                """
{
  "intent": "MUTATION",
  "kind": "CLARIFICATION",
  "message": "I need one decision.",
  "questions": [
    {
      "id": "delivery",
      "prompt": "Which delivery guarantee is required?",
      "selectionMode": "SINGLE",
      "allowFreeText": true,
      "options": [
        {"id": "once", "label": "At least once", "description": "Allow deduplication."}
      ]
    }
  ],
  "elements": [],
  "references": [],
  "attributeUpdates": [],
  "deletions": [],
  "assumptions": []
}
""",
                "test",
                "model"));

    AssistantOrchestrator.AssistantTurnResponse response =
        orchestrator.handleMessage(user, "session", request("Design the delivery workflow"));

    assertEquals(AssistantWorkflowState.WAITING_FOR_CHOICE, response.workflowState());
    assertEquals(1, response.choices().size());
    verify(memory).savePendingInteraction(anyString(), any(), any());
    verify(models, never()).patch(any(), any(), anyString(), anyString(), any(), anyLong());
  }

  @Test
  void missingIdempotencyKeyIsRejectedWhenRequired() {
    PlatformException failure =
        assertThrows(
            PlatformException.class,
            () ->
                orchestrator.handleMessage(
                    user,
                    "session",
                    new AssistantOrchestrator.AssistantTurnRequest(
                        "Explain without an idempotency key", null, null, "pim", List.of(), null)));

    assertEquals(400, failure.status());
    verify(provider, never()).completeStructured(any());
  }

  @Test
  void cimAttachmentSourceEvidenceIsPersisted() {
    AssistantSessionStore.AssistantSession cimSession =
        new AssistantSessionStore.AssistantSession(
            "cim-session",
            "user",
            "project",
            ModelLevel.CIM,
            "Workshop",
            Instant.now(),
            Instant.now());
    when(sessions.require("cim-session", "user")).thenReturn(cimSession);
    when(memory.requireThread("cim-session"))
        .thenReturn(
            new ThreadRecord(
                "cim-session",
                "user",
                "project",
                ModelLevel.CIM,
                "Workshop",
                null,
                null,
                Instant.now(),
                Instant.now()));
    when(provider.analyzeSource(any(), any()))
        .thenReturn(
            new AssistantModelProvider.AssistantReply(
                """
                {
                  "sourceId": "workshop",
                  "facts": [
                    {
                      "id": "fact-1",
                      "chunkId": "chunk-1",
                      "kind": "SOURCE_NOTE",
                      "summary": "Customer requests appointment booking.",
                      "suggestedTypes": []
                    }
                  ],
                  "coverage": [
                    {"chunkId": "chunk-1", "state": "COMPRESSED", "note": "Compressed"}
                  ],
                  "gaps": []
                }
                """,
                "test",
                "model"));
    when(provider.completeStructured(any()))
        .thenReturn(
            new AssistantModelProvider.AssistantReply(
                """
                {
                  "intent": "INFORMATION",
                  "kind": "ANSWER",
                  "message": "I read the source.",
                  "questions": [],
                  "elements": [],
                  "references": [],
                  "attributeUpdates": [],
                  "deletions": [],
                  "assumptions": []
                }
                """,
                "test",
                "model"));

    AssistantOrchestrator.AssistantTurnResponse response =
        orchestrator.handleMessage(
            user,
            "cim-session",
            new AssistantOrchestrator.AssistantTurnRequest(
                "Read these notes",
                null,
                null,
                "cim",
                List.of(),
                null,
                "notes.md",
                "Customer requests appointment booking.",
                "source-key"));

    assertEquals(AssistantWorkflowState.EXPLAINED, response.workflowState());
    ArgumentCaptor<AssistantSourceEvidenceStore.SourceEvidenceRecord> record =
        ArgumentCaptor.forClass(AssistantSourceEvidenceStore.SourceEvidenceRecord.class);
    verify(sourceEvidenceStore).save(record.capture());
    assertEquals("cim-session", record.getValue().sessionId());
    assertTrue(record.getValue().sourceId().startsWith("cim-session:"));
    assertTrue(record.getValue().coverage().path("compressedChunks").asInt() > 0);
  }

  private AssistantOrchestrator.AssistantTurnRequest request(String message) {
    return new AssistantOrchestrator.AssistantTurnRequest(
        message,
        null,
        null,
        "pim",
        List.of(),
        null,
        "",
        "",
        "test-" + Math.abs(message.hashCode()));
  }
}
