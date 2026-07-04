package io.mehdieidi.modless.platform.assistant.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantEvalModelFixturesTest {

  @Test
  void discoversPsmRootContainmentTypes() {
    AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
    List<String> rootOwned =
        schemas.coverage(ModelLevel.PSM).typeNames().stream()
            .filter(type -> schemas.rootContainment(ModelLevel.PSM, type).isPresent())
            .sorted()
            .toList();
    assertFalse(
        rootOwned.isEmpty(),
        () -> "Expected at least one PSM type with root containment: " + rootOwned);
    System.out.println("PSM root-owned types: " + rootOwned);
    System.out.println(
        "TraceModel containment: " + schemas.rootContainment(ModelLevel.PSM, "TraceModel"));
  }

  @Test
  void printsPsmRootType() {
    AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
    System.out.println("PSM root type: " + schemas.rootType(ModelLevel.PSM));
    assertFalse(schemas.rootType(ModelLevel.PSM).isBlank());
  }

  @Test
  void pimWorkflowHasRootContainment() {
    AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
    assertTrue(schemas.rootContainment(ModelLevel.PIM, "Workflow").isPresent());
    assertTrue(schemas.rootContainment(ModelLevel.PIM, "Function").isPresent());
  }
}
