package io.mehdieidi.varka.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentActionSchemaTest {
  @Test
  void declaresAClosedNativeActionEnvelope() throws Exception {
    var schema = new ObjectMapper().readTree(AgentActionSchema.json());
    assertEquals("object", schema.path("type").asText());
    assertFalse(schema.path("additionalProperties").asBoolean());
    assertTrue(schema.path("required").toString().contains("arguments"));
  }
}
