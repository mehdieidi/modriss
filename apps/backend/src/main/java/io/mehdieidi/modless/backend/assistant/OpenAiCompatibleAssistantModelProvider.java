package io.mehdieidi.modless.backend.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * OpenAI-compatible provider implemented through Spring AI's OpenAI chat model.
 */
@Component
public class OpenAiCompatibleAssistantModelProvider extends AbstractAssistantModelProvider {

    /**
     * Creates the provider using the dedicated AI-only HTTP client.
     */
    public OpenAiCompatibleAssistantModelProvider(AiProperties properties,
            ProxyAvailability proxyAvailability,
            AssistantPromptGuard promptGuard,
            AssistantToolService tools,
            AssistantHardeningService hardening,
            @Qualifier("aiRestClientBuilder") RestClient.Builder restClientBuilder) {
        super(AiProperties.Provider.OPENAI.key(), properties, proxyAvailability, promptGuard, tools,
                hardening, ChatClient.create(chatModel(properties, restClientBuilder)));
    }

    private static OpenAiChatModel chatModel(AiProperties properties,
            RestClient.Builder restClientBuilder) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(properties.openaiCompatible().baseUrl())
                .apiKey(properties.openaiCompatible().apiKey().isBlank() ? "not-configured"
                        : properties.openaiCompatible().apiKey())
                .restClientBuilder(restClientBuilder)
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(properties.models().forRole(AiProperties.Provider.OPENAI,
                                AssistantModelRole.RESPONDER))
                        .temperature(0.2)
                        .build())
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
    protected OpenAiChatOptions options(String model) {
        return OpenAiChatOptions.builder().model(model).temperature(0.2)
                .maxCompletionTokens(Math.min(properties.tokenBudget(), 4000)).build();
    }
}
