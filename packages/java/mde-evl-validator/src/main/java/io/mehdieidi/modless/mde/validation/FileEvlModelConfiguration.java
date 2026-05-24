package io.mehdieidi.modless.mde.validation;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.emc.emf.EmfModel;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.models.IModel;
import org.eclipse.epsilon.eol.models.Model;

public record FileEvlModelConfiguration(
        String name,
        List<String> aliases,
        Path modelFile,
        List<Path> metamodelFiles,
        boolean validate) implements EvlModelConfiguration {

    public FileEvlModelConfiguration {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(modelFile, "modelFile");
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        metamodelFiles = metamodelFiles == null ? List.of() : List.copyOf(metamodelFiles);
    }

    public static FileEvlModelConfiguration readOnly(
            String name, List<String> aliases, Path modelFile, List<Path> metamodelFiles) {
        return new FileEvlModelConfiguration(name, aliases, modelFile, metamodelFiles, true);
    }

    @Override
    public IModel load() throws EolModelLoadingException {
        EmfModel model = new EmfModel();
        StringProperties properties = new StringProperties();
        properties.put(Model.PROPERTY_NAME, name);
        if (!aliases.isEmpty()) {
            properties.put(Model.PROPERTY_ALIASES, String.join(",", aliases));
        }
        properties.put(Model.PROPERTY_READONLOAD, "true");
        properties.put(Model.PROPERTY_STOREONDISPOSAL, "false");
        properties.put(Model.PROPERTY_READONLY, "true");
        properties.put(EmfModel.PROPERTY_MODEL_URI, fileUri(modelFile));
        properties.put(EmfModel.PROPERTY_FILE_BASED_METAMODEL_URI, joinFileUris(metamodelFiles));
        properties.put(EmfModel.PROPERTY_VALIDATE, "false");
        model.load(properties);
        if (validate) {
            validateModelResource(model);
        }
        return model;
    }

    private void validateModelResource(EmfModel model) throws EolModelLoadingException {
        Resource resource = model.getResource();
        if (resource == null) {
            return;
        }
        if (!resource.getErrors().isEmpty() || !resource.getWarnings().isEmpty()) {
            List<Resource.Diagnostic> diagnostics = new java.util.ArrayList<>();
            diagnostics.addAll(resource.getErrors());
            diagnostics.addAll(resource.getWarnings());
            Resource.Diagnostic diagnostic = diagnostics.get(0);
            throw new EolModelLoadingException(new Exception(
                    diagnostic.getMessage() + " (" + diagnostic.getLocation() + "@"
                            + diagnostic.getLine() + ")"),
                    model);
        }
        for (EObject root : resource.getContents()) {
            Diagnostic problem = firstProblem(Diagnostician.INSTANCE.validate(root));
            if (problem != null) {
                throw new EolModelLoadingException(new Exception(problem.getMessage()), model);
            }
        }
    }

    private Diagnostic firstProblem(Diagnostic diagnostic) {
        if (diagnostic == null || diagnostic.getSeverity() == Diagnostic.OK
                || diagnostic.getSeverity() == Diagnostic.INFO) {
            return null;
        }
        for (Diagnostic child : diagnostic.getChildren()) {
            Diagnostic childProblem = firstProblem(child);
            if (childProblem != null) {
                return childProblem;
            }
        }
        return diagnostic;
    }

    private String joinFileUris(List<Path> paths) {
        return paths.stream()
                .map(this::fileUri)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String fileUri(Path path) {
        return URI.createFileURI(path.toAbsolutePath().normalize().toString()).toString();
    }
}
