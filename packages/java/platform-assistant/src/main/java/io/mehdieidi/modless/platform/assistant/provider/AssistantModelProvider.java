package io.mehdieidi.modless.platform.assistant.provider;

import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
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

  /**
   * Completes one structured-output prompt without tool callbacks. Providers may override this to
   * use native JSON/schema response settings; the default preserves compatibility.
   *
   * @param prompt structured prompt request
   * @return provider response
   */
  default AssistantReply completeStructured(AssistantPrompt prompt) {
    return complete(prompt);
  }

  /**
   * Completes one prompt with whitelisted model-safe tools enabled.
   *
   * @param prompt prompt request
   * @return provider response
   */
  default AssistantReply completeWithTools(AssistantPrompt prompt) {
    return complete(prompt);
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
