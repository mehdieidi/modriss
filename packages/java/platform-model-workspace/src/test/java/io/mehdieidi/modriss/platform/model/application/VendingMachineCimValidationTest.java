package io.mehdieidi.modriss.platform.model.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.model.domain.ModelRecord;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Regression coverage for the vending-machine CIM model's stored validation workflow. */
@ResourceLock("emf-registry")
@ResourceLock("epsilon-runtime")
class VendingMachineCimValidationTest {

  @TempDir Path tempDir;

  /**
   * Imports the case study into a fresh project and checks the trigger and decision-transition EVL
   * rules before and after ordinary model edits.
   *
   * @throws Exception if import, persistence, or validation fails
   */
  @Test
  void projectImportAndStoredValidationFollowCimEcoreAndEvlRules() throws Exception {
    PlatformTestFixtures.ServiceStack services = PlatformTestFixtures.createServices(tempDir);
    ModelService service = services.models();
    PlatformTestFixtures.AuthenticatedContext context =
        PlatformTestFixtures.registerOwner(
            services, "vending-validator@example.com", "Owner", "Vending Machine Validation");

    ModelService.ImportResult imported =
        service.importModel(
            context.user(),
            context.project().id(),
            ModelLevel.CIM,
            "vending-machine.cim.xmi",
            PlatformTestFixtures.vendingMachineCimXmi(),
            "xmi");
    assertTrue(imported.issues().isEmpty(), "XMI import should not add validation findings");

    ModelRecord created =
        service.create(
            context.user(),
            ModelLevel.CIM,
            context.project().id(),
            imported.name(),
            imported.modelJson());
    ModelService.ValidationResult importedValidation =
        service.validate(context.user(), ModelLevel.CIM, created.id());
    assertTrue(
        importedValidation.valid(),
        () -> "The imported model should validate: " + importedValidation.issues());
    assertTrue(
        importedValidation.issues().isEmpty(),
        () -> "The imported model should have no warnings: " + importedValidation.issues());

    List<String> labelledDecisionTransitions =
        List.of(
            "proc-catalogue-flow-3",
            "proc-termination-flow-2",
            "proc-refund-flow-7",
            "proc-notices-flow-5");
    for (String transitionId : labelledDecisionTransitions) {
      JsonNode transition = findById(imported.modelJson(), transitionId);
      assertNotNull(transition, "Expected imported transition " + transitionId);
      assertFalse(
          transition.path("label").asText("").isBlank(),
          "The case-study transition should have a semantic Ecore label: " + transitionId);
    }
    ObjectNode storedBase =
        ((ObjectNode) service.get(context.user(), ModelLevel.CIM, created.id()).modelJson())
            .deepCopy();

    // This is the editor action from the report: clear only triggeringActor, keeping the
    // descriptive trigger and completion criterion. The EVL explicitly allows that form.
    ObjectNode actorRemoved = storedBase.deepCopy();
    ObjectNode termination = (ObjectNode) findById(actorRemoved, "proc-termination");
    termination.remove("triggeringActor");
    service.update(context.user(), ModelLevel.CIM, created.id(), imported.name(), actorRemoved);
    ModelService.ValidationResult actorRemovedValidation =
        service.validate(context.user(), ModelLevel.CIM, created.id());
    assertTrue(
        actorRemovedValidation.valid(),
        () ->
            "Removing only triggeringActor should remain valid when businessTriggerDescription and "
                + "completionCriterion remain: "
                + actorRemovedValidation.issues());
    assertTrue(
        actorRemovedValidation.issues().isEmpty(),
        () ->
            "Removing triggeringActor should not create unrelated warnings: "
                + actorRemovedValidation.issues());

    // Removing every trigger representation and the descriptive substitute violates the EVL
    // policy even though the Ecore reference itself has no lower bound.
    ObjectNode noTriggerDescription = storedBase.deepCopy();
    ObjectNode triggerlessTermination =
        (ObjectNode) findById(noTriggerDescription, "proc-termination");
    triggerlessTermination.remove(
        List.of(
            "trigger",
            "triggeringEvent",
            "triggeringCommand",
            "triggeringActor",
            "businessTriggerDescription"));
    service.update(
        context.user(), ModelLevel.CIM, created.id(), imported.name(), noTriggerDescription);
    ModelService.ValidationResult noTriggerValidation =
        service.validate(context.user(), ModelLevel.CIM, created.id());
    assertFalse(
        noTriggerValidation.valid(), "A process with no trigger representation should fail EVL");
    assertTrue(
        noTriggerValidation.issues().stream()
            .anyMatch(
                issue ->
                    "ERROR".equals(issue.severity())
                        && "ProcessHasTriggerAndCompletionCriterion".equals(issue.constraint())
                        && "proc-termination".equals(issue.elementId())),
        () -> "Expected CIM-PROC-002 for proc-termination: " + noTriggerValidation.issues());

    // ProcessTransition.label is a semantic Ecore attribute. Removing it from a decision branch
    // should produce the documented warning while leaving the model valid.
    ObjectNode unlabelledBranch = storedBase.deepCopy();
    ((ObjectNode) findById(unlabelledBranch, "proc-catalogue-flow-3")).remove("label");
    service.update(context.user(), ModelLevel.CIM, created.id(), imported.name(), unlabelledBranch);
    ModelService.ValidationResult unlabelledValidation =
        service.validate(context.user(), ModelLevel.CIM, created.id());
    assertTrue(
        unlabelledValidation.valid(),
        () ->
            "An unlabeled decision branch is a recommendation, not an error: "
                + unlabelledValidation.issues());
    assertTrue(
        unlabelledValidation.issues().stream()
            .anyMatch(
                issue ->
                    "WARNING".equals(issue.severity())
                        && "ConditionalTransitionIsLabelled".equals(issue.constraint())
                        && "proc-catalogue-flow-3".equals(issue.elementId())),
        () -> "Expected CIM-TRANS-003 for proc-catalogue-flow-3: " + unlabelledValidation.issues());
  }

  private static JsonNode findById(JsonNode current, String id) {
    if (current == null) {
      return null;
    }
    if (current.isObject()) {
      if (id.equals(current.path("id").asText())) {
        return current;
      }
      var fields = current.properties().iterator();
      while (fields.hasNext()) {
        JsonNode match = findById(fields.next().getValue(), id);
        if (match != null) {
          return match;
        }
      }
    } else if (current.isArray()) {
      for (JsonNode child : current) {
        JsonNode match = findById(child, id);
        if (match != null) {
          return match;
        }
      }
    }
    return null;
  }
}
