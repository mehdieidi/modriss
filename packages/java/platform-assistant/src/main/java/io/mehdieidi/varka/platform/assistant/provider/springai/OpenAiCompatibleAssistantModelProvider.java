package io.mehdieidi.varka.platform.assistant.provider.springai;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.mehdieidi.varka.platform.assistant.application.AssistantHardeningService;
import io.mehdieidi.varka.platform.assistant.application.AssistantPromptGuard;
import io.mehdieidi.varka.platform.assistant.config.AiProperties;
import io.mehdieidi.varka.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.varka.platform.assistant.provider.ProxyAvailability;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/** OpenAI-compatible provider implemented through Spring AI's OpenAI chat model. */
public class OpenAiCompatibleAssistantModelProvider extends AbstractAssistantModelProvider {

  /** Creates the provider using the dedicated AI-only HTTP client. */
  public OpenAiCompatibleAssistantModelProvider(
      AiProperties properties,
      ProxyAvailability proxyAvailability,
      AssistantPromptGuard promptGuard,
      AssistantHardeningService hardening) {
    super(
        AiProperties.Provider.OPENAI.key(),
        properties,
        proxyAvailability,
        promptGuard,
        hardening,
        () -> chatModel(properties));
  }

  private static OpenAiChatModel chatModel(AiProperties properties) {
    String apiKey =
        properties.openaiCompatible().apiKey().isBlank()
            ? "sk-not-configured"
            : properties.openaiCompatible().apiKey();
    OpenAIOkHttpClient.Builder client =
        OpenAIOkHttpClient.builder()
            .fromEnv()
            .baseUrl(properties.openaiCompatible().baseUrl())
            .apiKey(apiKey)
            .timeout(properties.requestTimeout())
            .maxRetries(0);
    AiProperties.Proxy proxy = properties.proxyFor(AiProperties.Provider.OPENAI.key());
    if (proxy.enabled() && proxy.type() != AiProperties.ProxyType.DIRECT) {
      client.proxy(
          new java.net.Proxy(
              proxy.type() == AiProperties.ProxyType.SOCKS
                  ? java.net.Proxy.Type.SOCKS
                  : java.net.Proxy.Type.HTTP,
              proxy.address()));
    }
    return OpenAiChatModel.builder()
        .openAiClient(client.build())
        .options(
            OpenAiChatOptions.builder()
                .model(
                    properties
                        .models()
                        .forRole(AiProperties.Provider.OPENAI, AssistantModelRole.RESPONDER))
                .temperature(0.2)
                .build())
        .build();
  }

  @Override
  protected String baseUrl() {
    return properties.openaiCompatible().baseUrl();
  }

  @Override
  protected boolean apiKeyConfigured() {
    return !properties.openaiCompatible().apiKey().isBlank();
  }

  @Override
  protected String modelFor(AssistantModelRole role) {
    return properties.models().forRole(AiProperties.Provider.OPENAI, role);
  }

  @Override
  protected OpenAiChatOptions.Builder options(String model, AssistantModelRole role) {
    OpenAiChatOptions.Builder builder =
        OpenAiChatOptions.builder()
            .model(model)
            .temperature(0.2)
            .maxCompletionTokens(Math.min(properties.tokenBudget(), completionLimit(role)));
    // Several OpenAI-compatible proxies reject JSON Schema with a polymorphic arguments object.
    // The action codec and model-command compiler still validate the closed envelope and every
    // nested model command; JSON_OBJECT keeps this provider path interoperable.
    builder.responseFormat(
        OpenAiChatModel.ResponseFormat.builder()
            .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
            .build());
    return builder;
  }

  private int completionLimit(AssistantModelRole role) {
    return 3000;
  }
}
