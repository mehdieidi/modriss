package io.mehdieidi.varka.platform.assistant.provider.springai;

import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.ProxyOptions;
import com.google.genai.types.ProxyType;
import io.mehdieidi.varka.platform.assistant.application.AssistantHardeningService;
import io.mehdieidi.varka.platform.assistant.application.AssistantPromptGuard;
import io.mehdieidi.varka.platform.assistant.config.AiProperties;
import io.mehdieidi.varka.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.varka.platform.assistant.provider.ProxyAvailability;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

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
        configured(properties) ? ChatClient.create(chatModel(properties)) : null);
  }

  private static boolean configured(AiProperties properties) {
    return properties.enabled() && !properties.gemini().apiKey().isBlank();
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
  protected GoogleGenAiChatOptions.Builder options(String model, AssistantModelRole role) {
    return GoogleGenAiChatOptions.builder()
        .model(model)
        .temperature(0.2)
        .maxOutputTokens(Math.min(properties.tokenBudget(), completionLimit(role)));
  }

  private int completionLimit(AssistantModelRole role) {
    return 3000;
  }
}
