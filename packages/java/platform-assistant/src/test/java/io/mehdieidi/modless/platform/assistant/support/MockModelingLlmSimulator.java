package io.mehdieidi.modless.platform.assistant.support;

import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Scripted no-network LLM simulator for assistant orchestration tests. */
public final class MockModelingLlmSimulator implements AssistantModelProvider {

  private final Queue<String> structuredReplies = new ArrayDeque<>();
  private final List<AssistantPrompt> sourceAnalysisPrompts = new ArrayList<>();
  private final List<AssistantPrompt> planningPrompts = new ArrayList<>();
  private String sourceAnalysis = "";
  private PlatformException structuredFailure;
  private CountDownLatch structuredBarrier;
  private volatile boolean structuredCallEntered;

  public MockModelingLlmSimulator sourceAnalysis(String value) {
    this.sourceAnalysis = value == null ? "" : value;
    return this;
  }

  public MockModelingLlmSimulator thenStructuredReply(String reply) {
    structuredReplies.add(reply == null ? "" : reply);
    return this;
  }

  public MockModelingLlmSimulator thenStructuredFailure(PlatformException failure) {
    this.structuredFailure = failure;
    return this;
  }

  public MockModelingLlmSimulator blockStructuredUntilReleased() {
    this.structuredBarrier = new CountDownLatch(1);
    return this;
  }

  public void releaseStructured() {
    if (structuredBarrier != null) {
      structuredBarrier.countDown();
    }
  }

  public boolean structuredCallEntered() {
    return structuredCallEntered;
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
  public AssistantReply completeStructured(AssistantPrompt prompt) {
    structuredCallEntered = true;
    awaitStructuredBarrier();
    if (structuredFailure != null) {
      throw structuredFailure;
    }
    planningPrompts.add(prompt);
    String reply =
        structuredReplies.isEmpty()
            ? """
            {
              "intent": "INFORMATION",
              "kind": "ANSWER",
              "message": "Mock simulator has no scripted reply.",
              "questions": [],
              "elements": [],
              "references": [],
              "attributeUpdates": [],
              "deletions": [],
              "assumptions": []
            }
            """
            : structuredReplies.remove();
    return new AssistantReply(reply, "mock-simulator", "mock-model");
  }

  @Override
  public AssistantReply analyzeSource(AssistantPrompt prompt, AgentProgress progress) {
    sourceAnalysisPrompts.add(prompt);
    if (progress != null) {
      progress.onProgress("ANALYZING_SOURCE", "Mock source analysis completed");
    }
    return new AssistantReply(sourceAnalysis, "mock-simulator", "mock-model");
  }

  private void awaitStructuredBarrier() {
    if (structuredBarrier == null) {
      return;
    }
    try {
      if (!structuredBarrier.await(30, TimeUnit.SECONDS)) {
        throw new PlatformException(504, "Mock structured barrier timed out.");
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new PlatformException(499, "Mock structured barrier interrupted.");
    }
  }
}
