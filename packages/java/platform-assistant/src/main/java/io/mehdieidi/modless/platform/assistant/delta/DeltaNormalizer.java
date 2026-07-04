package io.mehdieidi.modless.platform.assistant.delta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService.AttributeContract;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
    return new ModelDelta(
        delta.intent(),
        delta.kind(),
        delta.message(),
        delta.questions(),
        elements,
        delta.references().stream()
            .map(reference -> normalizeReference(reference, localIds))
            .toList(),
        delta.attributeUpdates().stream().map(update -> normalizeUpdate(update, localIds)).toList(),
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
            .map(reference -> normalizeReference(reference, localIds))
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

  private JsonNode canonicalizeAttributes(ModelLevel level, String type, JsonNode attributes) {
    if (!(attributes instanceof ObjectNode object)) {
      return attributes;
    }
    ObjectNode result = object.deepCopy();
    object
        .fields()
        .forEachRemaining(
            entry ->
                attributeContract(level, type, entry.getKey())
                    .filter(attribute -> !attribute.enumLiterals().isEmpty())
                    .flatMap(
                        attribute ->
                            attribute.enumLiterals().stream()
                                .filter(
                                    option -> option.equalsIgnoreCase(entry.getValue().asText("")))
                                .findFirst())
                    .ifPresent(option -> result.put(entry.getKey(), option)));
    result.remove("id");
    result.remove("eClass");
    return result;
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

  private ModelDelta.Reference normalizeReference(
      ModelDelta.Reference reference, Map<String, String> localIds) {
    return new ModelDelta.Reference(
        localIds.getOrDefault(reference.sourceId(), reference.sourceId()),
        reference.referenceName(),
        localIds.getOrDefault(reference.targetId(), reference.targetId()));
  }

  private ModelDelta.AttributeUpdate normalizeUpdate(
      ModelDelta.AttributeUpdate update, Map<String, String> localIds) {
    return new ModelDelta.AttributeUpdate(
        localIds.getOrDefault(update.elementId(), update.elementId()),
        update.attributeName(),
        update.value());
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
