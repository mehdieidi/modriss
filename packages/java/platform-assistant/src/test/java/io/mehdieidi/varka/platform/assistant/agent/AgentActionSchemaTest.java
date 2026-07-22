package io.mehdieidi.varka.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentActionSchemaTest {
  @Test
  void declaresAClosedNativeActionEnvelope() throws Exception {
    var schema = new ObjectMapper().readTree(AgentActionSchema.json());
    assertEquals("object", schema.path("type").asText());
    assertTrue(schema.path("required").toString().contains("arguments"));
    assertTrue(schema.path("properties").path("arguments").path("properties").isObject());
    assertTrue(schema.path("properties").path("arguments").path("required").isArray());
  }

  @Test
  void generatesClosedSchemasForEveryNativeTool() {
    for (String tool : AgentActionSchema.toolNames()) {
      var schema = AgentActionSchema.toolSchema(tool);
      assertEquals("object", schema.get("type"));
      assertEquals(Boolean.FALSE, schema.get("additionalProperties"));
    }
  }
}
