package io.mehdieidi.modless.platform.assistant.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockModelingLlmSimulatorTest {

  @Test
  void simulatesSourceAnalysisAndPlanningWithoutNetwork() {
    MockModelingLlmSimulator simulator =
        new MockModelingLlmSimulator()
            .sourceAnalysis("Commands: Register patient. Events: Patient registered.")
            .thenStructuredReply(
                """
                {
                  "intent": "MUTATION",
                  "kind": "MODEL_DELTA",
                  "message": "Prepared CIM delta.",
                  "questions": [],
                  "elements": [],
                  "references": [],
                  "attributeUpdates": [],
                  "deletions": [],
                  "assumptions": []
                }
                """);
    List<String> progress = new ArrayList<>();

    AssistantModelProvider.AssistantReply analysis =
        simulator.analyzeSource(
            new AssistantModelProvider.AssistantPrompt(
                AssistantModelRole.SOURCE_ANALYST,
                "Analyze source",
                "Create CIM",
                List.of(
                    new AssistantModelProvider.ContextSnippet(
                        "user-attachment", "notes.md", "Register patient"))),
            (stage, message) -> progress.add(stage + ":" + message));
    AssistantModelProvider.AssistantReply planned =
        simulator.completeStructured(
            new AssistantModelProvider.AssistantPrompt(
                AssistantModelRole.PLANNER, "Plan", "Create CIM", List.of()));

    assertTrue(analysis.content().contains("Register patient"));
    assertTrue(planned.content().contains("MODEL_DELTA"));
    assertEquals(1, simulator.sourceAnalysisPrompts().size());
    assertEquals(1, simulator.planningPrompts().size());
    assertTrue(progress.stream().anyMatch(item -> item.startsWith("ANALYZING_SOURCE")));
  }
}
