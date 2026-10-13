package io.mehdieidi.modriss.platform.assistant.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modriss.platform.assistant.metamodel.AssistantMetamodelMode;
import io.mehdieidi.modriss.platform.assistant.metamodel.AssistantMetamodelProfile;
import io.mehdieidi.modriss.platform.assistant.metamodel.AssistantMetamodelSemantics;
import io.mehdieidi.modriss.platform.assistant.metamodel.EcoreContractExtractor;
import io.mehdieidi.modriss.platform.assistant.metamodel.MetamodelGuideGenerator;
import io.mehdieidi.modriss.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.modriss.platform.assistant.metamodel.TypeContractService;
import io.mehdieidi.modriss.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.modeling.config.ModelingConfigService;
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

  @Test
  void excerptModeExposesCoreCimConceptsAndRejectsPeripheralOnes() {
    AssistantMetamodelSchemaService excerpt =
        new AssistantMetamodelSchemaService(AssistantMetamodelProfile.excerpt());

    assertEquals(AssistantMetamodelMode.EXCERPT, excerpt.profile().mode());
    assertEquals(schemas.metamodelSha(ModelLevel.CIM), excerpt.metamodelSha(ModelLevel.CIM));
    assertEquals("BusinessProcess", excerpt.canonicalType(ModelLevel.CIM, "BusinessProcess"));
    assertEquals("DomainEntity", excerpt.canonicalType(ModelLevel.CIM, "DomainEntity"));
    assertThrows(PlatformException.class, () -> excerpt.canonicalType(ModelLevel.CIM, "Risk"));
    assertTrue(
        excerpt.coverage(ModelLevel.CIM).creatableTypes()
            < schemas.coverage(ModelLevel.CIM).creatableTypes());
  }

  @Test
  void excerptModeExposesCorePimConceptsAndFiltersKnowledgeIndex() {
    AssistantMetamodelSchemaService excerpt =
        new AssistantMetamodelSchemaService(AssistantMetamodelProfile.excerpt());
    MetamodelKnowledgeService knowledge = new MetamodelKnowledgeService(excerpt);

    assertEquals("Function", excerpt.canonicalType(ModelLevel.PIM, "Function"));
    assertEquals("Workflow", excerpt.canonicalType(ModelLevel.PIM, "Workflow"));
    assertEquals("DataModel", excerpt.canonicalType(ModelLevel.PIM, "DataModel"));
    assertThrows(
        PlatformException.class,
        () -> excerpt.canonicalType(ModelLevel.PIM, "PlatformMappingAssessment"));
    assertThrows(
        PlatformException.class,
        () -> knowledge.typeContract(ModelLevel.PIM, "PlatformMappingAssessment"));
    assertTrue(
        knowledge.typeContract(ModelLevel.PIM, "Function").references().stream()
            .noneMatch(reference -> reference.targetType().equals("EnvironmentVariable")));
  }

  @Test
  void normalModeRetainsTheCompleteExistingSurface() {
    assertEquals(AssistantMetamodelMode.NORMAL, schemas.profile().mode());
    assertEquals("Risk", schemas.canonicalType(ModelLevel.CIM, "Risk"));
    assertEquals(
        "PlatformMappingAssessment",
        schemas.canonicalType(ModelLevel.PIM, "PlatformMappingAssessment"));
  }

  @Test
  void excerptRetainsEveryRequiredReferenceOfItsIncludedTypes() {
    AssistantMetamodelSchemaService excerpt =
        new AssistantMetamodelSchemaService(AssistantMetamodelProfile.excerpt());

    for (ModelLevel level : List.of(ModelLevel.CIM, ModelLevel.PIM)) {
      for (AssistantMetamodelSchemaService.TypeSchema type : excerpt.types(level)) {
        List<String> excerptReferences =
            type.references().stream()
                .map(AssistantMetamodelSchemaService.ReferenceSchema::name)
                .toList();
        schemas.typeSchema(level, type.name()).orElseThrow().references().stream()
            .filter(AssistantMetamodelSchemaService.ReferenceSchema::required)
            .forEach(
                reference ->
                    assertTrue(
                        excerptReferences.contains(reference.name()),
                        () ->
                            level
                                + " excerpt dropped required reference "
                                + type.name()
                                + "."
                                + reference.name()));
      }
    }
  }

  @Test
  void everyExcerptCreatableTypeHasAResolvableRequiredContractClosure() {
    AssistantMetamodelSchemaService excerpt =
        new AssistantMetamodelSchemaService(AssistantMetamodelProfile.excerpt());
    MetamodelKnowledgeService knowledge = new MetamodelKnowledgeService(excerpt);
    TypeContractService contracts = new TypeContractService(knowledge);

    for (ModelLevel level : List.of(ModelLevel.CIM, ModelLevel.PIM)) {
      knowledge.typeContracts(level).stream()
          .filter(MetamodelKnowledgeService.TypeContract::creatable)
          .forEach(type -> contracts.requiredContainmentClosure(level, List.of(type.eClass())));
    }

    assertTrue(!knowledge.typeContract(ModelLevel.PIM, "FlowEndpoint").creatable());
    assertTrue(!knowledge.typeContract(ModelLevel.PIM, "FunctionTarget").creatable());
    String candidateIndex = new MetamodelGuideGenerator(knowledge).index(ModelLevel.PIM);
    assertTrue(!candidateIndex.contains("FlowEndpoint"));
    assertTrue(!candidateIndex.contains("FunctionTarget"));
  }

  @Test
  void normalModeUsesTheOriginalUnprojectedEcoreKnowledgeIndex() {
    MetamodelKnowledgeService normal = new MetamodelKnowledgeService(schemas);
    var direct = new EcoreContractExtractor(new ModelingConfigService()).extract();

    for (ModelLevel level : ModelLevel.values()) {
      assertEquals(direct.typeContracts(level), normal.typeContracts(level));
      assertEquals(direct.records(level), normal.index().records(level));
    }
  }

  @Test
  void sourceQualityMetadataNamesCanonicalCimTypes() {
    for (String type : AssistantMetamodelSemantics.configuredTypes(ModelLevel.CIM)) {
      assertEquals(type, schemas.canonicalType(ModelLevel.CIM, type));
    }
  }
}
