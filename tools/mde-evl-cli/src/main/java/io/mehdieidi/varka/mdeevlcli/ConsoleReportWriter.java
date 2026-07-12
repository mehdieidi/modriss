package io.mehdieidi.varka.mdeevlcli;

import io.mehdieidi.varka.mde.validation.EvlConstraintKind;
import io.mehdieidi.varka.mde.validation.EvlConstraintViolation;
import io.mehdieidi.varka.mde.validation.EvlDiagnostic;
import io.mehdieidi.varka.mde.validation.EvlValidationReport;
import java.io.PrintWriter;

/** Renders EVL validation reports for interactive console use. */
final class ConsoleReportWriter {

  /**
   * Writes a completed validation summary and any constraint violations.
   *
   * @param out destination writer
   * @param report completed validation report
   * @param verbose whether to include timing, captured output, and detailed references
   */
  void writeSuccess(PrintWriter out, EvlValidationReport report, boolean verbose) {
    long mandatory = count(report, EvlConstraintKind.MANDATORY);
    long optional = count(report, EvlConstraintKind.OPTIONAL);
    out.printf("EVL validation succeeded in %d ms.%n", report.duration().toMillis());
    out.printf("EVL root: %s%n", report.evlRoot());
    out.printf("Violations: %d mandatory, %d optional.%n", mandatory, optional);
    writeViolations(out, report, verbose);
    if (verbose) {
      writeTimings(out, report);
      writeCaptured(out, report);
    }
  }

  /**
   * Writes a failed validation summary, diagnostics, and collected violations.
   *
   * @param err destination writer
   * @param report failed validation report
   * @param verbose whether to include timing, captured output, and detailed references
   */
  void writeFailure(PrintWriter err, EvlValidationReport report, boolean verbose) {
    err.printf("EVL validation failed in %d ms.%n", report.duration().toMillis());
    err.printf("EVL root: %s%n", report.evlRoot());
    for (EvlDiagnostic diagnostic : report.diagnostics()) {
      err.printf("[%s/%s] %s%n", diagnostic.severity(), diagnostic.phase(), diagnostic.reason());
      if (diagnostic.file() != null) {
        err.printf("  where: %s", diagnostic.file());
        if (diagnostic.line() > 0) {
          err.printf(":%d", diagnostic.line());
          if (diagnostic.column() > 0) {
            err.printf(":%d", diagnostic.column());
          }
        }
        err.println();
      }
      if (!diagnostic.whatWentWrong().isBlank()) {
        err.printf("  what went wrong: %s%n", diagnostic.whatWentWrong());
      }
      if (!diagnostic.howToFix().isBlank()) {
        err.printf("  how to fix: %s%n", diagnostic.howToFix());
      }
      if (verbose && !diagnostic.exceptionType().isBlank()) {
        err.printf("  exception: %s%n", diagnostic.exceptionType());
      }
    }
    writeViolations(err, report, verbose);
    if (verbose) {
      writeTimings(err, report);
      writeCaptured(err, report);
    }
  }

  /**
   * Counts violations of a given constraint kind.
   *
   * @param report validation report
   * @param kind constraint kind
   * @return matching violation count
   */
  private long count(EvlValidationReport report, EvlConstraintKind kind) {
    return report.violations().stream().filter(v -> v.kind() == kind).count();
  }

  /**
   * Writes constraint violations in a human-readable format.
   *
   * @param writer destination writer
   * @param report validation report
   * @param verbose whether to include element URI fragments
   */
  private void writeViolations(PrintWriter writer, EvlValidationReport report, boolean verbose) {
    for (EvlConstraintViolation violation : report.violations()) {
      writer.printf(
          "[%s] %s (%s)%n", violation.kind(), violation.constraintName(), violation.contextType());
      writer.printf("  message: %s%n", violation.message());
      if (violation.file() != null) {
        writer.printf("  rule: %s", violation.file());
        if (violation.line() > 0) {
          writer.printf(":%d", violation.line());
          if (violation.column() > 0) {
            writer.printf(":%d", violation.column());
          }
        }
        writer.println();
      }
      if (!violation.element().summary().isBlank()) {
        writer.printf("  element: %s%n", violation.element().summary());
      }
      if (verbose && !violation.element().uriFragment().isBlank()) {
        writer.printf("  element URI fragment: %s%n", violation.element().uriFragment());
      }
    }
  }

  /**
   * Writes non-empty captured EVL output streams.
   *
   * @param writer destination writer
   * @param report validation report
   */
  private void writeCaptured(PrintWriter writer, EvlValidationReport report) {
    if (!report.stdout().isBlank()) {
      writer.println("---- EVL stdout ----");
      writer.println(report.stdout());
    }
    if (!report.warnings().isBlank()) {
      writer.println("---- EVL warnings ----");
      writer.println(report.warnings());
    }
    if (!report.stderr().isBlank()) {
      writer.println("---- EVL stderr ----");
      writer.println(report.stderr());
    }
  }

  /**
   * Writes module discovery and per-module validation timings.
   *
   * @param writer destination writer
   * @param report validation report
   */
  private void writeTimings(PrintWriter writer, EvlValidationReport report) {
    writer.printf("Module discovery: %d ms.%n", report.moduleDiscoveryDuration().toMillis());
    for (var module : report.moduleReports()) {
      writer.printf(
          "Module %s: total=%d ms, parse=%d ms, load=%d ms, structural=%d ms, evl=%d ms, map=%d ms,"
              + " dispose=%d ms.%n",
          module.moduleFile(),
          module.duration().toMillis(),
          module.parseDuration().toMillis(),
          module.modelLoadDuration().toMillis(),
          module.structuralValidationDuration().toMillis(),
          module.evlExecuteDuration().toMillis(),
          module.violationMappingDuration().toMillis(),
          module.disposeDuration().toMillis());
    }
  }
}
