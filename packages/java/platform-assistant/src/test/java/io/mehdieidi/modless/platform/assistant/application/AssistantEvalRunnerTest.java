package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompleter;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantEvalRunnerTest {

  @Test
  void liveGatePromptFixtureLoadsRepresentativeCategories() {
    AssistantEvalRunner runner = runner();
    List<AssistantEvalRunner.EvalPrompt> prompts = runner.loadLiveGatePrompts();
    assertFalse(prompts.isEmpty());
    assertTrue(prompts.size() >= 5);
    assertTrue(prompts.stream().anyMatch(prompt -> "analysis".equals(prompt.category())));
    assertTrue(
        prompts.stream().anyMatch(prompt -> "cim-source-document".equals(prompt.category())));
    assertTrue(prompts.stream().anyMatch(prompt -> "retrieval".equals(prompt.category())));
  }

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
    assertTrue(
        results.stream()
            .filter(r -> !"resilience".equals(r.category()))
            .allMatch(AssistantEvalRunner.EvalResult::validationPassed));
    assertTrue(
        results.stream()
            .filter(result -> "resilience".equals(result.category()))
            .allMatch(AssistantEvalRunner.EvalResult::validationPassed));
    assertFalse(runner.qualityGateReport(results).passedGates());
  }

  @Test
  void stubModeDoesNotAutoPassQualityGates() {
    AssistantEvalRunner runner = runner();
    assertFalse(runner.qualityGateReport(runner.run(false)).passedGates());
  }

  @Test
  void resilienceStubFixturesPass() {
    AssistantEvalRunner runner = runner();
    var results =
        runner.run(
            false,
            null,
            runner.loadPrompts().stream()
                .filter(prompt -> "resilience".equals(prompt.category()))
                .toList());
    assertFalse(results.isEmpty());
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
          public AssistantReply completeStructured(AssistantPrompt prompt) {
            StringBuilder elements = new StringBuilder();
            elements.append(
                """
{"localId":"actor-0","eClass":"Actor","attributes":{"name":"Actor 0"},"placement":{"ownerId":"root","referenceName":"actors"}}
""");
            for (int index = 1; index <= 34; index++) {
              elements
                  .append(",")
                  .append(
                      """
{"localId":"command-%d","eClass":"Command","attributes":{"name":"Command %d"},"placement":{"ownerId":"root","referenceName":"commands"}}
"""
                          .formatted(index, index));
            }
            StringBuilder references = new StringBuilder();
            for (int index = 1; index <= 10; index++) {
              if (index > 1) {
                references.append(",");
              }
              references.append(
                  """
                  {"sourceId":"command-%d","referenceName":"issuedBy","targetId":"actor-0"}
                  """
                      .formatted(index));
            }
            return new AssistantReply(
                """
                {
                  "intent": "MUTATION",
                  "kind": "MODEL_DELTA",
                  "message": "ok",
                  "questions": [],
                  "elements": [%s],
                  "references": [%s],
                  "attributeUpdates": [],
                  "deletions": [],
                  "assumptions": []
                }
                """
                    .formatted(elements, references),
                "stub",
                "stub");
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
    assertEquals(1, results.get(0).toolCalls());
  }

  @Test
  void nonLiveEvalMeasuresRetrievalRecallForRequiredContracts() {
    AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
    String requiredType = schemas.coverage(ModelLevel.PIM).typeNames().get(0);
    AssistantEvalRunner runner =
        new AssistantEvalRunner(
            stubProvider(), schemas, new AssistantPatchCompleter(schemas), new ObjectMapper());
    AssistantEvalRunner.EvalPrompt prompt =
        new AssistantEvalRunner.EvalPrompt(
            "retrieval-pim-01",
            "retrieval",
            "PIM",
            "Create a small model using the first formal contract.",
            true,
            List.of(),
            "",
            List.of(requiredType),
            1,
            1,
            0,
            false);

    var results = runner.run(false, null, List.of(prompt));
    AssistantEvalRunner.EvalResult result = results.get(0);

    assertEquals(1, result.requiredContractCount());
    assertEquals(1, result.retrievedRequiredContractCount());
    assertEquals(1.0, runner.qualityGateReport(results).retrievalRecallRate());
  }

  @Test
  void qualityGateReportCapturesLatencyProviderCallsRepairRetrievalAndSourceCoverage() {
    AssistantEvalRunner runner = runner();
    List<AssistantEvalRunner.EvalResult> results =
        List.of(
            new AssistantEvalRunner.EvalResult(
                "ok",
                "create-empty",
                "PIM",
                "MODEL_DELTA",
                12,
                8,
                4,
                false,
                true,
                0,
                1,
                0,
                0,
                2,
                2,
                "",
                "",
                40,
                120),
            new AssistantEvalRunner.EvalResult(
                "source",
                "cim-source-document",
                "CIM",
                "MODEL_DELTA",
                42,
                30,
                12,
                true,
                true,
                0,
                2,
                1,
                1,
                3,
                3,
                "",
                "",
                180,
                240));

    AssistantEvalRunner.QualityGateReport report =
        runner.qualityGateReport(
            results,
            new AssistantEvalRunner.QualityGateConfig(0.95, 1.0, 1.0, 0.90, 0.5, 2.0, 500));

    assertTrue(report.passedGates(), report::summary);
    assertEquals(1.0, report.structuralPassRate());
    assertEquals(1.0, report.modelDeltaSuccessRate());
    assertEquals(1.0, report.sourceCoverageRate());
    assertEquals(1.0, report.retrievalRecallRate());
    assertEquals(240, report.p95LatencyMs());
    assertTrue(report.summary().contains("avgProviderCalls=1.50"));
  }

  @Test
  void latencyReportSeparatesProviderWaitFromBackendWork() {
    AssistantEvalRunner runner = runner();
    List<AssistantEvalRunner.EvalResult> results =
        List.of(
            new AssistantEvalRunner.EvalResult(
                "one",
                "create-empty",
                "PIM",
                "MODEL_DELTA",
                10,
                8,
                2,
                false,
                true,
                0,
                1,
                0,
                0,
                1,
                1,
                "",
                "",
                70,
                100),
            new AssistantEvalRunner.EvalResult(
                "two",
                "create-empty",
                "PIM",
                "MODEL_DELTA",
                11,
                8,
                3,
                false,
                true,
                0,
                2,
                0,
                0,
                1,
                1,
                "",
                "",
                130,
                200));

    AssistantEvalRunner.LatencyReport report = runner.latencyReport(results);

    assertEquals(100, report.p50LatencyMs());
    assertEquals(200, report.p95LatencyMs());
    assertEquals(70, report.p50ProviderWaitMs());
    assertEquals(130, report.p95ProviderWaitMs());
    assertEquals(30, report.p50BackendLatencyMs());
    assertEquals(70, report.p95BackendLatencyMs());
    assertTrue(report.summary().contains("avgProviderCalls=1.50"));
  }

  @Test
  void qualityGateReportFailsWhenAnyRedesignGateRegresses() {
    AssistantEvalRunner runner = runner();
    List<AssistantEvalRunner.EvalResult> results =
        List.of(
            new AssistantEvalRunner.EvalResult(
                "bad",
                "create-empty",
                "PIM",
                "PATCH",
                1,
                0,
                0,
                false,
                false,
                2,
                5,
                1,
                0,
                4,
                1,
                "STRUCTURE_REJECTED",
                "invalid",
                650,
                900));

    AssistantEvalRunner.QualityGateReport report =
        runner.qualityGateReport(
            results,
            new AssistantEvalRunner.QualityGateConfig(0.95, 1.0, 1.0, 0.90, 0.5, 2.0, 500));

    assertFalse(report.passedGates());
    assertTrue(
        report.violations().stream().anyMatch(item -> item.contains("structural pass rate")));
    assertTrue(report.violations().stream().anyMatch(item -> item.contains("source coverage")));
    assertTrue(
        report.violations().stream().anyMatch(item -> item.contains("retrieval contract recall")));
    assertTrue(report.violations().stream().anyMatch(item -> item.contains("average repair")));
    assertTrue(report.violations().stream().anyMatch(item -> item.contains("average provider")));
    assertTrue(report.violations().stream().anyMatch(item -> item.contains("p95 latency")));
  }

  private AssistantEvalRunner runner() {
    AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
    return new AssistantEvalRunner(
        stubProvider(), schemas, new AssistantPatchCompleter(schemas), new ObjectMapper());
  }

  private AssistantModelProvider stubProvider() {
    return new AssistantModelProvider() {
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
    };
  }
}
