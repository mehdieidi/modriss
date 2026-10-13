package io.mehdieidi.modriss.platform.assistant.metamodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modriss.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import org.junit.jupiter.api.Test;

class MetamodelGuideGeneratorTest {

  private final MetamodelKnowledgeService knowledge =
      new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());

  @Test
  void guideContainsRootTypesFeaturesAndEnumLiterals() {
    String guide = new MetamodelGuideGenerator(knowledge).generate(ModelLevel.CIM);

    assertTrue(guide.contains("# CIM modeling language"));
    assertTrue(guide.contains("Root: "));
    assertTrue(guide.contains("attributes:"));
    assertTrue(guide.contains("contains:"));
  }

  @Test
  void unknownTypeOffersCloseMatches() {
    TypeContractService contracts = new TypeContractService(knowledge);
    String known = knowledge.typeContracts(ModelLevel.CIM).get(0).eClass();
    String misspelled = known.substring(0, Math.max(1, known.length() - 1));

    PlatformException error =
        assertThrows(PlatformException.class, () -> contracts.require(ModelLevel.CIM, misspelled));

    assertTrue(error.getMessage().contains(known));
  }

  @Test
  void acceptsExactLiveEcoreContracts() {
    TypeContractService contracts = new TypeContractService(knowledge);

    assertEquals("BusinessProcess", contracts.require(ModelLevel.CIM, "BusinessProcess").eClass());
    assertEquals(
        "ServerlessService", contracts.require(ModelLevel.PIM, "ServerlessService").eClass());
  }

  @Test
  void rejectsProviderAliasesInsteadOfSilentlyGuessingEcoreTypes() {
    TypeContractService contracts = new TypeContractService(knowledge);

    assertThrows(PlatformException.class, () -> contracts.require(ModelLevel.CIM, "Concept"));
    assertThrows(PlatformException.class, () -> contracts.require(ModelLevel.CIM, "Process"));
    assertThrows(
        PlatformException.class, () -> contracts.require(ModelLevel.CIM, "CommunityPantryRequest"));
    assertThrows(PlatformException.class, () -> contracts.require(ModelLevel.PIM, "Requirement"));
    assertThrows(
        PlatformException.class, () -> contracts.require(ModelLevel.PIM, "IdempotencyPattern"));
    assertThrows(PlatformException.class, () -> contracts.require(ModelLevel.PIM, "CachingPolicy"));
  }
}
