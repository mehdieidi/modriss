package io.mehdieidi.modless.platform.assistant.support;

import io.mehdieidi.modless.platform.assistant.application.AssistantEvalRunner;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Formats assistant eval quality-gate results for internal documentation. */
public final class AssistantEvalGateReportWriter {

  private AssistantEvalGateReportWriter() {}

  public static String markdown(
      boolean live,
      List<AssistantEvalRunner.EvalResult> results,
      AssistantEvalRunner.QualityGateReport gates,
      AssistantEvalRunner.LatencyReport latency,
      String baselineReport) {
    StringBuilder report = new StringBuilder();
    report.append("# Assistant live eval quality gate report\n\n");
    report
        .append("Generated: ")
        .append(DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC).format(Instant.now()))
        .append("\n\n");
    report.append("Mode: ").append(live ? "live provider" : "stub").append("\n\n");
    report.append("## Summary\n\n");
    report.append("- ").append(gates.summary()).append('\n');
    report.append("- ").append(latency.summary()).append('\n');
    report.append("- Latency JSON: `").append(latency.toJson()).append("`\n");
    report.append("- Overall: **").append(gates.passedGates() ? "PASS" : "FAIL").append("**\n\n");

    report.append("## Quality gates\n\n");
    report.append("| Gate | Threshold | Actual | Status |\n");
    report.append("|------|-----------|--------|--------|\n");
    appendGateRow(
        report,
        "Structural pass rate",
        ">= 95%",
        percent(gates.structuralPassRate()),
        gates.structuralPassRate() >= 0.95);
    appendGateRow(
        report,
        "ModelDelta success rate",
        "100%",
        percent(gates.modelDeltaSuccessRate()),
        gates.modelDeltaSuccessRate() >= 1.0);
    appendGateRow(
        report,
        "Retrieval contract recall",
        ">= 90%",
        percent(gates.retrievalRecallRate()),
        gates.retrievalRecallRate() >= 0.90);
    appendGateRow(
        report,
        "Source coverage rate",
        "100% when chunks present",
        percent(gates.sourceCoverageRate()),
        gates.sourceCoverageRate() >= 1.0);
    appendGateRow(
        report,
        "Average repair attempts",
        "< 0.5",
        String.format(Locale.ROOT, "%.2f", gates.averageRepairAttempts()),
        gates.averageRepairAttempts() <= 0.5);
    appendGateRow(
        report,
        "Average provider calls",
        "<= 3.0",
        String.format(Locale.ROOT, "%.2f", gates.averageProviderCalls()),
        gates.averageProviderCalls() <= 3.0);
    appendGateRow(
        report,
        "p95 latency",
        "<= 300000ms",
        gates.p95LatencyMs() + "ms",
        gates.p95LatencyMs() <= 300_000L);

    if (!gates.violations().isEmpty()) {
      report.append("\n### Violations\n\n");
      for (String violation : gates.violations()) {
        report.append("- ").append(violation).append('\n');
      }
    }

    report.append("\n## Per-category baseline\n\n```\n");
    report.append(baselineReport == null ? "" : baselineReport.trim());
    report.append("\n```\n\n");

    report.append("## Failing prompts\n\n");
    List<AssistantEvalRunner.EvalResult> failures =
        results.stream().filter(result -> !result.validationPassed()).toList();
    if (failures.isEmpty()) {
      report.append("None.\n");
    } else {
      for (AssistantEvalRunner.EvalResult failure : failures) {
        report
            .append("- `")
            .append(failure.id())
            .append("` (")
            .append(failure.category())
            .append(") at ")
            .append(failure.failureStage().isBlank() ? "UNKNOWN" : failure.failureStage())
            .append(": ")
            .append(failure.failureMessage().isBlank() ? "no message" : failure.failureMessage())
            .append('\n');
      }
    }
    return report.toString().trim() + "\n";
  }

  private static void appendGateRow(
      StringBuilder report, String gate, String threshold, String actual, boolean passed) {
    report
        .append("| ")
        .append(gate)
        .append(" | ")
        .append(threshold)
        .append(" | ")
        .append(actual)
        .append(" | ")
        .append(passed ? "PASS" : "FAIL")
        .append(" |\n");
  }

  private static String percent(double value) {
    return String.format(Locale.ROOT, "%.1f%%", value * 100.0);
  }
}
