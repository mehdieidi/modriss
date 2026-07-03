package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.assistant.domain.AssistantWorkflowState;
import io.mehdieidi.modless.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.modless.platform.assistant.spi.AssistantTurnExecutionStore;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssistantTurnCoordinatorTest {

  @Test
  void startsExecutionWithDeadlineAndPropagatesCancel() {
    CancellationRegistry cancellations = new CancellationRegistry();
    CapturingExecutionStore store = new CapturingExecutionStore();
    AssistantTurnCoordinator coordinator = new AssistantTurnCoordinator(cancellations, store);
    AssistantSessionStore.AssistantSession session = session();

    AssistantTurnCoordinator.StartedTurn started =
        coordinator.start(session, "model-1", 7L, "key-1", Duration.ofMinutes(5));

    assertFalse(started.turnId().isBlank());
    assertTrue(started.deadlineAt().isAfter(Instant.now()));
    assertEquals(started.turnId(), store.execution.turnId());
    assertEquals("key-1", store.execution.idempotencyKey());
    assertEquals("model-1", store.execution.modelId());
    assertEquals(Optional.of(started.turnId()), coordinator.cancel(session.id()));
    assertEquals(started.turnId(), store.cancelRequestedTurnId);
  }

  @Test
  void replaysTerminalResponsesFromExecutionStoreBeforeRegistry() {
    CancellationRegistry cancellations = new CancellationRegistry();
    CapturingExecutionStore store = new CapturingExecutionStore();
    AssistantOrchestrator.AssistantTurnResponse response =
        new AssistantOrchestrator.AssistantTurnResponse(
            "Done", "model", 2L, null, List.of(), AssistantWorkflowState.APPLIED);
    store.terminal = response;
    AssistantTurnCoordinator coordinator = new AssistantTurnCoordinator(cancellations, store);

    assertEquals(response, coordinator.terminalResponse("key-1").orElseThrow());
  }

  private AssistantSessionStore.AssistantSession session() {
    return new AssistantSessionStore.AssistantSession(
        "session", "user", "project", ModelLevel.PIM, "Orders", Instant.now(), Instant.now());
  }

  private static final class CapturingExecutionStore implements AssistantTurnExecutionStore {
    private TurnExecution execution;
    private String cancelRequestedTurnId;
    private AssistantOrchestrator.AssistantTurnResponse terminal;

    @Override
    public Optional<AssistantOrchestrator.AssistantTurnResponse> terminalResponse(
        String idempotencyKey) {
      return Optional.ofNullable(terminal);
    }

    @Override
    public void start(TurnExecution execution) {
      this.execution = execution;
    }

    @Override
    public void requestCancel(String turnId) {
      this.cancelRequestedTurnId = turnId;
    }
  }
}
