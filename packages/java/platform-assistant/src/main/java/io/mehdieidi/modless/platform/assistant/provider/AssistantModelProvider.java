package io.mehdieidi.modless.platform.assistant.provider;

import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import java.util.List;

/** Provider-neutral boundary for assistant model calls. */
public interface AssistantModelProvider {

  /**
   * Returns provider metadata used for audit records and diagnostics.
   *
   * @return metadata
   */
  AssistantProviderMetadata metadata();

  /**
   * Returns whether the provider is locally callable.
   *
   * @return availability state
   */
  boolean available();

  /**
   * Completes one bounded assistant prompt.
   *
   * @param prompt prompt request
   * @return provider response
   */
  AssistantReply complete(AssistantPrompt prompt);

  /** Produces one structured answer, clarification, or semantic patch decision. */
  default AssistantTurnPlan planTurn(AssistantPrompt prompt) {
    AssistantReply reply = complete(prompt);
    return new AssistantTurnPlan(
        AssistantTurnPlan.Kind.ANSWER,
        reply.content(),
        List.of(),
        new SemanticModelPatch(List.of()));
  }

  /**
   * Produces a schema-converted semantic patch. Providers that do not support structured output
   * return an empty patch.
   *
   * @param prompt compact planner prompt
   * @return semantic patch
   */
  default SemanticModelPatch proposePatch(AssistantPrompt prompt) {
    return new SemanticModelPatch(List.of());
  }

  /**
   * Runs the bounded agentic planner loop for mutation turns.
   *
   * @param prompt planner prompt
   * @param progress progress callback
   * @return structured turn plan and loop metrics
   */
  default AgentLoopResult planMutationTurn(AssistantPrompt prompt, AgentProgress progress) {
    return new AgentLoopResult(planTurn(prompt), 0, 1);
  }

  /**
   * Produces a compact source-material analysis for requirements or event-storming documents.
   *
   * @param prompt source-analysis prompt
   * @param progress progress callback
   * @return source analysis text
   */
  default AssistantReply analyzeSource(AssistantPrompt prompt, AgentProgress progress) {
    if (progress != null) {
      progress.onProgress("ANALYZING_SOURCE", "Reading source material for modeling evidence");
    }
    return complete(prompt);
  }

  /** Progress callback for agent loop stages. */
  @FunctionalInterface
  interface AgentProgress {
    void onProgress(String stage, String message);
  }

  /**
   * Agent loop result with planner output and loop metrics.
   *
   * @param plan structured turn plan
   * @param toolCalls number of tool invocations
   * @param steps number of agent loop steps
   */
  record AgentLoopResult(AssistantTurnPlan plan, int toolCalls, int steps) {}

  /**
   * Structured assistant prompt.
   *
   * @param role model role to use
   * @param system compact system instructions
   * @param user user message
   * @param snippets compact retrieved context snippets
   */
  record AssistantPrompt(
      AssistantModelRole role, String system, String user, List<ContextSnippet> snippets) {

    /** Applies immutable collection semantics. */
    public AssistantPrompt {
      role = role == null ? AssistantModelRole.RESPONDER : role;
      system = system == null ? "" : system;
      user = user == null ? "" : user;
      snippets = snippets == null ? List.of() : List.copyOf(snippets);
    }
  }

  /**
   * Compact context passed to the provider.
   *
   * @param source source identifier
   * @param title display title
   * @param content bounded content
   */
  record ContextSnippet(String source, String title, String content) {}

  /**
   * Provider response.
   *
   * @param content assistant text
   * @param provider provider key
   * @param model model name
   */
  record AssistantReply(String content, String provider, String model) {}

  /**
   * Provider metadata.
   *
   * @param provider provider key
   * @param baseUrl API base URL
   * @param proxy proxy description
   */
  record AssistantProviderMetadata(String provider, String baseUrl, String proxy) {}
}
