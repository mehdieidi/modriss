package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.modeling.config.ModelingConfigService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Live planner smoke test against the configured OpenAI-compatible provider.
 *
 * <p>Run with {@code MODLESS_AI_LIVE_TEST=true} and provider credentials in the environment.
 */
class AssistantLivePlanningTest {

  @Test
  void vendingMachinePromptProducesSubstantiveValidatedPatch() {
    assumeTrue("true".equalsIgnoreCase(System.getenv("MODLESS_AI_LIVE_TEST")));

    String apiKey =
        firstNonBlank(System.getenv("OPENAI_COMPATIBLE_API_KEY"), System.getenv("OPENAI_API_KEY"));
    String baseUrl =
        firstNonBlank(System.getenv("OPENAI_COMPATIBLE_BASE_URL"), "https://api.openai.com/v1");
    String model = firstNonBlank(System.getenv("MODLESS_AI_PLANNER_MODEL"), "gapgpt-qwen-3.6");
    assumeTrue(apiKey != null && !apiKey.isBlank());

    AiProperties properties =
        new AiProperties(
            true,
            null,
            "openai",
            Duration.ofSeconds(120),
            96,
            2,
            6000,
            24,
            2400,
            14000,
            null,
            null,
            null,
            new AiProperties.Proxy(false, AiProperties.ProxyType.DIRECT, null, null, null),
            new AiProperties.OpenAiCompatible(baseUrl, apiKey),
            null,
            new AiProperties.Models(model, model, model));
    OpenAiCompatibleAssistantModelProvider provider =
        new OpenAiCompatibleAssistantModelProvider(
            properties,
            new ProxyAvailability(properties),
            new AssistantPromptGuard(properties),
            new AssistantToolService(
                new AssistantCatalogService(null),
                new AssistantPatchCompiler(),
                new ObjectMapper()),
            new AssistantHardeningService(properties, null),
            new SemanticModelPatchParser(new ObjectMapper()),
            RestClient.builder());
    assumeTrue(provider.available());

    AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
    AssistantPatchCompleter patchCompleter = new AssistantPatchCompleter(schemas);
    ModelingConfigService modeling = new ModelingConfigService();
    String prompt = "Create a serverless model for vending machine backend";
    modeling.starterModel(ModelLevel.PIM, "Vending Machine");

    List<AssistantModelProvider.ContextSnippet> snippets = new ArrayList<>();
    snippets.addAll(schemas.planningContracts(ModelLevel.PIM, prompt, 14, true));
    snippets.add(
        new AssistantModelProvider.ContextSnippet(
            "runtime-metamodel", "PIM language index", schemas.languageIndex(ModelLevel.PIM)));

    AssistantTurnPlan plan =
        provider.planTurn(
            new AssistantModelProvider.AssistantPrompt(
                AssistantModelRole.PLANNER,
                """
                You are operating a formal modeling workbench in guarded-apply mode.
                """
                    + schemas.domainCreationBlueprint(ModelLevel.PIM, prompt)
                    + "\nSchema enum defaults: "
                    + schemas.planningDefaults(ModelLevel.PIM),
                prompt,
                snippets));

    SemanticModelPatch completed = patchCompleter.complete(ModelLevel.PIM, plan.patch(), Map.of());
    long additions =
        completed.operations().stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .count();
    assertTrue(
        plan.kind() == AssistantTurnPlan.Kind.PATCH && additions >= 3,
        () ->
            "Expected a substantive PATCH scaffold, got kind="
                + plan.kind()
                + " operations="
                + completed.operations().size());
    assertTrue(
        completed.operations().stream()
                .anyMatch(operation -> "Function".equals(operation.elementType()))
            || completed.operations().stream()
                .anyMatch(operation -> "Api".equals(operation.elementType()))
            || completed.operations().stream()
                .anyMatch(operation -> "ServerlessService".equals(operation.elementType())),
        () -> "Expected core serverless elements in " + completed.operations());
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return "";
  }
}
