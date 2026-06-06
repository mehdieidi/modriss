package io.mehdieidi.modless.mdeevlcli;

import io.mehdieidi.modless.mde.validation.EvlValidationRequest;
import io.mehdieidi.modless.mde.validation.FileEvlModelConfiguration;
import java.nio.file.Path;
import java.util.List;

/**
 * Builds validation requests for repository-standard model-level profiles.
 */
final class ProfileCommandSupport {

    /**
     * Prevents construction of this utility class.
     */
    private ProfileCommandSupport() {
    }

    /**
     * Creates a validation request using the conventional repository profile layout.
     *
     * @param repositoryRoot       repository root
     * @param profile              model profile directory name
     * @param entryModule          profile entry EVL module
     * @param model                input XMI model
     * @param modelName            Epsilon model name
     * @param aliases              additional Epsilon aliases
     * @param structuralValidation whether to perform EMF structural validation
     * @return normalized validation request
     */
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
