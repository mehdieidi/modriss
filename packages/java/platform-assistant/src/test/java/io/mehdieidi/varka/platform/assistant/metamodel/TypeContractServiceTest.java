package io.mehdieidi.varka.platform.assistant.metamodel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.varka.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class TypeContractServiceTest {

  @Test
  void describesEcoreDerivedConstructionOwnersAndMarksExistingRootNonCreatable() {
    TypeContractService service =
        new TypeContractService(
            new MetamodelKnowledgeService(new AssistantMetamodelSchemaService()));

    var contracts = service.requiredContainmentClosure(ModelLevel.PIM, List.of("FunctionContract"));
    var names = contracts.stream().map(contract -> contract.eClass()).toList();

    assertTrue(names.contains("PIMModel"));
    assertTrue(names.contains("ServerlessService"));
    assertTrue(names.contains("Function"));
    assertTrue(names.contains("FunctionContract"));
    assertFalse(
        contracts.stream()
            .filter(contract -> contract.eClass().equals("PIMModel"))
            .findFirst()
            .orElseThrow()
            .creatable());
    assertTrue(
        service
            .containmentPlacements(ModelLevel.PIM, "Function")
            .contains("ServerlessService.functions"));
    assertTrue(
        service.containmentPlacements(ModelLevel.PIM, "EventType").contains("PIMModel.eventTypes"));
  }

  @Test
  void rejectsProviderAliasesAndCaseVariantsInsteadOfChangingTheirMeaning() {
    TypeContractService service =
        new TypeContractService(
            new MetamodelKnowledgeService(new AssistantMetamodelSchemaService()));

    assertThrows(
        io.mehdieidi.varka.platform.kernel.PlatformException.class,
        () -> service.require(ModelLevel.PIM, "service"));
    assertThrows(
        io.mehdieidi.varka.platform.kernel.PlatformException.class,
        () -> service.require(ModelLevel.PIM, "serverlessservice"));
  }
}
