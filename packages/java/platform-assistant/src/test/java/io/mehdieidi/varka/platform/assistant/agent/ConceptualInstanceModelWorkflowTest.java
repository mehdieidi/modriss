package io.mehdieidi.varka.platform.assistant.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.varka.platform.assistant.metamodel.TypeContractService;
import io.mehdieidi.varka.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConceptualInstanceModelWorkflowTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final TypeContractService contracts =
      new TypeContractService(new MetamodelKnowledgeService(new AssistantMetamodelSchemaService()));

  @Test
  void compilesPaperParentToChildCompositionsInDependencyOrder() throws Exception {
    var conceptual =
        ConceptualInstanceModelWorkflow.ConceptualModel.parse(
            mapper,
            """
{
  "contract": {
    "type": "FunctionContract",
    "attributes": [],
    "associations": {"compositions": [], "references": []}
  },
  "function": {
    "type": "Function",
    "attributes": [
      {"dataType": "", "attributeName": "name", "value": "Process order"},
      {"dataType": "", "attributeName": "functionKind", "value": "COMMAND_HANDLER"}
    ],
    "associations": {
      "compositions": [
        {"associationName": "contract", "associatedClassName": "FunctionContract", "instanceID": "contract"}
      ],
      "references": []
    }
  },
  "service": {
    "type": "ServerlessService",
    "attributes": [
      {"dataType": "", "attributeName": "name", "value": "Orders"},
      {"dataType": "", "attributeName": "boundaryType", "value": "CAPABILITY_BASED"}
    ],
    "associations": {
      "compositions": [
        {"associationName": "functions", "associatedClassName": "Function", "instanceID": "function"}
      ],
      "references": []
    }
  }
}
""");

    var batch = conceptual.commands(emptyPim(), contracts, ModelLevel.PIM);

    assertEquals(
        java.util.List.of("service", "function", "contract"),
        batch.creates().stream().map(create -> create.clientRef()).toList());
    assertEquals("rootId", batch.creates().get(0).owner());
    assertEquals("services", batch.creates().get(0).reference());
    assertEquals("service", batch.creates().get(1).owner());
    assertEquals("functions", batch.creates().get(1).reference());
    assertEquals("function", batch.creates().get(2).owner());
    assertEquals("contract", batch.creates().get(2).reference());
  }

  @Test
  void updatesExactPersistedIdWithoutDeletingOmittedExistingContent() throws Exception {
    var conceptual =
        ConceptualInstanceModelWorkflow.ConceptualModel.parse(
            mapper,
            """
            {
              "actor-existing": {
                "type": "Actor",
                "attributes": [
                  {"dataType": "", "attributeName": "authenticationExpectation", "value": "MFA"}
                ],
                "associations": {"compositions": [], "references": []}
              }
            }
            """);

    var batch = conceptual.commands(existingCim(), contracts, ModelLevel.CIM);

    assertEquals(1, batch.updates().size());
    assertEquals("actor-existing", batch.updates().get(0).elementId());
    assertTrue(batch.creates().isEmpty());
    assertTrue(batch.deletions().isEmpty());
  }

  @Test
  void rejectsWrongCompositionDirectionWithPreciseOwnerDiagnostic() throws Exception {
    var conceptual =
        ConceptualInstanceModelWorkflow.ConceptualModel.parse(
            mapper,
            """
{
  "service": {
    "type": "ServerlessService",
    "attributes": [],
    "associations": {"compositions": [], "references": []}
  },
  "function": {
    "type": "Function",
    "attributes": [],
    "associations": {
      "compositions": [
        {"associationName": "functions", "associatedClassName": "ServerlessService", "instanceID": "service"}
      ],
      "references": []
    }
  }
}
""");

    PlatformException error =
        assertThrows(
            PlatformException.class,
            () -> conceptual.commands(emptyPim(), contracts, ModelLevel.PIM));

    assertTrue(error.getMessage().contains("not writable on Function"));
    assertTrue(error.getMessage().contains("Legal compositions"));
  }

  @Test
  void compilesSourceEvidenceWithoutChangingBusinessContent() throws Exception {
    var conceptual =
        ConceptualInstanceModelWorkflow.ConceptualModel.parse(
            mapper,
            """
{
  "actor": {
    "type": "Actor",
    "attributes": [{"dataType": "", "attributeName": "name", "value": "Visitor"}],
    "associations": {"compositions": [], "references": []},
    "evidence": [
      {"sourceUnitId": "turn:source-1", "requirementId": "US-1", "kind": "SOURCE_GROUNDED", "assumption": ""}
    ]
  }
}
""");

    var batch = conceptual.commands(emptyCim(), contracts, ModelLevel.CIM);

    assertEquals(1, batch.evidence().size());
    assertEquals("actor", batch.evidence().get(0).elementRef());
    assertEquals("turn:source-1", batch.evidence().get(0).sourceUnitId());
  }

  private tools.jackson.databind.JsonNode emptyCim() throws Exception {
    return mapper.readTree(
        """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","diagram":{"elements":[],"relationships":[]}}
""");
  }

  private tools.jackson.databind.JsonNode existingCim() throws Exception {
    return mapper.readTree(
        """
        {"id":"root","eClass":"CIMModel","modelLevel":"CIM",
         "actors":[{"id":"actor-existing","eClass":"Actor","name":"Customer"}],
         "goals":[{"id":"goal-existing","eClass":"BusinessGoal","name":"Place order"}],
         "diagram":{"elements":[],"relationships":[]}}
        """);
  }

  private tools.jackson.databind.JsonNode emptyPim() throws Exception {
    return mapper.readTree(
        """
{"id":"root","eClass":"PIMModel","modelLevel":"PIM","diagram":{"elements":[],"relationships":[]}}
""");
  }
}
