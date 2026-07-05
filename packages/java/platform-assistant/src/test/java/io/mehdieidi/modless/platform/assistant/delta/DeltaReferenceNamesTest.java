package io.mehdieidi.modless.platform.assistant.delta;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import org.junit.jupiter.api.Test;

class DeltaReferenceNamesTest {

  private final AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();

  @Test
  void resolvesFunctionPublishesEventsAliasToPublishesReference() {
    assertEquals(
        "publishes",
        DeltaReferenceNames.canonicalReferenceName(
            schemas, ModelLevel.PIM, "Function", "publishesEvents", "EventType"));
  }
}
