package io.mehdieidi.modriss.mde.etl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Serializes ETL execution reports to a stable JSON representation. */
public final class EtlExecutionReportWriter {

  /**
   * Writes a report to disk, creating parent directories when necessary.
   *
   * @param file destination JSON file; {@code null} is ignored
   * @param report report to serialize
   * @throws IOException when the report cannot be written
   */
  public void write(Path file, EtlExecutionReport report) throws IOException {
    if (file == null) {
      return;
    }
    Path parent = file.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(file, toJson(report), StandardCharsets.UTF_8);
  }

  /**
   * Converts a report to JSON without requiring a JSON dependency at runtime.
   *
   * @param report report to serialize
   * @return JSON document ending with a newline
   */
  public String toJson(EtlExecutionReport report) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    appendProperty(json, "status", report.status().name(), true);
    appendProperty(json, "moduleFile", report.moduleFile().toString(), true);
    appendProperty(json, "startedAt", report.startedAt().toString(), true);
    appendProperty(json, "finishedAt", report.finishedAt().toString(), true);
    appendProperty(json, "durationMillis", Long.toString(report.duration().toMillis()), false);
    json.append("  \"phaseTiming\": {\n");
    int timingIndex = 0;
    for (var timing : report.phaseTiming().asMap().entrySet()) {
      appendProperty(
          json,
          timing.getKey(),
          Long.toString(timing.getValue()),
          false,
          4,
          ++timingIndex < report.phaseTiming().asMap().size());
    }
    json.append("  },\n");
    json.append("  \"diagnostics\": [\n");
    for (int i = 0; i < report.diagnostics().size(); i++) {
      EtlDiagnostic diagnostic = report.diagnostics().get(i);
      json.append("    {\n");
      appendProperty(json, "severity", diagnostic.severity().name(), true, 6);
      appendProperty(json, "phase", diagnostic.phase().name(), true, 6);
      appendProperty(
          json, "file", diagnostic.file() == null ? "" : diagnostic.file().toString(), true, 6);
      appendProperty(json, "line", Integer.toString(diagnostic.line()), false, 6);
      appendProperty(json, "column", Integer.toString(diagnostic.column()), false, 6);
      appendProperty(json, "reason", diagnostic.reason(), true, 6);
      appendProperty(json, "whatWentWrong", diagnostic.whatWentWrong(), true, 6);
      appendProperty(json, "howToFix", diagnostic.howToFix(), true, 6);
      appendProperty(json, "exceptionType", diagnostic.exceptionType(), true, 6, false);
      json.append("    }");
      if (i + 1 < report.diagnostics().size()) {
        json.append(',');
      }
      json.append('\n');
    }
    json.append("  ],\n");
    appendProperty(json, "standardOutput", report.standardOutput(), true);
    appendProperty(json, "warningOutput", report.warningOutput(), true);
    appendProperty(json, "errorOutput", report.errorOutput(), true, 2, false);
    json.append("}\n");
    return json.toString();
  }

  /**
   * Appends a top-level property followed by a comma.
   *
   * @param json target JSON buffer
   * @param name property name
   * @param value property value
   * @param quoteValue whether the value should be JSON-quoted
   */
  private void appendProperty(StringBuilder json, String name, String value, boolean quoteValue) {
    appendProperty(json, name, value, quoteValue, 2, true);
  }

  /**
   * Appends an indented property followed by a comma.
   *
   * @param json target JSON buffer
   * @param name property name
   * @param value property value
   * @param quoteValue whether the value should be JSON-quoted
   * @param indent indentation width in spaces
   */
  private void appendProperty(
      StringBuilder json, String name, String value, boolean quoteValue, int indent) {
    appendProperty(json, name, value, quoteValue, indent, true);
  }

  /**
   * Appends an indented property with explicit comma control.
   *
   * @param json target JSON buffer
   * @param name property name
   * @param value property value
   * @param quoteValue whether the value should be JSON-quoted
   * @param indent indentation width in spaces
   * @param comma whether to append a trailing comma
   */
  private void appendProperty(
      StringBuilder json,
      String name,
      String value,
      boolean quoteValue,
      int indent,
      boolean comma) {
    json.append(" ".repeat(indent));
    json.append('"').append(escape(name)).append("\": ");
    if (quoteValue) {
      json.append('"').append(escape(value)).append('"');
    } else {
      json.append(value);
    }
    if (comma) {
      json.append(',');
    }
    json.append('\n');
  }

  /**
   * Escapes a value for inclusion in a JSON string.
   *
   * @param value raw value
   * @return escaped JSON string content
   */
  private String escape(String value) {
    return value == null
        ? ""
        : value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
  }
}
