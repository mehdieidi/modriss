package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.assistant.domain.AssistantWorkflowState;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CancellationRegistryTest {

  @Test
  void cancelMakesActiveTurnFailChecks() {
    CancellationRegistry registry = new CancellationRegistry();
    registry.start("session", "turn", "key", Instant.now().plusSeconds(60));

    assertTrue(registry.cancel("session").isPresent());
    PlatformException failure =
        assertThrows(PlatformException.class, () -> registry.check("session", "turn"));

    assertEquals(499, failure.status());
  }

  @Test
  void cachesTerminalResponseByIdempotencyKey() {
    CancellationRegistry registry = new CancellationRegistry();
    registry.start("session", "turn", "key", Instant.now().plusSeconds(60));
    AssistantOrchestrator.AssistantTurnResponse response =
        new AssistantOrchestrator.AssistantTurnResponse(
            "Done", "model", 2L, null, List.of(), AssistantWorkflowState.APPLIED);

    registry.complete("session", "turn", response);

    assertEquals(response, registry.terminalResponse("key").orElseThrow());
  }
}
