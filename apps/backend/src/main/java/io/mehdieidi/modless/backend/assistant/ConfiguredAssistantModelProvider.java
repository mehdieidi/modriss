package io.mehdieidi.modless.backend.assistant;

import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.Optional;
import java.util.function.Function;

/** Delegates assistant calls to the provider selected by configuration. */
final class ConfiguredAssistantModelProvider implements AssistantModelProvider {

  private final AiProperties properties;
  private final OpenAiCompatibleAssistantModelProvider openai;
  private final GeminiAssistantModelProvider gemini;

  ConfiguredAssistantModelProvider(
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
  public AssistantTurnPlan planTurn(AssistantPrompt prompt) {
    return withFallback(provider -> provider.planTurn(prompt));
  }

  @Override
  public SemanticModelPatch proposePatch(AssistantPrompt prompt) {
    return withFallback(provider -> provider.proposePatch(prompt));
  }

  @Override
  public AgentLoopResult planMutationTurn(AssistantPrompt prompt, AgentProgress progress) {
    return withFallback(provider -> provider.planMutationTurn(prompt, progress));
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
