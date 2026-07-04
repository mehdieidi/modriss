package io.mehdieidi.modless.platform.assistant.application;

import io.mehdieidi.modless.platform.assistant.agent.IntentPlanner;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import java.util.List;

/** Strips deletions unless the turn was classified as explicit delete intent. */
public final class DeletionPolicyGate {

  private DeletionPolicyGate() {}

  public static SemanticModelPatch enforce(
      IntentPlanner.IntentDecision intent, SemanticModelPatch patch) {
    if (patch == null || patch.operations().isEmpty()) {
      return patch;
    }
    if (intent != null && "DELETE_MODEL".equalsIgnoreCase(intent.taskKind())) {
      return patch;
    }
    List<SemanticModelPatch.Operation> operations =
        patch.operations().stream()
            .filter(
                operation ->
                    operation == null
                        || operation.type() != SemanticModelPatch.OperationType.DELETE_ELEMENT)
            .toList();
    if (operations.size() == patch.operations().size()) {
      return patch;
    }
    return new SemanticModelPatch(operations);
  }
}
