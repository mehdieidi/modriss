package io.mehdieidi.modless.platform.assistant.delta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import org.junit.jupiter.api.Test;

class DeltaPlacementNamesTest {

  private final AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();

  @Test
  void resolvesServerlessServiceContainmentAlias() {
    assertEquals(
        "services",
        DeltaPlacementNames.canonicalContainmentName(
            schemas, ModelLevel.PIM, "PIMModel", "serverlessServices", "ServerlessService"));
  }

  @Test
  void serverlessServiceContainsFunctionsByContainment() {
    assertFalse(schemas.containments(ModelLevel.PIM, "ServerlessService", "Function").isEmpty());
  }

  @Test
  void resolvesFunctionContractContainmentAlias() {
    assertEquals(
        "contract",
        DeltaPlacementNames.canonicalContainmentName(
            schemas, ModelLevel.PIM, "Function", "functionContract", "FunctionContract"));
  }
}
