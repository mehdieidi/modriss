package io.mehdieidi.modless.mdeevlcli;

import io.mehdieidi.modless.mde.validation.EvlValidationRequest;
import io.mehdieidi.modless.mde.validation.FileEvlModelConfiguration;
import java.nio.file.Path;
import java.util.List;

final class ProfileCommandSupport {

    private ProfileCommandSupport() {
    }

    static EvlValidationRequest request(
            Path repositoryRoot,
            String profile,
            String entryModule,
            Path model,
            String modelName,
            List<String> aliases,
            boolean structuralValidation) {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        Path evlRoot = root.resolve("mde/validation").resolve(profile);
        return new EvlValidationRequest(
                evlRoot,
                List.of(Path.of(entryModule)),
                List.of(new FileEvlModelConfiguration(
                        modelName,
                        aliases,
                        model.toAbsolutePath().normalize(),
                        List.of(root.resolve("mde/metamodels").resolve(profile)
                                .resolve(profile + "-combined.ecore")),
                        structuralValidation)),
                true);
    }
}
