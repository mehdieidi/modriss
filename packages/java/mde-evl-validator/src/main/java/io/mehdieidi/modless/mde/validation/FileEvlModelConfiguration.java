package io.mehdieidi.modless.mde.validation;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.eclipse.emf.common.util.URI;
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
        return model;
    }

    @Override
    public List<EvlDiagnostic> validateLoadedModel(IModel model) {
        if (!validate || !(model instanceof EmfModel emfModel)) {
            return List.of();
        }
        return EvlModelResourceDiagnostics.validate(emfModel.getResource(), modelFile);
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
