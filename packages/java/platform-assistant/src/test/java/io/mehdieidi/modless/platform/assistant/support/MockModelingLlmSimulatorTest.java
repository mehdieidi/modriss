package io.mehdieidi.modless.platform.assistant.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
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
            .thenPlan(
                new AssistantTurnPlan(
                    AssistantTurnPlan.Intent.MUTATION,
                    AssistantTurnPlan.Kind.PATCH,
                    "Prepared CIM patch.",
                    List.of(),
                    new SemanticModelPatch(List.of())))
            .loopMetrics(4, 2);
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
    AssistantModelProvider.AgentLoopResult planned =
        simulator.planMutationTurn(
            new AssistantModelProvider.AssistantPrompt(
                AssistantModelRole.PLANNER, "Plan", "Create CIM", List.of()),
            (stage, message) -> progress.add(stage + ":" + message));

    assertTrue(analysis.content().contains("Register patient"));
    assertEquals(AssistantTurnPlan.Kind.PATCH, planned.plan().kind());
    assertEquals(4, planned.toolCalls());
    assertEquals(2, planned.steps());
    assertEquals(1, simulator.sourceAnalysisPrompts().size());
    assertEquals(1, simulator.planningPrompts().size());
    assertTrue(progress.stream().anyMatch(item -> item.startsWith("ANALYZING_SOURCE")));
    assertTrue(progress.stream().anyMatch(item -> item.startsWith("PLANNING")));
  }
}
