package io.mehdieidi.modless.backend.assistant;

import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Delegates assistant calls to the provider selected by configuration. */
final class ConfiguredAssistantModelProvider implements AssistantModelProvider {

  private static final Logger log = LoggerFactory.getLogger(ConfiguredAssistantModelProvider.class);

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
    return candidates().stream().anyMatch(AssistantModelProvider::available);
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

  private AssistantModelProvider primary() {
    return properties.providerKind() == AiProperties.Provider.GEMINI ? gemini : openai;
  }

  private List<AssistantModelProvider> candidates() {
    return primary() == openai ? List.of(openai, gemini) : List.of(gemini, openai);
  }

  private <T> T withFallback(Function<AssistantModelProvider, T> call) {
    PlatformException last = null;
    for (AssistantModelProvider candidate : candidates()) {
      if (!candidate.available()) {
        continue;
      }
      try {
        return call.apply(candidate);
      } catch (PlatformException failure) {
        if (!recoverable(failure)) {
          throw failure;
        }
        last = failure;
        log.warn(
            "Assistant provider failed; trying configured fallback provider={} status={}",
            candidate.metadata().provider(),
            failure.status());
      }
    }
    if (last != null) {
      throw last;
    }
    throw new PlatformException(503, "No configured AI provider is currently available.");
  }

  private boolean recoverable(PlatformException failure) {
    return failure.status() == 429 || failure.status() >= 500;
  }
}
