package io.mehdieidi.modless.mdecli.diagnostics;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReportFileWriter {

  public void write(Path target, ConversionReport report) {
    if (target == null) {
      return;
    }
    try {
      Path parent = target.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(target, render(report));
    } catch (IOException ex) {
      throw new UncheckedIOException("Failed to write report file: " + target, ex);
    }
  }

  private String render(ConversionReport report) {
    StringBuilder content = new StringBuilder();
    content.append("status=").append(report.getStatus()).append(System.lineSeparator());
    content.append("summary=").append(report.getSummary()).append(System.lineSeparator());
    if (!report.getResolutionHint().isBlank()) {
      content.append("resolution=").append(report.getResolutionHint()).append(System.lineSeparator());
    }
    if (report.getOutputPath() != null) {
      content.append("output=").append(report.getOutputPath()).append(System.lineSeparator());
    }
    content.append("durationMs=").append(report.duration().toMillis()).append(System.lineSeparator());
    content.append(System.lineSeparator()).append("[diagnostics]").append(System.lineSeparator());
    for (DiagnosticEntry diagnostic : report.getDiagnostics()) {
      content.append(diagnostic.severity())
          .append(" | ")
          .append(diagnostic.location())
          .append(" | ")
          .append(diagnostic.line())
          .append(" | ")
          .append(diagnostic.column())
          .append(" | ")
          .append(diagnostic.message())
          .append(System.lineSeparator());
      if (!diagnostic.hint().isBlank()) {
        content.append("hint=").append(diagnostic.hint()).append(System.lineSeparator());
      }
      if (!diagnostic.sourceExcerpt().isBlank()) {
        content.append("source=").append(diagnostic.sourceExcerpt()).append(System.lineSeparator());
      }
    }
    content.append(System.lineSeparator()).append("[events]").append(System.lineSeparator());
    for (String event : report.getEvents()) {
      content.append(event).append(System.lineSeparator());
    }
    if (report.getCause() != null) {
      content.append(System.lineSeparator()).append("[cause]").append(System.lineSeparator());
      content.append(report.getCause().toString()).append(System.lineSeparator());
      for (StackTraceElement element : report.getCause().getStackTrace()) {
        content.append("  at ").append(element).append(System.lineSeparator());
      }
    }
    return content.toString();
  }
}
