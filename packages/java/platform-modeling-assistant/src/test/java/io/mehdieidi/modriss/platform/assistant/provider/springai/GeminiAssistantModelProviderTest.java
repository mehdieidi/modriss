package io.mehdieidi.modriss.platform.assistant.provider.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.mehdieidi.modriss.platform.assistant.provider.AssistantModelProvider.AssistantPrompt;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiAssistantModelProviderTest {

  @Test
  void conversationsHaveNoToolsOrOutputSchemaWhileRoutingRemainsStructured() {
    var properties = mock(io.mehdieidi.modriss.platform.assistant.config.AiProperties.class);
    when(properties.tokenBudget()).thenReturn(16000);
    when(properties.providerCapabilities())
        .thenReturn(
            io.mehdieidi.modriss.platform.assistant.provider.AssistantModelProvider
                .ProviderCapabilities.defaults());
    var provider = new GeminiAssistantModelProvider(properties, null, null, null);
    var conversation = provider.textOptions("test").build();
    assertTrue(
        conversation.getToolCallbacks() == null || conversation.getToolCallbacks().isEmpty());
    assertTrue(conversation.getOutputSchema() == null || conversation.getOutputSchema().isBlank());
    assertTrue(
        conversation.getResponseMimeType() == null
            || !conversation.getResponseMimeType().equals("application/json"));
    var routing =
        provider
            .options(
                "test",
                false,
                new AssistantPrompt("system", "user", List.of(), List.of(), "assistant_strategy"))
            .build();
    assertEquals("application/json", routing.getResponseMimeType());
    assertTrue(routing.getOutputSchema().contains("strategy"));
  }

  @Test
  void exposesEveryClosedAgentActionAsANativeFunction() {
    var callbacks =
        GeminiAssistantModelProvider.toolCallbacks(
            new AssistantPrompt("system", "user", List.of(), List.of()));

    assertEquals(5, callbacks.size());
    assertEquals(
        List.of(
            "plan_model_edit",
            "inspect_model",
            "describe_types",
            "commit_model_batch",
            "respond_to_user"),
        callbacks.stream().map(callback -> callback.getToolDefinition().name()).toList());
    assertTrue(
        callbacks.stream()
            .allMatch(callback -> callback.getToolDefinition().inputSchema().contains("object")));
  }

  @Test
  void narrowsGeminiToTheWorkflowRequiredAction() {
    var callbacks =
        GeminiAssistantModelProvider.toolCallbacks(
            new AssistantPrompt("system", "user", List.of(), List.of(), "commit_model_batch"));

    assertEquals(1, callbacks.size());
    assertEquals("commit_model_batch", callbacks.get(0).getToolDefinition().name());
  }

  @Test
  void translatesExactContractUnionsToGeminiSchemaKeywords() throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var schema =
        mapper.readTree(
            """
            {"oneOf":[{"properties":{"eClass":{"type":"string","const":"Api"}}}]}
            """);

    GeminiAssistantModelProvider.normalizeSchemaForGemini(schema);

    assertFalse(schema.has("oneOf"));
    assertTrue(schema.has("anyOf"));
    assertFalse(schema.at("/anyOf/0/properties/eClass").has("const"));
    assertEquals("Api", schema.at("/anyOf/0/properties/eClass/enum/0").asText());
  }
}
