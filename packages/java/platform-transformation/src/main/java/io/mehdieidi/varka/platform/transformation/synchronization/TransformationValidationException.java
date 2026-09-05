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
            + " then run generation again."
            + summarize(issues));
    this.issues = issues == null ? List.of() : List.copyOf(issues);
  }

  private static String summarize(List<ModelService.ValidationIssue> issues) {
    if (issues == null || issues.isEmpty()) {
      return "";
    }
    return " Issues: "
        + issues.stream()
            .limit(5)
            .map(
                issue ->
                    issue.severity()
                        + " "
                        + issue.constraint()
                        + (issue.elementId() == null ? "" : " [" + issue.elementId() + "]")
                        + ": "
                        + issue.message())
            .collect(java.util.stream.Collectors.joining("; "))
        + (issues.size() > 5 ? "; and " + (issues.size() - 5) + " more" : "");
  }

  public List<ModelService.ValidationIssue> issues() {
    return issues;
  }
}
