package io.mehdieidi.modless.platform.assistant.delta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Truncates oversized provider ModelDelta payloads to a per-pass element budget. */
public final class ModelDeltaPayloadLimiter {

  private ModelDeltaPayloadLimiter() {}

  /**
   * Keeps at most {@code maxElements} new elements and drops dependent mutations that reference
   * pruned ids.
   */
  public static ModelDelta limit(ModelDelta delta, int maxElements) {
    if (delta == null || maxElements <= 0 || delta.elements().size() <= maxElements) {
      return delta;
    }
    List<ModelDelta.Element> keptElements = delta.elements().subList(0, maxElements);
    Set<String> keptIds = new LinkedHashSet<>();
    for (ModelDelta.Element element : keptElements) {
      if (element != null && !element.localId().isBlank()) {
        keptIds.add(element.localId());
      }
    }
    List<ModelDelta.Reference> references = new ArrayList<>();
    for (ModelDelta.Reference reference : delta.references()) {
      if (reference == null) {
        continue;
      }
      if (keptIds.contains(reference.sourceId()) && keptIds.contains(reference.targetId())) {
        references.add(reference);
      }
    }
    List<ModelDelta.AttributeUpdate> attributeUpdates = new ArrayList<>();
    for (ModelDelta.AttributeUpdate update : delta.attributeUpdates()) {
      if (update != null && keptIds.contains(update.elementId())) {
        attributeUpdates.add(update);
      }
    }
    List<ModelDelta.Deletion> deletions = new ArrayList<>();
    for (ModelDelta.Deletion deletion : delta.deletions()) {
      if (deletion != null && keptIds.contains(deletion.elementId())) {
        deletions.add(deletion);
      }
    }
    List<ModelDelta.Element> normalizedElements = new ArrayList<>();
    for (ModelDelta.Element element : keptElements) {
      if (element == null) {
        continue;
      }
      List<ModelDelta.Reference> elementReferences = new ArrayList<>();
      for (ModelDelta.Reference reference : element.references()) {
        if (reference != null
            && keptIds.contains(reference.sourceId())
            && keptIds.contains(reference.targetId())) {
          elementReferences.add(reference);
        }
      }
      normalizedElements.add(
          new ModelDelta.Element(
              element.localId(),
              element.eClass(),
              element.attributes(),
              element.placement(),
              elementReferences,
              element.evidenceIds()));
    }
    return new ModelDelta(
        delta.intent(),
        delta.kind(),
        delta.message(),
        delta.questions(),
        List.copyOf(normalizedElements),
        List.copyOf(references),
        List.copyOf(attributeUpdates),
        List.copyOf(deletions),
        delta.assumptions());
  }
}
