package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.assistant.agent.IntentPlanner;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeletionPolicyGateTest {

  @Test
  void stripsDeletionsUnlessTaskKindIsDeleteModel() {
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.DELETE_ELEMENT,
                    "fn-1",
                    "Function",
                    null,
                    null,
                    null),
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.SET_ATTRIBUTE,
                    "fn-2",
                    "Function",
                    null,
                    null,
                    "name")));

    SemanticModelPatch cleaned =
        DeletionPolicyGate.enforce(
            new IntentPlanner.IntentDecision(
                IntentPlanner.Intent.MUTATION, "EXTEND_MODEL", false, List.of(), List.of()),
            patch);

    assertEquals(1, cleaned.operations().size());
    assertEquals(
        SemanticModelPatch.OperationType.SET_ATTRIBUTE, cleaned.operations().get(0).type());

    SemanticModelPatch allowed =
        DeletionPolicyGate.enforce(
            new IntentPlanner.IntentDecision(
                IntentPlanner.Intent.MUTATION, "DELETE_MODEL", false, List.of(), List.of()),
            patch);

    assertEquals(2, allowed.operations().size());
    assertTrue(
        allowed.operations().stream()
            .anyMatch(
                operation -> operation.type() == SemanticModelPatch.OperationType.DELETE_ELEMENT));
  }

  @Test
  void stripsDeletionsWhenIntentDecisionIsMissing() {
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.DELETE_ELEMENT,
                    "fn-1",
                    "Function",
                    null,
                    null,
                    null)));

    SemanticModelPatch cleaned = DeletionPolicyGate.enforce(null, patch);

    assertTrue(cleaned.operations().isEmpty());
  }

  @Test
  void preservesEmptyPatch() {
    SemanticModelPatch patch = new SemanticModelPatch(List.of());
    assertEquals(patch, DeletionPolicyGate.enforce(null, patch));
    assertTrue(DeletionPolicyGate.enforce(null, patch).operations().isEmpty());
  }
}
