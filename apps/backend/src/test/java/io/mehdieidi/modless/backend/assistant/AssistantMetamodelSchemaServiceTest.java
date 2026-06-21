package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.List;
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

  @Test
  void exposesRequiredFeaturesForTypesNamedInTheRequest() {
    var snippets = schemas.planningContracts(ModelLevel.PIM, "Add a Function handler", 4);

    assertTrue(snippets.stream().anyMatch(snippet -> "Function".equals(snippet.title())));
    AssistantModelProvider.ContextSnippet function =
        snippets.stream()
            .filter(snippet -> "Function".equals(snippet.title()))
            .findFirst()
            .orElseThrow();
    assertTrue(function.content().contains("functionKind"));
    assertTrue(function.content().contains("contract -> FunctionContract required single"));
  }

  @Test
  void ranksRelevantTypesForServerlessCreationPrompts() {
    List<String> types =
        schemas.relevantTypes(
            ModelLevel.PIM, "Create a serverless model for vending machine backend", true, 8);

    assertTrue(types.contains("ServerlessService"));
    assertTrue(types.contains("Function"));
    assertTrue(types.contains("Api"));
    var snippets =
        schemas.planningContracts(
            ModelLevel.PIM, "Create a serverless model for vending machine backend", 6, true);
    assertTrue(snippets.size() >= 4);
    assertTrue(
        snippets.stream()
            .map(AssistantModelProvider.ContextSnippet::title)
            .anyMatch("Function"::equals));
  }
}
