package io.mehdieidi.varka.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.mehdieidi.varka.backend.assistant.AssistantRealtimeHub;
import io.mehdieidi.varka.backend.upload.UploadService;
import io.mehdieidi.varka.platform.assistant.application.AgenticAssistantFacade;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.project.application.ProjectService;
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
