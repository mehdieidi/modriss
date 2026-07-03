package io.mehdieidi.modless.platform.assistant.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * Backend-owned operation IR used after ModelDelta compilation.
 *
 * @param operations typed semantic operations against stable model element IDs
 */
public record SemanticModelPatch(List<Operation> operations) {

  /** Applies immutable collection semantics. */
  public SemanticModelPatch {
    operations = operations == null ? List.of() : List.copyOf(operations);
  }

  /** Whitelisted assistant mutation operations. */
  public enum OperationType {
    /** Add a model element. */
    ADD_ELEMENT,
    /** Connect two existing model elements. */
    CONNECT_ELEMENTS,
    /** Set a typed attribute on an existing model element. */
    SET_ATTRIBUTE,
    /** Delete an existing model element. */
    DELETE_ELEMENT
  }

  /**
   * One semantic model operation.
   *
   * @param type operation type
   * @param targetElementId stable target element ID
   * @param elementType metamodel element type
   * @param attributes operation attributes
   * @param sourceElementId stable source element ID
   * @param referenceName metamodel reference name
   */
  public record Operation(
      OperationType type,
      String targetElementId,
      String elementType,
      JsonNode attributes,
      String sourceElementId,
      String referenceName) {}
}
