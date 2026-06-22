package io.mehdieidi.modless.platform.assistant.domain;

import java.util.List;

/**
 * Validation preview attached to an assistant proposal.
 *
 * @param structurallyValid whether structural validation passed
 * @param mandatoryPassed whether mandatory EVL constraints passed
 * @param optionalIssues optional critiques to surface to the user
 * @param issues compact validation issues
 */
public record AssistantValidationSummary(
    boolean structurallyValid, boolean mandatoryPassed, int optionalIssues, List<Issue> issues) {

  /** Applies immutable collection semantics. */
  public AssistantValidationSummary {
    issues = issues == null ? List.of() : List.copyOf(issues);
  }

  /**
   * Compact validation issue.
   *
   * @param severity issue severity
   * @param constraint constraint name
   * @param elementId affected element ID
   * @param message user-facing message
   */
  public record Issue(String severity, String constraint, String elementId, String message) {}
}
