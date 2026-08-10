package io.mehdieidi.varka.platform.assistant.provider.springai;

import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.ProxyOptions;
import com.google.genai.types.ProxyType;
import io.mehdieidi.varka.platform.assistant.agent.AgentActionSchema;
import io.mehdieidi.varka.platform.assistant.application.AssistantHardeningService;
import io.mehdieidi.varka.platform.assistant.application.AssistantPromptGuard;
import io.mehdieidi.varka.platform.assistant.config.AiProperties;
import io.mehdieidi.varka.platform.assistant.provider.ProxyAvailability;
import java.util.List;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

/** Google Gemini provider implemented through Spring AI's Google GenAI model. */
public class GeminiAssistantModelProvider extends AbstractAssistantModelProvider {

  private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com";

  /** Creates the provider using the Google GenAI SDK client and AI-only proxy settings. */
  public GeminiAssistantModelProvider(
      AiProperties properties,
      ProxyAvailability proxyAvailability,
      AssistantPromptGuard promptGuard,
      AssistantHardeningService hardening) {
    super(
        AiProperties.Provider.GEMINI.key(),
        properties,
        proxyAvailability,
        promptGuard,
        hardening,
        () -> chatModel(properties));
  }

  private static GoogleGenAiChatModel chatModel(AiProperties properties) {
    Client client =
        Client.builder()
            .apiKey(
                properties.gemini().apiKey().isBlank()
                    ? "not-configured"
                    : properties.gemini().apiKey())
            .clientOptions(clientOptions(properties))
            .build();
    return GoogleGenAiChatModel.builder()
        .genAiClient(client)
        .options(
            GoogleGenAiChatOptions.builder()
                .model(properties.models().model(AiProperties.Provider.GEMINI))
                .temperature(0.2)
                .build())
        .build();
  }

  private static ClientOptions clientOptions(AiProperties properties) {
    AiProperties.Proxy proxy = properties.proxyFor(AiProperties.Provider.GEMINI.key());
    ProxyOptions.Builder proxyOptions = ProxyOptions.builder();
    if (!proxy.enabled() || proxy.type() == AiProperties.ProxyType.DIRECT) {
      proxyOptions.type(ProxyType.Known.DIRECT);
    } else {
      proxyOptions
          .type(
              proxy.type() == AiProperties.ProxyType.SOCKS
                  ? ProxyType.Known.SOCKS
                  : ProxyType.Known.HTTP)
          .host(proxy.host())
          .port(proxy.port());
    }
    return ClientOptions.builder().proxyOptions(proxyOptions).build();
  }

  @Override
  protected String baseUrl() {
    return GEMINI_BASE_URL;
  }

  @Override
  protected boolean apiKeyConfigured() {
    return !properties.gemini().apiKey().isBlank();
  }

  @Override
  protected String model() {
    return properties.models().model(AiProperties.Provider.GEMINI);
  }

  @Override
  protected GoogleGenAiChatOptions.Builder options(
      String model,
      boolean toolsRequested,
      io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider.AssistantPrompt
          prompt) {
    return GoogleGenAiChatOptions.builder()
        .model(model)
        .temperature(0.2)
        .toolCallbacks(toolCallbacks(prompt))
        .maxOutputTokens(Math.min(properties.tokenBudget(), capabilities().maxCompletionTokens()));
  }

  /**
   * Exposes the agent action protocol as native Gemini functions. Gemini's structured-output schema
   * accepts an object shape but does not reliably honor the action envelope's top-level {@code
   * oneOf}; affected models return {@code null} instead of an action. Native functions keep each
   * action's exact closed schema and let the workflow execute, validate, and audit the call.
   */
  static List<ToolCallback> toolCallbacks(
      io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider.AssistantPrompt
          prompt) {
    String required = OpenAiCompatibleAssistantModelProvider.forcedToolName(prompt);
    return OpenAiCompatibleAssistantModelProvider.availableToolNames(prompt).stream()
        .filter(name -> required == null || required.equals(name))
        .map(
            name ->
                (ToolCallback)
                    FunctionToolCallback.<String, String>builder(name, arguments -> arguments)
                        .description("Varka assistant action; backend validates and executes it.")
                        .inputType(String.class)
                        .inputSchema(toolArgumentsSchema(name, prompt))
                        .build())
        .toList();
  }

  private static String toolArgumentsSchema(
      String name,
      io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider.AssistantPrompt
          prompt) {
    try {
      var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      var schema = mapper.valueToTree(AgentActionSchema.toolSchema(name, prompt.patchContracts()));
      normalizeSchemaForGemini(schema);
      return mapper.writeValueAsString(schema);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("Unable to encode Gemini assistant tool schema", ex);
    }
  }

  /** Converts equivalent JSON Schema keywords to the subset accepted by Gemini functions. */
  static void normalizeSchemaForGemini(com.fasterxml.jackson.databind.JsonNode node) {
    if (node == null) return;
    if (node.isArray()) {
      node.forEach(GeminiAssistantModelProvider::normalizeSchemaForGemini);
      return;
    }
    if (!node.isObject()) return;
    var object = (com.fasterxml.jackson.databind.node.ObjectNode) node;
    if (object.has("oneOf")) {
      object.set("anyOf", object.remove("oneOf"));
    }
    if (object.has("const")) {
      var values = new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode();
      values.add(object.remove("const"));
      object.set("enum", values);
    }
    object.properties().forEach(entry -> normalizeSchemaForGemini(entry.getValue()));
  }
}
