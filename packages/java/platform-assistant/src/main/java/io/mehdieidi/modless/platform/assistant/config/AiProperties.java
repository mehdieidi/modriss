package io.mehdieidi.modless.platform.assistant.config;

import io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSettings;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Provider-neutral assistant settings for model selection and proxy routing.
 *
 * @param enabled whether outbound AI calls are allowed
 * @param provider configured provider key, currently {@code openai} or {@code gemini}
 * @param requestTimeout outbound AI request timeout
 * @param turnTimeout overall assistant turn timeout
 * @param maxToolCalls legacy operation/tool limit; zero or negative means unbounded
 * @param validationRepairAttempts maximum validator-guided replanning passes per mutation
 * @param tokenBudget approximate prompt budget per turn
 * @param maxContextSnippets maximum retrieved snippets passed to the provider per turn
 * @param maxSnippetChars maximum characters per retrieved snippet
 * @param maxSystemChars maximum characters in the system prompt
 * @param maxAgentSteps maximum agentic planner loop iterations per mutation turn
 * @param maxToolCallsPerStep maximum tool invocations allowed per agent loop step
 * @param reservedSchemaSnippets minimum retrieval slots reserved for tier-1 schema contracts
 * @param fallbackProvider optional provider used only after HTTP 429 from the configured provider
 * @param hardening rate-limit and circuit-breaker settings
 * @param embeddings local embedding settings
 * @param proxy AI-only outbound proxy settings
 * @param openaiCompatible OpenAI-compatible endpoint settings
 * @param gemini Google Gemini Developer API settings
 * @param models role-specific model names
 * @param maxRepairAttempts preferred maximum repair attempts; overrides validationRepairAttempts
 * @param maxPromptTokens maximum prompt budget per provider call
 * @param maxSourceChunkTokens maximum source chunk token budget
 * @param maxSourceChunksPerTurn maximum source chunks processed in one turn
 * @param requireIdempotencyKey whether client turn idempotency keys are required
 */
@ConfigurationProperties(prefix = "modless.ai")
public record AiProperties(
    boolean enabled,
    String provider,
    Duration requestTimeout,
    int maxToolCalls,
    int validationRepairAttempts,
    int tokenBudget,
    int maxContextSnippets,
    int maxSnippetChars,
    int maxSystemChars,
    int maxAgentSteps,
    int maxToolCallsPerStep,
    int reservedSchemaSnippets,
    String fallbackProvider,
    Hardening hardening,
    Embeddings embeddings,
    Proxy proxy,
    OpenAiCompatible openaiCompatible,
    Gemini gemini,
    Models models,
    Duration turnTimeout,
    int maxRepairAttempts,
    int maxPromptTokens,
    int maxSourceChunkTokens,
    int maxSourceChunksPerTurn,
    boolean requireIdempotencyKey)
    implements AssistantSettings {

  /** Applies conservative defaults for local development. */
  public AiProperties {
    provider = Provider.from(provider).key();
    requestTimeout = requestTimeout == null ? Duration.ofMinutes(5) : requestTimeout;
    turnTimeout = turnTimeout == null ? Duration.ofMinutes(5) : turnTimeout;
    maxToolCalls = maxToolCalls <= 0 ? 24 : maxToolCalls;
    validationRepairAttempts =
        maxRepairAttempts > 0
            ? maxRepairAttempts
            : validationRepairAttempts <= 0 ? 1 : validationRepairAttempts;
    maxRepairAttempts = validationRepairAttempts;
    tokenBudget = tokenBudget <= 0 ? 16000 : tokenBudget;
    maxPromptTokens = maxPromptTokens <= 0 ? 24000 : maxPromptTokens;
    maxSourceChunkTokens = maxSourceChunkTokens <= 0 ? 4000 : maxSourceChunkTokens;
    maxSourceChunksPerTurn = maxSourceChunksPerTurn <= 0 ? 24 : maxSourceChunksPerTurn;
    maxContextSnippets = maxContextSnippets <= 0 ? 24 : maxContextSnippets;
    maxSnippetChars = maxSnippetChars <= 0 ? 2400 : maxSnippetChars;
    maxSystemChars = maxSystemChars <= 0 ? 14000 : maxSystemChars;
    maxAgentSteps = maxAgentSteps <= 0 ? 8 : maxAgentSteps;
    maxToolCallsPerStep = maxToolCallsPerStep <= 0 ? 4 : maxToolCallsPerStep;
    reservedSchemaSnippets = reservedSchemaSnippets <= 0 ? 10 : reservedSchemaSnippets;
    fallbackProvider = fallbackProvider == null ? "" : fallbackProvider.trim();
    hardening =
        hardening == null
            ? new Hardening(
                30, Duration.ofMinutes(1), 3, Duration.ofMinutes(1), 2, Duration.ofMillis(250), 12)
            : hardening;
    embeddings =
        embeddings == null
            ? new Embeddings(null, null, null, null, null, false, -1, true)
            : embeddings;
    proxy = proxy == null ? new Proxy(false, ProxyType.HTTP, null, null, null) : proxy;
    openaiCompatible =
        openaiCompatible == null ? new OpenAiCompatible(null, null) : openaiCompatible;
    gemini = gemini == null ? new Gemini(null) : gemini;
    models = models == null ? new Models(null, null, null) : models;
  }

  /**
   * Resolves the configured provider key.
   *
   * @return configured provider enum
   */
  public Provider providerKind() {
    return Provider.from(provider);
  }

  /**
   * Resolves the optional fallback provider configured for rate-limit recovery.
   *
   * @return fallback provider when configured, otherwise empty
   */
  public Optional<Provider> fallbackProviderKind() {
    if (fallbackProvider == null || fallbackProvider.isBlank()) {
      return Optional.empty();
    }
    try {
      Provider resolved = Provider.from(fallbackProvider);
      return resolved == providerKind() ? Optional.empty() : Optional.of(resolved);
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
  }

  /**
   * Resolves the configured model for the currently selected provider.
   *
   * @param role assistant model role
   * @return configured or provider-default model name
   */
  public String modelFor(AssistantModelRole role) {
    return models.forRole(providerKind(), role);
  }

  private static String blankToDefault(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }

  private static String normalizeOpenAiCompatibleBaseUrl(String value) {
    String normalized = blankToDefault(value, "https://api.openai.com");
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (normalized.endsWith("/v1")) {
      return normalized.substring(0, normalized.length() - 3);
    }
    return normalized;
  }

  /** Supported assistant chat providers. */
  public enum Provider {
    /** OpenAI or any provider exposing an OpenAI-compatible chat API. */
    OPENAI("openai"),
    /** Google Gemini through the Gemini Developer API. */
    GEMINI("gemini");

    private final String key;

    Provider(String key) {
      this.key = key;
    }

    /**
     * Configuration key for this provider.
     *
     * @return provider key
     */
    public String key() {
      return key;
    }

    private static Provider from(String value) {
      String normalized = blankToDefault(value, "openai").toLowerCase(java.util.Locale.ROOT);
      if ("openai-compatible".equals(normalized) || "openai_compatible".equals(normalized)) {
        return OPENAI;
      }
      for (Provider provider : values()) {
        if (provider.key.equals(normalized)) {
          return provider;
        }
      }
      throw new IllegalArgumentException("Unsupported AI provider: " + value);
    }
  }

  /** Supported outbound proxy types. */
  public enum ProxyType {
    /** No proxy. */
    DIRECT,
    /** HTTP proxy, Nekoray default port 2081. */
    HTTP,
    /** SOCKS proxy, Nekoray default port 2082. */
    SOCKS
  }

  /** Local embedding providers. */
  public enum EmbeddingProvider {
    /** Spring AI ONNX sentence-transformer embeddings. */
    ONNX,
    /** Deterministic local hash embeddings for fallback and tests. */
    HASH
  }

  /**
   * Local embedding settings.
   *
   * @param provider embedding implementation
   * @param modelResource optional ONNX model resource URI/path
   * @param tokenizerResource optional tokenizer resource URI/path
   * @param modelOutputName optional ONNX output tensor name
   * @param cacheDirectory optional Spring AI transformers cache directory
   * @param disableCaching whether Spring AI transformers caching is disabled
   * @param gpuDeviceId optional GPU device, negative for CPU/default
   * @param fallbackToHash whether ONNX initialization failures fall back to hash vectors
   */
  public record Embeddings(
      EmbeddingProvider provider,
      String modelResource,
      String tokenizerResource,
      String modelOutputName,
      String cacheDirectory,
      boolean disableCaching,
      int gpuDeviceId,
      boolean fallbackToHash) {

    /** Applies local ONNX defaults with safe fallback. */
    public Embeddings {
      provider = provider == null ? EmbeddingProvider.ONNX : provider;
      modelResource = modelResource == null ? "" : modelResource.trim();
      tokenizerResource = tokenizerResource == null ? "" : tokenizerResource.trim();
      modelOutputName = modelOutputName == null ? "" : modelOutputName.trim();
      cacheDirectory = cacheDirectory == null ? "" : cacheDirectory.trim();
    }
  }

  /**
   * AI-only outbound proxy settings.
   *
   * @param enabled whether the proxy is used and health-checked
   * @param type proxy type
   * @param host proxy host
   * @param port proxy port
   * @param connectTimeout proxy socket check timeout
   */
  public record Proxy(
      boolean enabled, ProxyType type, String host, Integer port, Duration connectTimeout) {

    /** Applies local Nekoray defaults. */
    public Proxy {
      type = type == null ? ProxyType.HTTP : type;
      host = blankToDefault(host, "127.0.0.1");
      port = port == null || port <= 0 ? defaultPort(type) : port;
      connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
    }

    private static int defaultPort(ProxyType type) {
      return type == ProxyType.SOCKS ? 2082 : 2081;
    }

    /**
     * Returns this proxy as a socket address.
     *
     * @return proxy address
     */
    public InetSocketAddress address() {
      return new InetSocketAddress(host, port);
    }
  }

  /**
   * Production hardening settings.
   *
   * @param perUserRequestsPerWindow maximum requests per user and window
   * @param rateLimitWindow rate limit window
   * @param circuitFailureThreshold consecutive provider failures before opening circuit
   * @param circuitOpenDuration open-circuit duration
   * @param providerRetryAttempts provider retry attempts per call
   * @param retryBackoff retry backoff
   * @param recentMessageWindow recent durable messages included in prompt context
   */
  public record Hardening(
      int perUserRequestsPerWindow,
      Duration rateLimitWindow,
      int circuitFailureThreshold,
      Duration circuitOpenDuration,
      int providerRetryAttempts,
      Duration retryBackoff,
      int recentMessageWindow)
      implements AssistantSettings.Hardening {

    /** Applies conservative defaults. */
    public Hardening {
      perUserRequestsPerWindow = perUserRequestsPerWindow <= 0 ? 30 : perUserRequestsPerWindow;
      rateLimitWindow = rateLimitWindow == null ? Duration.ofMinutes(1) : rateLimitWindow;
      circuitFailureThreshold = circuitFailureThreshold <= 0 ? 3 : circuitFailureThreshold;
      circuitOpenDuration =
          circuitOpenDuration == null ? Duration.ofMinutes(1) : circuitOpenDuration;
      providerRetryAttempts = providerRetryAttempts <= 0 ? 2 : providerRetryAttempts;
      retryBackoff = retryBackoff == null ? Duration.ofMillis(250) : retryBackoff;
      recentMessageWindow = recentMessageWindow <= 0 ? 24 : recentMessageWindow;
    }
  }

  /**
   * OpenAI-compatible endpoint settings.
   *
   * @param baseUrl provider API base URL
   * @param apiKey provider API key
   */
  public record OpenAiCompatible(String baseUrl, String apiKey) {

    /** Applies OpenAI's API base URL when no compatible endpoint is configured. */
    public OpenAiCompatible {
      baseUrl = normalizeOpenAiCompatibleBaseUrl(baseUrl);
      apiKey = apiKey == null ? "" : apiKey.trim();
    }
  }

  /**
   * Google Gemini Developer API settings.
   *
   * @param apiKey provider API key
   */
  public record Gemini(String apiKey) {

    /** Applies empty key default. */
    public Gemini {
      apiKey = apiKey == null ? "" : apiKey.trim();
    }
  }

  /**
   * Role-specific model settings.
   *
   * @param planner model used for planning
   * @param responder model used for user-facing answers
   * @param summarizer model used for rolling summaries
   */
  public record Models(String planner, String responder, String summarizer) {

    /** Normalizes configured model names. */
    public Models {
      planner = planner == null ? "" : planner.trim();
      responder = responder == null ? "" : responder.trim();
      summarizer = summarizer == null ? "" : summarizer.trim();
    }

    /**
     * Resolves a model by assistant role.
     *
     * @param role assistant model role
     * @return configured model name
     */
    public String forRole(AssistantModelRole role) {
      return forRole(Provider.OPENAI, role);
    }

    /**
     * Resolves a model by assistant role and provider.
     *
     * @param provider assistant provider
     * @param role assistant model role
     * @return configured or provider-default model name
     */
    public String forRole(Provider provider, AssistantModelRole role) {
      Provider resolvedProvider = provider == null ? Provider.OPENAI : provider;
      return switch (role == null ? AssistantModelRole.RESPONDER : role) {
        case PLANNER -> blankToDefault(planner, defaultModel(resolvedProvider));
        case SOURCE_ANALYST -> blankToDefault(planner, defaultModel(resolvedProvider));
        case RESPONDER -> blankToDefault(responder, defaultModel(resolvedProvider));
        case SUMMARIZER -> blankToDefault(summarizer, defaultModel(resolvedProvider));
      };
    }

    private static String defaultModel(Provider provider) {
      return provider == Provider.GEMINI ? "gemini-2.0-flash" : "gpt-4o-mini";
    }
  }
}
