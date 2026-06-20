package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import org.junit.jupiter.api.Test;

class AssistantMetamodelSchemaServiceTest {

  private final AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();

  @Test
  void canonicalizesOnlyUniqueMetamodelTypePrefixes() {
    assertEquals("ObservabilityConfig", schemas.canonicalType(ModelLevel.PIM, "ObservabilityConf"));
    assertThrows(PlatformException.class, () -> schemas.canonicalType(ModelLevel.PIM, "Event"));
  }

  @Test
  void derivesNestedPolicyContainmentsFromTheRuntimeMetamodel() {
    assertEquals(
        "retry",
        schemas.containments(ModelLevel.PIM, "ResiliencePolicy", "RetryPolicy").get(0).name());
    assertEquals(
        "alerts",
        schemas.containments(ModelLevel.PIM, "ObservabilityConfig", "AlertPolicy").get(0).name());
  }
}
