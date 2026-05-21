package io.mehdieidi.modless.mdecli.service;

import io.mehdieidi.modless.mdecli.diagnostics.DiagnosticEntry;
import io.mehdieidi.modless.mdecli.diagnostics.DiagnosticEntry.Severity;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.ecore.resource.Resource;

public final class DiagnosticMapper {

    public List<DiagnosticEntry> map(Resource resource) {
        List<DiagnosticEntry> diagnostics = new ArrayList<>();
        resource.getErrors()
                .forEach(error -> diagnostics.add(fromResourceDiagnostic(error, Severity.ERROR)));
        resource.getWarnings().forEach(
                warning -> diagnostics.add(fromResourceDiagnostic(warning, Severity.WARNING)));
        return diagnostics;
    }

    public DiagnosticEntry fromException(String location, Exception ex) {
        return new DiagnosticEntry(
                Severity.ERROR,
                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                location,
                -1,
                -1,
                hintForMessage(ex.getMessage()),
                "");
    }

    private DiagnosticEntry fromResourceDiagnostic(Resource.Diagnostic diagnostic,
            Severity severity) {
        return new DiagnosticEntry(
                severity,
                diagnostic.getMessage(),
                diagnostic.getLocation() == null ? "" : diagnostic.getLocation(),
                diagnostic.getLine(),
                diagnostic.getColumn(),
                hintForMessage(diagnostic.getMessage()),
                "");
    }

    private String hintForMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String normalized = message.toLowerCase();
        if (normalized.contains("mismatched input") || normalized.contains("syntax error")) {
            return "Fix the Emfatic syntax near the reported line and column, then run the CLI again.";
        }
        if (normalized.contains("couldn't resolve reference") || normalized.contains(
                "cannot find")) {
            return "Check imported package names and ensure the referenced .ecore or module file exists beside the current file.";
        }
        if (normalized.contains(".ecore")) {
            return "For modular metamodels, run the CLI on the containing directory or ensure required sibling modules are available.";
        }
        return "";
    }
}
