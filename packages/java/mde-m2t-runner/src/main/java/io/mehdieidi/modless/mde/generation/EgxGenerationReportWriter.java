package io.mehdieidi.modless.mde.generation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EgxGenerationReportWriter {

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

    private void appendDiagnostics(StringBuilder json, EgxGenerationReport report) {
        json.append("  \"diagnostics\": [\n");
        for (int i = 0; i < report.diagnostics().size(); i++) {
            GenerationDiagnostic diagnostic = report.diagnostics().get(i);
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
