package io.mehdieidi.modless.backend.assistant;

import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.ProxyOptions;
import com.google.genai.types.ProxyType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;

/** Google Gemini provider implemented through Spring AI's Google GenAI model. */
@Component
public class GeminiAssistantModelProvider extends AbstractAssistantModelProvider {

  private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com";

  /** Creates the provider using the Google GenAI SDK client and AI-only proxy settings. */
  public GeminiAssistantModelProvider(
      AiProperties properties,
      ProxyAvailability proxyAvailability,
      AssistantPromptGuard promptGuard,
      AssistantToolService tools,
      AssistantHardeningService hardening) {
    super(
        AiProperties.Provider.GEMINI.key(),
        properties,
        proxyAvailability,
        promptGuard,
        tools,
        hardening,
        ChatClient.create(chatModel(properties)));
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
        .defaultOptions(
            GoogleGenAiChatOptions.builder()
                .model(
                    properties
                        .models()
                        .forRole(AiProperties.Provider.GEMINI, AssistantModelRole.RESPONDER))
                .temperature(0.2)
                .build())
        .build();
  }

  private static ClientOptions clientOptions(AiProperties properties) {
    AiProperties.Proxy proxy = properties.proxy();
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
  protected String modelFor(AssistantModelRole role) {
    return properties.models().forRole(AiProperties.Provider.GEMINI, role);
  }

  @Override
  protected GoogleGenAiChatOptions options(String model) {
    return GoogleGenAiChatOptions.builder()
        .model(model)
        .temperature(0.2)
        .maxOutputTokens(Math.min(properties.tokenBudget(), 4000))
        .build();
  }

  @Override
  protected boolean registerTools(AssistantModelRole role) {
    return false;
  }
}
