package io.mehdieidi.modless.mde.validation;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
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
        properties.put(EmfModel.PROPERTY_IS_METAMODEL_FILE_BASED, "true");
        properties.put(EmfModel.PROPERTY_MODEL_FILE, modelFile.toAbsolutePath().toString());
        properties.put(EmfModel.PROPERTY_METAMODEL_FILE, joinPaths(metamodelFiles));
        properties.put(EmfModel.PROPERTY_VALIDATE, Boolean.toString(validate));
        model.load(properties);
        return model;
    }

    private String joinPaths(List<Path> paths) {
        return paths.stream()
                .map(path -> path.toAbsolutePath().toString())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }
}
