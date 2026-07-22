package io.mehdieidi.varka.platform.assistant.provider.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleAssistantModelProviderTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void translatesExactlyOneNativeResponseToolCallToTheClosedActionEnvelope() throws Exception {
    var message =
        mapper.readTree(
            "{\"tool_calls\":[{\"function\":{\"name\":\"respond_to_user\",\"arguments\":\"{\\\"message\\\":\\\"CIM"
                + " explains business intent.\\\"}\"}}]}");

    assertEquals(
        "{\"action\":\"answer_user\",\"arguments\":{\"message\":\"CIM explains business"
            + " intent.\"}}",
        OpenAiCompatibleAssistantModelProvider.nativeToolAction(mapper, message));
  }

  @Test
  void rejectsMissingOrNonExecutableNativeToolCalls() throws Exception {
    var noCall = mapper.readTree("{}");
    var search =
        mapper.readTree(
            "{\"tool_calls\":[{\"function\":{\"name\":\"search_language\",\"arguments\":\"{}\"}}]}");

    assertEquals(
        422,
        assertThrows(
                PlatformException.class,
                () -> OpenAiCompatibleAssistantModelProvider.nativeToolAction(mapper, noCall))
            .status());
    assertEquals(
        422,
        assertThrows(
                PlatformException.class,
                () -> OpenAiCompatibleAssistantModelProvider.nativeToolAction(mapper, search))
            .status());
  }
}
