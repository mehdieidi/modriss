package io.mehdieidi.modless.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.delta.DeltaNormalizer;
import io.mehdieidi.modless.platform.assistant.delta.ModelDelta;
import io.mehdieidi.modless.platform.assistant.delta.ModelDeltaParser;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import org.junit.jupiter.api.Test;

class ModelDeltaProviderClientTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void returnsStructuredModelDeltaFromOneProviderCall() {
    CountingProvider provider =
        new CountingProvider(
            """
            {"intent":"MUTATION","kind":"MODEL_DELTA","message":"ok",
             "elements":[],"references":[],"attributeUpdates":[],"deletions":[]}
            """,
            null);
    ModelDeltaProviderClient client = client(provider);

    ModelDelta delta = client.complete(ModelLevel.PIM, prompt());

    assertEquals(ModelDelta.Kind.MODEL_DELTA, delta.kind());
    assertEquals(1, provider.calls);
  }

  @Test
  void rejectsInvalidSchemaWithoutRetryingProvider() {
    CountingProvider provider = new CountingProvider("not-json", null);
    ModelDeltaProviderClient client = client(provider);

    assertThrows(PlatformException.class, () -> client.complete(ModelLevel.PIM, prompt()));

    assertEquals(1, provider.calls);
  }

  @Test
  void propagatesProviderTimeoutWithoutRetrying() {
    CountingProvider provider =
        new CountingProvider("", new PlatformException(504, "PROVIDER_TIMEOUT"));
    ModelDeltaProviderClient client = client(provider);

    PlatformException failure =
        assertThrows(PlatformException.class, () -> client.complete(ModelLevel.PIM, prompt()));

    assertEquals(504, failure.status());
    assertEquals(1, provider.calls);
  }

  private ModelDeltaProviderClient client(AssistantModelProvider provider) {
    AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
    return new ModelDeltaProviderClient(
        provider, new ModelDeltaParser(mapper), new DeltaNormalizer(schemas));
  }

  private AssistantModelProvider.AssistantPrompt prompt() {
    return new AssistantModelProvider.AssistantPrompt(
        io.mehdieidi.modless.platform.assistant.domain.AssistantModelRole.PLANNER,
        "system",
        "user",
        java.util.List.of());
  }

  private static final class CountingProvider implements AssistantModelProvider {
    private final String content;
    private final RuntimeException failure;
    private int calls;

    private CountingProvider(String content, RuntimeException failure) {
      this.content = content;
      this.failure = failure;
    }

    @Override
    public AssistantProviderMetadata metadata() {
      return new AssistantProviderMetadata("test", "", "");
    }

    @Override
    public boolean available() {
      return true;
    }

    @Override
    public AssistantReply complete(AssistantPrompt prompt) {
      return completeStructured(prompt);
    }

    @Override
    public AssistantReply completeStructured(AssistantPrompt prompt) {
      calls++;
      if (failure != null) {
        throw failure;
      }
      return new AssistantReply(content, "test", "model");
    }
  }
}
