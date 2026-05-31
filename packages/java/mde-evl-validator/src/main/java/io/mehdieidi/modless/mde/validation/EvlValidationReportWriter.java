package io.mehdieidi.modless.mde.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class EvlValidationReportWriter {

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

    public String toJson(EvlValidationReport report) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        appendProperty(json, "status", report.status().name(), true);
        appendProperty(json, "evlRoot", report.evlRoot().toString(), true);
        appendProperty(json, "startedAt", report.startedAt().toString(), true);
        appendProperty(json, "finishedAt", report.finishedAt().toString(), true);
        appendProperty(json, "durationMillis", Long.toString(report.duration().toMillis()), false);
        appendProperty(json, "mandatoryViolationCount",
                Long.toString(report.violations().stream()
                        .filter(v -> v.kind() == EvlConstraintKind.MANDATORY).count()),
                false);
        appendProperty(json, "optionalViolationCount",
                Long.toString(report.violations().stream()
                        .filter(v -> v.kind() == EvlConstraintKind.OPTIONAL).count()),
                false);
        appendViolations(json, report);
        appendDiagnostics(json, report);
        appendProperty(json, "standardOutput", report.stdout(), true);
        appendProperty(json, "warningOutput", report.warnings(), true);
        appendProperty(json, "errorOutput", report.stderr(), true, 2, false);
        json.append("}\n");
        return json.toString();
    }

    private void appendViolations(StringBuilder json, EvlValidationReport report) {
        json.append("  \"violations\": [\n");
        for (int i = 0; i < report.violations().size(); i++) {
            EvlConstraintViolation violation = report.violations().get(i);
            json.append("    {\n");
            appendProperty(json, "kind", violation.kind().name(), true, 6);
            appendProperty(json, "constraintName", violation.constraintName(), true, 6);
            appendProperty(json, "contextType", violation.contextType(), true, 6);
            appendProperty(json, "message", violation.message(), true, 6);
            appendProperty(json, "file",
                    violation.file() == null ? "" : violation.file().toString(), true, 6);
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

    private void appendElement(StringBuilder json, EvlElementReference element) {
        json.append("      \"element\": {\n");
        appendProperty(json, "modelType", element.modelType(), true, 8);
        appendProperty(json, "uri", element.uri(), true, 8);
        appendProperty(json, "uriFragment", element.uriFragment(), true, 8);
        appendProperty(json, "summary", element.summary(), true, 8);
        appendStringMap(json, "attributes", element.attributes(), 8, false);
        json.append("      },\n");
    }

    private void appendDiagnostics(StringBuilder json, EvlValidationReport report) {
        json.append("  \"diagnostics\": [\n");
        for (int i = 0; i < report.diagnostics().size(); i++) {
            EvlDiagnostic diagnostic = report.diagnostics().get(i);
            json.append("    {\n");
            appendProperty(json, "severity", diagnostic.severity().name(), true, 6);
            appendProperty(json, "phase", diagnostic.phase().name(), true, 6);
            appendProperty(json, "file",
                    diagnostic.file() == null ? "" : diagnostic.file().toString(), true, 6);
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

    private void appendStringMap(
            StringBuilder json, String name, Map<String, String> values, int indent,
            boolean comma) {
        json.append(" ".repeat(indent)).append('"').append(escape(name)).append("\": {\n");
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            appendProperty(json, entry.getKey(), entry.getValue(), true, indent + 2,
                    index + 1 < values.size());
            index++;
        }
        json.append(" ".repeat(indent)).append('}');
        if (comma) {
            json.append(',');
        }
        json.append('\n');
    }

    private void appendProperty(StringBuilder json, String name, String value, boolean quoteValue) {
        appendProperty(json, name, value, quoteValue, 2, true);
    }

    private void appendProperty(StringBuilder json, String name, String value, boolean quoteValue,
            int indent) {
        appendProperty(json, name, value, quoteValue, indent, true);
    }

    private void appendProperty(
            StringBuilder json, String name, String value, boolean quoteValue, int indent,
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

    private String escape(String value) {
        return value == null
                ? ""
                : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r")
                        .replace("\n", "\\n");
    }
}
