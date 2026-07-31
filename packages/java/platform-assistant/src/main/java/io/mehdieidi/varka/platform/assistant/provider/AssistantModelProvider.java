package io.mehdieidi.varka.platform.assistant.provider;

import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import java.util.List;

/** Provider-neutral boundary for assistant model calls. */
public interface AssistantModelProvider {

  /**
   * Returns provider metadata used for audit records and diagnostics.
   *
   * @return metadata
   */
  AssistantProviderMetadata metadata();

  /** Returns provider capability limits used by the agent loop and provider transport. */
  default ProviderCapabilityProfile capabilities() {
    return ProviderCapabilityProfile.standard();
  }

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
   * Structured assistant prompt.
   *
   * @param system compact system instructions
   * @param user user message
   * @param snippets compact retrieved context snippets
   */
  record AssistantPrompt(
      String system,
      String user,
      List<ContextSnippet> snippets,
      List<TypeContract> patchContracts) {

    public AssistantPrompt(String system, String user, List<ContextSnippet> snippets) {
      this(system, user, snippets, List.of());
    }

    /** Applies immutable collection semantics. */
    public AssistantPrompt {
      system = system == null ? "" : system;
      user = user == null ? "" : user;
      snippets = snippets == null ? List.of() : List.copyOf(snippets);
      patchContracts = patchContracts == null ? List.of() : List.copyOf(patchContracts);
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
  record AssistantReply(
      String content,
      String provider,
      String model,
      TokenUsage usage,
      String systemPrompt,
      String userPrompt) {
    public AssistantReply(String content, String provider, String model, TokenUsage usage) {
      this(content, provider, model, usage, null, null);
    }

    public AssistantReply(String content, String provider, String model) {
      this(content, provider, model, TokenUsage.unavailable(), null, null);
    }
  }

  /**
   * Provider-reported usage for a single call. Negative values mean the provider did not report it.
   */
  record TokenUsage(long promptTokens, long completionTokens) {
    public static TokenUsage unavailable() {
      return new TokenUsage(-1, -1);
    }

    public boolean reported() {
      return promptTokens >= 0 || completionTokens >= 0;
    }
  }

  /**
   * Provider metadata.
   *
   * @param provider provider key
   * @param baseUrl API base URL
   * @param proxy proxy description
   */
  record AssistantProviderMetadata(String provider, String baseUrl, String proxy) {}

  /** Conservative provider-specific limits. */
  record ProviderCapabilityProfile(
      int maxCompletionTokens,
      int maxPatchCreates,
      int maxPatchConnections,
      int maxPatchEvidence,
      int maxContractCount,
      boolean nativeToolsPreferred,
      boolean forcedToolChoiceReliable) {
    public ProviderCapabilityProfile {
      maxCompletionTokens = maxCompletionTokens <= 0 ? 4096 : maxCompletionTokens;
      maxPatchCreates = maxPatchCreates <= 0 ? 12 : maxPatchCreates;
      maxPatchConnections = maxPatchConnections <= 0 ? 18 : maxPatchConnections;
      maxPatchEvidence = maxPatchEvidence <= 0 ? 12 : maxPatchEvidence;
      maxContractCount = maxContractCount <= 0 ? 8 : maxContractCount;
    }

    public static ProviderCapabilityProfile standard() {
      return new ProviderCapabilityProfile(4096, 12, 18, 12, 8, false, true);
    }

    public static ProviderCapabilityProfile conservative() {
      return new ProviderCapabilityProfile(2048, 3, 4, 3, 2, true, false);
    }
  }
}
