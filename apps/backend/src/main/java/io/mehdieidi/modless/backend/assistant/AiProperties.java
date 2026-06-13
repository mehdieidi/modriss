package io.mehdieidi.modless.backend.assistant;

import java.net.InetSocketAddress;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Provider-neutral assistant settings for model selection, proxy routing, and rollout mode.
 *
 * @param enabled whether outbound AI calls are allowed
 * @param mode assistant rollout mode
 * @param provider configured provider key, currently {@code openai} or {@code gemini}
 * @param requestTimeout outbound AI request timeout
 * @param maxToolCalls maximum tool calls per assistant turn
 * @param tokenBudget approximate prompt budget per turn
 * @param hardening rate-limit and circuit-breaker settings
 * @param embeddings local embedding settings
 * @param proxy AI-only outbound proxy settings
 * @param openaiCompatible OpenAI-compatible endpoint settings
 * @param gemini Google Gemini Developer API settings
 * @param models role-specific model names
 */
@ConfigurationProperties(prefix = "modless.ai")
public record AiProperties(
    boolean enabled,
    RolloutMode mode,
    String provider,
    Duration requestTimeout,
    int maxToolCalls,
    int tokenBudget,
    Hardening hardening,
    Embeddings embeddings,
    Proxy proxy,
    OpenAiCompatible openaiCompatible,
    Gemini gemini,
    Models models) {

  /** Applies conservative defaults for local development. */
  public AiProperties {
    mode = mode == null ? RolloutMode.EXPLAIN_ONLY : mode;
    provider = Provider.from(provider).key();
    requestTimeout = requestTimeout == null ? Duration.ofSeconds(120) : requestTimeout;
    maxToolCalls = maxToolCalls <= 0 ? 24 : maxToolCalls;
    tokenBudget = tokenBudget <= 0 ? 6000 : tokenBudget;
    hardening =
        hardening == null
            ? new Hardening(
                30, Duration.ofMinutes(1), 3, Duration.ofMinutes(1), 2, Duration.ofMillis(250), 12)
            : hardening;
    embeddings =
        embeddings == null
            ? new Embeddings(null, null, null, null, null, false, -1, true)
            : embeddings;
    proxy = proxy == null ? new Proxy(true, ProxyType.HTTP, null, null, null) : proxy;
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

  /** Assistant rollout modes. */
  public enum RolloutMode {
    /** Explanations only, no proposals or mutations. */
    EXPLAIN_ONLY,
    /** Draft proposals without applying them. */
    PROPOSAL_ONLY,
    /** Allow guarded low-risk apply after validation gates. */
    GUARDED_APPLY
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
      int recentMessageWindow) {

    /** Applies conservative defaults. */
    public Hardening {
      perUserRequestsPerWindow = perUserRequestsPerWindow <= 0 ? 30 : perUserRequestsPerWindow;
      rateLimitWindow = rateLimitWindow == null ? Duration.ofMinutes(1) : rateLimitWindow;
      circuitFailureThreshold = circuitFailureThreshold <= 0 ? 3 : circuitFailureThreshold;
      circuitOpenDuration =
          circuitOpenDuration == null ? Duration.ofMinutes(1) : circuitOpenDuration;
      providerRetryAttempts = providerRetryAttempts <= 0 ? 2 : providerRetryAttempts;
      retryBackoff = retryBackoff == null ? Duration.ofMillis(250) : retryBackoff;
      recentMessageWindow = recentMessageWindow <= 0 ? 12 : recentMessageWindow;
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
        case RESPONDER -> blankToDefault(responder, defaultModel(resolvedProvider));
        case SUMMARIZER -> blankToDefault(summarizer, defaultModel(resolvedProvider));
      };
    }

    private static String defaultModel(Provider provider) {
      return provider == Provider.GEMINI ? "gemini-2.0-flash" : "gpt-4o-mini";
    }
  }
}
