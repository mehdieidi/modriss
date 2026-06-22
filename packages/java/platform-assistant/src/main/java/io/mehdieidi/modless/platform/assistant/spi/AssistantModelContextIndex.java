package io.mehdieidi.modless.platform.assistant.spi;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes.AssistantModelContext;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.model.application.ModelService;
import io.mehdieidi.modless.platform.model.domain.ModelRecord;

/** Compact model context indexing for assistant prompts and tools. */
public interface AssistantModelContextIndex {

  /**
   * Builds a compact context snapshot for the current model.
   *
   * @param model stored model
   * @param validationResult validation preview
   * @return compact context
   */
  AssistantModelContext snapshot(ModelRecord model, ModelService.ValidationResult validationResult);

  /**
   * Builds a transient context snapshot without persistence.
   *
   * @param projectId project ID
   * @param level model level
   * @param modelName model display name
   * @param revision transient revision
   * @param modelJson model JSON
   * @param validationResult validation preview
   * @return compact context
   */
  AssistantModelContext transientSnapshot(
      String projectId,
      ModelLevel level,
      String modelName,
      long revision,
      JsonNode modelJson,
      ModelService.ValidationResult validationResult);

  /**
   * Summarizes a context for planner prompts.
   *
   * @param context compact context
   * @return summary text
   */
  String summarize(AssistantModelContext context);

  /**
   * Focuses context on selected element IDs.
   *
   * @param context compact context
   * @param selectedElementIds selected IDs
   * @param schemas metamodel schema service
   * @return focused context text
   */
  String focusContext(
      AssistantModelContext context,
      java.util.Collection<String> selectedElementIds,
      AssistantMetamodelSchemaService schemas);

  /**
   * Returns whether the context represents an empty modeling canvas.
   *
   * @param context compact context
   * @return true when empty
   */
  boolean isEmptyCanvas(AssistantModelContext context);
}
