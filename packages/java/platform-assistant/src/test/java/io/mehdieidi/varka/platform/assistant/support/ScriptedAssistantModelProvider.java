package io.mehdieidi.varka.platform.assistant.support;

import io.mehdieidi.varka.platform.assistant.application.ProviderCallBudget;
import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic, ordered provider script for workflow, repair, restart, and UI tests. */
public final class ScriptedAssistantModelProvider implements AssistantModelProvider {
  private final ArrayDeque<Step> script;
  private final List<AssistantPrompt> prompts = new ArrayList<>();

  public ScriptedAssistantModelProvider(List<Step> script) {
    this.script = new ArrayDeque<>(script == null ? List.of() : script);
  }

  public static Step reply(String content) {
    return new Reply(content, new TokenUsage(0, 0));
  }

  public static Step reply(String content, long promptTokens, long completionTokens) {
    return new Reply(content, new TokenUsage(promptTokens, completionTokens));
  }

  public static Step failure(RuntimeException failure) {
    return new Failure(failure);
  }

  @Override
  public AssistantProviderMetadata metadata() {
    return new AssistantProviderMetadata("scripted", "test://scripted", "none");
  }

  @Override
  public boolean available() {
    return true;
  }

  @Override
  public AssistantReply complete(AssistantPrompt prompt) {
    prompts.add(prompt);
    ProviderCallBudget.consume(prompt.role());
    Step step = script.pollFirst();
    if (step == null) throw new IllegalStateException("Scripted provider has no remaining step.");
    return step.execute();
  }

  public List<AssistantPrompt> prompts() {
    return List.copyOf(prompts);
  }

  public int remainingSteps() {
    return script.size();
  }

  /** One scripted provider outcome. */
  public sealed interface Step permits Reply, Failure {
    AssistantReply execute();
  }

  private record Reply(String content, TokenUsage usage) implements Step {
    @Override
    public AssistantReply execute() {
      return new AssistantReply(Objects.requireNonNull(content), "scripted", "scripted", usage);
    }
  }

  private record Failure(RuntimeException cause) implements Step {
    @Override
    public AssistantReply execute() {
      throw Objects.requireNonNull(cause);
    }
  }
}
