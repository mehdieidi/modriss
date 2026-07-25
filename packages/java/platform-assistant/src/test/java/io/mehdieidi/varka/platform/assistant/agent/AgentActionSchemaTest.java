package io.mehdieidi.varka.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.AttributeContract;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AgentActionSchemaTest {
  @Test
  void declaresAClosedNativeActionEnvelope() throws Exception {
    var schema = new ObjectMapper().readTree(AgentActionSchema.json());
    assertEquals("object", schema.path("type").asText());
    assertTrue(schema.path("oneOf").isArray());
    assertTrue(schema.path("oneOf").size() >= 4);
  }

  @Test
  void generatesClosedSchemasForEveryNativeTool() {
    for (String tool : AgentActionSchema.toolNames()) {
      var schema = AgentActionSchema.toolSchema(tool);
      assertEquals("object", schema.get("type"));
      assertEquals(Boolean.FALSE, schema.get("additionalProperties"));
    }
  }

  @Test
  void closesPatchAttributesOverTheDescribedEcoreTypes() throws Exception {
    var contract =
        new TypeContract(
            ModelLevel.PIM,
            "RetryPolicy",
            true,
            List.of(),
            List.of(new AttributeContract("backoffSeconds", "EInt", false, List.of())),
            List.of());
    var schema =
        new ObjectMapper()
            .valueToTree(AgentActionSchema.toolSchema("apply_draft_patch", List.of(contract)));
    var attributes =
        schema
            .path("properties")
            .path("creates")
            .path("items")
            .path("oneOf")
            .get(0)
            .path("properties")
            .path("attributes");
    assertFalse(attributes.path("additionalProperties").asBoolean(true));
    assertTrue(attributes.path("properties").has("backoffSeconds"));
    assertFalse(attributes.path("properties").has("maxRetries"));
  }
}
