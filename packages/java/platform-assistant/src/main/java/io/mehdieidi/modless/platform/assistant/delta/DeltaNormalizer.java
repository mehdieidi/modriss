package io.mehdieidi.modless.platform.assistant.delta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService.AttributeContract;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Metamodel-driven normalization for provider-returned ModelDelta. */
public class DeltaNormalizer {

  private final MetamodelKnowledgeService metamodels;
  private final AssistantMetamodelSchemaService schemas;

  public DeltaNormalizer(AssistantMetamodelSchemaService schemas) {
    this(new MetamodelKnowledgeService(schemas), schemas);
  }

  public DeltaNormalizer(
      MetamodelKnowledgeService metamodels, AssistantMetamodelSchemaService schemas) {
    this.metamodels = metamodels == null ? new MetamodelKnowledgeService(schemas) : metamodels;
    this.schemas = schemas == null ? new AssistantMetamodelSchemaService() : schemas;
  }

  /** Normalizes only formal details: type casing, local IDs, enum casing, and root placement. */
  public ModelDelta normalize(ModelLevel level, ModelDelta delta) {
    if (delta == null) {
      return null;
    }
    Map<String, String> localIds = new LinkedHashMap<>();
    List<ModelDelta.Element> elements =
        delta.elements().stream()
            .filter(element -> !isRootModelType(level, element.eClass()))
            .map(element -> normalizeElement(level, element, localIds))
            .toList();
    Map<String, String> elementTypes = new LinkedHashMap<>();
    elements.forEach(element -> elementTypes.put(element.localId(), element.eClass()));
    String rootType = metamodels.rootType(level);
    elements =
        elements.stream()
            .map(
                element ->
                    canonicalizeElementPlacement(level, element, localIds, elementTypes, rootType))
            .toList();
    List<ModelDelta.Reference> promotedReferences = new ArrayList<>();
    elements =
        elements.stream()
            .map(
                element ->
                    promoteReferencePlacement(
                        level, element, localIds, elementTypes, rootType, promotedReferences))
            .map(
                element -> resolveOrphanPlacement(level, element, localIds, elementTypes, rootType))
            .toList();
    elements =
        elements.stream()
            .map(element -> canonicalizeElementReferences(level, element, localIds, elementTypes))
            .map(element -> pruneElementReferences(level, element, elementTypes))
            .toList();
    List<ModelDelta.Reference> references = new ArrayList<>();
    delta
        .references()
        .forEach(
            reference ->
                references.add(normalizeReference(level, reference, localIds, elementTypes)));
    references.addAll(promotedReferences);
    List<ModelDelta.Reference> groundedReferences =
        references.stream()
            .filter(reference -> isGroundedReference(level, reference, elementTypes))
            .toList();
    return new ModelDelta(
        delta.intent(),
        delta.kind(),
        delta.message(),
        delta.questions(),
        elements,
        List.copyOf(groundedReferences),
        delta.attributeUpdates().stream()
            .map(update -> normalizeUpdate(level, update, localIds, elementTypes))
            .toList(),
        delta.deletions(),
        delta.assumptions());
  }

  private ModelDelta.Element normalizeElement(
      ModelLevel level, ModelDelta.Element element, Map<String, String> localIds) {
    String type = canonical(level, element.eClass());
    String originalId = element.localId();
    String localId =
        sanitizeLocalId(originalId.isBlank() ? "new_" + (localIds.size() + 1) : originalId);
    registerLocalIdAliases(localIds, originalId, localId);
    ModelDelta.Placement placement = normalizePlacement(level, type, element.placement(), localIds);
    JsonNode attributes = canonicalizeAttributes(level, type, element.attributes());
    return new ModelDelta.Element(
        localId,
        type,
        attributes,
        placement,
        element.references().stream()
            .map(reference -> normalizeReferenceIds(reference, localIds))
            .toList(),
        element.evidenceIds());
  }

  private ModelDelta.Placement normalizePlacement(
      ModelLevel level, String type, ModelDelta.Placement placement, Map<String, String> localIds) {
    if (placement != null && !placement.referenceName().isBlank()) {
      return new ModelDelta.Placement(
          localIds.getOrDefault(placement.ownerId(), placement.ownerId()),
          placement.referenceName());
    }
    return metamodels
        .rootContainment(level, type)
        .map(reference -> new ModelDelta.Placement("root", reference.name()))
        .orElse(placement);
  }

  private ModelDelta.Element canonicalizeElementPlacement(
      ModelLevel level,
      ModelDelta.Element element,
      Map<String, String> localIds,
      Map<String, String> elementTypes,
      String rootType) {
    ModelDelta.Placement placement = element.placement();
    if (placement == null || placement.referenceName().isBlank()) {
      return element;
    }
    String ownerId = localIds.getOrDefault(placement.ownerId(), placement.ownerId());
    String ownerType = ownerType(level, ownerId, elementTypes, rootType);
    if (ownerType == null || ownerType.isBlank()) {
      return element;
    }
    String referenceName =
        DeltaPlacementNames.canonicalContainmentName(
            schemas, level, ownerType, placement.referenceName(), element.eClass());
    return new ModelDelta.Element(
        element.localId(),
        element.eClass(),
        element.attributes(),
        new ModelDelta.Placement(ownerId, referenceName),
        element.references(),
        element.evidenceIds());
  }

  private String ownerType(
      ModelLevel level, String ownerId, Map<String, String> elementTypes, String rootType) {
    if (ownerId == null || ownerId.isBlank() || "root".equalsIgnoreCase(ownerId)) {
      return rootType;
    }
    return elementTypes.get(ownerId);
  }

  private ModelDelta.Element promoteReferencePlacement(
      ModelLevel level,
      ModelDelta.Element element,
      Map<String, String> localIds,
      Map<String, String> elementTypes,
      String rootType,
      List<ModelDelta.Reference> promotedReferences) {
    ModelDelta.Placement placement = element.placement();
    if (placement == null
        || placement.referenceName().isBlank()
        || "root".equalsIgnoreCase(placement.ownerId())) {
      return element;
    }
    String ownerId = localIds.getOrDefault(placement.ownerId(), placement.ownerId());
    String ownerType = ownerType(level, ownerId, elementTypes, rootType);
    if (ownerType == null || ownerType.isBlank()) {
      return element;
    }
    String childType = element.eClass();
    String referenceName = placement.referenceName();
    if (isContainment(level, ownerType, referenceName, childType)) {
      return element;
    }
    referenceName =
        resolveWritableReference(level, ownerType, referenceName, childType).orElse(null);
    if (referenceName == null) {
      return element;
    }
    java.util.Optional<AssistantMetamodelSchemaService.ReferenceSchema> rootContainment =
        schemas.rootContainment(level, childType);
    if (rootContainment.isEmpty()) {
      return element;
    }
    promotedReferences.add(new ModelDelta.Reference(ownerId, referenceName, element.localId()));
    return new ModelDelta.Element(
        element.localId(),
        element.eClass(),
        element.attributes(),
        new ModelDelta.Placement("root", rootContainment.get().name()),
        element.references(),
        element.evidenceIds());
  }

  private java.util.Optional<String> resolveWritableReference(
      ModelLevel level, String ownerType, String requestedName, String childType) {
    if (requestedName != null
        && !requestedName.isBlank()
        && schemas.acceptsReferenceTarget(level, ownerType, requestedName, childType)) {
      return java.util.Optional.of(requestedName);
    }
    String canonical =
        DeltaReferenceNames.canonicalReferenceName(
            schemas, level, ownerType, requestedName, childType);
    if (canonical != null
        && !canonical.isBlank()
        && schemas.acceptsReferenceTarget(level, ownerType, canonical, childType)) {
      return java.util.Optional.of(canonical);
    }
    return schemas.typeSchema(level, ownerType).stream()
        .flatMap(type -> type.references().stream())
        .filter(reference -> !reference.containment() && !reference.readonly())
        .filter(
            reference ->
                schemas.acceptsReferenceTarget(level, ownerType, reference.name(), childType))
        .map(AssistantMetamodelSchemaService.ReferenceSchema::name)
        .findFirst();
  }

  private ModelDelta.Element resolveOrphanPlacement(
      ModelLevel level,
      ModelDelta.Element element,
      Map<String, String> localIds,
      Map<String, String> elementTypes,
      String rootType) {
    ModelDelta.Placement placement = element.placement();
    if (placement == null || placement.referenceName().isBlank()) {
      return element;
    }
    String ownerId = localIds.getOrDefault(placement.ownerId(), placement.ownerId());
    if ("root".equalsIgnoreCase(ownerId) || elementTypes.containsKey(ownerId)) {
      return element;
    }
    java.util.Optional<AssistantMetamodelSchemaService.ReferenceSchema> rootContainment =
        schemas.rootContainment(level, element.eClass());
    if (rootContainment.isEmpty()) {
      return element;
    }
    return new ModelDelta.Element(
        element.localId(),
        element.eClass(),
        element.attributes(),
        new ModelDelta.Placement("root", rootContainment.get().name()),
        element.references(),
        element.evidenceIds());
  }

  private boolean isContainment(
      ModelLevel level, String ownerType, String referenceName, String childType) {
    try {
      schemas.requireContainment(level, ownerType, referenceName, childType);
      return true;
    } catch (PlatformException ignored) {
      return false;
    }
  }

  private JsonNode canonicalizeAttributes(ModelLevel level, String type, JsonNode attributes) {
    if (!(attributes instanceof ObjectNode object)) {
      return attributes;
    }
    ObjectNode result = object.deepCopy();
    object
        .fields()
        .forEachRemaining(
            entry -> {
              attributeContract(level, type, entry.getKey())
                  .ifPresent(
                      attribute -> {
                        if (attribute.enumLiterals().isEmpty()) {
                          return;
                        }
                        JsonNode canonical = canonicalizeEnumValue(attribute, entry.getValue());
                        if (canonical != null) {
                          result.set(entry.getKey(), canonical);
                        }
                      });
            });
    result.remove("id");
    result.remove("eClass");
    return result;
  }

  private JsonNode canonicalizeEnumValue(AttributeContract attribute, JsonNode value) {
    if (attribute.enumLiterals().isEmpty()) {
      return value;
    }
    String text = value == null || value.isNull() ? "" : value.asText("").trim();
    if (text.isBlank()) {
      return JsonNodeFactory.instance.textNode(attribute.enumLiterals().get(0));
    }
    for (String option : attribute.enumLiterals()) {
      if (option.equalsIgnoreCase(text)) {
        return JsonNodeFactory.instance.textNode(option);
      }
    }
    String alias = enumAlias(attribute.name(), text);
    if (alias != null) {
      for (String option : attribute.enumLiterals()) {
        if (option.equalsIgnoreCase(alias)) {
          return JsonNodeFactory.instance.textNode(option);
        }
      }
    }
    return JsonNodeFactory.instance.textNode(attribute.enumLiterals().get(0));
  }

  private String enumAlias(String attributeName, String value) {
    if (attributeName == null || value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if ("functionKind".equals(attributeName)) {
      return switch (normalized) {
        case "standard", "default", "generic", "handler", "lambda", "function" -> "EVENT_HANDLER";
        case "command", "write", "mutation" -> "COMMAND_HANDLER";
        case "query", "read" -> "QUERY_HANDLER";
        case "policy" -> "POLICY_HANDLER";
        case "scheduled", "cron", "timer" -> "SCHEDULED_TASK";
        case "stream", "processor" -> "STREAM_PROCESSOR";
        default -> null;
      };
    }
    return null;
  }

  private java.util.Optional<AttributeContract> attributeContract(
      ModelLevel level, String type, String name) {
    try {
      return metamodels.typeContract(level, type).attributes().stream()
          .filter(attribute -> attribute.name().equals(name))
          .findFirst();
    } catch (PlatformException ignored) {
      return schemas.attribute(level, type, name).map(this::toAttributeContract);
    }
  }

  private AttributeContract toAttributeContract(
      AssistantMetamodelSchemaService.AttributeSchema attribute) {
    return new AttributeContract(
        attribute.name(), attribute.type(), attribute.required(), attribute.options());
  }

  private ModelDelta.Element canonicalizeElementReferences(
      ModelLevel level,
      ModelDelta.Element element,
      Map<String, String> localIds,
      Map<String, String> elementTypes) {
    return new ModelDelta.Element(
        element.localId(),
        element.eClass(),
        element.attributes(),
        element.placement(),
        element.references().stream()
            .map(reference -> normalizeReference(level, reference, localIds, elementTypes))
            .toList(),
        element.evidenceIds());
  }

  private ModelDelta.Reference normalizeReferenceIds(
      ModelDelta.Reference reference, Map<String, String> localIds) {
    return new ModelDelta.Reference(
        localIds.getOrDefault(reference.sourceId(), reference.sourceId()),
        reference.referenceName(),
        localIds.getOrDefault(reference.targetId(), reference.targetId()));
  }

  private ModelDelta.Reference normalizeReference(
      ModelLevel level,
      ModelDelta.Reference reference,
      Map<String, String> localIds,
      Map<String, String> elementTypes) {
    String sourceId = localIds.getOrDefault(reference.sourceId(), reference.sourceId());
    String targetId = localIds.getOrDefault(reference.targetId(), reference.targetId());
    String sourceType = elementTypes.get(sourceId);
    String targetType = elementTypes.get(targetId);
    String referenceName = reference.referenceName();
    if (sourceType != null
        && targetType != null
        && referenceName != null
        && !referenceName.isBlank()) {
      referenceName =
          DeltaReferenceNames.canonicalReferenceName(
              schemas, level, sourceType, referenceName, targetType);
    }
    return new ModelDelta.Reference(sourceId, referenceName, targetId);
  }

  private ModelDelta.Element pruneElementReferences(
      ModelLevel level, ModelDelta.Element element, Map<String, String> elementTypes) {
    List<ModelDelta.Reference> references =
        element.references().stream()
            .filter(reference -> isGroundedReference(level, reference, elementTypes))
            .toList();
    return new ModelDelta.Element(
        element.localId(),
        element.eClass(),
        element.attributes(),
        element.placement(),
        references,
        element.evidenceIds());
  }

  private boolean isGroundedReference(
      ModelLevel level, ModelDelta.Reference reference, Map<String, String> elementTypes) {
    if (reference == null
        || reference.sourceId() == null
        || reference.sourceId().isBlank()
        || reference.targetId() == null
        || reference.targetId().isBlank()
        || reference.referenceName() == null
        || reference.referenceName().isBlank()) {
      return false;
    }
    String sourceType = elementTypes.get(reference.sourceId());
    String targetType = elementTypes.get(reference.targetId());
    if (sourceType == null || targetType == null) {
      return false;
    }
    String referenceName =
        DeltaReferenceNames.canonicalReferenceName(
            schemas, level, sourceType, reference.referenceName(), targetType);
    return schemas.acceptsReferenceTarget(level, sourceType, referenceName, targetType);
  }

  private ModelDelta.AttributeUpdate normalizeUpdate(
      ModelLevel level,
      ModelDelta.AttributeUpdate update,
      Map<String, String> localIds,
      Map<String, String> elementTypes) {
    String elementId = localIds.getOrDefault(update.elementId(), update.elementId());
    String attributeName = update.attributeName();
    String elementType = elementTypes.get(elementId);
    if (elementType != null && attributeName != null && !attributeName.isBlank()) {
      attributeName =
          schemas
              .canonicalAttribute(level, elementType, attributeName)
              .map(AssistantMetamodelSchemaService.AttributeSchema::name)
              .orElse(attributeName);
    }
    final JsonNode rawValue = update.value();
    JsonNode value = rawValue;
    if (elementType != null
        && attributeName != null
        && !attributeName.isBlank()
        && rawValue != null) {
      java.util.Optional<JsonNode> canonicalValue =
          attributeContract(level, elementType, attributeName)
              .filter(attribute -> !attribute.enumLiterals().isEmpty())
              .flatMap(
                  attribute ->
                      attribute.enumLiterals().stream()
                          .filter(option -> option.equalsIgnoreCase(rawValue.asText("")))
                          .findFirst()
                          .map(JsonNodeFactory.instance::textNode));
      if (canonicalValue.isPresent()) {
        value = canonicalValue.get();
      }
    }
    return new ModelDelta.AttributeUpdate(elementId, attributeName, value);
  }

  private String canonical(ModelLevel level, String type) {
    try {
      return metamodels.canonicalType(level, type);
    } catch (PlatformException failure) {
      throw failure;
    }
  }

  private String sanitizeLocalId(String id) {
    if (id == null || id.isBlank()) {
      return id == null ? "" : id;
    }
    return id.replaceAll("[^A-Za-z0-9_-]", "_");
  }

  private void registerLocalIdAliases(
      Map<String, String> localIds, String originalId, String canonicalId) {
    for (String alias : idAliases(originalId)) {
      localIds.putIfAbsent(alias, canonicalId);
    }
    for (String alias : idAliases(canonicalId)) {
      localIds.putIfAbsent(alias, canonicalId);
    }
  }

  private Set<String> idAliases(String id) {
    Set<String> aliases = new LinkedHashSet<>();
    if (id == null || id.isBlank()) {
      return aliases;
    }
    aliases.add(id);
    aliases.add(sanitizeLocalId(id));
    aliases.add(id.replace('.', '-'));
    aliases.add(id.replace('.', '_'));
    aliases.add(id.replace('-', '_'));
    aliases.add(id.replace('_', '-'));
    return aliases;
  }

  private boolean isRootModelType(ModelLevel level, String type) {
    if (type == null || type.isBlank()) {
      return false;
    }
    String trimmed = type.trim();
    if (metamodels.rootType(level).equalsIgnoreCase(trimmed)) {
      return true;
    }
    return switch (level) {
      case PSM -> trimmed.equalsIgnoreCase("PSMModel");
      case PIM -> trimmed.equalsIgnoreCase("PIMModel");
      case CIM -> trimmed.equalsIgnoreCase("CIMModel");
      default -> false;
    };
  }
}
