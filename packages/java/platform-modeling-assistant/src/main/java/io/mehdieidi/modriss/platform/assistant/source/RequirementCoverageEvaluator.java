package io.mehdieidi.modriss.platform.assistant.source;

import java.util.LinkedHashSet;
import java.util.Set;

/** Deterministic recall/precision evaluator for labelled requirement-to-element benchmark cases. */
public final class RequirementCoverageEvaluator {
  private RequirementCoverageEvaluator() {}

  /** Compares the expected requirement labels with labels evidenced by committed model elements. */
  public static Score score(
      Set<String> expectedRequirementIds, Set<String> representedRequirementIds) {
    Set<String> expected = clean(expectedRequirementIds);
    Set<String> represented = clean(representedRequirementIds);
    Set<String> matched = new LinkedHashSet<>(represented);
    matched.retainAll(expected);
    int falsePositives = represented.size() - matched.size();
    int falseNegatives = expected.size() - matched.size();
    return new Score(
        expected.size(),
        represented.size(),
        matched.size(),
        falsePositives,
        falseNegatives,
        expected.isEmpty() ? 1d : (double) matched.size() / expected.size(),
        represented.isEmpty()
            ? (expected.isEmpty() ? 1d : 0d)
            : (double) matched.size() / represented.size());
  }

  private static Set<String> clean(Set<String> values) {
    Set<String> result = new LinkedHashSet<>();
    if (values != null)
      for (String value : values) if (value != null && !value.isBlank()) result.add(value.trim());
    return result;
  }

  /** Labelled benchmark outcome. */
  public record Score(
      int expected,
      int represented,
      int truePositives,
      int falsePositives,
      int falseNegatives,
      double recall,
      double precision) {}
}
