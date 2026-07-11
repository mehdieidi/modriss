package io.mehdieidi.modless.platform.assistant.metamodel;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
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
}
