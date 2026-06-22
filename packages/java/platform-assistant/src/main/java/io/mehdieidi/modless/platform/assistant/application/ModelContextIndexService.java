package io.mehdieidi.modless.platform.assistant.application;

import io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes.AssistantModelContext;
import io.mehdieidi.modless.platform.assistant.spi.AssistantModelContextIndex;
import io.mehdieidi.modless.platform.model.application.ModelService;
import io.mehdieidi.modless.platform.model.domain.ModelRecord;

/** Plan-facing facade for compact model-context indexing. */
public class ModelContextIndexService {

  private final AssistantModelContextIndex delegate;

  public ModelContextIndexService(AssistantModelContextIndex delegate) {
    this.delegate = delegate;
  }

  public AssistantModelContext snapshot(
      ModelRecord model, ModelService.ValidationResult validation) {
    return delegate.snapshot(model, validation);
  }

  public String summarize(AssistantModelContext context) {
    return delegate.summarize(context);
  }
}
