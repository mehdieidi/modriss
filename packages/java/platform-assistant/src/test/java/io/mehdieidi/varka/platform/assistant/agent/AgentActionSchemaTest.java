package io.mehdieidi.varka.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.AttributeContract;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.ReferenceContract;
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
  void doesNotExposeDeprecatedSourcePlanningAction() throws Exception {
    var schema = new ObjectMapper().readTree(AgentActionSchema.json());

    assertFalse(AgentActionSchema.toolNames().contains("plan_source_model"));
    assertFalse(schema.toString().contains("plan_source_model"));
  }

  @Test
  void keepsCreateSchemaFlatForStructuredOutputProviders() throws Exception {
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
    var create = schema.path("properties").path("creates").path("items");
    var attributes =
        schema
            .path("properties")
            .path("creates")
            .path("items")
            .path("properties")
            .path("attributes");
    assertFalse(create.has("oneOf"));
    assertTrue(attributes.path("additionalProperties").asBoolean(false));
  }

  @Test
  void avoidsUnsupportedNotKeywordBeforeTypesAreDescribed() throws Exception {
    var schema =
        new ObjectMapper()
            .valueToTree(AgentActionSchema.toolSchema("apply_draft_patch", List.of()));
    var create = schema.path("properties").path("creates").path("items");
    assertEquals("object", create.path("type").asText());
    assertFalse(create.has("not"));
    assertEquals(
        "__describe_types_required__",
        create.path("properties").path("eClass").path("enum").get(0).asText());
  }

  @Test
  void exposesStructuredInspectSelectorsInToolSchema() throws Exception {
    var schema =
        new ObjectMapper().valueToTree(AgentActionSchema.toolSchema("inspect_model", List.of()));
    var properties = schema.path("properties");

    assertTrue(properties.has("id"));
    assertEquals("array", properties.path("ids").path("type").asText());
    assertEquals("array", properties.path("eClasses").path("type").asText());
    assertEquals("array", properties.path("ownerIds").path("type").asText());
    assertEquals("string", properties.path("query").path("type").asText());
    assertEquals("integer", properties.path("page").path("type").asText());
    assertEquals("integer", properties.path("pageSize").path("type").asText());
    assertEquals(100, properties.path("pageSize").path("maximum").asInt());
  }

  @Test
  void boundsOnePatchToolCallToADurableCheckpointSlice() throws Exception {
    var contract =
        new TypeContract(
            ModelLevel.PIM,
            "Function",
            true,
            List.of(),
            List.of(new AttributeContract("name", "EString", true, List.of())),
            List.of());
    var schema =
        new ObjectMapper()
            .valueToTree(AgentActionSchema.toolSchema("apply_draft_patch", List.of(contract)));
    var properties = schema.path("properties");

    assertEquals(48, properties.path("creates").path("maxItems").asInt());
    assertEquals(96, properties.path("connections").path("maxItems").asInt());
    assertEquals(64, properties.path("evidence").path("maxItems").asInt());
    assertEquals(48, properties.path("updates").path("maxItems").asInt());
    assertTrue(
        properties
            .path("updates")
            .path("items")
            .path("properties")
            .path("attributes")
            .path("properties")
            .has("name"));
  }

  @Test
  void excludesRootModelTypesFromCreatablePatchVariants() throws Exception {
    var root =
        new TypeContract(
            ModelLevel.CIM,
            "CIMModel",
            true,
            List.of(),
            List.of(),
            List.of(
                new ReferenceContract("requirements", "Requirement", false, true, true, false)));
    var requirement =
        new TypeContract(
            ModelLevel.CIM,
            "Requirement",
            true,
            List.of(),
            List.of(new AttributeContract("name", "EString", true, List.of())),
            List.of());
    var schema =
        new ObjectMapper()
            .valueToTree(
                AgentActionSchema.toolSchema("apply_draft_patch", List.of(root, requirement)));
    var eClasses =
        schema
            .path("properties")
            .path("creates")
            .path("items")
            .path("properties")
            .path("eClass")
            .path("enum");
    assertEquals(1, eClasses.size());
    assertEquals("Requirement", eClasses.get(0).asText());
  }

  @Test
  void narrowsPatchReferencesToDescribedWritableEcoreReferences() throws Exception {
    var root =
        new TypeContract(
            ModelLevel.CIM,
            "CIMModel",
            false,
            List.of(),
            List.of(),
            List.of(
                new ReferenceContract("requirements", "Requirement", false, true, true, false)));
    var requirement =
        new TypeContract(
            ModelLevel.CIM,
            "Requirement",
            true,
            List.of(),
            List.of(new AttributeContract("name", "EString", true, List.of())),
            List.of(
                new ReferenceContract("dependsOn", "Requirement", false, true, false, false),
                new ReferenceContract("source", "Requirement", false, false, false, true)));
    var schema =
        new ObjectMapper()
            .valueToTree(
                AgentActionSchema.toolSchema("apply_draft_patch", List.of(root, requirement)));

    var createReference =
        schema
            .path("properties")
            .path("creates")
            .path("items")
            .path("properties")
            .path("reference")
            .path("enum");
    assertEquals(1, createReference.size());
    assertEquals("requirements", createReference.get(0).asText());

    var connectionReference =
        schema
            .path("properties")
            .path("connections")
            .path("items")
            .path("properties")
            .path("reference")
            .path("enum");
    assertEquals(1, connectionReference.size());
    assertEquals("dependsOn", connectionReference.get(0).asText());
  }
}
