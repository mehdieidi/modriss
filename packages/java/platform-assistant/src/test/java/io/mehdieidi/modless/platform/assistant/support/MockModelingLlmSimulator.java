package io.mehdieidi.modless.platform.assistant.support;

import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/** Scripted no-network LLM simulator for assistant orchestration tests. */
public final class MockModelingLlmSimulator implements AssistantModelProvider {

  private final Queue<AssistantTurnPlan> plans = new ArrayDeque<>();
  private final List<AssistantPrompt> sourceAnalysisPrompts = new ArrayList<>();
  private final List<AssistantPrompt> planningPrompts = new ArrayList<>();
  private String sourceAnalysis = "";
  private int toolCalls;
  private int steps = 1;

  public MockModelingLlmSimulator sourceAnalysis(String value) {
    this.sourceAnalysis = value == null ? "" : value;
    return this;
  }

  public MockModelingLlmSimulator thenPlan(AssistantTurnPlan plan) {
    plans.add(plan);
    return this;
  }

  public MockModelingLlmSimulator loopMetrics(int toolCalls, int steps) {
    this.toolCalls = Math.max(0, toolCalls);
    this.steps = Math.max(1, steps);
    return this;
  }

  public List<AssistantPrompt> sourceAnalysisPrompts() {
    return List.copyOf(sourceAnalysisPrompts);
  }

  public List<AssistantPrompt> planningPrompts() {
    return List.copyOf(planningPrompts);
  }

  @Override
  public AssistantProviderMetadata metadata() {
    return new AssistantProviderMetadata("mock-simulator", "memory://mock", "direct");
  }

  @Override
  public boolean available() {
    return true;
  }

  @Override
  public AssistantReply complete(AssistantPrompt prompt) {
    return new AssistantReply("", "mock-simulator", "mock-model");
  }

  @Override
  public AssistantReply analyzeSource(AssistantPrompt prompt, AgentProgress progress) {
    sourceAnalysisPrompts.add(prompt);
    if (progress != null) {
      progress.onProgress("ANALYZING_SOURCE", "Mock source analysis completed");
    }
    return new AssistantReply(sourceAnalysis, "mock-simulator", "mock-model");
  }

  @Override
  public AgentLoopResult planMutationTurn(AssistantPrompt prompt, AgentProgress progress) {
    planningPrompts.add(prompt);
    if (progress != null) {
      progress.onProgress("PLANNING", "Mock planning completed");
    }
    AssistantTurnPlan plan =
        plans.isEmpty()
            ? new AssistantTurnPlan(
                AssistantTurnPlan.Kind.ANSWER,
                "Mock simulator has no scripted plan.",
                List.of(),
                new SemanticModelPatch(List.of()))
            : plans.remove();
    return new AgentLoopResult(plan, toolCalls, steps);
  }
}
