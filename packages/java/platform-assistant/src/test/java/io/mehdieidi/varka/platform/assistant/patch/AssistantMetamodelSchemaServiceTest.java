package io.mehdieidi.varka.platform.assistant.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantMetamodelSchemaServiceTest {

  private final AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();

  @Test
  void rejectsSpacedAndUnderscoredTypeGuesses() {
    assertThrows(
        PlatformException.class, () -> schemas.canonicalType(ModelLevel.PIM, "Serverless Service"));
    assertThrows(
        PlatformException.class, () -> schemas.canonicalType(ModelLevel.PIM, "Function_Contract"));
  }

  @Test
  void filtersUnknownCandidateTypesWithoutThrowing() {
    List<String> known =
        schemas.knownTypes(
            ModelLevel.PIM, List.of("Function", "Machine Operations Service", "Api", "Event"));
    assertEquals(List.of("Function", "Api"), known);
  }

  @Test
  void canonicalizesOnlyExactOrUniqueCaseInsensitiveAttributes() {
    assertEquals(
        "summary",
        schemas.canonicalAttribute(ModelLevel.CIM, "Actor", "Summary").orElseThrow().name());
    assertTrue(schemas.canonicalAttribute(ModelLevel.CIM, "Actor", "details").isEmpty());
    assertTrue(schemas.canonicalAttribute(ModelLevel.CIM, "Actor", "label").isEmpty());
  }

  @Test
  void rejectsMetamodelTypePrefixes() {
    assertThrows(
        PlatformException.class, () -> schemas.canonicalType(ModelLevel.PIM, "ObservabilityConf"));
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
    AssistantModelProvider.ContextSnippet function =
        schemas.typeContract(ModelLevel.PIM, "Function");
    assertTrue(function.content().contains("functionKind"));
    assertTrue(function.content().contains("contract -> FunctionContract required single"));
  }

  @Test
  void exposesCreatableTypesWithoutPromptKeywordRanking() {
    List<String> types =
        schemas.relevantTypes(
            ModelLevel.PIM, "Create a serverless model for vending machine backend", true, 8);

    assertEquals(types, schemas.relevantTypes(ModelLevel.PIM, "unrelated wording", true, 8));
    assertTrue(types.contains("Function"));
    assertTrue(types.contains("Api"));
    assertEquals(6, schemas.planningContracts(ModelLevel.PIM, "anything", 6, true).size());
  }

  @Test
  void exposesCombinedEcoreShaAsTheDriftKey() {
    String sha = schemas.metamodelSha(ModelLevel.CIM);
    assertTrue(sha.matches("[0-9a-f]{64}"));
  }
}
