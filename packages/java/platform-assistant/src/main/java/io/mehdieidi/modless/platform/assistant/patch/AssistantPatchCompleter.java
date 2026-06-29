package io.mehdieidi.modless.platform.assistant.patch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.application.AssistantValidationFeedbackResolver;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Deterministically adds required nested elements and attributes omitted by the LLM. */
public class AssistantPatchCompleter {

  private final AssistantMetamodelSchemaService schemas;

  public AssistantPatchCompleter(AssistantMetamodelSchemaService schemas) {
    this.schemas = schemas;
  }

  /**
   * Expands a semantic patch until required single-valued containments and attributes are present.
   *
   * @param level model level
   * @param patch planner patch
   * @param existingTypes stable IDs to metamodel types already in the model context
   * @return completed patch with operations ordered for compilation
   */
  public SemanticModelPatch complete(
      ModelLevel level, SemanticModelPatch patch, Map<String, String> existingTypes) {
    if (patch.operations().isEmpty()) {
      return patch;
    }
    Map<String, String> types =
        new LinkedHashMap<>(existingTypes == null ? Map.of() : existingTypes);
    List<SemanticModelPatch.Operation> operations =
        orderOperationsForCompilation(stripOrphanContainedAdds(level, patch.operations(), types));
    if (operations.isEmpty()) {
      return patch;
    }
    operations = normalizeReferenceShapedOperations(level, operations, types);
    if (operations.isEmpty()) {
      return new SemanticModelPatch(List.of());
    }
    boolean changed;
    do {
      changed = false;
      List<SemanticModelPatch.Operation> additions = new ArrayList<>();
      for (SemanticModelPatch.Operation operation : operations) {
        if (operation == null || operation.type() != SemanticModelPatch.OperationType.ADD_ELEMENT) {
          continue;
        }
        String targetId = operation.targetElementId();
        if (blank(targetId)) {
          continue;
        }
        String canonicalType;
        try {
          canonicalType = schemas.canonicalType(level, operation.elementType());
        } catch (PlatformException ignored) {
          continue;
        }
        types.putIfAbsent(targetId, canonicalType);
        SemanticModelPatch.Operation withAttributes =
            fillRequiredAttributes(level, canonicalType, operation);
        if (withAttributes != operation) {
          replaceOperation(operations, operation, withAttributes);
          changed = true;
        }
        AssistantMetamodelSchemaService.TypeSchema typeSchema =
            schemas.typeSchema(level, canonicalType).orElse(null);
        if (typeSchema == null) {
          continue;
        }
        for (AssistantMetamodelSchemaService.ReferenceSchema reference : typeSchema.references()) {
          if (!reference.required() || !reference.containment()) {
            continue;
          }
          if (hasContainedChild(operations, targetId, reference.name())) {
            continue;
          }
          String childId = java.util.UUID.randomUUID().toString();
          String childName = deriveChildName(withAttributes, canonicalType, reference);
          ObjectNode attributes = JsonNodeFactory.instance.objectNode();
          attributes.put("name", childName);
          SemanticModelPatch.Operation child =
              new SemanticModelPatch.Operation(
                  SemanticModelPatch.OperationType.ADD_ELEMENT,
                  childId,
                  reference.targetType(),
                  attributes,
                  targetId,
                  reference.name());
          additions.add(child);
          types.put(childId, schemas.canonicalType(level, reference.targetType()));
          changed = true;
        }
      }
      if (!additions.isEmpty()) {
        operations.addAll(additions);
        operations = orderOperationsForCompilation(operations);
      }
    } while (changed);
    operations = ensureContractSchemas(level, operations, types);
    operations = ensureFunctionIdempotency(level, operations, types);
    operations = ensureDataStoreStructure(level, operations, types);
    operations = ensureDataModelSchemas(level, operations, types);
    operations = ensureRequiredReferences(level, operations, types);
    return new SemanticModelPatch(orderOperationsForCompilation(operations));
  }

  /**
   * Adds missing required containments identified by validator feedback for existing elements.
   *
   * @param level model level
   * @param patch planner patch
   * @param existingTypes stable IDs to metamodel types already in the model context
   * @param missing required features reported by validation
   * @return expanded patch
   */
  public SemanticModelPatch repairFromValidationFeedback(
      ModelLevel level,
      SemanticModelPatch patch,
      Map<String, String> existingTypes,
      List<AssistantValidationFeedbackResolver.MissingRequiredFeature> missing) {
    SemanticModelPatch completed = complete(level, patch, existingTypes);
    if (missing == null || missing.isEmpty()) {
      return completed;
    }
    Map<String, String> types =
        new LinkedHashMap<>(existingTypes == null ? Map.of() : existingTypes);
    List<SemanticModelPatch.Operation> operations = new ArrayList<>(completed.operations());
    for (SemanticModelPatch.Operation operation : operations) {
      if (operation == null || operation.type() != SemanticModelPatch.OperationType.ADD_ELEMENT) {
        continue;
      }
      if (blank(operation.targetElementId())) {
        continue;
      }
      try {
        types.putIfAbsent(
            operation.targetElementId(), schemas.canonicalType(level, operation.elementType()));
      } catch (PlatformException ignored) {
        types.putIfAbsent(operation.targetElementId(), operation.elementType());
      }
    }
    boolean changed = false;
    for (AssistantValidationFeedbackResolver.MissingRequiredFeature requirement : missing) {
      if (requirement == null
          || blank(requirement.ownerElementId())
          || blank(requirement.featureName())) {
        continue;
      }
      String ownerType = types.get(requirement.ownerElementId());
      if (ownerType == null) {
        continue;
      }
      AssistantMetamodelSchemaService.ReferenceSchema reference =
          schemas
              .reference(level, ownerType, requirement.featureName())
              .filter(ref -> ref.required() && ref.containment() && !ref.many())
              .orElse(null);
      if (reference == null
          || hasContainedChild(
              operations, requirement.ownerElementId(), requirement.featureName())) {
        continue;
      }
      String childId = java.util.UUID.randomUUID().toString();
      ObjectNode attributes = JsonNodeFactory.instance.objectNode();
      attributes.put("name", humanize(reference.targetType()));
      operations.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.ADD_ELEMENT,
              childId,
              reference.targetType(),
              attributes,
              requirement.ownerElementId(),
              requirement.featureName()));
      types.put(childId, schemas.canonicalType(level, reference.targetType()));
      changed = true;
    }
    if (!changed) {
      return completed;
    }
    return new SemanticModelPatch(orderOperationsForCompilation(operations));
  }

  private SemanticModelPatch.Operation fillRequiredAttributes(
      ModelLevel level, String canonicalType, SemanticModelPatch.Operation operation) {
    AssistantMetamodelSchemaService.TypeSchema typeSchema =
        schemas.typeSchema(level, canonicalType).orElse(null);
    if (typeSchema == null) {
      return operation;
    }
    ObjectNode attributes =
        operation.attributes() == null || operation.attributes().isNull()
            ? JsonNodeFactory.instance.objectNode()
            : ((ObjectNode) operation.attributes().deepCopy());
    boolean changed = false;
    String parentName = attributes.path("name").asText(canonicalType);
    if (typeSchema.attribute("name").isPresent()) {
      JsonNode currentName = attributes.get("name");
      if (currentName == null
          || currentName.isNull()
          || currentName.asText("").isBlank()
          || currentName.asText("").trim().equalsIgnoreCase(canonicalType)) {
        attributes.put("name", defaultElementName(canonicalType, operation.referenceName()));
        changed = true;
      }
    }
    for (AssistantMetamodelSchemaService.AttributeSchema attribute : typeSchema.attributes()) {
      if (!attribute.required()) {
        continue;
      }
      JsonNode current = attributes.get(attribute.name());
      if (current != null && !current.isNull() && !current.asText("").isBlank()) {
        continue;
      }
      JsonNode defaultValue = defaultAttributeValue(attribute, parentName, canonicalType);
      if (defaultValue != null) {
        attributes.set(attribute.name(), defaultValue);
        changed = true;
      }
    }
    return changed
        ? new SemanticModelPatch.Operation(
            operation.type(),
            operation.targetElementId(),
            operation.elementType(),
            attributes,
            operation.sourceElementId(),
            operation.referenceName())
        : operation;
  }

  private String defaultElementName(String canonicalType, String referenceName) {
    if (referenceName != null && !referenceName.isBlank()) {
      return capitalize(humanize(referenceName));
    }
    return "Modeled " + capitalize(humanize(canonicalType));
  }

  private String capitalize(String value) {
    if (value == null || value.isBlank()) {
      return "Element";
    }
    String trimmed = value.trim();
    return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1);
  }

  private JsonNode defaultAttributeValue(
      AssistantMetamodelSchemaService.AttributeSchema attribute,
      String parentName,
      String canonicalType) {
    if ("name".equals(attribute.name())) {
      return JsonNodeFactory.instance.textNode(parentName);
    }
    if (!attribute.options().isEmpty()) {
      return JsonNodeFactory.instance.textNode(attribute.options().get(0));
    }
    String type = attribute.type() == null ? "" : attribute.type().toLowerCase(Locale.ROOT);
    if (type.contains("boolean")) {
      return JsonNodeFactory.instance.booleanNode(false);
    }
    if (type.contains("int") || type.contains("long") || type.contains("double")) {
      return JsonNodeFactory.instance.numberNode(0);
    }
    return JsonNodeFactory.instance.textNode(humanize(canonicalType));
  }

  private String deriveChildName(
      SemanticModelPatch.Operation parent,
      String parentType,
      AssistantMetamodelSchemaService.ReferenceSchema reference) {
    String parentName =
        parent.attributes() == null ? "" : parent.attributes().path("name").asText("");
    if (!parentName.isBlank()) {
      return parentName + " " + humanize(reference.targetType());
    }
    return humanize(parentType) + " " + humanize(reference.name());
  }

  private String humanize(String value) {
    if (value == null || value.isBlank()) {
      return "Element";
    }
    return value.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').trim();
  }

  private boolean hasContainedChild(
      List<SemanticModelPatch.Operation> operations, String ownerId, String referenceName) {
    return operations.stream()
        .anyMatch(
            operation ->
                operation != null
                    && operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT
                    && ownerId.equals(operation.sourceElementId())
                    && referenceName.equals(operation.referenceName()));
  }

  private List<SemanticModelPatch.Operation> normalizeReferenceShapedOperations(
      ModelLevel level, List<SemanticModelPatch.Operation> operations, Map<String, String> types) {
    collectAddTypes(level, operations, types);
    List<SemanticModelPatch.Operation> normalized = new ArrayList<>();
    for (SemanticModelPatch.Operation operation : operations) {
      if (operation == null) {
        continue;
      }
      if (operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT) {
        normalized.add(
            stripAndLiftEmbeddedReferences(level, operation, operations, normalized, types));
        continue;
      }
      if (operation.type() == SemanticModelPatch.OperationType.SET_ATTRIBUTE) {
        String targetType = types.get(operation.targetElementId());
        AssistantMetamodelSchemaService.ReferenceSchema reference =
            targetType == null
                ? null
                : schemas.reference(level, targetType, operation.referenceName()).orElse(null);
        if (reference == null) {
          normalized.add(operation);
          continue;
        }
        if (reference.containment()) {
          addContainedValues(
              level,
              normalized,
              operations,
              types,
              operation.targetElementId(),
              reference,
              operation.attributes());
        } else {
          addReferenceConnections(
              normalized, operation.targetElementId(), reference.name(), operation.attributes());
        }
        continue;
      }
      normalized.add(operation);
    }
    return orderOperationsForCompilation(normalized);
  }

  private void collectAddTypes(
      ModelLevel level, List<SemanticModelPatch.Operation> operations, Map<String, String> types) {
    for (SemanticModelPatch.Operation operation : operations) {
      if (operation == null
          || operation.type() != SemanticModelPatch.OperationType.ADD_ELEMENT
          || blank(operation.targetElementId())) {
        continue;
      }
      try {
        types.putIfAbsent(
            operation.targetElementId(), schemas.canonicalType(level, operation.elementType()));
      } catch (PlatformException ignored) {
        // Unknown types are handled by the normal validation/repair path.
      }
    }
  }

  private SemanticModelPatch.Operation stripAndLiftEmbeddedReferences(
      ModelLevel level,
      SemanticModelPatch.Operation operation,
      List<SemanticModelPatch.Operation> allOperations,
      List<SemanticModelPatch.Operation> normalized,
      Map<String, String> types) {
    if (!(operation.attributes() instanceof ObjectNode attributes)
        || blank(operation.targetElementId())) {
      return operation;
    }
    String canonicalType;
    try {
      canonicalType = schemas.canonicalType(level, operation.elementType());
    } catch (PlatformException ignored) {
      return operation;
    }
    AssistantMetamodelSchemaService.TypeSchema typeSchema =
        schemas.typeSchema(level, canonicalType).orElse(null);
    if (typeSchema == null) {
      return operation;
    }
    ObjectNode sanitized = attributes.deepCopy();
    boolean changed = false;
    for (AssistantMetamodelSchemaService.ReferenceSchema reference : typeSchema.references()) {
      JsonNode embedded = sanitized.remove(reference.name());
      if (embedded == null || embedded.isNull()) {
        continue;
      }
      changed = true;
      if (reference.containment()) {
        addContainedValues(
            level,
            normalized,
            allOperations,
            types,
            operation.targetElementId(),
            reference,
            embedded);
      } else {
        addReferenceConnections(
            normalized, operation.targetElementId(), reference.name(), embedded);
      }
    }
    if (!changed) {
      return operation;
    }
    return new SemanticModelPatch.Operation(
        operation.type(),
        operation.targetElementId(),
        operation.elementType(),
        sanitized,
        operation.sourceElementId(),
        operation.referenceName());
  }

  private void addContainedValues(
      ModelLevel level,
      List<SemanticModelPatch.Operation> normalized,
      List<SemanticModelPatch.Operation> allOperations,
      Map<String, String> types,
      String ownerId,
      AssistantMetamodelSchemaService.ReferenceSchema reference,
      JsonNode value) {
    if (blank(ownerId)
        || hasContainedChild(allOperations, ownerId, reference.name())
        || hasContainedChild(normalized, ownerId, reference.name())) {
      return;
    }
    if (value instanceof ArrayNode array) {
      if (!reference.many() && !array.isEmpty()) {
        addContainedValue(level, normalized, types, ownerId, reference, array.get(0));
        return;
      }
      array.forEach(item -> addContainedValue(level, normalized, types, ownerId, reference, item));
      return;
    }
    addContainedValue(level, normalized, types, ownerId, reference, value);
  }

  private void addContainedValue(
      ModelLevel level,
      List<SemanticModelPatch.Operation> normalized,
      Map<String, String> types,
      String ownerId,
      AssistantMetamodelSchemaService.ReferenceSchema reference,
      JsonNode value) {
    ObjectNode attributes =
        value instanceof ObjectNode object
            ? object.deepCopy()
            : JsonNodeFactory.instance.objectNode();
    attributes.remove("id");
    attributes.remove("eClass");
    if (!attributes.hasNonNull("name")) {
      attributes.put("name", humanize(reference.targetType()));
    }
    String childId = java.util.UUID.randomUUID().toString();
    normalized.add(
        new SemanticModelPatch.Operation(
            SemanticModelPatch.OperationType.ADD_ELEMENT,
            childId,
            reference.targetType(),
            attributes,
            ownerId,
            reference.name()));
    try {
      types.put(childId, schemas.canonicalType(level, reference.targetType()));
    } catch (PlatformException ignored) {
      types.put(childId, reference.targetType());
    }
  }

  private void addReferenceConnections(
      List<SemanticModelPatch.Operation> normalized,
      String sourceId,
      String referenceName,
      JsonNode value) {
    if (blank(sourceId)) {
      return;
    }
    for (String targetId : referenceTargetIds(value)) {
      if (blank(targetId) || hasRelationship(normalized, sourceId, referenceName)) {
        continue;
      }
      normalized.add(connectOperation(sourceId, targetId, referenceName));
    }
  }

  private List<String> referenceTargetIds(JsonNode value) {
    if (value == null || value.isNull()) {
      return List.of();
    }
    if (value instanceof ArrayNode array) {
      List<String> ids = new ArrayList<>();
      array.forEach(item -> ids.addAll(referenceTargetIds(item)));
      return ids;
    }
    if (value.isTextual()) {
      return List.of(value.asText());
    }
    if (value instanceof ObjectNode object) {
      String id = object.path("id").asText("");
      if (!id.isBlank()) {
        return List.of(id);
      }
      String targetElementId = object.path("targetElementId").asText("");
      if (!targetElementId.isBlank()) {
        return List.of(targetElementId);
      }
    }
    return List.of();
  }

  private void replaceOperation(
      List<SemanticModelPatch.Operation> operations,
      SemanticModelPatch.Operation previous,
      SemanticModelPatch.Operation replacement) {
    for (int index = 0; index < operations.size(); index++) {
      if (operations.get(index) == previous) {
        operations.set(index, replacement);
        return;
      }
    }
  }

  private List<SemanticModelPatch.Operation> orderOperationsForCompilation(
      List<SemanticModelPatch.Operation> operations) {
    List<SemanticModelPatch.Operation> pendingAdds =
        operations.stream()
            .filter(java.util.Objects::nonNull)
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .collect(Collectors.toCollection(ArrayList::new));
    List<SemanticModelPatch.Operation> ordered = new ArrayList<>();
    Set<String> pendingIds =
        pendingAdds.stream()
            .map(SemanticModelPatch.Operation::targetElementId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    while (!pendingAdds.isEmpty()) {
      List<SemanticModelPatch.Operation> ready =
          pendingAdds.stream()
              .filter(
                  operation ->
                      blank(operation.sourceElementId())
                          || !pendingIds.contains(operation.sourceElementId()))
              .toList();
      if (ready.isEmpty()) {
        ordered.addAll(pendingAdds);
        break;
      }
      ordered.addAll(ready);
      pendingAdds.removeAll(ready);
      ready.stream().map(SemanticModelPatch.Operation::targetElementId).forEach(pendingIds::remove);
    }
    operations.stream()
        .filter(
            operation ->
                operation == null
                    || operation.type() != SemanticModelPatch.OperationType.ADD_ELEMENT)
        .forEach(ordered::add);
    return ordered;
  }

  private List<SemanticModelPatch.Operation> ensureContractSchemas(
      ModelLevel level, List<SemanticModelPatch.Operation> operations, Map<String, String> types) {
    List<SemanticModelPatch.Operation> result = new ArrayList<>(operations);
    for (String typeId : new ArrayList<>(types.keySet())) {
      String typeName = types.get(typeId);
      if (!"FunctionContract".equals(typeName)) {
        continue;
      }
      if (hasRelationship(result, typeId, "inputSchema")
          || hasRelationship(result, typeId, "outputSchema")) {
        continue;
      }
      String schemaId = java.util.UUID.randomUUID().toString();
      String fieldId = java.util.UUID.randomUUID().toString();
      ObjectNode schemaAttributes = JsonNodeFactory.instance.objectNode();
      String contractName = contractName(result, typeId);
      schemaAttributes.put("name", contractName + " input");
      schemaAttributes.put("schemaKind", "REQUEST");
      schemaAttributes.put("semanticVersion", "1.0.0");
      result.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.ADD_ELEMENT,
              schemaId,
              "Schema",
              schemaAttributes,
              null,
              null));
      ObjectNode fieldAttributes = JsonNodeFactory.instance.objectNode();
      fieldAttributes.put("name", "payload");
      fieldAttributes.put("fieldType", "STRING");
      fieldAttributes.put("required", true);
      result.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.ADD_ELEMENT,
              fieldId,
              "SchemaField",
              fieldAttributes,
              schemaId,
              "fields"));
      result.add(connectOperation(typeId, schemaId, "inputSchema"));
      types.put(schemaId, "Schema");
      types.put(fieldId, "SchemaField");
    }
    return result;
  }

  private List<SemanticModelPatch.Operation> ensureDataModelSchemas(
      ModelLevel level, List<SemanticModelPatch.Operation> operations, Map<String, String> types) {
    List<SemanticModelPatch.Operation> result = new ArrayList<>(operations);
    for (String typeId : new ArrayList<>(types.keySet())) {
      String typeName = types.get(typeId);
      if (!"DataModel".equals(typeName)) {
        continue;
      }
      if (hasRelationship(result, typeId, "schema")) {
        continue;
      }
      String schemaId = java.util.UUID.randomUUID().toString();
      String fieldId = java.util.UUID.randomUUID().toString();
      ObjectNode schemaAttributes = JsonNodeFactory.instance.objectNode();
      schemaAttributes.put("name", humanize(typeId) + " schema");
      schemaAttributes.put("schemaKind", "REQUEST");
      schemaAttributes.put("semanticVersion", "1.0.0");
      result.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.ADD_ELEMENT,
              schemaId,
              "Schema",
              schemaAttributes,
              null,
              null));
      ObjectNode fieldAttributes = JsonNodeFactory.instance.objectNode();
      fieldAttributes.put("name", "id");
      fieldAttributes.put("fieldType", "STRING");
      fieldAttributes.put("required", true);
      result.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.ADD_ELEMENT,
              fieldId,
              "SchemaField",
              fieldAttributes,
              schemaId,
              "fields"));
      result.add(connectOperation(typeId, schemaId, "schema"));
      types.put(schemaId, "Schema");
      types.put(fieldId, "SchemaField");
    }
    return result;
  }

  private List<SemanticModelPatch.Operation> ensureDataStoreStructure(
      ModelLevel level, List<SemanticModelPatch.Operation> operations, Map<String, String> types) {
    List<SemanticModelPatch.Operation> result = new ArrayList<>(operations);
    for (SemanticModelPatch.Operation operation : operations) {
      if (operation == null
          || operation.type() != SemanticModelPatch.OperationType.ADD_ELEMENT
          || !"DataStore".equals(operation.elementType())) {
        continue;
      }
      String storeId = operation.targetElementId();
      String storeName =
          operation.attributes() == null
              ? "Inventory store"
              : operation.attributes().path("name").asText("Inventory store");
      if (!hasContainedChild(result, storeId, "ownedDataModels")) {
        String dataModelId = java.util.UUID.randomUUID().toString();
        ObjectNode dataModelAttributes = JsonNodeFactory.instance.objectNode();
        dataModelAttributes.put("name", storeName + " model");
        dataModelAttributes.put("sourceOfTruth", true);
        result.add(
            new SemanticModelPatch.Operation(
                SemanticModelPatch.OperationType.ADD_ELEMENT,
                dataModelId,
                "DataModel",
                dataModelAttributes,
                storeId,
                "ownedDataModels"));
        types.put(dataModelId, "DataModel");
      }
      if (!hasContainedChild(result, storeId, "accessPatterns")) {
        String accessPatternId = java.util.UUID.randomUUID().toString();
        ObjectNode accessAttributes = JsonNodeFactory.instance.objectNode();
        accessAttributes.put("name", storeName + " access");
        accessAttributes.put("operation", "READ_WRITE");
        accessAttributes.put("queryBy", "id");
        result.add(
            new SemanticModelPatch.Operation(
                SemanticModelPatch.OperationType.ADD_ELEMENT,
                accessPatternId,
                "AccessPattern",
                accessAttributes,
                storeId,
                "accessPatterns"));
        types.put(accessPatternId, "AccessPattern");
      }
    }
    return result;
  }

  private String contractName(List<SemanticModelPatch.Operation> operations, String contractId) {
    return operations.stream()
        .filter(
            operation ->
                operation != null
                    && operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT
                    && "FunctionContract".equals(operation.elementType())
                    && contractId.equals(operation.targetElementId()))
        .map(operation -> operation.attributes().path("name").asText("Contract"))
        .findFirst()
        .orElse("Contract");
  }

  private String functionName(SemanticModelPatch.Operation operation) {
    if (operation.attributes() == null) {
      return "Function";
    }
    String name = operation.attributes().path("name").asText("");
    return name.isBlank() ? "Function" : name;
  }

  private List<SemanticModelPatch.Operation> ensureFunctionIdempotency(
      ModelLevel level, List<SemanticModelPatch.Operation> operations, Map<String, String> types) {
    List<SemanticModelPatch.Operation> result = new ArrayList<>(operations);
    for (SemanticModelPatch.Operation operation : operations) {
      if (operation == null
          || operation.type() != SemanticModelPatch.OperationType.ADD_ELEMENT
          || !"Function".equals(operation.elementType())) {
        continue;
      }
      JsonNode attributes = operation.attributes();
      boolean writesState = attributes != null && attributes.path("writesState").asBoolean(false);
      if (!writesState || hasRelationship(result, operation.targetElementId(), "idempotency")) {
        continue;
      }
      String policyId = java.util.UUID.randomUUID().toString();
      ObjectNode policyAttributes = JsonNodeFactory.instance.objectNode();
      String functionName = functionName(operation);
      policyAttributes.put("name", functionName + " idempotency");
      policyAttributes.put("keySource", "request.idempotencyKey");
      policyAttributes.put("scope", "FUNCTION");
      policyAttributes.put("storeRequired", true);
      policyAttributes.put("expirationSeconds", 3600);
      result.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.ADD_ELEMENT,
              policyId,
              "IdempotencyPolicy",
              policyAttributes,
              null,
              null));
      result.add(connectOperation(operation.targetElementId(), policyId, "idempotency"));
      types.put(policyId, "IdempotencyPolicy");
    }
    return result;
  }

  private boolean hasRelationship(
      List<SemanticModelPatch.Operation> operations, String sourceId, String referenceName) {
    return operations.stream()
        .anyMatch(
            operation ->
                operation != null
                    && operation.type() == SemanticModelPatch.OperationType.CONNECT_ELEMENTS
                    && sourceId.equals(operation.sourceElementId())
                    && referenceName.equals(operation.referenceName()));
  }

  private List<SemanticModelPatch.Operation> ensureRequiredReferences(
      ModelLevel level, List<SemanticModelPatch.Operation> operations, Map<String, String> types) {
    List<SemanticModelPatch.Operation> result = new ArrayList<>(operations);
    for (Map.Entry<String, String> entry : new ArrayList<>(types.entrySet())) {
      String sourceId = entry.getKey();
      String sourceType = entry.getValue();
      AssistantMetamodelSchemaService.TypeSchema typeSchema =
          schemas.typeSchema(level, sourceType).orElse(null);
      if (typeSchema == null) {
        continue;
      }
      for (AssistantMetamodelSchemaService.ReferenceSchema reference : typeSchema.references()) {
        if (!reference.required()
            || reference.containment()
            || reference.readonly()
            || hasRelationship(result, sourceId, reference.name())) {
          continue;
        }
        compatibleTargets(level, sourceId, reference.name(), types).stream()
            .findFirst()
            .map(targetId -> connectOperation(sourceId, targetId, reference.name()))
            .ifPresent(result::add);
      }
    }
    return result;
  }

  private List<String> compatibleTargets(
      ModelLevel level, String sourceId, String referenceName, Map<String, String> types) {
    String sourceType = types.get(sourceId);
    if (sourceType == null) {
      return List.of();
    }
    return types.entrySet().stream()
        .filter(entry -> !entry.getKey().equals(sourceId))
        .filter(
            entry ->
                schemas.acceptsReferenceTarget(level, sourceType, referenceName, entry.getValue()))
        .map(Map.Entry::getKey)
        .toList();
  }

  private SemanticModelPatch.Operation connectOperation(
      String sourceId, String targetId, String referenceName) {
    return new SemanticModelPatch.Operation(
        SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
        targetId,
        null,
        null,
        sourceId,
        referenceName);
  }

  private List<SemanticModelPatch.Operation> stripOrphanContainedAdds(
      ModelLevel level,
      List<SemanticModelPatch.Operation> operations,
      Map<String, String> existingTypes) {
    Set<String> plannedParents =
        operations.stream()
            .filter(java.util.Objects::nonNull)
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .map(SemanticModelPatch.Operation::targetElementId)
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    List<SemanticModelPatch.Operation> sanitized = new ArrayList<>();
    for (SemanticModelPatch.Operation operation : operations) {
      if (operation == null) {
        continue;
      }
      if (operation.type() != SemanticModelPatch.OperationType.ADD_ELEMENT) {
        sanitized.add(operation);
        continue;
      }
      if (!blank(operation.sourceElementId())) {
        sanitized.add(operation);
        continue;
      }
      try {
        String canonicalType = schemas.canonicalType(level, operation.elementType());
        if (schemas.rootCollection(level, canonicalType).isPresent()) {
          sanitized.add(operation);
          continue;
        }
        Optional<AssistantMetamodelSchemaService.TypeSchema> typeSchema =
            schemas.typeSchema(level, canonicalType);
        if (typeSchema.isPresent() && !typeSchema.get().creatable()) {
          continue;
        }
        List<ContainmentCandidate> candidates = new ArrayList<>();
        existingTypes.entrySet().stream()
            .flatMap(
                entry ->
                    schemas.containments(level, entry.getValue(), canonicalType).stream()
                        .filter(reference -> reference.required() && !reference.many())
                        .map(
                            reference ->
                                new ContainmentCandidate(entry.getKey(), reference.name())))
            .forEach(candidates::add);
        operations.stream()
            .filter(op -> op != null && op.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .filter(op -> plannedParents.contains(op.targetElementId()))
            .flatMap(
                op -> {
                  try {
                    return schemas
                        .containments(
                            level, schemas.canonicalType(level, op.elementType()), canonicalType)
                        .stream()
                        .filter(reference -> reference.required() && !reference.many())
                        .map(
                            reference ->
                                new ContainmentCandidate(op.targetElementId(), reference.name()));
                  } catch (PlatformException ignored) {
                    return java.util.stream.Stream.empty();
                  }
                })
            .forEach(candidates::add);
        if (candidates.size() == 1) {
          ContainmentCandidate candidate = candidates.get(0);
          sanitized.add(
              new SemanticModelPatch.Operation(
                  operation.type(),
                  operation.targetElementId(),
                  operation.elementType(),
                  operation.attributes(),
                  candidate.ownerId(),
                  candidate.referenceName()));
        } else {
          sanitized.add(operation);
        }
      } catch (PlatformException ignored) {
        sanitized.add(operation);
      }
    }
    return sanitized;
  }

  private record ContainmentCandidate(String ownerId, String referenceName) {}

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
