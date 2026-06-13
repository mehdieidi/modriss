package io.mehdieidi.modless.backend.assistant;

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
    return delegate().metadata();
  }

  @Override
  public boolean available() {
    return delegate().available();
  }

  @Override
  public AssistantReply complete(AssistantPrompt prompt) {
    return delegate().complete(prompt);
  }

  @Override
  public SemanticModelPatch proposePatch(AssistantPrompt prompt) {
    return delegate().proposePatch(prompt);
  }

  private AssistantModelProvider delegate() {
    return properties.providerKind() == AiProperties.Provider.GEMINI ? gemini : openai;
  }
}
