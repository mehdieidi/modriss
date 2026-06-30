package io.mehdieidi.modless.platform.assistant.subset;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.assistant.domain.AssistantChoice;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import java.util.List;

/** Parsed LLM response for the JSON model-subset protocol. */
public record ModelSubsetTurnPlan(
    AssistantTurnPlan.Intent intent,
    Kind kind,
    String message,
    List<AssistantChoice> questions,
    ModelSubset subset) {

  public ModelSubsetTurnPlan {
    intent = intent == null ? AssistantTurnPlan.Intent.INFORMATION : intent;
    kind = kind == null ? Kind.ANSWER : kind;
    message = message == null ? "" : message.trim();
    questions = questions == null ? List.of() : List.copyOf(questions);
    subset = subset == null ? ModelSubset.empty() : subset;
  }

  /** Supported response shapes for the subset planner. */
  public enum Kind {
    ANSWER,
    CLARIFICATION,
    MODEL_SUBSET
  }

  /** A mergeable partial model graph produced by the LLM. */
  public record ModelSubset(
      String subsetId,
      String scope,
      List<Element> elements,
      List<Reference> references,
      List<AttributeUpdate> attributeUpdates,
      List<Deletion> deletions) {

    public ModelSubset {
      subsetId = subsetId == null ? "" : subsetId.trim();
      scope = scope == null ? "" : scope.trim();
      elements = elements == null ? List.of() : List.copyOf(elements);
      references = references == null ? List.of() : List.copyOf(references);
      attributeUpdates = attributeUpdates == null ? List.of() : List.copyOf(attributeUpdates);
      deletions = deletions == null ? List.of() : List.copyOf(deletions);
    }

    static ModelSubset empty() {
      return new ModelSubset("", "", List.of(), List.of(), List.of(), List.of());
    }
  }

  /** One new or upsert-intended model element in the partial subset. */
  public record Element(
      String localId,
      String eClass,
      JsonNode attributes,
      Containment containedBy,
      List<Reference> references) {

    public Element {
      localId = localId == null ? "" : localId.trim();
      eClass = eClass == null ? "" : eClass.trim();
      references = references == null ? List.of() : List.copyOf(references);
    }
  }

  /** Containment placement for a new element. */
  public record Containment(String ownerId, String referenceName) {
    public Containment {
      ownerId = ownerId == null ? "" : ownerId.trim();
      referenceName = referenceName == null ? "" : referenceName.trim();
    }
  }

  /** One writable non-containment EReference between subset or existing elements. */
  public record Reference(String sourceId, String referenceName, String targetId) {
    public Reference {
      sourceId = sourceId == null ? "" : sourceId.trim();
      referenceName = referenceName == null ? "" : referenceName.trim();
      targetId = targetId == null ? "" : targetId.trim();
    }
  }

  /** One attribute update for an existing or subset-local element. */
  public record AttributeUpdate(String elementId, String attributeName, JsonNode value) {
    public AttributeUpdate {
      elementId = elementId == null ? "" : elementId.trim();
      attributeName = attributeName == null ? "" : attributeName.trim();
    }
  }

  /** One explicit deletion of an existing element. */
  public record Deletion(String elementId) {
    public Deletion {
      elementId = elementId == null ? "" : elementId.trim();
    }
  }
}
