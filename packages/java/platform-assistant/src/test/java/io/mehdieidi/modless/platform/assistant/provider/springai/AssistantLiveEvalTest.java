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
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompleter;
import io.mehdieidi.modless.platform.assistant.patch.SemanticModelPatchParser;
import io.mehdieidi.modless.platform.assistant.persistence.jdbc.JdbcAssistantModelContextIndex;
import io.mehdieidi.modless.platform.assistant.provider.ProxyAvailability;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.assistant.spi.AssistantToolBridge;
import io.mehdieidi.modless.platform.assistant.tools.AssistantToolService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.model.application.ModelService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
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
    AiProperties properties =
        new AiProperties(
            true,
            "openai",
            Duration.ofMinutes(5),
            Integer.MAX_VALUE,
            6,
            24000,
            32,
            4000,
            18000,
            20,
            12,
            14,
            "",
            null,
            null,
            new AiProperties.Proxy(false, AiProperties.ProxyType.DIRECT, null, null, null),
            new AiProperties.OpenAiCompatible(
                env.get("OPENAI_COMPATIBLE_BASE_URL"), env.get("OPENAI_COMPATIBLE_API_KEY")),
            null,
            new AiProperties.Models(
                env.getOrDefault("MODLESS_AI_PLANNER_MODEL", "auto"),
                env.getOrDefault("MODLESS_AI_RESPONDER_MODEL", "auto"),
                env.getOrDefault("MODLESS_AI_SUMMARIZER_MODEL", "auto")));
    ModelService models = mock(ModelService.class);
    when(models.validateStructural(any(), any()))
        .thenReturn(new ModelService.ValidationResult(true, List.of()));
    AssistantToolService tools =
        new AssistantToolService(
            emptyCatalog(),
            new AssistantPatchCompiler(),
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
            new SemanticModelPatchParser(mapper),
            RestClient.builder());
    AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
    AssistantEvalRunner runner =
        new AssistantEvalRunner(provider, schemas, new AssistantPatchCompleter(schemas), mapper);
    return new LiveEvalHarness(mapper, tools, runner);
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
