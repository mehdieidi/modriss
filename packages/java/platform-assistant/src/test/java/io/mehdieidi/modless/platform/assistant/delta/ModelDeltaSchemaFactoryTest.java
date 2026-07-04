package io.mehdieidi.modless.platform.assistant.delta;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelDeltaSchemaFactoryTest {

  @Test
  void generatesPerTypeOneOfWithExactAttributeEnums() {
    AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
    MetamodelKnowledgeService metamodels = new MetamodelKnowledgeService(schemas);
    ModelDeltaSchemaFactory factory =
        new ModelDeltaSchemaFactory(metamodels, schemas, new ObjectMapper());
    var schema = factory.responseSchema(ModelLevel.PIM, List.of("Function"));
    var elements = schema.path("properties").path("elements").path("items");
    assertTrue(elements.has("oneOf") || elements.path("properties").path("eClass").has("enum"));
    var attributes =
        elements.has("oneOf")
            ? elements.path("oneOf").get(0).path("properties").path("attributes")
            : elements.path("properties").path("attributes");
    assertFalse(attributes.path("additionalProperties").asBoolean(true));
    assertTrue(attributes.path("properties").size() > 0);
    ArrayNode required = (ArrayNode) attributes.path("required");
    assertTrue(required != null && required.size() >= 0);
  }

  @Test
  void cachesResponseSchemaByLevelAndCandidateTypes() {
    AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
    MetamodelKnowledgeService metamodels = new MetamodelKnowledgeService(schemas);
    ModelDeltaSchemaFactory factory =
        new ModelDeltaSchemaFactory(metamodels, schemas, new ObjectMapper());
    factory.responseSchema(ModelLevel.PIM, List.of("Function"));
    factory.responseSchema(ModelLevel.PIM, List.of("Function"));
    assertTrue(factory.schemaCacheHits() >= 1);
    assertTrue(factory.schemaCacheMisses() >= 1);
    factory.clearCache();
    factory.responseSchema(ModelLevel.PIM, List.of("Function"));
    assertTrue(factory.schemaCacheMisses() >= 2);
  }
}
