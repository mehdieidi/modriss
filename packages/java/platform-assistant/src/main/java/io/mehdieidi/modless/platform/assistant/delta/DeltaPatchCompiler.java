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
        referenceName = canonicalContainmentName(level, ownerType, referenceName, type);
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
    String referenceName =
        canonicalReferenceName(level, sourceType, reference.referenceName(), targetType);
    if (sourceType == null
        || targetType == null
        || referenceName.isBlank()
        || !schemas.acceptsReferenceTarget(level, sourceType, referenceName, targetType)) {
      throw new PlatformException(
          422,
          "ModelDelta reference is not grounded in the metamodel: sourceId="
              + reference.sourceId()
              + ", sourceType="
              + (sourceType == null ? "unknown" : sourceType)
              + ", referenceName="
              + reference.referenceName()
              + ", targetId="
              + reference.targetId()
              + ", targetType="
              + (targetType == null ? "unknown" : targetType)
              + ".");
    }
    return new SemanticModelPatch.Operation(
        SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
        reference.targetId(),
        null,
        null,
        reference.sourceId(),
        referenceName);
  }

  private String canonicalReferenceName(
      ModelLevel level, String sourceType, String requestedName, String targetType) {
    if (sourceType == null
        || targetType == null
        || requestedName == null
        || requestedName.isBlank()) {
      return requestedName == null ? "" : requestedName;
    }
    if (schemas.acceptsReferenceTarget(level, sourceType, requestedName, targetType)) {
      return requestedName;
    }
    List<String> candidates =
        schemas.typeSchema(level, sourceType).stream()
            .flatMap(type -> type.references().stream())
            .filter(reference -> !reference.containment() && !reference.readonly())
            .filter(
                reference ->
                    schemas.acceptsReferenceTarget(level, sourceType, reference.name(), targetType))
            .map(AssistantMetamodelSchemaService.ReferenceSchema::name)
            .toList();
    if (candidates.isEmpty()) {
      return requestedName;
    }
    String normalized = requestedName.trim().toLowerCase(java.util.Locale.ROOT);
    if (List.of(
            "emit",
            "emits",
            "emitted",
            "publish",
            "publishes",
            "produce",
            "produces",
            "raise",
            "raises")
        .contains(normalized)) {
      if ("Command".equals(sourceType) && candidates.contains("expectedEvents")) {
        return "expectedEvents";
      }
      if ("Policy".equals(sourceType)
          && "BusinessEvent".equals(targetType)
          && candidates.contains("emitsEvents")) {
        return "emitsEvents";
      }
      if ("Policy".equals(sourceType)
          && "Command".equals(targetType)
          && candidates.contains("emitsCommands")) {
        return "emitsCommands";
      }
    }
    if (List.of("trigger", "triggers", "causes", "causedBy", "caused_by").contains(normalized)) {
      if ("Policy".equals(sourceType)
          && "BusinessEvent".equals(targetType)
          && candidates.contains("triggeredBy")) {
        return "triggeredBy";
      }
      if ("BusinessEvent".equals(sourceType) && candidates.contains("consumedByPolicies")) {
        return "consumedByPolicies";
      }
    }
    if ("ApiGatewayIntegration".equals(sourceType) && "AwsLambdaFunction".equals(targetType)) {
      if ((normalized.contains("lambda") || normalized.contains("function"))
          && candidates.contains("lambdaTarget")) {
        return "lambdaTarget";
      }
    }
    if ("EventBridgeRule".equals(sourceType) && "EventBridgeBus".equals(targetType)) {
      if ((normalized.contains("bus") || normalized.equals("eventbus"))
          && candidates.contains("bus")) {
        return "bus";
      }
    }
    if ("WorkflowState".equals(sourceType) && "Function".equals(targetType)) {
      if ((normalized.contains("invoke") || normalized.contains("function"))
          && candidates.contains("invokesFunction")) {
        return "invokesFunction";
      }
    }
    return candidates.size() == 1 ? candidates.get(0) : requestedName;
  }

  private String canonicalContainmentName(
      ModelLevel level, String ownerType, String requestedName, String childType) {
    if (ownerType == null
        || ownerType.isBlank()
        || requestedName == null
        || requestedName.isBlank()) {
      return requestedName == null ? "" : requestedName;
    }
    try {
      schemas.requireContainment(level, ownerType, requestedName, childType);
      return requestedName;
    } catch (PlatformException ignored) {
      // Resolve common LLM containment aliases below.
    }
    List<AssistantMetamodelSchemaService.ReferenceSchema> candidates =
        schemas.containments(level, ownerType, childType);
    if (candidates.isEmpty()) {
      return requestedName;
    }
    String normalized = requestedName.trim().toLowerCase(java.util.Locale.ROOT);
    for (AssistantMetamodelSchemaService.ReferenceSchema candidate : candidates) {
      String name = candidate.name();
      if (name.equalsIgnoreCase(requestedName)) {
        return name;
      }
      String lower = name.toLowerCase(java.util.Locale.ROOT);
      if (normalized.equals(lower)
          || normalized.equals(lower + "s")
          || (normalized.endsWith("s")
              && normalized.substring(0, normalized.length() - 1).equals(lower))) {
        return name;
      }
    }
    return candidates.size() == 1 ? candidates.get(0).name() : requestedName;
  }

  private SemanticModelPatch.Operation attributeOperation(
      ModelLevel level, Map<String, String> types, AttributeUpdate update) {
    String targetType = types.get(update.elementId());
    java.util.Optional<AssistantMetamodelSchemaService.AttributeSchema> attribute =
        targetType == null || update.attributeName().isBlank()
            ? java.util.Optional.empty()
            : schemas.canonicalAttribute(level, targetType, update.attributeName());
    if (targetType == null || update.attributeName().isBlank() || attribute.isEmpty()) {
      throw new PlatformException(
          422,
          "ModelDelta attribute update is not grounded in the metamodel: elementId="
              + update.elementId()
              + ", attributeName="
              + update.attributeName()
              + ", elementType="
              + (targetType == null ? "unknown" : targetType)
              + ".");
    }
    return new SemanticModelPatch.Operation(
        SemanticModelPatch.OperationType.SET_ATTRIBUTE,
        update.elementId(),
        null,
        update.value(),
        null,
        attribute.get().name());
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
