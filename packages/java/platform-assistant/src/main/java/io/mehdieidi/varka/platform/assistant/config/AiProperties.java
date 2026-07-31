package io.mehdieidi.varka.platform.assistant.config;

import io.mehdieidi.varka.platform.assistant.spi.AssistantSettings;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
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
 * @param providerProxies optional provider-specific proxy overrides (for example openai or gemini)
 * @param openaiCompatible OpenAI-compatible endpoint settings
 * @param gemini Google Gemini Developer API settings
 * @param models production and test model names
 * @param maxRepairAttempts preferred maximum repair attempts; overrides validationRepairAttempts
 * @param maxPromptTokens maximum prompt budget per provider call
 * @param maxSourceChunkTokens maximum source chunk token budget
 * @param maxSourceChunksPerTurn maximum source chunks processed in one turn
 * @param requireIdempotencyKey whether client turn idempotency keys are required
 * @param maxProviderCallsPerTurn maximum provider calls for a standard turn
 * @param maxProviderCallsSourceTurn maximum provider calls when source analysis runs
 * @param llmContractRerankEnabled whether hybrid retrieval may invoke LLM reranking
 * @param sourceTurnTimeout overall assistant turn timeout when a source attachment is present
 * @param maxCimModelingPasses maximum incremental CIM modeling passes per source-backed turn
 * @param preferLlmSourceExtraction whether CIM attachments use LLM evidence extraction
 */
@ConfigurationProperties(prefix = "varka.ai")
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
    Proxy proxy,
    Map<String, Proxy> providerProxies,
    OpenAiCompatible openaiCompatible,
    Gemini gemini,
    Models models,
    Duration turnTimeout,
    int maxRepairAttempts,
    int maxPromptTokens,
    int maxSourceChunkTokens,
    int maxSourceChunksPerTurn,
    boolean requireIdempotencyKey,
    int maxProviderCallsPerTurn,
    int maxProviderCallsSourceTurn,
    boolean llmContractRerankEnabled,
    Duration sourceTurnTimeout,
    int maxCimModelingPasses,
    boolean preferLlmSourceExtraction)
    implements AssistantSettings {
  private static final String PROVIDER_PROFILES_RESOURCE = "assistant-provider-profiles.properties";
  private static volatile ProviderProfileConfig providerProfileConfig;

  /** Applies conservative defaults for local development. */
  public AiProperties {
    provider = Provider.from(provider).key();
    requestTimeout = requestTimeout == null ? Duration.ofSeconds(90) : requestTimeout;
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
                30, Duration.ofMinutes(1), 3, Duration.ofMinutes(1), 1, Duration.ofMillis(250), 12)
            : hardening;
    proxy = proxy == null ? new Proxy(false, ProxyType.HTTP, null, null, null) : proxy;
    providerProxies = providerProxies == null ? Map.of() : Map.copyOf(providerProxies);
    openaiCompatible =
        openaiCompatible == null ? new OpenAiCompatible(null, null) : openaiCompatible;
    gemini = gemini == null ? new Gemini(null) : gemini;
    models = models == null ? new Models(null, null) : models;
    maxProviderCallsPerTurn = maxProviderCallsPerTurn <= 0 ? 2 : maxProviderCallsPerTurn;
    maxProviderCallsSourceTurn = maxProviderCallsSourceTurn <= 0 ? 6 : maxProviderCallsSourceTurn;
    sourceTurnTimeout = sourceTurnTimeout == null ? Duration.ofMinutes(5) : sourceTurnTimeout;
    maxCimModelingPasses = maxCimModelingPasses <= 0 ? 4 : maxCimModelingPasses;
  }

  @Override
  public Duration sourceTurnTimeout() {
    return sourceTurnTimeout;
  }

  @Override
  public int maxCimModelingPasses() {
    return maxCimModelingPasses;
  }

  @Override
  public boolean preferLlmSourceExtraction() {
    return preferLlmSourceExtraction;
  }

  @Override
  public boolean llmContractRerankEnabled() {
    return llmContractRerankEnabled;
  }

  /**
   * Resolves the configured provider key.
   *
   * @return configured provider enum
   */
  public Provider providerKind() {
    return Provider.from(provider);
  }

  /** Resolves the proxy for a provider, falling back to the legacy shared proxy configuration. */
  public Proxy proxyFor(String provider) {
    if (provider == null || provider.isBlank()) return proxy;
    Proxy configured = providerProxies.get(provider.trim().toLowerCase(java.util.Locale.ROOT));
    return configured == null ? proxy : configured;
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
   * Resolves the configured production model for the currently selected provider.
   *
   * @return configured or provider-default model name
   */
  public String model() {
    return models.model(providerKind());
  }

  /** Resolves provider behavior limits without weakening capable providers. */
  public io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider
          .ProviderCapabilityProfile
      providerProfile(String providerKey, String model, String baseUrl) {
    String fingerprint =
        ((providerKey == null ? "" : providerKey)
                + " "
                + (model == null ? "" : model)
                + " "
                + (baseUrl == null ? "" : baseUrl))
            .toLowerCase(Locale.ROOT);
    boolean nativeTools =
        openaiCompatible != null && openaiCompatible.protocol() == OpenAiProtocol.TOOLS;
    return providerProfileConfig().resolve(fingerprint, nativeTools);
  }

  private static ProviderProfileConfig providerProfileConfig() {
    ProviderProfileConfig local = providerProfileConfig;
    if (local != null) return local;
    synchronized (AiProperties.class) {
      local = providerProfileConfig;
      if (local == null) {
        local = ProviderProfileConfig.load();
        providerProfileConfig = local;
      }
      return local;
    }
  }

  private record ProviderProfileConfig(
      Map<String, ProviderProfileSpec> profiles, List<ProviderProfileRule> rules) {
    private static ProviderProfileConfig load() {
      Properties properties = new Properties();
      try (InputStream input = profileConfigInput()) {
        if (input != null) properties.load(input);
      } catch (IOException ex) {
        properties.clear();
      }
      Map<String, ProviderProfileSpec> loadedProfiles =
          Map.of(
              "standard", profile(properties, "standard", standardSpec()),
              "conservative", profile(properties, "conservative", conservativeSpec()));
      return new ProviderProfileConfig(loadedProfiles, rules(properties));
    }

    private io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider
            .ProviderCapabilityProfile
        resolve(String fingerprint, boolean nativeToolsActive) {
      for (ProviderProfileRule rule : rules) {
        if (rule.matches(fingerprint)) {
          ProviderProfileSpec profile = profiles.getOrDefault(rule.profile(), conservativeSpec());
          return profile.toCapabilityProfile(nativeToolsActive);
        }
      }
      return profiles
          .getOrDefault("standard", standardSpec())
          .toCapabilityProfile(nativeToolsActive);
    }

    private static InputStream profileConfigInput() throws IOException {
      String external = System.getenv("VARKA_AI_PROVIDER_PROFILES_FILE");
      if (external != null && !external.isBlank()) {
        Path path = Path.of(external.trim());
        if (Files.isRegularFile(path)) return Files.newInputStream(path);
      }
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      InputStream input =
          loader == null ? null : loader.getResourceAsStream(PROVIDER_PROFILES_RESOURCE);
      return input == null
          ? AiProperties.class.getClassLoader().getResourceAsStream(PROVIDER_PROFILES_RESOURCE)
          : input;
    }

    private static ProviderProfileSpec profile(
        Properties properties, String name, ProviderProfileSpec defaults) {
      String prefix = "profiles." + name + ".";
      return new ProviderProfileSpec(
          intValue(properties, prefix + "max-completion-tokens", defaults.maxCompletionTokens()),
          intValue(properties, prefix + "max-patch-creates", defaults.maxPatchCreates()),
          intValue(properties, prefix + "max-patch-connections", defaults.maxPatchConnections()),
          intValue(properties, prefix + "max-patch-evidence", defaults.maxPatchEvidence()),
          intValue(properties, prefix + "max-contract-count", defaults.maxContractCount()),
          stringValue(
              properties, prefix + "native-tools-preferred", defaults.nativeToolsPreferred()),
          booleanValue(
              properties,
              prefix + "forced-tool-choice-reliable",
              defaults.forcedToolChoiceReliable()));
    }

    private static List<ProviderProfileRule> rules(Properties properties) {
      List<ProviderProfileRule> result = new ArrayList<>();
      for (int index = 0; ; index++) {
        String match = properties.getProperty("provider-rules." + index + ".match");
        if (match == null) break;
        String profile = properties.getProperty("provider-rules." + index + ".profile");
        List<String> needles =
            java.util.Arrays.stream(match.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .toList();
        if (!needles.isEmpty() && profile != null && !profile.isBlank()) {
          result.add(new ProviderProfileRule(needles, profile.trim().toLowerCase(Locale.ROOT)));
        }
      }
      return List.copyOf(result);
    }

    private static int intValue(Properties properties, String key, int defaultValue) {
      String value = properties.getProperty(key);
      if (value == null || value.isBlank()) return defaultValue;
      try {
        return Integer.parseInt(value.trim());
      } catch (NumberFormatException ex) {
        return defaultValue;
      }
    }

    private static boolean booleanValue(Properties properties, String key, boolean defaultValue) {
      String value = properties.getProperty(key);
      return value == null || value.isBlank() ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    private static String stringValue(Properties properties, String key, String defaultValue) {
      String value = properties.getProperty(key);
      return value == null || value.isBlank() ? defaultValue : value.trim();
    }
  }

  private record ProviderProfileRule(List<String> matches, String profile) {
    private boolean matches(String fingerprint) {
      return matches.stream().anyMatch(fingerprint::contains);
    }
  }

  private record ProviderProfileSpec(
      int maxCompletionTokens,
      int maxPatchCreates,
      int maxPatchConnections,
      int maxPatchEvidence,
      int maxContractCount,
      String nativeToolsPreferred,
      boolean forcedToolChoiceReliable) {
    private io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider
            .ProviderCapabilityProfile
        toCapabilityProfile(boolean nativeToolsActive) {
      return new io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider
          .ProviderCapabilityProfile(
          maxCompletionTokens,
          maxPatchCreates,
          maxPatchConnections,
          maxPatchEvidence,
          maxContractCount,
          nativeTools(nativeToolsActive),
          forcedToolChoiceReliable);
    }

    private boolean nativeTools(boolean nativeToolsActive) {
      String normalized =
          nativeToolsPreferred == null ? "auto" : nativeToolsPreferred.toLowerCase(Locale.ROOT);
      return switch (normalized) {
        case "true", "yes", "tools" -> true;
        case "false", "no", "json_schema", "json-schema" -> false;
        default -> nativeToolsActive;
      };
    }
  }

  private static ProviderProfileSpec standardSpec() {
    return new ProviderProfileSpec(4096, 12, 18, 12, 8, "auto", true);
  }

  private static ProviderProfileSpec conservativeSpec() {
    return new ProviderProfileSpec(2048, 3, 4, 3, 2, "true", false);
  }

  private static String blankToDefault(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }

  private static String normalizeOpenAiCompatibleBaseUrl(String value) {
    String environment = System.getenv("OPENAI_COMPATIBLE_BASE_URL");
    String normalized =
        blankToDefault(
            value == null || value.isBlank() ? environment : value, "https://api.openai.com/v1");
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized.endsWith("/v1") ? normalized : normalized + "/v1";
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
   * @param providerRetryAttempts additional provider retries after the initial request
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
      providerRetryAttempts = Math.max(0, providerRetryAttempts);
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
  public record OpenAiCompatible(String baseUrl, String apiKey, OpenAiProtocol protocol) {

    /** Retains the pre-ACI two-value configuration constructor. */
    public OpenAiCompatible(String baseUrl, String apiKey) {
      this(baseUrl, apiKey, OpenAiProtocol.AUTO);
    }

    /** Applies OpenAI's API base URL when no compatible endpoint is configured. */
    public OpenAiCompatible {
      baseUrl = normalizeOpenAiCompatibleBaseUrl(baseUrl);
      apiKey = apiKey == null ? "" : apiKey.trim();
      if (protocol == null || protocol == OpenAiProtocol.AUTO) {
        protocol = OpenAiProtocol.from(System.getenv("VARKA_AI_OPENAI_PROTOCOL"));
      }
    }
  }

  /** OpenAI-compatible response protocol. */
  public enum OpenAiProtocol {
    AUTO,
    TOOLS,
    JSON_SCHEMA;

    /** Binds the documented environment values safely. */
    public static OpenAiProtocol from(String value) {
      if (value == null || value.isBlank()) return AUTO;
      return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
        case "auto" -> AUTO;
        case "tools" -> TOOLS;
        case "json_schema", "json-schema" -> JSON_SCHEMA;
        default ->
            throw new IllegalArgumentException("Unsupported VARKA_AI_OPENAI_PROTOCOL: " + value);
      };
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
   * Assistant model settings.
   *
   * @param model model used by production assistant calls
   * @param testModel model used by tests or evaluations that intentionally override production
   */
  public record Models(String model, String testModel) {

    /** Normalizes configured model names. */
    public Models {
      model = model == null ? "" : model.trim();
      testModel = testModel == null ? "" : testModel.trim();
    }

    /**
     * Resolves the production assistant model.
     *
     * @return configured model name
     */
    public String model() {
      return model(Provider.OPENAI);
    }

    /**
     * Resolves the production assistant model for the provider.
     *
     * @param provider assistant provider
     * @return configured or provider-default model name
     */
    public String model(Provider provider) {
      Provider resolvedProvider = provider == null ? Provider.OPENAI : provider;
      String selected = model;
      if (selected == null || selected.isBlank()) selected = System.getenv("VARKA_AI_MODEL");
      return blankToDefault(selected, defaultModel(resolvedProvider));
    }

    /**
     * Resolves the test/evaluation assistant model for the provider.
     *
     * @param provider assistant provider
     * @return configured test model, or the production model when no test override is configured
     */
    public String testModel(Provider provider) {
      String selected = testModel;
      if (selected == null || selected.isBlank()) selected = System.getenv("VARKA_AI_TEST_MODEL");
      return blankToDefault(selected, model(provider));
    }

    private static String defaultModel(Provider provider) {
      return provider == Provider.GEMINI ? "gemini-2.0-flash" : "gpt-4o-mini";
    }
  }
}
