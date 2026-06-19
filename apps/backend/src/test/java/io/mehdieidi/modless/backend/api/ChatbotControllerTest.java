package io.mehdieidi.modless.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import io.mehdieidi.modless.backend.assistant.AssistantOrchestrator;
import io.mehdieidi.modless.backend.assistant.AssistantRealtimeHub;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import io.mehdieidi.modless.platform.project.application.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatbotControllerTest {

  @Mock private AssistantOrchestrator assistant;

  @Mock private AssistantRealtimeHub realtime;

  @Mock private AuthSupport auth;

  @Mock private ProjectService projects;

  @Test
  void createSessionRejectsMissingProjectIdBeforeProjectLookup() {
    ChatbotController controller = new ChatbotController(assistant, realtime, auth, projects);

    PlatformException ex =
        assertThrows(
            PlatformException.class,
            () ->
                controller.createSession(
                    "token",
                    new ChatbotController.CreateSessionRequest("pim", "Assistant", null, null)));

    assertEquals(400, ex.status());
    assertEquals("Project id is required.", ex.getMessage());
    verifyNoInteractions(auth, projects, assistant);
  }
}
