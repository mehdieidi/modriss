package io.mehdieidi.modless.mde.generation;

import java.nio.file.Path;
import java.util.List;

/**
 * Supplies repository-relative defaults for generating deployable artifacts from an AWS PSM model.
 */
public final class AwsPsmToArtifactsDefaults {

    /**
     * Model aliases expected by the AWS PSM generation modules.
     */
    public static final List<String> AWS_PSM_ALIASES = List.of(
            "AWSPSM",
            "AWSPSMENUMS",
            "AWSPSMCORE",
            "AWSPSMSECURITY",
            "AWSPSMOBSERVABILITY",
            "AWSPSMNETWORKING",
            "AWSPSMMESSAGING",
            "AWSPSMSTORAGE",
            "AWSPSMIDENTITY",
            "AWSPSMWORKFLOW",
            "AWSPSMCOMPUTE",
            "AWSPSMAPI",
            "AWSPSMEVENTS",
            "AWSPSMINTEGRATIONS",
            "KERNEL");

    /**
     * Prevents construction of this constants-and-factory holder.
     */
    private AwsPsmToArtifactsDefaults() {
    }

    /**
     * Builds an EGX generation request for the bundled AWS PSM artifact generator.
     *
     * @param repositoryRoot                  repository root containing the {@code mde} assets
     * @param sourcePsmModel                  source AWS PSM XMI file
     * @param outputDirectory                 directory that receives generated files
     * @param failIfOutputDirectoryIsNotEmpty whether non-empty output should fail
     * @param captureOutput                   whether Epsilon output streams should be captured
     * @return configured generation request
     */
    public static EgxGenerationRequest request(
            Path repositoryRoot,
            Path sourcePsmModel,
            Path outputDirectory,
            boolean failIfOutputDirectoryIsNotEmpty,
            boolean captureOutput) {
        Path generatorRoot = repositoryRoot.resolve("mde/generation/awspsm-to-artifacts");
        Path psmMetamodel = repositoryRoot.resolve("mde/metamodels/psm/psm-combined.ecore");
        return new EgxGenerationRequest(
                generatorRoot.resolve("awspsm2artifacts.egx"),
                generatorRoot.resolve("templates"),
                outputDirectory,
                List.of(new GenerationModelConfiguration(
                        "AWSPSM",
                        AWS_PSM_ALIASES,
                        sourcePsmModel,
                        List.of(psmMetamodel),
                        false)),
                failIfOutputDirectoryIsNotEmpty,
                captureOutput);
    }
}
