package io.mehdieidi.modless.backend.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** OpenAI-compatible provider implemented through Spring AI's OpenAI chat model. */
@Component
public class OpenAiCompatibleAssistantModelProvider extends AbstractAssistantModelProvider {

  /** Creates the provider using the dedicated AI-only HTTP client. */
  public OpenAiCompatibleAssistantModelProvider(
      AiProperties properties,
      ProxyAvailability proxyAvailability,
      AssistantPromptGuard promptGuard,
      AssistantToolService tools,
      AssistantHardeningService hardening,
      SemanticModelPatchParser patchParser,
      @Qualifier("aiRestClientBuilder") RestClient.Builder restClientBuilder) {
    super(
        AiProperties.Provider.OPENAI.key(),
        properties,
        proxyAvailability,
        promptGuard,
        tools,
        hardening,
        patchParser,
        ChatClient.create(chatModel(properties, restClientBuilder)));
  }

  private static OpenAiChatModel chatModel(
      AiProperties properties, RestClient.Builder restClientBuilder) {
    OpenAiApi api =
        OpenAiApi.builder()
            .baseUrl(properties.openaiCompatible().baseUrl())
            .apiKey(
                properties.openaiCompatible().apiKey().isBlank()
                    ? "not-configured"
                    : properties.openaiCompatible().apiKey())
            .restClientBuilder(restClientBuilder)
            .build();
    OpenAiChatModel model =
        OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(
                OpenAiChatOptions.builder()
                    .model(
                        properties
                            .models()
                            .forRole(AiProperties.Provider.OPENAI, AssistantModelRole.RESPONDER))
                    .temperature(0.2)
                    .build())
            // The assistant hardening layer owns retries and circuit breaking.
            .retryTemplate(RetryTemplate.builder().maxAttempts(1).noBackoff().build())
            .build();
    return model;
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
  protected OpenAiChatOptions options(String model, AssistantModelRole role) {
    OpenAiChatOptions.Builder builder =
        OpenAiChatOptions.builder()
            .model(model)
            .temperature(0.2)
            .maxCompletionTokens(Math.min(properties.tokenBudget(), completionLimit(role)));
    if (role == AssistantModelRole.PLANNER) {
      builder.responseFormat(new ResponseFormat(ResponseFormat.Type.JSON_OBJECT, null));
    }
    return builder.build();
  }

  private int completionLimit(AssistantModelRole role) {
    return switch (role) {
      case PLANNER -> 6000;
      case SUMMARIZER -> 800;
      case RESPONDER -> 3000;
    };
  }

  @Override
  protected boolean registerTools(AssistantModelRole role) {
    return role != AssistantModelRole.PLANNER;
  }
}
