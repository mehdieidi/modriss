package io.mehdieidi.modriss.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.mehdieidi.modriss.backend.assistant.AssistantRealtimeHub;
import io.mehdieidi.modriss.backend.upload.UploadService;
import io.mehdieidi.modriss.platform.assistant.application.AgenticAssistantFacade;
import io.mehdieidi.modriss.platform.assistant.turn.AssistantTurn;
import io.mehdieidi.modriss.platform.assistant.turn.AssistantTurnStore;
import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.project.application.ProjectService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatbotControllerTest {

  @Mock private AgenticAssistantFacade assistant;

  @Mock private AssistantRealtimeHub realtime;

  @Mock private AuthSupport auth;

  @Mock private ProjectService projects;

  @Mock private UploadService uploads;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void turnStatusReportsDurableTimingForTheCurrentRun(boolean resumed) {
    var turns = org.mockito.Mockito.mock(AssistantTurnStore.class);
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
    Instant start = resumed ? Instant.EPOCH.plusSeconds(100) : Instant.EPOCH;
    Instant end = start.plusSeconds(12);
    var events = new java.util.ArrayList<AssistantTurn.Event>();
    if (resumed) {
      events.add(
          new AssistantTurn.Event(
              1, "turn", 1, "turn.completed", Instant.EPOCH.plusSeconds(10), Map.of()));
      events.add(new AssistantTurn.Event(2, "turn", 2, "turn.resumed", start, Map.of()));
    }
    events.add(new AssistantTurn.Event(3, "turn", 3, "turn.completed", end, Map.of()));
    events.add(new AssistantTurn.Event(4, "turn", 4, "turn.audit", end.plusSeconds(50), Map.of()));
    when(turns.events("turn", 0)).thenReturn(events);
    var controller = new ChatbotController(assistant, realtime, auth, projects, uploads, turns);
    for (var state : AssistantTurn.State.values()) {
      when(turns.find("turn")).thenReturn(Optional.of(timedTurn(user.id(), state)));
      var response = controller.turn("token", "turn");
      assertEquals((state.terminal() ? start : Instant.EPOCH).toString(), response.startedAt());
      if (state.terminal()) assertEquals(end.toString(), response.completedAt());
      else
        assertNull(response.completedAt(), "Running turns must ignore previous completion events");
    }
  }

  private AssistantTurn timedTurn(String userId, AssistantTurn.State state) {
    return new AssistantTurn(
        "turn",
        "thread",
        userId,
        "project",
        ModelLevel.CIM,
        "model",
        1L,
        "key",
        "Update the model",
        null,
        List.of(),
        state,
        Instant.EPOCH,
        Instant.EPOCH.plusSeconds(300),
        null,
        null,
        false,
        2L,
        0,
        0,
        null,
        null,
        "Done",
        1,
        100,
        20);
  }

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
