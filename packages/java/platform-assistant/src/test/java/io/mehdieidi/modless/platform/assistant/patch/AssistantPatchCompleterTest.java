package io.mehdieidi.modless.platform.assistant.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.application.AssistantValidationFeedbackResolver;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssistantPatchCompleterTest {

  private final AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();
  private final AssistantPatchCompleter completer = new AssistantPatchCompleter(schemas);
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void addsMissingFunctionContractForFunction() throws Exception {
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "function-1",
                    "Function",
                    mapper.readTree("{\"name\":\"Dispense item\"}"),
                    null,
                    null)));

    SemanticModelPatch completed = completer.complete(ModelLevel.PIM, patch, Map.of());

    assertTrue(completed.operations().size() >= 2);
    SemanticModelPatch.Operation contract =
        completed.operations().stream()
            .filter(operation -> "FunctionContract".equals(operation.elementType()))
            .findFirst()
            .orElseThrow();
    assertEquals("function-1", contract.sourceElementId());
    assertEquals("contract", contract.referenceName());
    assertTrue(contract.attributes().has("name"));
    assertTrue(
        completed.operations().stream()
            .anyMatch(operation -> "Schema".equals(operation.elementType())));
  }

  @Test
  void dropsOrphanFunctionContractsAddedAtRootByPlanner() throws Exception {
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "function-1",
                    "Function",
                    mapper.readTree("{\"name\":\"Dispense item\"}"),
                    null,
                    null),
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "contract-1",
                    "FunctionContract",
                    mapper.readTree("{\"name\":\"Dispense contract\"}"),
                    null,
                    null)));

    SemanticModelPatch completed = completer.complete(ModelLevel.PIM, patch, Map.of());

    assertTrue(completed.operations().size() >= 2);
    assertTrue(
        completed.operations().stream()
            .noneMatch(
                operation ->
                    "FunctionContract".equals(operation.elementType())
                        && blank(operation.sourceElementId())));
  }

  @Test
  void stripsEmbeddedContainmentReferencesAlreadyModeledAsChildAdds() throws Exception {
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "customer",
                    "DomainEntity",
                    mapper.readTree(
                        "{\"name\":\"Customer\",\"identityStrategy\":\"BUSINESS_KEY\"}"),
                    null,
                    null),
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "order",
                    "DomainEntity",
                    mapper.readTree("{\"name\":\"Order\",\"identityStrategy\":\"BUSINESS_KEY\"}"),
                    null,
                    null),
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "customer-orders",
                    "DomainRelationship",
                    mapper.readTree(
                        """
                        {
                          "name":"Customer places orders",
                          "sourceMultiplicity":{"lowerBound":1,"upperBound":1},
                          "targetMultiplicity":{"lowerBound":0,"unbounded":true}
                        }
                        """),
                    null,
                    null),
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "source-multiplicity",
                    "Multiplicity",
                    mapper.readTree("{\"name\":\"Customer multiplicity\",\"lowerBound\":1}"),
                    "customer-orders",
                    "sourceMultiplicity"),
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "target-multiplicity",
                    "Multiplicity",
                    mapper.readTree(
                        "{\"name\":\"Order multiplicity\",\"lowerBound\":0,"
                            + "\"unbounded\":true}"),
                    "customer-orders",
                    "targetMultiplicity"),
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.SET_ATTRIBUTE,
                    "customer-orders",
                    "DomainRelationship",
                    mapper.readTree("{\"lowerBound\":1}"),
                    null,
                    "sourceMultiplicity")));

    SemanticModelPatch completed = completer.complete(ModelLevel.CIM, patch, Map.of());

    SemanticModelPatch.Operation relationship =
        completed.operations().stream()
            .filter(operation -> "customer-orders".equals(operation.targetElementId()))
            .findFirst()
            .orElseThrow();
    assertTrue(relationship.attributes().path("sourceMultiplicity").isMissingNode());
    assertTrue(relationship.attributes().path("targetMultiplicity").isMissingNode());
    assertEquals(
        1,
        completed.operations().stream()
            .filter(operation -> "customer-orders".equals(operation.sourceElementId()))
            .filter(operation -> "sourceMultiplicity".equals(operation.referenceName()))
            .count());
    assertEquals(
        1,
        completed.operations().stream()
            .filter(operation -> "customer-orders".equals(operation.sourceElementId()))
            .filter(operation -> "targetMultiplicity".equals(operation.referenceName()))
            .count());
    assertTrue(
        completed.operations().stream()
            .noneMatch(
                operation ->
                    operation.type() == SemanticModelPatch.OperationType.SET_ATTRIBUTE
                        && "sourceMultiplicity".equals(operation.referenceName())));
  }

  @Test
  void prefersManyValuedRootContainmentsForCimTopLevelElements() {
    assertEquals("goals", schemas.rootCollection(ModelLevel.CIM, "BusinessGoal").orElseThrow());
    assertEquals("entities", schemas.rootCollection(ModelLevel.CIM, "DomainEntity").orElseThrow());
    assertEquals(
        "relationships",
        schemas.rootCollection(ModelLevel.CIM, "DomainRelationship").orElseThrow());
  }

  @Test
  void addsMeaningfulNameForUnlabeledContainedMultiplicity() throws Exception {
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "relationship",
                    "DomainRelationship",
                    mapper.readTree("{\"name\":\"Patient has visits\"}"),
                    null,
                    null),
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "multiplicity",
                    "Multiplicity",
                    mapper.readTree("{\"lowerBound\":0,\"unbounded\":true}"),
                    "relationship",
                    "sourceMultiplicity")));

    SemanticModelPatch completed = completer.complete(ModelLevel.CIM, patch, Map.of());

    SemanticModelPatch.Operation multiplicity =
        completed.operations().stream()
            .filter(operation -> "multiplicity".equals(operation.targetElementId()))
            .findFirst()
            .orElseThrow();
    assertEquals("Source Multiplicity", multiplicity.attributes().path("name").asText());
  }

  @Test
  void completesRequiredCimReferencesFromCompatiblePlannedElements() throws Exception {
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "patient",
                    "DomainEntity",
                    mapper.readTree("{\"name\":\"Patient\",\"identityStrategy\":\"BUSINESS_KEY\"}"),
                    null,
                    null),
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "patient-id",
                    "InformationItem",
                    mapper.readTree("{\"name\":\"Patient identity\",\"type\":\"TEXT\"}"),
                    null,
                    null),
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "patient-query",
                    "Query",
                    mapper.readTree("{\"name\":\"Patient lookup\"}"),
                    null,
                    null)));

    SemanticModelPatch completed = completer.complete(ModelLevel.CIM, patch, Map.of());

    assertTrue(hasConnection(completed, "patient", "patient-id", "identityAttributes"));
    assertTrue(hasConnection(completed, "patient", "patient-id", "primaryIdentityAttribute"));
    assertTrue(hasConnection(completed, "patient-query", "patient-id", "output"));
  }

  @Test
  void completesRequiredContainmentWithConcreteCreatableSubtype() throws Exception {
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "booking-process",
                    "BusinessProcess",
                    mapper.readTree("{\"name\":\"Booking process\"}"),
                    null,
                    null)));

    SemanticModelPatch completed = completer.complete(ModelLevel.CIM, patch, Map.of());

    assertTrue(
        completed.operations().stream()
            .noneMatch(
                operation ->
                    operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT
                        && "ProcessStep".equals(operation.elementType())));
    assertTrue(
        completed.operations().stream()
            .anyMatch(
                operation ->
                    operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT
                        && "booking-process".equals(operation.sourceElementId())
                        && "steps".equals(operation.referenceName())
                        && "HumanTaskStep".equals(operation.elementType())));
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private boolean hasConnection(
      SemanticModelPatch patch, String sourceId, String targetId, String referenceName) {
    return patch.operations().stream()
        .anyMatch(
            operation ->
                operation.type() == SemanticModelPatch.OperationType.CONNECT_ELEMENTS
                    && sourceId.equals(operation.sourceElementId())
                    && targetId.equals(operation.targetElementId())
                    && referenceName.equals(operation.referenceName()));
  }

  @Test
  void repairsMissingContractForExistingFunctionId() {
    String functionId = "68ae9350-f377-409e-bfad-12dbc945f0b6";
    SemanticModelPatch patch = new SemanticModelPatch(List.of());
    List<AssistantValidationFeedbackResolver.MissingRequiredFeature> missing =
        List.of(
            new AssistantValidationFeedbackResolver.MissingRequiredFeature(functionId, "contract"));

    SemanticModelPatch repaired =
        completer.repairFromValidationFeedback(
            ModelLevel.PIM, patch, Map.of(functionId, "Function"), missing);

    assertEquals(1, repaired.operations().size());
    assertEquals(functionId, repaired.operations().get(0).sourceElementId());
    assertEquals("contract", repaired.operations().get(0).referenceName());
    assertEquals("FunctionContract", repaired.operations().get(0).elementType());
  }
}
