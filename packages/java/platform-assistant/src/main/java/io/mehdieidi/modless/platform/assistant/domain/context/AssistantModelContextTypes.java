package io.mehdieidi.modless.platform.assistant.domain.context;

import io.mehdieidi.modless.platform.assistant.domain.AssistantValidationSummary;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;
import java.util.Map;

/** Compact assistant model context types. */
public final class AssistantModelContextTypes {

  private AssistantModelContextTypes() {}

  /**
   * Compact assistant model context.
   *
   * @param modelId model ID
   * @param projectId project ID
   * @param level model level
   * @param modelName model name
   * @param revision revision
   * @param elements stable elements
   * @param relationships stable relationships
   * @param neighborhoods adjacency map
   * @param validationIssues latest validation issues
   */
  public record AssistantModelContext(
      String modelId,
      String projectId,
      ModelLevel level,
      String modelName,
      long revision,
      List<ContextElement> elements,
      List<ContextRelationship> relationships,
      Map<String, List<String>> neighborhoods,
      List<AssistantValidationSummary.Issue> validationIssues) {

    public AssistantModelContext {
      elements = elements == null ? List.of() : List.copyOf(elements);
      relationships = relationships == null ? List.of() : List.copyOf(relationships);
      neighborhoods = neighborhoods == null ? Map.of() : Map.copyOf(neighborhoods);
      validationIssues = validationIssues == null ? List.of() : List.copyOf(validationIssues);
    }
  }

  /**
   * Compact model element index entry.
   *
   * @param id stable element ID
   * @param type element type
   * @param name element name
   * @param path JSON path
   */
  public record ContextElement(String id, String type, String name, String path) {}

  /**
   * Compact relationship index entry.
   *
   * @param id stable relationship ID
   * @param sourceId source ID
   * @param targetId target ID
   * @param kind relationship kind
   * @param path JSON path
   */
  public record ContextRelationship(
      String id, String sourceId, String targetId, String kind, String path) {}
}
