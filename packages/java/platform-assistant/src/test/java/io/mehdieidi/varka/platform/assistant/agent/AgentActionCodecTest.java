package io.mehdieidi.varka.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.mehdieidi.varka.platform.kernel.PlatformException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentActionCodecTest {

  private final AgentActionCodec codec = new AgentActionCodec(new ObjectMapper());

  @Test
  void acceptsJsonWrappedInAMarkdownFence() {
    AgentAction action =
        codec.parse(
            """
            ```json
            {"tool":"describe_types","arguments":{"names":["Workflow"]}}
            ```
            """);

    assertEquals(AgentAction.Kind.DESCRIBE_TYPES, action.tool());
    assertEquals("Workflow", action.arguments().path("names").get(0).asText());
  }

  @Test
  void acceptsOneJsonObjectAfterAProviderPreamble() {
    AgentAction action =
        codec.parse(
            "I will use the modeling tool now."
                + " {\"tool\":\"answer_user\",\"arguments\":{\"message\":\"Done\"}}");

    assertEquals(AgentAction.Kind.ANSWER_USER, action.tool());
    assertEquals("Done", action.arguments().path("message").asText());
  }

  @Test
  void rejectsActionWithoutObjectArguments() {
    assertThrows(
        PlatformException.class,
        () -> codec.parse("{\"tool\":\"describe_types\",\"arguments\":[]}"));
  }
}
