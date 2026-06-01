package io.mehdieidi.modless.mde.validation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.Diagnostician;

final class EvlModelResourceDiagnostics {

    private EvlModelResourceDiagnostics() {
    }

    static List<EvlDiagnostic> validate(Resource resource, Path modelFile) {
        if (resource == null) {
            return List.of();
        }
        List<EvlDiagnostic> diagnostics = new ArrayList<>();
        resource.getErrors().forEach(error -> diagnostics.add(resourceDiagnostic(
                ValidationSeverity.ERROR, error, modelFile)));
        resource.getWarnings().forEach(warning -> diagnostics.add(resourceDiagnostic(
                ValidationSeverity.WARNING, warning, modelFile)));
        for (EObject root : resource.getContents()) {
            collectDiagnostics(Diagnostician.INSTANCE.validate(root), modelFile, diagnostics);
        }
        return diagnostics;
    }

    private static EvlDiagnostic resourceDiagnostic(
            ValidationSeverity severity, Resource.Diagnostic diagnostic, Path modelFile) {
        return new EvlDiagnostic(
                severity,
                ValidationPhase.MODEL_LOADING,
                modelFile,
                diagnostic.getLine(),
                -1,
                diagnostic.getMessage(),
                "The EMF resource reported a structural model problem.",
                "Fix the model XMI so it conforms to the loaded Ecore metamodel.",
                diagnostic.getClass().getName());
    }

    private static void collectDiagnostics(
            Diagnostic diagnostic, Path modelFile, List<EvlDiagnostic> out) {
        if (diagnostic == null || diagnostic.getSeverity() == Diagnostic.OK
                || diagnostic.getSeverity() == Diagnostic.INFO) {
            return;
        }
        int before = out.size();
        diagnostic.getChildren().forEach(child -> collectDiagnostics(child, modelFile, out));
        if (out.size() == before) {
            out.add(new EvlDiagnostic(
                    severity(diagnostic),
                    ValidationPhase.MODEL_LOADING,
                    modelFile,
                    -1,
                    -1,
                    diagnostic.getMessage(),
                    "The EMF model does not conform to its Ecore metamodel.",
                    "Fix the reported structural problem before running semantic EVL validation.",
                    Diagnostic.class.getName()));
        }
    }

    private static ValidationSeverity severity(Diagnostic diagnostic) {
        return diagnostic.getSeverity() == Diagnostic.WARNING
                ? ValidationSeverity.WARNING
                : ValidationSeverity.ERROR;
    }
}
