package io.mehdieidi.modless.mde.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Serializes EVL validation reports to a stable JSON representation. */
public final class EvlValidationReportWriter {

  /**
   * Writes a validation report to disk, creating parent directories when necessary.
   *
   * @param file destination JSON file; {@code null} is ignored
   * @param report report to serialize
   * @throws IOException when the report cannot be written
   */
  public void write(Path file, EvlValidationReport report) throws IOException {
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
   * Converts a validation report to JSON without requiring a JSON dependency at runtime.
   *
   * @param report report to serialize
   * @return JSON document ending with a newline
   */
  public String toJson(EvlValidationReport report) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    appendProperty(json, "status", report.status().name(), true);
    appendProperty(json, "evlRoot", report.evlRoot().toString(), true);
    appendProperty(json, "startedAt", report.startedAt().toString(), true);
    appendProperty(json, "finishedAt", report.finishedAt().toString(), true);
    appendProperty(json, "durationMillis", Long.toString(report.duration().toMillis()), false);
    appendProperty(json, "totalMillis", Long.toString(report.totalDuration().toMillis()), false);
    appendProperty(
        json,
        "moduleDiscoveryMillis",
        Long.toString(report.moduleDiscoveryDuration().toMillis()),
        false);
    appendProperty(
        json,
        "mandatoryViolationCount",
        Long.toString(
            report.violations().stream()
                .filter(v -> v.kind() == EvlConstraintKind.MANDATORY)
                .count()),
        false);
    appendProperty(
        json,
        "optionalViolationCount",
        Long.toString(
            report.violations().stream()
                .filter(v -> v.kind() == EvlConstraintKind.OPTIONAL)
                .count()),
        false);
    appendModules(json, report);
    appendViolations(json, report);
    appendDiagnostics(json, report);
    appendProperty(json, "standardOutput", report.stdout(), true);
    appendProperty(json, "warningOutput", report.warnings(), true);
    appendProperty(json, "errorOutput", report.stderr(), true, 2, false);
    json.append("}\n");
    return json.toString();
  }

  /**
   * Appends module summary objects.
   *
   * @param json target JSON buffer
   * @param report report containing module summaries
   */
  private void appendModules(StringBuilder json, EvlValidationReport report) {
    json.append("  \"modules\": [\n");
    for (int i = 0; i < report.moduleReports().size(); i++) {
      EvlModuleReport module = report.moduleReports().get(i);
      json.append("    {\n");
      appendProperty(json, "moduleFile", module.moduleFile().toString(), true, 6);
      appendProperty(json, "totalMillis", Long.toString(module.duration().toMillis()), false, 6);
      appendProperty(
          json, "parseMillis", Long.toString(module.parseDuration().toMillis()), false, 6);
      appendProperty(
          json, "modelLoadMillis", Long.toString(module.modelLoadDuration().toMillis()), false, 6);
      appendProperty(
          json,
          "structuralValidationMillis",
          Long.toString(module.structuralValidationDuration().toMillis()),
          false,
          6);
      appendProperty(
          json,
          "evlExecuteMillis",
          Long.toString(module.evlExecuteDuration().toMillis()),
          false,
          6);
      appendProperty(
          json,
          "violationMappingMillis",
          Long.toString(module.violationMappingDuration().toMillis()),
          false,
          6);
      appendProperty(
          json, "disposeMillis", Long.toString(module.disposeDuration().toMillis()), false, 6);
      appendProperty(
          json, "violationCount", Integer.toString(module.violations().size()), false, 6);
      appendProperty(
          json, "diagnosticCount", Integer.toString(module.diagnostics().size()), false, 6, false);
      json.append("    }");
      if (i + 1 < report.moduleReports().size()) {
        json.append(',');
      }
      json.append('\n');
    }
    json.append("  ],\n");
  }

  /**
   * Appends flattened violation objects.
   *
   * @param json target JSON buffer
   * @param report report containing violations
   */
  private void appendViolations(StringBuilder json, EvlValidationReport report) {
    json.append("  \"violations\": [\n");
    for (int i = 0; i < report.violations().size(); i++) {
      EvlConstraintViolation violation = report.violations().get(i);
      json.append("    {\n");
      appendProperty(json, "kind", violation.kind().name(), true, 6);
      appendProperty(json, "constraintName", violation.constraintName(), true, 6);
      appendProperty(json, "contextType", violation.contextType(), true, 6);
      appendProperty(json, "message", violation.message(), true, 6);
      appendProperty(
          json, "file", violation.file() == null ? "" : violation.file().toString(), true, 6);
      appendProperty(json, "line", Integer.toString(violation.line()), false, 6);
      appendProperty(json, "column", Integer.toString(violation.column()), false, 6);
      appendElement(json, violation.element());
      appendStringMap(json, "extras", violation.extras(), 6, false);
      json.append("    }");
      if (i + 1 < report.violations().size()) {
        json.append(',');
      }
      json.append('\n');
    }
    json.append("  ],\n");
  }

  /**
   * Appends the safe element reference nested within a violation.
   *
   * @param json target JSON buffer
   * @param element element reference to serialize
   */
  private void appendElement(StringBuilder json, EvlElementReference element) {
    json.append("      \"element\": {\n");
    appendProperty(json, "modelType", element.modelType(), true, 8);
    appendProperty(json, "uri", element.uri(), true, 8);
    appendProperty(json, "uriFragment", element.uriFragment(), true, 8);
    appendProperty(json, "summary", element.summary(), true, 8);
    appendStringMap(json, "attributes", element.attributes(), 8, false);
    json.append("      },\n");
  }

  /**
   * Appends diagnostic objects.
   *
   * @param json target JSON buffer
   * @param report report containing diagnostics
   */
  private void appendDiagnostics(StringBuilder json, EvlValidationReport report) {
    json.append("  \"diagnostics\": [\n");
    for (int i = 0; i < report.diagnostics().size(); i++) {
      EvlDiagnostic diagnostic = report.diagnostics().get(i);
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
   * Appends a string-valued JSON object.
   *
   * @param json target JSON buffer
   * @param name property name
   * @param values map values to serialize
   * @param indent indentation width in spaces
   * @param comma whether to append a trailing comma
   */
  private void appendStringMap(
      StringBuilder json, String name, Map<String, String> values, int indent, boolean comma) {
    json.append(" ".repeat(indent)).append('"').append(escape(name)).append("\": {\n");
    int index = 0;
    for (Map.Entry<String, String> entry : values.entrySet()) {
      appendProperty(
          json, entry.getKey(), entry.getValue(), true, indent + 2, index + 1 < values.size());
      index++;
    }
    json.append(" ".repeat(indent)).append('}');
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
