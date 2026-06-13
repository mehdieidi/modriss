package io.mehdieidi.modless.backend.assistant;

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
   * Produces a schema-converted semantic patch. Providers that do not support structured output
   * remain explain-only.
   *
   * @param prompt compact planner prompt
   * @return semantic patch
   */
  default SemanticModelPatch proposePatch(AssistantPrompt prompt) {
    return new SemanticModelPatch(List.of());
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
