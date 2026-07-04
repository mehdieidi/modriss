package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompleter;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Stub resilience eval coverage wired through the curated eval runner. */
class AssistantResilienceEvalTest {

  @Test
  void resiliencePromptsPassStubClassificationChecks() {
    AssistantEvalRunner runner =
        new AssistantEvalRunner(
            mock(AssistantModelProvider.class),
            new AssistantMetamodelSchemaService(),
            new AssistantPatchCompleter(new AssistantMetamodelSchemaService()),
            new ObjectMapper());
    List<AssistantEvalRunner.EvalResult> results =
        runner.run(
            false,
            null,
            runner.loadPrompts().stream()
                .filter(prompt -> "resilience".equals(prompt.category()))
                .toList());
    assertTrue(results.size() >= 3, "expected resilience prompt fixtures");
    long passed = results.stream().filter(AssistantEvalRunner.EvalResult::validationPassed).count();
    assertTrue(
        passed == results.size(),
        () ->
            "resilience stubs should pass: "
                + results.stream()
                    .filter(result -> !result.validationPassed())
                    .map(AssistantEvalRunner.EvalResult::id)
                    .toList());
  }
}
