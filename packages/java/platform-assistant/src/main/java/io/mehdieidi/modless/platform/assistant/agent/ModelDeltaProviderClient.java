package io.mehdieidi.modless.platform.assistant.agent;

import io.mehdieidi.modless.platform.assistant.delta.DeltaNormalizer;
import io.mehdieidi.modless.platform.assistant.delta.ModelDelta;
import io.mehdieidi.modless.platform.assistant.delta.ModelDeltaParser;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;

/** Provider client for the single ModelDelta mutation protocol. */
public class ModelDeltaProviderClient {

  private final AssistantModelProvider provider;
  private final ModelDeltaParser parser;
  private final DeltaNormalizer normalizer;

  public ModelDeltaProviderClient(
      AssistantModelProvider provider, ModelDeltaParser parser, DeltaNormalizer normalizer) {
    this.provider = provider;
    this.parser = parser;
    this.normalizer = normalizer;
  }

  /** Performs one structured provider call and rejects invalid ModelDelta output immediately. */
  public ModelDelta complete(ModelLevel level, AssistantModelProvider.AssistantPrompt prompt) {
    AssistantModelProvider.AssistantReply reply = provider.completeStructured(prompt);
    try {
      return normalizer.normalize(level, parser.parse(reply.content()));
    } catch (RuntimeException failure) {
      if (failure instanceof PlatformException platform) {
        throw platform;
      }
      throw new PlatformException(422, "AI assistant ModelDelta was rejected by schema parsing.");
    }
  }
}
