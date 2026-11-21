package io.mehdieidi.modriss.platform.assistant.agent;

import io.mehdieidi.modriss.platform.assistant.metamodel.MetamodelKnowledgeService.ReferenceContract;
import io.mehdieidi.modriss.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import io.mehdieidi.modriss.platform.assistant.metamodel.TypeContractService;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/** A prompt-sized view of the saved model, focused by the LLM's EClass and instance choices. */
final class PersistedModelContext {
  private final ModelLevel level;
  private final TypeContractService contracts;
  private final int maxChars;
  private final JsonNode root;
  private final Map<String, Element> elements = new LinkedHashMap<>();
  private final Map<String, TypeContract> byType = new LinkedHashMap<>();

  PersistedModelContext(
      ModelLevel level, TypeContractService contracts, JsonNode root, int maxPromptTokens) {
    this.level = level;
    this.contracts = contracts;
    this.root = root;
    // Leave the bulk of the wire budget for source, contracts, schemas, and repair evidence.
    // The provider also escapes this JSON when serializing the final request.
    this.maxChars = Math.min(12_000, maxPromptTokens / 2);
    contracts.all(level).forEach(type -> byType.put(type.eClass(), type));
    collect(root, null, null);
  }

  JsonNode view(Set<String> focusTypes, Set<String> pinnedIds) {
    var result = JsonNodeFactory.instance.objectNode();
    String savedRootId = root.path("id").asText();
    result.put("rootId", "rootId");
    result.put("rootType", root.path("eClass").asText(""));
    int total = elements.size() - (elements.containsKey(savedRootId) ? 1 : 0);
    result.put("totalElements", total);
    var visible = result.putArray("elements");
    Set<String> candidates = compatibleTypes(focusTypes);
    Set<String> pinned = new LinkedHashSet<>(pinnedIds);
    for (String id : pinnedIds) {
      Element element = elements.get(id);
      if (element != null && element.ownerId() != null) pinned.add(element.ownerId());
    }
    pinned.remove(savedRootId);

    Map<String, ArrayDeque<Element>> focused = new LinkedHashMap<>();
    Map<String, ArrayDeque<Element>> other = new LinkedHashMap<>();
    for (Element element : elements.values()) {
      if (pinned.contains(element.id()) || savedRootId.equals(element.id())) continue;
      (candidates.contains(element.type()) ? focused : other)
          .computeIfAbsent(element.type(), ignored -> new ArrayDeque<>())
          .add(element);
    }
    int remaining = maxChars - result.toString().length() - 64;
    // Reserve space for identity and ownership even with many pinned objects.
    for (String id : pinned) {
      Element element = elements.get(id);
      if (element != null) remaining = append(visible, record(element, true), remaining);
    }
    // Keep a cross-section of the architecture even when one relevant EClass dominates the model.
    int architectureBudget = Math.min(remaining, maxChars / 5);
    remaining -= architectureBudget - appendGroups(other, visible, architectureBudget);
    remaining = appendGroups(focused, visible, remaining);
    appendGroups(other, visible, remaining);
    result.put("omittedElements", total - visible.size());
    return result;
  }

  private int appendGroups(
      Map<String, ArrayDeque<Element>> groups, ArrayNode visible, int remaining) {
    var queues = new ArrayDeque<>(groups.values());
    while (!queues.isEmpty() && remaining > 0) {
      ArrayDeque<Element> queue = queues.remove();
      if (queue.isEmpty()) continue;
      ObjectNode record = record(queue.peek(), false);
      int next = append(visible, record, remaining);
      if (next == remaining) continue;
      remaining = next;
      queue.remove();
      if (!queue.isEmpty()) queues.add(queue);
    }
    return remaining;
  }

  private int append(ArrayNode visible, ObjectNode record, int remaining) {
    int size = record.toString().length() + 1;
    if (size > remaining) {
      record.remove(List.of("attributes", "relationships"));
      size = record.toString().length() + 1;
    }
    if (size > remaining) return remaining;
    visible.add(record);
    return remaining - size;
  }

  private Set<String> compatibleTypes(Set<String> focusTypes) {
    var referenceTargets = new LinkedHashSet<String>();
    for (String type : focusTypes) {
      TypeContract contract = byType.get(type);
      if (contract == null) continue;
      contract.references().stream()
          .filter(reference -> !reference.containment() && !reference.readonly())
          .map(ReferenceContract::targetType)
          .forEach(referenceTargets::add);
    }
    Set<String> result = new LinkedHashSet<>();
    for (TypeContract type : byType.values()) {
      if (focusTypes.stream().anyMatch(focus -> contracts.assignable(level, type.eClass(), focus))
          || referenceTargets.stream()
              .anyMatch(target -> contracts.assignable(level, type.eClass(), target))
          || type.references().stream()
              .filter(reference -> reference.containment() && !reference.readonly())
              .anyMatch(
                  reference ->
                      focusTypes.stream()
                          .anyMatch(
                              focus ->
                                  contracts.assignable(level, focus, reference.targetType())))) {
        result.add(type.eClass());
      }
    }
    return result;
  }

  private ObjectNode record(Element element, boolean pinned) {
    var record = JsonNodeFactory.instance.objectNode();
    record.put("id", element.id());
    record.put("type", element.type());
    if (element.ownerId() != null) {
      String owner =
          element.ownerId().equals(root.path("id").asText()) ? "rootId" : element.ownerId();
      record.put("ownerId", owner);
    }
    if (element.ownerFeature() != null) record.put("ownerFeature", element.ownerFeature());
    for (String feature : List.of("name", "label", "term", "stateName")) {
      String value = element.node().path(feature).asText("");
      if (!value.isBlank()) record.put(feature, preview(value));
    }
    TypeContract type = byType.get(element.type());
    if (type == null) return record;
    if (pinned) {
      var attributes = record.putObject("attributes");
      for (var attribute : type.attributes()) {
        JsonNode value = element.node().get(attribute.name());
        if (value != null
            && value.isValueNode()
            && !value.isNull()
            && !"id".equals(attribute.name())
            && value.toString().length() <= 240) {
          attributes.set(attribute.name(), value);
        }
      }
      if (attributes.isEmpty()) record.remove("attributes");
    }
    var relationships = record.putArray("relationships");
    for (ReferenceContract reference : type.references()) {
      if (reference.containment() || reference.readonly()) continue;
      JsonNode value = element.node().path(reference.name());
      if (value.isArray()) {
        for (JsonNode target : value) {
          addTarget(relationships, reference.name(), target);
          if (relationships.size() >= 8) break;
        }
      } else {
        addTarget(relationships, reference.name(), value);
      }
      if (relationships.size() >= 8) break;
    }
    if (relationships.isEmpty()) record.remove("relationships");
    return record;
  }

  private void addTarget(ArrayNode relationships, String feature, JsonNode value) {
    String id =
        value.isObject()
            ? value.path("$ref").asText(value.path("id").asText(""))
            : value.asText("");
    Element target = elements.get(id);
    if (target != null && target.ownerId() != null) {
      var link = relationships.addObject();
      link.put("feature", feature);
      link.put("targetId", id);
      link.put("targetType", target.type());
    }
  }

  private void collect(JsonNode node, String ownerId, String feature) {
    if (node == null || !node.isObject()) return;
    String id = node.path("id").asText("");
    String type = node.path("eClass").asText("");
    if (id.isBlank() || type.isBlank()) return;
    if (elements.putIfAbsent(id, new Element(id, type, ownerId, feature, node)) != null) return;
    TypeContract contract = byType.get(type);
    if (contract == null) return;
    for (ReferenceContract reference : contract.references()) {
      if (!reference.containment()) continue;
      JsonNode child = node.path(reference.name());
      if (child.isArray()) child.forEach(item -> collect(item, id, reference.name()));
      else collect(child, id, reference.name());
    }
  }

  private String preview(String value) {
    int length = value.codePointCount(0, value.length());
    return length <= 120 ? value : value.substring(0, value.offsetByCodePoints(0, 119)) + "…";
  }

  private record Element(
      String id, String type, String ownerId, String ownerFeature, JsonNode node) {}
}
