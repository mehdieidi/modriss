package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompleter;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantEvalRunnerTest {

  @Test
  void stubBaselineCoversAllCategories() {
    AssistantModelProvider provider =
        new AssistantModelProvider() {
          @Override
          public AssistantProviderMetadata metadata() {
            return new AssistantProviderMetadata("stub", "stub", "direct");
          }

          @Override
          public boolean available() {
            return true;
          }

          @Override
          public AssistantReply complete(AssistantPrompt prompt) {
            return new AssistantReply("", "stub", "stub");
          }

          @Override
          public AgentLoopResult planMutationTurn(AssistantPrompt prompt, AgentProgress progress) {
            return new AgentLoopResult(
                new AssistantTurnPlan(
                    AssistantTurnPlan.Intent.MUTATION,
                    AssistantTurnPlan.Kind.PATCH,
                    "ok",
                    List.of(),
                    new SemanticModelPatch(List.of())),
                3,
                4);
          }
        };
    AssistantEvalRunner runner =
        new AssistantEvalRunner(
            provider,
            new AssistantMetamodelSchemaService(),
            new AssistantPatchCompleter(new AssistantMetamodelSchemaService()),
            new ObjectMapper());

    var results = runner.run(false);
    var report = runner.baselineReport(results);

    assertFalse(results.isEmpty());
    assertTrue(results.size() >= 35, () -> "Expected curated prompt matrix, got " + results.size());
    assertTrue(report.contains("create-empty"));
    assertTrue(report.contains("selected-element"));
    assertTrue(report.contains("explain-only"));
    assertTrue(results.stream().allMatch(AssistantEvalRunner.EvalResult::validationPassed));
  }
}
