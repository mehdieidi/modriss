package io.mehdieidi.modless.platform.assistant.delta;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.assistant.domain.AssistantChoice;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import java.util.List;

/** Provider-facing mutation protocol for autonomous modeling turns. */
public record ModelDelta(
    AssistantTurnPlan.Intent intent,
    Kind kind,
    String message,
    List<AssistantChoice> questions,
    List<Element> elements,
    List<Reference> references,
    List<AttributeUpdate> attributeUpdates,
    List<Deletion> deletions,
    List<String> assumptions) {

  public ModelDelta {
    intent = intent == null ? AssistantTurnPlan.Intent.INFORMATION : intent;
    kind = kind == null ? Kind.ANSWER : kind;
    message = message == null ? "" : message.trim();
    questions = questions == null ? List.of() : List.copyOf(questions);
    elements = elements == null ? List.of() : List.copyOf(elements);
    references = references == null ? List.of() : List.copyOf(references);
    attributeUpdates = attributeUpdates == null ? List.of() : List.copyOf(attributeUpdates);
    deletions = deletions == null ? List.of() : List.copyOf(deletions);
    assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
  }

  /** High-level response kind. */
  public enum Kind {
    ANSWER,
    CLARIFICATION,
    MODEL_DELTA
  }

  /** One new model element owned by a legal containment. */
  public record Element(
      String localId,
      String eClass,
      JsonNode attributes,
      Placement placement,
      List<Reference> references,
      List<String> evidenceIds) {

    public Element {
      localId = localId == null ? "" : localId.trim();
      eClass = eClass == null ? "" : eClass.trim();
      references = references == null ? List.of() : List.copyOf(references);
      evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
  }

  /** Explicit containment placement for a new element. */
  public record Placement(String ownerId, String referenceName) {
    public Placement {
      ownerId = ownerId == null ? "" : ownerId.trim();
      referenceName = referenceName == null ? "" : referenceName.trim();
    }
  }

  /** One writable non-containment EReference. */
  public record Reference(String sourceId, String referenceName, String targetId) {
    public Reference {
      sourceId = sourceId == null ? "" : sourceId.trim();
      referenceName = referenceName == null ? "" : referenceName.trim();
      targetId = targetId == null ? "" : targetId.trim();
    }
  }

  /** One scalar/enum attribute update. */
  public record AttributeUpdate(String elementId, String attributeName, JsonNode value) {
    public AttributeUpdate {
      elementId = elementId == null ? "" : elementId.trim();
      attributeName = attributeName == null ? "" : attributeName.trim();
    }
  }

  /** One explicit deletion of an existing model element. */
  public record Deletion(String elementId, String reason) {
    public Deletion {
      elementId = elementId == null ? "" : elementId.trim();
      reason = reason == null ? "" : reason.trim();
    }
  }
}
