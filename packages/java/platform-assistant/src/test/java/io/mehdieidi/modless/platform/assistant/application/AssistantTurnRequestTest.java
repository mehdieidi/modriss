package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantTurnRequestTest {

  @Test
  void preservesTheRootInstructionAcrossClarificationContinuations() {
    var initial =
        new AssistantOrchestrator.AssistantTurnRequest(
            "Create the order model.", null, null, "logical", List.of(), null);
    var continued =
        new AssistantOrchestrator.AssistantTurnRequest(
            "Continuation with one answer.",
            null,
            null,
            "logical",
            List.of(),
            null,
            initial.rootMessage());

    assertEquals("Create the order model.", continued.rootMessage());
  }
}
