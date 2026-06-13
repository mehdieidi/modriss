package io.mehdieidi.modless.backend.assistant;

import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.service.ModelService;
import org.springframework.stereotype.Service;

/** Plan-facing facade for compact model-context indexing. */
@Service
public class ModelContextIndexService {

  private final AssistantModelContextIndexService delegate;

  public ModelContextIndexService(AssistantModelContextIndexService delegate) {
    this.delegate = delegate;
  }

  /**
   * Builds or loads a compact model context snapshot.
   *
   * @param model model record
   * @param validation validation result
   * @return compact model context
   */
  public AssistantModelContextIndexService.AssistantModelContext snapshot(
      ModelRecord model, ModelService.ValidationResult validation) {
    return delegate.snapshot(model, validation);
  }

  /**
   * Summarizes a compact context for prompts.
   *
   * @param context compact context
   * @return bounded text summary
   */
  public String summarize(AssistantModelContextIndexService.AssistantModelContext context) {
    return delegate.summarize(context);
  }
}
