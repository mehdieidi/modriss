package io.mehdieidi.modless.platform.assistant.delta;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministically lowers a ModelDelta draft into executable backend operations. */
class DeltaPatchCompiler {

  private final AssistantMetamodelSchemaService schemas;

  DeltaPatchCompiler(AssistantMetamodelSchemaService schemas) {
    this.schemas = schemas;
  }

  SemanticModelPatch compile(
      ModelLevel level, JsonNode baseModel, Map<String, String> existingTypes, PatchDraft draft) {
    if (draft == null) {
      return new SemanticModelPatch(List.of());
    }
    Map<String, String> types = new LinkedHashMap<>();
    if (existingTypes != null) {
      types.putAll(existingTypes);
    }
    String rootId = baseModel == null ? "" : baseModel.path("id").asText("");
    String rootType = baseModel == null ? "" : baseModel.path("eClass").asText("");
    if (!rootId.isBlank() && !rootType.isBlank()) {
      types.putIfAbsent(rootId, schemas.canonicalType(level, rootType));
    }
    Map<String, Element> elements = new LinkedHashMap<>();
    for (Element element : draft.elements()) {
      if (element == null || element.localId().isBlank() || element.eClass().isBlank()) {
        throw new PlatformException(422, "Every ModelDelta element requires localId and eClass.");
      }
      if (types.containsKey(element.localId()) || elements.containsKey(element.localId())) {
        throw new PlatformException(
            422, "ModelDelta element IDs must be unique and not already saved.");
      }
      elements.put(element.localId(), element);
    }

    List<SemanticModelPatch.Operation> operations = new ArrayList<>();
    Set<String> emitted = new LinkedHashSet<>();
    while (emitted.size() < elements.size()) {
      int before = emitted.size();
      for (Element element : elements.values()) {
        if (emitted.contains(element.localId())) {
          continue;
        }
        String ownerId = ownerId(element);
        if (!ownerId.isBlank()
            && !isRoot(ownerId, rootId)
            && !types.containsKey(ownerId)
            && !emitted.contains(ownerId)) {
          continue;
        }
        operations.add(addOperation(level, rootId, types, element));
        emitted.add(element.localId());
        types.put(element.localId(), schemas.canonicalType(level, element.eClass()));
      }
      if (before == emitted.size()) {
        throw new PlatformException(
            422, "ModelDelta containments contain an unknown or cyclic owner.");
      }
    }

    for (Element element : elements.values()) {
      for (Reference reference : element.references()) {
        operations.add(referenceOperation(level, types, referenceWithSource(element, reference)));
      }
    }
    for (Reference reference : draft.references()) {
      operations.add(referenceOperation(level, types, reference));
    }
    for (AttributeUpdate update : draft.attributeUpdates()) {
      operations.add(attributeOperation(level, types, update));
    }
    for (Deletion deletion : draft.deletions()) {
      if (deletion.elementId().isBlank() || !types.containsKey(deletion.elementId())) {
        throw new PlatformException(
            422, "ModelDelta deletion targets an unknown existing element.");
      }
      operations.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.DELETE_ELEMENT,
              deletion.elementId(),
              null,
              null,
              null,
              null));
    }
    return new SemanticModelPatch(operations);
  }

  private SemanticModelPatch.Operation addOperation(
      ModelLevel level, String rootId, Map<String, String> types, Element element) {
    String type = schemas.canonicalType(level, element.eClass());
    String ownerId = ownerId(element);
    String referenceName = ownerReference(element);
    String sourceElementId = null;
    if (!ownerId.isBlank()) {
      if (isRoot(ownerId, rootId)) {
        sourceElementId = rootId.isBlank() ? null : rootId;
      } else {
        sourceElementId = ownerId;
      }
    }
    if (sourceElementId != null && !sourceElementId.isBlank()) {
      String ownerType = types.get(sourceElementId);
      if (ownerType != null && !referenceName.isBlank()) {
        schemas.requireContainment(level, ownerType, referenceName, type);
      }
    } else if (schemas.rootContainment(level, type).isEmpty()) {
      throw new PlatformException(422, "ModelDelta element is missing a legal containment owner.");
    }
    return new SemanticModelPatch.Operation(
        SemanticModelPatch.OperationType.ADD_ELEMENT,
        element.localId(),
        type,
        element.attributes(),
        sourceElementId,
        referenceName);
  }

  private SemanticModelPatch.Operation referenceOperation(
      ModelLevel level, Map<String, String> types, Reference reference) {
    String sourceType = types.get(reference.sourceId());
    String targetType = types.get(reference.targetId());
    if (sourceType == null
        || targetType == null
        || reference.referenceName().isBlank()
        || !schemas.acceptsReferenceTarget(
            level, sourceType, reference.referenceName(), targetType)) {
      throw new PlatformException(422, "ModelDelta reference is not grounded in the metamodel.");
    }
    return new SemanticModelPatch.Operation(
        SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
        reference.targetId(),
        null,
        null,
        reference.sourceId(),
        reference.referenceName());
  }

  private SemanticModelPatch.Operation attributeOperation(
      ModelLevel level, Map<String, String> types, AttributeUpdate update) {
    String targetType = types.get(update.elementId());
    if (targetType == null
        || update.attributeName().isBlank()
        || schemas.attribute(level, targetType, update.attributeName()).isEmpty()) {
      throw new PlatformException(
          422, "ModelDelta attribute update is not grounded in the metamodel.");
    }
    return new SemanticModelPatch.Operation(
        SemanticModelPatch.OperationType.SET_ATTRIBUTE,
        update.elementId(),
        null,
        update.value(),
        null,
        update.attributeName());
  }

  private Reference referenceWithSource(Element element, Reference reference) {
    if (reference.sourceId().isBlank()) {
      return new Reference(element.localId(), reference.referenceName(), reference.targetId());
    }
    return reference;
  }

  private String ownerId(Element element) {
    return element.containedBy() == null ? "" : element.containedBy().ownerId();
  }

  private String ownerReference(Element element) {
    return element.containedBy() == null ? "" : element.containedBy().referenceName();
  }

  private boolean isRoot(String ownerId, String rootId) {
    return "root".equalsIgnoreCase(ownerId) || (!rootId.isBlank() && rootId.equals(ownerId));
  }

  record PatchDraft(
      List<Element> elements,
      List<Reference> references,
      List<AttributeUpdate> attributeUpdates,
      List<Deletion> deletions) {

    PatchDraft {
      elements = elements == null ? List.of() : List.copyOf(elements);
      references = references == null ? List.of() : List.copyOf(references);
      attributeUpdates = attributeUpdates == null ? List.of() : List.copyOf(attributeUpdates);
      deletions = deletions == null ? List.of() : List.copyOf(deletions);
    }
  }

  record Element(
      String localId,
      String eClass,
      JsonNode attributes,
      Containment containedBy,
      List<Reference> references) {

    Element {
      localId = localId == null ? "" : localId.trim();
      eClass = eClass == null ? "" : eClass.trim();
      references = references == null ? List.of() : List.copyOf(references);
    }
  }

  record Containment(String ownerId, String referenceName) {
    Containment {
      ownerId = ownerId == null ? "" : ownerId.trim();
      referenceName = referenceName == null ? "" : referenceName.trim();
    }
  }

  record Reference(String sourceId, String referenceName, String targetId) {
    Reference {
      sourceId = sourceId == null ? "" : sourceId.trim();
      referenceName = referenceName == null ? "" : referenceName.trim();
      targetId = targetId == null ? "" : targetId.trim();
    }
  }

  record AttributeUpdate(String elementId, String attributeName, JsonNode value) {
    AttributeUpdate {
      elementId = elementId == null ? "" : elementId.trim();
      attributeName = attributeName == null ? "" : attributeName.trim();
    }
  }

  record Deletion(String elementId) {
    Deletion {
      elementId = elementId == null ? "" : elementId.trim();
    }
  }
}
