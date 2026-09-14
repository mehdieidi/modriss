package io.mehdieidi.modriss.mdecli.diagnostics;

import java.io.PrintWriter;
import java.util.List;

/** Renders conversion reports for interactive console use. */
public final class ConsoleReportWriter {

  /**
   * Writes a successful conversion summary.
   *
   * @param out destination writer
   * @param report successful conversion report
   * @param verbose whether to include execution events
   */
  public void writeSuccess(PrintWriter out, ConversionReport report, boolean verbose) {
    out.println(report.getSummary());
    if (report.getOutputPath() != null) {
      out.println("Output: " + report.getOutputPath());
    }
    writeDiagnostics(out, report.getDiagnostics());
    writeVerboseEvents(out, report.getEvents(), verbose);
    out.flush();
  }

  /**
   * Writes a failed conversion summary and resolution guidance.
   *
   * @param err destination writer
   * @param report failed conversion report
   * @param verbose whether to include execution events
   */
  public void writeFailure(PrintWriter err, ConversionReport report, boolean verbose) {
    err.println(report.getSummary());
    if (!report.getResolutionHint().isBlank()) {
      err.println("How to fix: " + report.getResolutionHint());
    }
    writeDiagnostics(err, report.getDiagnostics());
    Throwable cause = report.getCause();
    if (cause != null) {
      err.println("Cause: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
    }
    writeVerboseEvents(err, report.getEvents(), verbose);
    err.flush();
  }

  /**
   * Writes structured diagnostics in a compact human-readable format.
   *
   * @param out destination writer
   * @param diagnostics diagnostics to render
   */
  private void writeDiagnostics(PrintWriter out, List<DiagnosticEntry> diagnostics) {
    for (DiagnosticEntry diagnostic : diagnostics) {
      StringBuilder line = new StringBuilder();
      line.append(diagnostic.severity()).append(": ").append(diagnostic.message());
      if (!diagnostic.location().isBlank()) {
        line.append(" [").append(diagnostic.location());
        if (diagnostic.line() > 0) {
          line.append(':').append(diagnostic.line());
          if (diagnostic.column() > 0) {
            line.append(':').append(diagnostic.column());
          }
        }
        line.append(']');
      }
      out.println(line);
      if (!diagnostic.hint().isBlank()) {
        out.println("Hint: " + diagnostic.hint());
      }
      if (!diagnostic.sourceExcerpt().isBlank()) {
        out.println("Source: " + diagnostic.sourceExcerpt());
      }
    }
  }

  /**
   * Writes recorded execution events when verbose output is enabled.
   *
   * @param out destination writer
   * @param events execution events
   * @param verbose whether verbose output is enabled
   */
  private void writeVerboseEvents(PrintWriter out, List<String> events, boolean verbose) {
    if (!verbose || events.isEmpty()) {
      return;
    }
    out.println("Execution trace:");
    for (String event : events) {
      out.println(" - " + event);
    }
  }
}
