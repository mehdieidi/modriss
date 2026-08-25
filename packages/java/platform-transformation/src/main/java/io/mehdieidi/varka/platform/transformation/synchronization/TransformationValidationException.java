package io.mehdieidi.varka.platform.transformation.synchronization;

import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.application.ModelService;
import java.util.List;

/** A generated target failed model validation before it could be persisted. */
public final class TransformationValidationException extends PlatformException {

  private final List<ModelService.ValidationIssue> issues;

  public TransformationValidationException(List<ModelService.ValidationIssue> issues) {
    super(
        422,
        "Generated model validation failed. Fix the validation issues shown in the issue board,"
            + " then run generation again.");
    this.issues = issues == null ? List.of() : List.copyOf(issues);
  }

  public List<ModelService.ValidationIssue> issues() {
    return issues;
  }
}
