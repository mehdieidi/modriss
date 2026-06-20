package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
  private AssistantOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    AiProperties properties =
        new AiProperties(true, null, null, null, 64, 6000, null, null, null, null, null, null);
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
    var attributes = new ObjectMapper().readTree("{\"name\":\"Submit order\"}");
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
                new SemanticModelPatch(List.of())));

    var response = orchestrator.handleMessage(user, "session", request("make it"));

    assertEquals(AssistantWorkflowState.WAITING_FOR_CHOICE, response.workflowState());
    assertNull(response.proposal());
  }

  private AssistantOrchestrator.AssistantTurnRequest request(String message) {
    return new AssistantOrchestrator.AssistantTurnRequest(
        message, null, null, "pim", List.of(), null);
  }
}
