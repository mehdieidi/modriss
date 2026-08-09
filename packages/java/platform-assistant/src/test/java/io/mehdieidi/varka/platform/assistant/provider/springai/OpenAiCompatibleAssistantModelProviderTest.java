package io.mehdieidi.varka.platform.assistant.provider.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider.AssistantPrompt;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleAssistantModelProviderTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void translatesExactlyOneNativeResponseToolCallToTheClosedActionEnvelope() throws Exception {
    var message =
        mapper.readTree(
            "{\"tool_calls\":[{\"function\":{\"name\":\"respond_to_user\",\"arguments\":\"{\\\"message\\\":\\\"CIM"
                + " explains business intent.\\\"}\"}}]}");

    assertEquals(
        "{\"action\":\"answer_user\",\"arguments\":{\"message\":\"CIM explains business"
            + " intent.\"}}",
        OpenAiCompatibleAssistantModelProvider.nativeToolAction(mapper, message));
  }

  @Test
  void rejectsMissingOrNonExecutableNativeToolCalls() throws Exception {
    var noCall = mapper.readTree("{}");
    var search =
        mapper.readTree(
            "{\"tool_calls\":[{\"function\":{\"name\":\"search_language\",\"arguments\":\"{}\"}}]}");

    assertEquals(
        422,
        assertThrows(
                PlatformException.class,
                () -> OpenAiCompatibleAssistantModelProvider.nativeToolAction(mapper, noCall))
            .status());
    assertEquals(
        422,
        assertThrows(
                PlatformException.class,
                () -> OpenAiCompatibleAssistantModelProvider.nativeToolAction(mapper, search))
            .status());
  }

  @Test
  void treatsTruncatedNativeToolArgumentsAsRetryableProviderFailure() throws Exception {
    var message =
        mapper.readTree(
            "{\"tool_calls\":[{\"function\":{\"name\":\"commit_model_batch\",\"arguments\":\"{\\\"creates\\\":[\"}}]}");

    assertEquals(
        502,
        assertThrows(
                PlatformException.class,
                () -> OpenAiCompatibleAssistantModelProvider.nativeToolAction(mapper, message))
            .status());
  }

  @Test
  void acceptsStrictActionJsonContentWhenCompatibleProviderSkipsToolCalls() throws Exception {
    var message =
        mapper.readTree(
            "{\"content\":\"{\\\"action\\\":\\\"describe_types\\\",\\\"arguments\\\":{\\\"names\\\":[\\\"Requirement\\\"]}}\"}");

    assertEquals(
        "{\"action\":\"describe_types\",\"arguments\":{\"names\":[\"Requirement\"]}}",
        OpenAiCompatibleAssistantModelProvider.nativeToolAction(mapper, message));
  }

  @Test
  void forcesNativePatchToolAfterLegacyCommitInstruction() {
    assertTrue(
        OpenAiCompatibleAssistantModelProvider.shouldForcePatchTool(
            "The next action must be commit_model_batch; do not answer."));
    assertTrue(
        OpenAiCompatibleAssistantModelProvider.shouldForcePatchTool(
            "Return one corrected JSON object."));
    assertFalse(
        OpenAiCompatibleAssistantModelProvider.shouldForcePatchTool(
            "Choose the exact types needed for the request."));
  }

  @Test
  void forcesOnlyTheValidNextNativeToolAtWorkflowGates() {
    assertEquals(
        "plan_model_edit",
        OpenAiCompatibleAssistantModelProvider.forcedToolName(
            new AssistantPrompt(
                "system",
                "SOURCE-TO-MODEL MODE: First return plan_model_edit with a durable progressive CIM"
                    + " modeling plan.",
                List.of(),
                List.of())));
    assertEquals(
        "describe_types",
        OpenAiCompatibleAssistantModelProvider.forcedToolName(
            new AssistantPrompt(
                "system",
                "Current durable modeling checkpoint:\n"
                    + "{}\n"
                    + "Now retrieve exact metamodel contracts using describe_types.",
                List.of(),
                List.of())));
    assertEquals(
        "commit_model_batch",
        OpenAiCompatibleAssistantModelProvider.forcedToolName(
            new AssistantPrompt(
                "system",
                "The next action must be commit_model_batch using these exact contracts.",
                List.of(),
                List.of())));
  }

  @Test
  void usesTypedRequiredToolWithoutInspectingPromptText() {
    assertEquals(
        "commit_model_batch",
        OpenAiCompatibleAssistantModelProvider.forcedToolName(
            new AssistantPrompt(
                "system",
                "an ordinary explanation request",
                List.of(),
                List.of(),
                "commit_model_batch")));
  }
}
