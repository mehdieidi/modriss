package io.mehdieidi.modless.platform.assistant.metamodel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import org.junit.jupiter.api.Test;

class MetamodelDriftReportTest {

  @Test
  void combinedEcoreIndexMatchesRuntimeSchemaCreatableTypes() {
    AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
    MetamodelKnowledgeService metamodels = new MetamodelKnowledgeService(schemas);
    var drift = MetamodelDriftReport.drift(metamodels.index(), schemas);
    assertTrue(
        drift.isEmpty(),
        () -> "Unexpected metamodel drift between combined Ecore and runtime schema:\n" + drift);
  }
}
