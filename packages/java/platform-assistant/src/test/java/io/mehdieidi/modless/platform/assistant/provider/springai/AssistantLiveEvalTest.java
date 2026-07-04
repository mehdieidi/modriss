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
import io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes.AssistantModelContext;
import io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes.ContextElement;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompleter;
import io.mehdieidi.modless.platform.assistant.persistence.jdbc.JdbcAssistantModelContextIndex;
import io.mehdieidi.modless.platform.assistant.provider.ProxyAvailability;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.assistant.spi.AssistantToolBridge;
import io.mehdieidi.modless.platform.assistant.support.AssistantEvalGateReportWriter;
import io.mehdieidi.modless.platform.assistant.support.AssistantEvalModelFixtures;
import io.mehdieidi.modless.platform.assistant.support.TestEnvFiles;
import io.mehdieidi.modless.platform.assistant.tools.AssistantToolService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.model.application.ModelService;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class AssistantLiveEvalTest {

  @Test
  void liveGatePromptsPassQualityGatesWithinTurnBudget() throws Exception {
    LiveEvalHarness harness = liveEvalHarness();
    AssistantEvalRunner.LiveEvalBudget budget = AssistantEvalRunner.LiveEvalBudget.gateSuite();
    long suiteStarted = System.currentTimeMillis();
    List<AssistantEvalRunner.EvalResult> results =
        harness
            .runner()
            .run(
                true,
                toolBinder(harness.tools(), harness.mapper()),
                harness.runner().loadLiveGatePrompts(),
                budget);
    AssistantEvalRunner.QualityGateReport gates = harness.runner().qualityGateReport(results);
    AssistantEvalRunner.LatencyReport latency = harness.runner().latencyReport(results);
    String baseline = harness.runner().baselineReport(results);

    System.out.println(
        "Live gate suite wall-clock: " + (System.currentTimeMillis() - suiteStarted) + "ms");
    System.out.println(gates.summary());
    System.out.println(latency.summary());
    System.out.println(baseline);

    writeGateReportIfRequested(true, results, gates, latency, baseline);

    for (AssistantEvalRunner.EvalResult result : results) {
      org.junit.jupiter.api.Assertions.assertTrue(
          result.latencyMs() <= budget.maxTurnLatencyMs(),
          () ->
              result.id()
                  + " exceeded 5m turn budget at "
                  + result.latencyMs()
                  + "ms (providerWait="
                  + result.providerWaitMs()
                  + "ms): "
                  + result.failureMessage());
    }

    assertTrue(
        gates.passedGates(), () -> gates.summary() + "\n" + gates.violations() + "\n" + baseline);
  }

  @org.junit.jupiter.api.Timeout(300)
  @Test
  void liveStressEventStormingDocumentWithinTurnBudget() throws Exception {
    LiveEvalHarness harness = liveEvalHarness();
    AssistantEvalRunner.EvalPrompt prompt =
        new AssistantEvalRunner.EvalPrompt(
            "cim-eventstorming-stress",
            "cim-source-document",
            "CIM",
            harness.runner().loadPrompts().stream()
                .filter(item -> "cim-eventstorming-01".equals(item.id()))
                .findFirst()
                .orElseThrow()
                .prompt(),
            true,
            List.of(),
            harness.runner().loadPrompts().stream()
                .filter(item -> "cim-eventstorming-01".equals(item.id()))
                .findFirst()
                .orElseThrow()
                .sourceDocument(),
            List.of(),
            12,
            8,
            2,
            true);

    List<AssistantEvalRunner.EvalResult> results =
        harness
            .runner()
            .run(
                true,
                toolBinder(harness.tools(), harness.mapper()),
                List.of(prompt),
                AssistantEvalRunner.LiveEvalBudget.defaults());

    AssistantEvalRunner.EvalResult result = results.get(0);
    System.out.println(
        "Stress eventstorming: operations="
            + result.operationCount()
            + ", latencyMs="
            + result.latencyMs()
            + ", providerWaitMs="
            + result.providerWaitMs()
            + ", stage="
            + result.failureStage());
    org.junit.jupiter.api.Assertions.assertTrue(
        result.latencyMs() <= 300_000L,
        () -> "stress eval exceeded 5m: " + result.latencyMs() + "ms");
    org.junit.jupiter.api.Assertions.assertFalse(
        "PLANNING".equals(result.failureStage()), () -> result.failureMessage());
  }

  @org.junit.jupiter.api.Disabled(
      "Optional long-running document eval; not part of live quality gates.")
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

  @org.junit.jupiter.api.Disabled(
      "Full 46-prompt matrix is for baseline recording only; use"
          + " liveGatePromptsPassQualityGatesWithinTurnBudget.")
  @Test
  void liveFullEvalMatrixPassesQualityGates() throws Exception {
    LiveEvalHarness harness = liveEvalHarness();
    List<AssistantEvalRunner.EvalResult> results =
        harness.runner().run(true, toolBinder(harness.tools(), harness.mapper()));
    AssistantEvalRunner.QualityGateReport gates = harness.runner().qualityGateReport(results);
    AssistantEvalRunner.LatencyReport latency = harness.runner().latencyReport(results);
    String baseline = harness.runner().baselineReport(results);

    System.out.println(gates.summary());
    System.out.println(latency.summary());
    System.out.println(baseline);

    writeGateReportIfRequested(true, results, gates, latency, baseline);

    assertTrue(
        gates.passedGates(), () -> gates.summary() + "\n" + gates.violations() + "\n" + baseline);
  }

  private void writeGateReportIfRequested(
      boolean live,
      List<AssistantEvalRunner.EvalResult> results,
      AssistantEvalRunner.QualityGateReport gates,
      AssistantEvalRunner.LatencyReport latency,
      String baseline)
      throws Exception {
    if (!"true".equalsIgnoreCase(TestEnvFiles.get("MODLESS_WRITE_EVAL_REPORT"))) {
      return;
    }
    Path reportPath = Path.of("../../../docs/internal/ai/live-eval-gate-report.md").normalize();
    String markdown =
        AssistantEvalGateReportWriter.markdown(live, results, gates, latency, baseline);
    Files.writeString(
        reportPath, markdown, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    System.out.println("Wrote eval gate report to " + reportPath);
  }

  private LiveEvalHarness liveEvalHarness() {
    Map<String, String> env = TestEnvFiles.load();
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
        bool(env, "MODLESS_AI_REQUIRE_IDEMPOTENCY_KEY", true));
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
    JdbcAssistantModelContextIndex contexts = new JdbcAssistantModelContextIndex();
    ThreadLocal<EvalSession> session = new ThreadLocal<>();
    return new AssistantEvalRunner.ToolBinder() {
      @Override
      public void bind(ModelLevel level, AssistantEvalRunner.EvalPrompt prompt) {
        try {
          JsonNode model = AssistantEvalModelFixtures.modelFor(level, prompt);
          AssistantModelContext context =
              contexts.transientSnapshot("eval-project", level, "Live Eval", 1L, model, null);
          Map<String, String> existingTypes =
              context.elements().stream()
                  .collect(
                      Collectors.toMap(
                          ContextElement::id,
                          ContextElement::type,
                          (left, right) -> left,
                          LinkedHashMap::new));
          session.set(new EvalSession(model, existingTypes));
          tools.bindSession(new AssistantToolBridge.ToolSession(level, model, context));
        } catch (Exception ex) {
          throw new IllegalStateException("Could not bind live eval session.", ex);
        }
      }

      @Override
      public JsonNode baseModel() {
        EvalSession current = session.get();
        return current == null ? null : current.model();
      }

      @Override
      public Map<String, String> existingTypes() {
        EvalSession current = session.get();
        return current == null ? Map.of() : current.existingTypes();
      }

      @Override
      public void clear() {
        session.remove();
        tools.clearSession();
      }
    };
  }

  private record EvalSession(JsonNode model, Map<String, String> existingTypes) {}

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
    return TestEnvFiles.load();
  }

  private record LiveEvalHarness(
      ObjectMapper mapper, AssistantToolService tools, AssistantEvalRunner runner) {}
}
