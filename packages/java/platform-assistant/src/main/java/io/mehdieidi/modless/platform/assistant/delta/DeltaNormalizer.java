package io.mehdieidi.modless.platform.assistant.delta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Metamodel-driven normalization for provider-returned ModelDelta. */
public class DeltaNormalizer {

  private final AssistantMetamodelSchemaService schemas;

  public DeltaNormalizer(AssistantMetamodelSchemaService schemas) {
    this.schemas = schemas;
  }

  /** Normalizes only formal details: type casing, local IDs, enum casing, and root placement. */
  public ModelDelta normalize(ModelLevel level, ModelDelta delta) {
    if (delta == null) {
      return null;
    }
    Map<String, String> localIds = new LinkedHashMap<>();
    List<ModelDelta.Element> elements =
        delta.elements().stream()
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
        originalId.isBlank()
            ? "new_" + (localIds.size() + 1)
            : originalId.replaceAll("[^A-Za-z0-9_-]", "_");
    localIds.putIfAbsent(originalId, localId);
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
    return schemas
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
                schemas
                    .attribute(level, type, entry.getKey())
                    .filter(attribute -> !attribute.options().isEmpty())
                    .flatMap(
                        attribute ->
                            attribute.options().stream()
                                .filter(
                                    option -> option.equalsIgnoreCase(entry.getValue().asText("")))
                                .findFirst())
                    .ifPresent(option -> result.put(entry.getKey(), option)));
    result.remove("id");
    result.remove("eClass");
    return result;
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
      return schemas.canonicalType(level, type);
    } catch (PlatformException failure) {
      throw failure;
    }
  }
}
