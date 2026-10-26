package io.mehdieidi.modriss.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.mehdieidi.modriss.backend.assistant.AssistantRealtimeHub;
import io.mehdieidi.modriss.backend.upload.UploadService;
import io.mehdieidi.modriss.platform.assistant.application.AgenticAssistantFacade;
import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.project.application.ProjectService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatbotControllerTest {

  @Mock private AgenticAssistantFacade assistant;

  @Mock private AssistantRealtimeHub realtime;

  @Mock private AuthSupport auth;

  @Mock private ProjectService projects;

  @Mock private UploadService uploads;

  @Test
  void confirmationKeepsTheStoredProposalAndItsRevision() {
    var turns =
        org.mockito.Mockito.mock(
            io.mehdieidi.modriss.platform.assistant.turn.AssistantTurnStore.class);
    var user =
        new UserRecord(
            "user-1",
            "assistant@example.test",
            "Assistant",
            "hash",
            "salt",
            Instant.EPOCH,
            Instant.EPOCH);
    when(auth.user("token")).thenReturn(user);
    var previous =
        new io.mehdieidi.modriss.platform.assistant.turn.AssistantTurn(
            "previous",
            "thread",
            user.id(),
            "project",
            io.mehdieidi.modriss.platform.kernel.ModelLevel.CIM,
            "model",
            17L,
            "key",
            "Delete the selected item",
            null,
            java.util.List.of(),
            io.mehdieidi.modriss.platform.assistant.turn.AssistantTurn.State.NEEDS_CONFIRMATION,
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(300),
            null,
            null,
            false,
            null,
            0,
            0,
            null,
            null,
            "Deletion requires turn confirmation.",
            3,
            1000,
            200);
    var plan =
        new tools.jackson.databind.ObjectMapper()
            .readTree(
                """
{"pendingModelBatch":{"baseRevision":17,"batch":{"creates":[],"updates":[],"connections":[],
"deletions":[{"elementId":"item","preconditionHash":"server-inspected-hash"}],"evidence":[],
"planSummary":"Delete item","turnComplete":true}}}
""");
    when(turns.find("previous")).thenReturn(java.util.Optional.of(previous));
    when(turns.workflow("previous"))
        .thenReturn(
            java.util.Optional.of(
                new io.mehdieidi.modriss.platform.assistant.turn.AssistantTurnStore.Workflow(
                    "previous", "PENDING_MODEL_BATCH", "NEEDS_CONFIRMATION", null, plan)));
    var controller = new ChatbotController(assistant, realtime, auth, projects, uploads, turns);
    var response = controller.confirmTurn("token", "previous");
    assertEquals(202, response.getStatusCode().value());
    var saved =
        org.mockito.ArgumentCaptor.forClass(
            io.mehdieidi.modriss.platform.assistant.turn.AssistantTurn.class);
    org.mockito.Mockito.verify(turns).create(saved.capture());
    assertEquals(17L, saved.getValue().expectedRevision());
    assertEquals(17L, saved.getValue().revision());
    var workflow =
        org.mockito.ArgumentCaptor.forClass(
            io.mehdieidi.modriss.platform.assistant.turn.AssistantTurnStore.Workflow.class);
    org.mockito.Mockito.verify(turns).saveWorkflow(workflow.capture());
    assertEquals(saved.getValue().id(), workflow.getValue().turnId());
    assertEquals(plan, workflow.getValue().plan());
    when(turns.activeForModel("model")).thenReturn(java.util.Optional.of(saved.getValue()));
    PlatformException busy =
        assertThrows(PlatformException.class, () -> controller.confirmTurn("token", "previous"));
    assertEquals(423, busy.status());
    org.mockito.Mockito.verify(turns).create(org.mockito.ArgumentMatchers.any());
    verifyNoInteractions(assistant);
  }

  @Test
  void createSessionRejectsMissingProjectIdBeforeProjectLookup() {
    ChatbotController controller =
        new ChatbotController(assistant, realtime, auth, projects, uploads);

    PlatformException ex =
        assertThrows(
            PlatformException.class,
            () ->
                controller.createSession(
                    "token",
                    new ChatbotController.CreateSessionRequest(
                        "pim", "Assistant", null, null, null, null)));

    assertEquals(400, ex.status());
    assertEquals("Project id is required.", ex.getMessage());
    verifyNoInteractions(auth, projects, assistant);
  }

  @Test
  void createSessionExplicitlyRejectsUnsupportedPsmAssistantLevel() {
    ChatbotController controller =
        new ChatbotController(assistant, realtime, auth, projects, uploads);
    UserRecord user =
        new UserRecord(
            "user-1",
            "assistant@example.test",
            "Assistant",
            "hash",
            "salt",
            Instant.EPOCH,
            Instant.EPOCH);
    when(auth.user("token")).thenReturn(user);

    PlatformException ex =
        assertThrows(
            PlatformException.class,
            () ->
                controller.createSession(
                    "token",
                    new ChatbotController.CreateSessionRequest(
                        "psm", "Assistant", null, "project-1", null, null)));

    assertEquals(422, ex.status());
    assertEquals("AI modeling is available only for CIM and PIM levels.", ex.getMessage());
    verifyNoInteractions(assistant);
  }
}
