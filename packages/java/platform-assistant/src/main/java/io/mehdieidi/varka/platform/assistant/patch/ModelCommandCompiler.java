package io.mehdieidi.varka.platform.assistant.patch;

import io.mehdieidi.varka.platform.assistant.domain.ModelCommandBatch;
import io.mehdieidi.varka.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.varka.platform.assistant.domain.SemanticModelPatch.Operation;
import io.mehdieidi.varka.platform.assistant.domain.SemanticModelPatch.OperationType;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** Converts stable-reference agent commands to the existing atomic structural compiler input. */
public final class ModelCommandCompiler {
  private final AssistantPatchCompiler compiler;

  public ModelCommandCompiler(AssistantPatchCompiler compiler) {
    this.compiler = compiler;
  }

  /** Compiles already-validated semantic commands through the same atomic structural path. */
  public AssistantPatchCompiler.CompiledPatch compile(JsonNode model, SemanticModelPatch semantic) {
    return compiler.compile(model, semantic);
  }

  /** Compiles the complete batch before any mutation is persisted. */
  public AssistantPatchCompiler.CompiledPatch compile(JsonNode model, ModelCommandBatch batch) {
    Map<String, String> refs = new LinkedHashMap<>();
    List<Operation> operations = new ArrayList<>();
    for (ModelCommandBatch.Create create : batch.creates()) {
      if (create.clientRef() == null || create.clientRef().isBlank())
        throw new PlatformException(422, "Create clientRef is required.");
      if (create.eClass() == null || create.eClass().isBlank())
        throw new PlatformException(422, "Create eClass is required.");
      if (create.owner() == null
          || create.owner().isBlank()
          || create.reference() == null
          || create.reference().isBlank())
        throw new PlatformException(
            422,
            "Create '"
                + create.clientRef()
                + "' requires an explicit owner and containment feature.");
      if (refs.put(create.clientRef(), create.clientRef()) != null)
        throw new PlatformException(422, "Duplicate clientRef: " + create.clientRef());
      ObjectNode attributes = JsonNodeFactory.instance.objectNode();
      if (create.attributes() != null) create.attributes().forEach(attributes::set);
      operations.add(
          new Operation(
              OperationType.ADD_ELEMENT,
              create.clientRef(),
              create.eClass(),
              attributes,
              resolve(create.owner(), refs),
              create.reference()));
    }
    for (ModelCommandBatch.Update update : batch.updates()) {
      if (update.attributes() == null || update.attributes().isEmpty())
        throw new PlatformException(422, "Update attributes are required.");
      update
          .attributes()
          .forEach(
              (feature, value) ->
                  operations.add(
                      new Operation(
                          OperationType.SET_ATTRIBUTE,
                          resolve(update.elementId(), refs),
                          null,
                          value,
                          null,
                          feature)));
    }
    for (ModelCommandBatch.Connection connection : batch.connections()) {
      operations.add(
          new Operation(
              OperationType.CONNECT_ELEMENTS,
              resolve(connection.target(), refs),
              null,
              null,
              resolve(connection.source(), refs),
              connection.reference()));
    }
    for (ModelCommandBatch.Deletion deletion : batch.deletions()) {
      // Deletions are intentionally represented but must be routed through confirmation by the
      // durable turn service before this compiler is invoked.
      throw new PlatformException(409, "Deletion requires turn confirmation.");
    }
    return compiler.compile(model, new SemanticModelPatch(operations));
  }

  private String resolve(String reference, Map<String, String> refs) {
    if (reference == null || reference.isBlank()) return null;
    return refs.getOrDefault(reference, reference);
  }
}
