package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    assertTrue(report.contains("analysis"));
    assertTrue(results.stream().allMatch(AssistantEvalRunner.EvalResult::validationPassed));
  }

  @Test
  void sourceDocumentEvalRequiresAnalysisAndConnectedModelVolume() {
    ObjectMapper mapper = new ObjectMapper();
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
          public AssistantReply analyzeSource(AssistantPrompt prompt, AgentProgress progress) {
            return new AssistantReply("Commands, events, actors, entities, risks.", "stub", "stub");
          }

          @Override
          public AgentLoopResult planMutationTurn(AssistantPrompt prompt, AgentProgress progress) {
            List<SemanticModelPatch.Operation> operations = new java.util.ArrayList<>();
            for (int index = 0; index < 35; index++) {
              operations.add(
                  new SemanticModelPatch.Operation(
                      SemanticModelPatch.OperationType.ADD_ELEMENT,
                      "actor-" + index,
                      "Actor",
                      mapper.createObjectNode().put("name", "Actor " + index),
                      null,
                      null));
            }
            for (int index = 1; index <= 10; index++) {
              operations.add(
                  new SemanticModelPatch.Operation(
                      SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
                      "actor-" + index,
                      "Actor",
                      null,
                      "actor-0",
                      "relatedTo"));
            }
            return new AgentLoopResult(
                new AssistantTurnPlan(
                    AssistantTurnPlan.Intent.MUTATION,
                    AssistantTurnPlan.Kind.PATCH,
                    "ok",
                    List.of(),
                    new SemanticModelPatch(operations)),
                5,
                6);
          }
        };
    AssistantEvalRunner runner =
        new AssistantEvalRunner(
            provider,
            new AssistantMetamodelSchemaService(),
            new AssistantPatchCompleter(new AssistantMetamodelSchemaService()),
            mapper);
    AssistantEvalRunner.EvalPrompt prompt =
        runner.loadPrompts().stream()
            .filter(item -> "cim-eventstorming-01".equals(item.id()))
            .findFirst()
            .orElseThrow();

    var results = runner.run(true, null, List.of(prompt));

    assertEquals(1, results.size());
    assertTrue(results.get(0).validationPassed());
    assertTrue(results.get(0).sourceAnalysisUsed());
    assertEquals(35, results.get(0).addElementCount());
    assertEquals(10, results.get(0).connectionCount());
  }
}
