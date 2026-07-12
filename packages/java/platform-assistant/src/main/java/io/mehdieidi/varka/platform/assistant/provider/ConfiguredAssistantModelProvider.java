package io.mehdieidi.varka.platform.assistant.provider;

import io.mehdieidi.varka.platform.assistant.config.AiProperties;
import io.mehdieidi.varka.platform.assistant.provider.springai.GeminiAssistantModelProvider;
import io.mehdieidi.varka.platform.assistant.provider.springai.OpenAiCompatibleAssistantModelProvider;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/** Delegates assistant calls to the provider selected by configuration. */
public final class ConfiguredAssistantModelProvider implements AssistantModelProvider {

  private final AiProperties properties;
  private final OpenAiCompatibleAssistantModelProvider openai;
  private final GeminiAssistantModelProvider gemini;

  public ConfiguredAssistantModelProvider(
      AiProperties properties,
      OpenAiCompatibleAssistantModelProvider openai,
      GeminiAssistantModelProvider gemini) {
    this.properties = properties;
    this.openai = openai;
    this.gemini = gemini;
  }

  @Override
  public AssistantProviderMetadata metadata() {
    return primary().metadata();
  }

  @Override
  public boolean available() {
    return primary().available();
  }

  @Override
  public AssistantReply complete(AssistantPrompt prompt) {
    return withFallback(provider -> provider.complete(prompt));
  }

  @Override
  public AssistantReply completeStructured(AssistantPrompt prompt) {
    return withFallback(provider -> provider.completeStructured(prompt));
  }

  @Override
  public AssistantReply completeWithTools(AssistantPrompt prompt) {
    return withFallback(provider -> provider.completeWithTools(prompt));
  }

  @Override
  public AssistantReply streamWithTools(AssistantPrompt prompt, Consumer<String> deltaConsumer) {
    return withFallback(provider -> provider.streamWithTools(prompt, deltaConsumer));
  }

  @Override
  public AssistantReply streamWithTools(
      AssistantPrompt prompt, Object scopedTools, Consumer<String> deltaConsumer) {
    return withFallback(provider -> provider.streamWithTools(prompt, scopedTools, deltaConsumer));
  }

  @Override
  public AssistantReply analyzeSource(AssistantPrompt prompt, AgentProgress progress) {
    return withFallback(provider -> provider.analyzeSource(prompt, progress));
  }

  private AssistantModelProvider primary() {
    return properties.providerKind() == AiProperties.Provider.GEMINI ? gemini : openai;
  }

  private AssistantModelProvider fallback() {
    Optional<AiProperties.Provider> fallback = properties.fallbackProviderKind();
    if (fallback.isEmpty()) {
      throw new PlatformException(503, "No fallback AI provider is configured.");
    }
    return fallback.get() == AiProperties.Provider.GEMINI ? gemini : openai;
  }

  private <T> T withFallback(Function<AssistantModelProvider, T> call) {
    AssistantModelProvider configured = primary();
    if (!configured.available()) {
      throw new PlatformException(
          503,
          "The configured AI provider '"
              + configured.metadata().provider()
              + "' is not currently available.");
    }
    try {
      return call.apply(configured);
    } catch (PlatformException failure) {
      if (failure.status() != 429 || properties.fallbackProviderKind().isEmpty()) {
        throw failure;
      }
      AssistantModelProvider alternate = fallback();
      if (!alternate.available()) {
        throw failure;
      }
      return call.apply(alternate);
    }
  }
}
