package io.mehdieidi.modless.backend.assistant;

import java.net.InetSocketAddress;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Provider-neutral assistant settings for model selection, proxy routing, and rollout mode.
 *
 * @param enabled        whether outbound AI calls are allowed
 * @param mode           assistant rollout mode
 * @param provider       configured provider key
 * @param requestTimeout outbound AI request timeout
 * @param maxToolCalls   maximum tool calls per assistant turn
 * @param tokenBudget    approximate prompt budget per turn
 * @param hardening      rate-limit and circuit-breaker settings
 * @param embeddings     local embedding settings
 * @param proxy          AI-only outbound proxy settings
 * @param groq           Groq OpenAI-compatible settings
 * @param models         role-specific model names
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
        Groq groq,
        Models models) {

    /**
     * Applies conservative defaults for local development.
     */
    public AiProperties {
        mode = mode == null ? RolloutMode.EXPLAIN_ONLY : mode;
        provider = blankToDefault(provider, "groq");
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(30) : requestTimeout;
        maxToolCalls = maxToolCalls <= 0 ? 6 : maxToolCalls;
        tokenBudget = tokenBudget <= 0 ? 6000 : tokenBudget;
        hardening = hardening == null ? new Hardening(30, Duration.ofMinutes(1), 3,
                Duration.ofMinutes(1), 2, Duration.ofMillis(250), 12) : hardening;
        embeddings = embeddings == null ? new Embeddings(null, null, null, null, null, false,
                -1, true) : embeddings;
        proxy = proxy == null ? new Proxy(true, ProxyType.HTTP, null, null, null) : proxy;
        groq = groq == null ? new Groq(null, null) : groq;
        models = models == null ? new Models(null, null, null) : models;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String normalizeOpenAiCompatibleBaseUrl(String value) {
        String normalized = blankToDefault(value, "https://api.groq.com/openai");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/v1")) {
            return normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }

    /**
     * Supported outbound proxy types.
     */
    public enum ProxyType {
        /**
         * No proxy.
         */
        DIRECT,
        /**
         * HTTP proxy, Nekoray default port 2081.
         */
        HTTP,
        /**
         * SOCKS proxy, Nekoray default port 2082.
         */
        SOCKS
    }

    /**
     * Assistant rollout modes.
     */
    public enum RolloutMode {
        /**
         * Explanations only, no proposals or mutations.
         */
        EXPLAIN_ONLY,
        /**
         * Draft proposals without applying them.
         */
        PROPOSAL_ONLY,
        /**
         * Allow guarded low-risk apply after validation gates.
         */
        GUARDED_APPLY
    }

    /**
     * Local embedding providers.
     */
    public enum EmbeddingProvider {
        /**
         * Spring AI ONNX sentence-transformer embeddings.
         */
        ONNX,
        /**
         * Deterministic local hash embeddings for fallback and tests.
         */
        HASH
    }

    /**
     * Local embedding settings.
     *
     * @param provider          embedding implementation
     * @param modelResource     optional ONNX model resource URI/path
     * @param tokenizerResource optional tokenizer resource URI/path
     * @param modelOutputName   optional ONNX output tensor name
     * @param cacheDirectory    optional Spring AI transformers cache directory
     * @param disableCaching    whether Spring AI transformers caching is disabled
     * @param gpuDeviceId       optional GPU device, negative for CPU/default
     * @param fallbackToHash    whether ONNX initialization failures fall back to hash vectors
     */
    public record Embeddings(EmbeddingProvider provider, String modelResource,
                             String tokenizerResource, String modelOutputName,
                             String cacheDirectory, boolean disableCaching, int gpuDeviceId,
                             boolean fallbackToHash) {

        /**
         * Applies local ONNX defaults with safe fallback.
         */
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
     * @param enabled        whether the proxy is used and health-checked
     * @param type           proxy type
     * @param host           proxy host
     * @param port           proxy port
     * @param connectTimeout proxy socket check timeout
     */
    public record Proxy(boolean enabled, ProxyType type, String host, Integer port,
                        Duration connectTimeout) {

        /**
         * Applies local Nekoray defaults.
         */
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
     * @param rateLimitWindow          rate limit window
     * @param circuitFailureThreshold  consecutive provider failures before opening circuit
     * @param circuitOpenDuration      open-circuit duration
     * @param providerRetryAttempts    provider retry attempts per call
     * @param retryBackoff             retry backoff
     * @param recentMessageWindow      recent durable messages included in prompt context
     */
    public record Hardening(int perUserRequestsPerWindow, Duration rateLimitWindow,
                            int circuitFailureThreshold, Duration circuitOpenDuration,
                            int providerRetryAttempts, Duration retryBackoff,
                            int recentMessageWindow) {

        /**
         * Applies conservative defaults.
         */
        public Hardening {
            perUserRequestsPerWindow = perUserRequestsPerWindow <= 0 ? 30
                    : perUserRequestsPerWindow;
            rateLimitWindow = rateLimitWindow == null ? Duration.ofMinutes(1)
                    : rateLimitWindow;
            circuitFailureThreshold = circuitFailureThreshold <= 0 ? 3
                    : circuitFailureThreshold;
            circuitOpenDuration = circuitOpenDuration == null ? Duration.ofMinutes(1)
                    : circuitOpenDuration;
            providerRetryAttempts = providerRetryAttempts <= 0 ? 2 : providerRetryAttempts;
            retryBackoff = retryBackoff == null ? Duration.ofMillis(250) : retryBackoff;
            recentMessageWindow = recentMessageWindow <= 0 ? 12 : recentMessageWindow;
        }
    }

    /**
     * OpenAI-compatible Groq endpoint settings.
     *
     * @param baseUrl provider API base URL
     * @param apiKey  provider API key
     */
    public record Groq(String baseUrl, String apiKey) {

        /**
         * Applies Groq's OpenAI-compatible API base URL.
         */
        public Groq {
            baseUrl = normalizeOpenAiCompatibleBaseUrl(baseUrl);
            apiKey = apiKey == null ? "" : apiKey.trim();
        }
    }

    /**
     * Role-specific model settings.
     *
     * @param planner    model used for planning
     * @param responder  model used for user-facing answers
     * @param summarizer model used for rolling summaries
     */
    public record Models(String planner, String responder, String summarizer) {

        /**
         * Applies default Groq model names.
         */
        public Models {
            planner = blankToDefault(planner, "llama-3.3-70b-versatile");
            responder = blankToDefault(responder, "llama-3.3-70b-versatile");
            summarizer = blankToDefault(summarizer, "llama-3.1-8b-instant");
        }

        /**
         * Resolves a model by assistant role.
         *
         * @param role assistant model role
         * @return configured model name
         */
        public String forRole(AssistantModelRole role) {
            return switch (role == null ? AssistantModelRole.RESPONDER : role) {
                case PLANNER -> planner;
                case RESPONDER -> responder;
                case SUMMARIZER -> summarizer;
            };
        }
    }
}
