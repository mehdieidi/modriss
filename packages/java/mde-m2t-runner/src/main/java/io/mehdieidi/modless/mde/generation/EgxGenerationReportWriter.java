package io.mehdieidi.modless.mde.generation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Serializes EGX generation reports to a stable JSON representation. */
public final class EgxGenerationReportWriter {

  /**
   * Writes a generation report to disk, creating parent directories when necessary.
   *
   * @param file destination JSON file; {@code null} is ignored
   * @param report report to serialize
   * @throws IOException when the report cannot be written
   */
  public void write(Path file, EgxGenerationReport report) throws IOException {
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
   * Converts a generation report to JSON without requiring a JSON dependency at runtime.
   *
   * @param report report to serialize
   * @return JSON document ending with a newline
   */
  public String toJson(EgxGenerationReport report) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    appendProperty(json, "status", report.status().name(), true);
    appendProperty(json, "moduleFile", report.moduleFile().toString(), true);
    appendProperty(json, "outputDirectory", report.outputDirectory().toString(), true);
    appendProperty(json, "startedAt", report.startedAt().toString(), true);
    appendProperty(json, "finishedAt", report.finishedAt().toString(), true);
    appendProperty(json, "durationMillis", Long.toString(report.duration().toMillis()), false);
    appendPathArray(json, "generatedFiles", report.generatedFiles(), 2, true);
    appendDiagnostics(json, report);
    appendProperty(json, "standardOutput", report.standardOutput(), true);
    appendProperty(json, "warningOutput", report.warningOutput(), true);
    appendProperty(json, "errorOutput", report.errorOutput(), true, 2, false);
    json.append("}\n");
    return json.toString();
  }

  /**
   * Appends diagnostic objects.
   *
   * @param json target JSON buffer
   * @param report report containing diagnostics
   */
  private void appendDiagnostics(StringBuilder json, EgxGenerationReport report) {
    json.append("  \"diagnostics\": [\n");
    for (int i = 0; i < report.diagnostics().size(); i++) {
      GenerationDiagnostic diagnostic = report.diagnostics().get(i);
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
  }

  /**
   * Appends a JSON array of relative paths.
   *
   * @param json target JSON buffer
   * @param name property name
   * @param values paths to serialize
   * @param indent indentation width in spaces
   * @param comma whether to append a trailing comma
   */
  private void appendPathArray(
      StringBuilder json, String name, Iterable<Path> values, int indent, boolean comma) {
    json.append(" ".repeat(indent)).append('"').append(escape(name)).append("\": [");
    boolean first = true;
    for (Path value : values) {
      if (!first) {
        json.append(", ");
      }
      json.append('"').append(escape(value.toString().replace('\\', '/'))).append('"');
      first = false;
    }
    json.append(']');
    if (comma) {
      json.append(',');
    }
    json.append('\n');
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
