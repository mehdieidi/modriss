package io.mehdieidi.modless.mde.generation;

import java.nio.file.Path;
import java.util.List;

public final class AwsPsmToArtifactsDefaults {

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

    private AwsPsmToArtifactsDefaults() {
    }

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
