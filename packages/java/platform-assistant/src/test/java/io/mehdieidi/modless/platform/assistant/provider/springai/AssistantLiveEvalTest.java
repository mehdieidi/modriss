package io.mehdieidi.modless.platform.assistant.provider.springai;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.application.AssistantEvalRunner;
import io.mehdieidi.modless.platform.assistant.application.AssistantHardeningService;
import io.mehdieidi.modless.platform.assistant.application.AssistantPromptGuard;
import io.mehdieidi.modless.platform.assistant.config.AiProperties;
import io.mehdieidi.modless.platform.assistant.delta.DeltaCompiler;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompleter;
import io.mehdieidi.modless.platform.assistant.persistence.jdbc.JdbcAssistantModelContextIndex;
import io.mehdieidi.modless.platform.assistant.provider.ProxyAvailability;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.assistant.spi.AssistantToolBridge;
import io.mehdieidi.modless.platform.assistant.tools.AssistantToolService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.model.application.ModelService;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class AssistantLiveEvalTest {

  @Test
  void liveProviderCreatesLargeConnectedCimFromEventStormingDocument() throws Exception {
    LiveEvalHarness harness = liveEvalHarness();
    AssistantEvalRunner.EvalPrompt prompt =
        harness.runner().loadPrompts().stream()
            .filter(item -> "cim-eventstorming-01".equals(item.id()))
            .findFirst()
            .orElseThrow();

    List<AssistantEvalRunner.EvalResult> results =
        harness.runner().run(true, toolBinder(harness.tools(), harness.mapper()), List.of(prompt));

    assertTrue(results.get(0).validationPassed(), () -> harness.runner().baselineReport(results));
  }

  @Test
  void liveProviderCreatesCimFromCommunityClinicUserStories() throws Exception {
    LiveEvalHarness harness = liveEvalHarness();
    String source =
        Files.readString(
            Path.of("../../../mde/samples/document-to-cim/community-clinic-user-stories.md")
                .normalize());
    AssistantEvalRunner.EvalPrompt prompt =
        new AssistantEvalRunner.EvalPrompt(
            "cim-userstory-community-clinic",
            "cim-source-document",
            "CIM",
            "Create a complete CIM model from this user story requirements document. Preserve "
                + "goals, actors, user stories, acceptance criteria, business rules, domain terms, "
                + "risks, assumptions, commands, events, and relationships.",
            true,
            List.of(),
            source,
            List.of("Actor", "Command", "Event", "BusinessPolicy"),
            38,
            24,
            8,
            true);

    List<AssistantEvalRunner.EvalResult> results =
        harness.runner().run(true, toolBinder(harness.tools(), harness.mapper()), List.of(prompt));
    AssistantEvalRunner.EvalResult result = results.get(0);
    System.out.println(
        "Live user-story CIM eval: operations="
            + result.operationCount()
            + ", additions="
            + result.addElementCount()
            + ", connections="
            + result.connectionCount()
            + ", sourceAnalysis="
            + result.sourceAnalysisUsed()
            + ", latencyMs="
            + result.latencyMs());

    assertTrue(result.validationPassed(), () -> harness.runner().baselineReport(results));
  }

  private LiveEvalHarness liveEvalHarness() {
    Map<String, String> env = env();
    assumeTrue(
        "true".equalsIgnoreCase(env.getOrDefault("MODLESS_RUN_LIVE_ASSISTANT_EVAL", "")),
        "Set MODLESS_RUN_LIVE_ASSISTANT_EVAL=true to run the live LLM eval.");
    assumeTrue(!env.getOrDefault("OPENAI_COMPATIBLE_API_KEY", "").isBlank());
    assumeTrue(!env.getOrDefault("OPENAI_COMPATIBLE_BASE_URL", "").isBlank());

    ObjectMapper mapper = new ObjectMapper();
    AiProperties properties = liveProperties(env);
    RestClient.Builder restClientBuilder = restClientBuilder(properties);
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(any(), any()))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    AssistantToolService tools =
        new AssistantToolService(
            emptyCatalog(),
            new AssistantPatchCompiler(),
            new DeltaCompiler(new AssistantMetamodelSchemaService()),
            new AssistantMetamodelSchemaService(),
            models,
            mapper);
    OpenAiCompatibleAssistantModelProvider provider =
        new OpenAiCompatibleAssistantModelProvider(
            properties,
            new ProxyAvailability(properties),
            new AssistantPromptGuard(properties),
            tools,
            new AssistantHardeningService(properties, null),
            restClientBuilder);
    AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
    AssistantEvalRunner runner =
        new AssistantEvalRunner(provider, schemas, new AssistantPatchCompleter(schemas), mapper);
    return new LiveEvalHarness(mapper, tools, runner);
  }

  private AiProperties liveProperties(Map<String, String> env) {
    return new AiProperties(
        true,
        env.getOrDefault("MODLESS_AI_PROVIDER", "openai"),
        duration(env, "MODLESS_AI_REQUEST_TIMEOUT", Duration.ofMinutes(5)),
        integer(env, "MODLESS_AI_MAX_TOOL_CALLS", 24),
        integer(env, "MODLESS_AI_MAX_REPAIR_ATTEMPTS", 1),
        integer(env, "MODLESS_AI_TOKEN_BUDGET", 16000),
        integer(env, "MODLESS_AI_MAX_CONTEXT_SNIPPETS", 24),
        integer(env, "MODLESS_AI_MAX_SNIPPET_CHARS", 2400),
        integer(env, "MODLESS_AI_MAX_SYSTEM_CHARS", 14000),
        integer(env, "MODLESS_AI_MAX_AGENT_STEPS", 8),
        integer(env, "MODLESS_AI_MAX_TOOL_CALLS_PER_STEP", 4),
        integer(env, "MODLESS_AI_RESERVED_SCHEMA_SNIPPETS", 10),
        env.getOrDefault("MODLESS_AI_FALLBACK_PROVIDER", ""),
        new AiProperties.Hardening(
            integer(env, "MODLESS_AI_RATE_LIMIT_REQUESTS", 30),
            duration(env, "MODLESS_AI_RATE_LIMIT_WINDOW", Duration.ofMinutes(1)),
            integer(env, "MODLESS_AI_CIRCUIT_FAILURE_THRESHOLD", 3),
            duration(env, "MODLESS_AI_CIRCUIT_OPEN_DURATION", Duration.ofMinutes(1)),
            integer(env, "MODLESS_AI_PROVIDER_RETRY_ATTEMPTS", 2),
            duration(env, "MODLESS_AI_RETRY_BACKOFF", Duration.ofMillis(250)),
            integer(env, "MODLESS_AI_RECENT_MESSAGE_WINDOW", 24)),
        null,
        proxy(env),
        new AiProperties.OpenAiCompatible(
            env.get("OPENAI_COMPATIBLE_BASE_URL"), env.get("OPENAI_COMPATIBLE_API_KEY")),
        null,
        new AiProperties.Models(
            env.getOrDefault("MODLESS_AI_PLANNER_MODEL", ""),
            env.getOrDefault("MODLESS_AI_RESPONDER_MODEL", ""),
            env.getOrDefault("MODLESS_AI_SUMMARIZER_MODEL", "")),
        duration(env, "MODLESS_AI_TURN_TIMEOUT", Duration.ofMinutes(5)),
        integer(env, "MODLESS_AI_MAX_REPAIR_ATTEMPTS", 1),
        integer(env, "MODLESS_AI_MAX_PROMPT_TOKENS", 24000),
        integer(env, "MODLESS_AI_MAX_SOURCE_CHUNK_TOKENS", 4000),
        integer(env, "MODLESS_AI_MAX_SOURCE_CHUNKS_PER_TURN", 24),
        bool(env, "MODLESS_AI_REQUIRE_IDEMPOTENCY_KEY", true),
        bool(env, "MODLESS_AI_NEW_AGENT_ENABLED", true));
  }

  private RestClient.Builder restClientBuilder(AiProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(
        properties.requestTimeout().compareTo(Duration.ofSeconds(10)) < 0
            ? properties.requestTimeout()
            : Duration.ofSeconds(10));
    factory.setReadTimeout(properties.requestTimeout());
    AiProperties.Proxy proxy = properties.proxy();
    if (proxy.enabled() && proxy.type() != AiProperties.ProxyType.DIRECT) {
      Proxy.Type type =
          proxy.type() == AiProperties.ProxyType.SOCKS ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
      factory.setProxy(new Proxy(type, proxy.address()));
    }
    return RestClient.builder().requestFactory(factory);
  }

  private AiProperties.Proxy proxy(Map<String, String> env) {
    return new AiProperties.Proxy(
        bool(env, "MODLESS_AI_PROXY_ENABLED", false),
        proxyType(env.get("MODLESS_AI_PROXY_TYPE")),
        localProxyHost(env.get("MODLESS_AI_PROXY_HOST")),
        integer(env, "MODLESS_AI_PROXY_PORT", 0),
        duration(env, "MODLESS_AI_PROXY_CONNECT_TIMEOUT", Duration.ofSeconds(2)));
  }

  private String localProxyHost(String host) {
    if (host != null && "host.docker.internal".equalsIgnoreCase(host.trim())) {
      return "127.0.0.1";
    }
    return host;
  }

  private AiProperties.ProxyType proxyType(String value) {
    if (value == null || value.isBlank()) {
      return AiProperties.ProxyType.HTTP;
    }
    try {
      return AiProperties.ProxyType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return AiProperties.ProxyType.HTTP;
    }
  }

  private boolean bool(Map<String, String> env, String key, boolean fallback) {
    String value = env.get(key);
    return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
  }

  private int integer(Map<String, String> env, String key, int fallback) {
    try {
      String value = env.get(key);
      return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private Duration duration(Map<String, String> env, String key, Duration fallback) {
    String value = env.get(key);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    try {
      if (normalized.endsWith("ms")) {
        return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
      }
      if (normalized.endsWith("s")) {
        return Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
      }
      if (normalized.endsWith("m")) {
        return Duration.ofMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
      }
      if (normalized.endsWith("h")) {
        return Duration.ofHours(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
      }
      return Duration.parse(value.trim());
    } catch (RuntimeException ignored) {
      return fallback;
    }
  }

  private AssistantEvalRunner.ToolBinder toolBinder(AssistantToolService tools, ObjectMapper mapper)
      throws Exception {
    JsonNode cimModel =
        mapper.readTree(
            """
            {"id":"cim-root","eClass":"CIMModel","modelLevel":"CIM","name":"Live CIM Eval",
             "diagram":{"elements":[],"relationships":[]}}
            """);
    JdbcAssistantModelContextIndex contexts = new JdbcAssistantModelContextIndex();
    return new AssistantEvalRunner.ToolBinder() {
      @Override
      public void bind(ModelLevel level, AssistantEvalRunner.EvalPrompt prompt) {
        var context =
            contexts.transientSnapshot("eval-project", level, "Live CIM Eval", 1L, cimModel, null);
        tools.bindSession(new AssistantToolBridge.ToolSession(level, cimModel, context));
      }

      @Override
      public void clear() {
        tools.clearSession();
      }
    };
  }

  private AssistantCatalog emptyCatalog() {
    return new AssistantCatalog() {
      @Override
      public void refresh() {}

      @Override
      public List<
              io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider
                  .ContextSnippet>
          search(String query, String level, int limit) {
        return List.of();
      }

      @Override
      public List<
              io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider
                  .ContextSnippet>
          describeType(String type, String level, int limit) {
        return List.of();
      }

      @Override
      public java.util.Optional<String> canonicalEnumLiteral(
          String ownerType, String featureName, String value, String level) {
        return java.util.Optional.empty();
      }
    };
  }

  private Map<String, String> env() {
    Map<String, String> values = new java.util.LinkedHashMap<>(System.getenv());
    Path envFile = Path.of(".env");
    if (Files.exists(envFile)) {
      try {
        for (String rawLine : Files.readAllLines(envFile)) {
          String line = rawLine.trim();
          if (line.isBlank() || line.startsWith("#") || !line.contains("=")) {
            continue;
          }
          String[] parts = line.split("=", 2);
          values.put(parts[0].trim(), unquote(parts[1].trim()));
        }
      } catch (Exception ignored) {
        // Environment variables are still enough for the opt-in live test.
      }
    }
    return values.entrySet().stream()
        .collect(
            Collectors.toMap(
                entry -> entry.getKey().toUpperCase(Locale.ROOT),
                Map.Entry::getValue,
                (left, right) -> right,
                java.util.LinkedHashMap::new));
  }

  private static String unquote(String value) {
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private record LiveEvalHarness(
      ObjectMapper mapper, AssistantToolService tools, AssistantEvalRunner runner) {}
}
