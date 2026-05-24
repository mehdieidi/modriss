package io.mehdieidi.modless.mde.etl;

import java.nio.file.Path;
import java.util.List;

public final class PimToAwsPsmDefaults {

    public static final List<String> SOURCE_ALIASES = List.of(
            "PIM",
            "DEPLOYMENT",
            "DEPLOY",
            "COMPUTE",
            "API",
            "CONTRACTS",
            "DATA",
            "INTEGRATION",
            "WORKFLOW",
            "EXTERNAL",
            "POLICY",
            "SECURITY",
            "CONFIG",
            "PIMTYPES");

    public static final List<String> TARGET_ALIASES = List.of(
            "AWSPSM",
            "AWSPSMAPI",
            "AWSPSMCOMPUTE",
            "AWSPSMCORE",
            "AWSPSMSTORAGE",
            "AWSPSMSECURITY",
            "AWSPSMOBSERVABILITY",
            "AWSPSMNETWORKING",
            "AWSPSMMESSAGING",
            "AWSPSMINTEGRATIONS",
            "AWSPSMIDENTITY",
            "AWSPSMEVENTS",
            "AWSPSMENUMS",
            "AWSPSMWORKFLOW",
            "KERNEL");

    private PimToAwsPsmDefaults() {
    }

    public static EtlExecutionRequest request(
            Path repositoryRoot,
            Path sourceModel,
            Path targetModel,
            boolean overwriteOutput,
            boolean captureOutput) {
        Path module = repositoryRoot.resolve("mde/transformations/pim-to-awspsm/pim-to-awspsm.etl");
        Path pimMetamodel = repositoryRoot.resolve("mde/metamodels/pim/pim-combined.ecore");
        Path awsPsmMetamodel = repositoryRoot.resolve("mde/metamodels/psm/psm-combined.ecore");
        boolean readTarget = !overwriteOutput && targetModel.toFile().isFile();

        return new EtlExecutionRequest(
                module,
                module.getParent(),
                List.of(
                        EtlModelConfiguration.source(
                                "PIM",
                                SOURCE_ALIASES,
                                sourceModel,
                                List.of(pimMetamodel)),
                        EtlModelConfiguration.target(
                                "AWSPSM",
                                TARGET_ALIASES,
                                targetModel,
                                List.of(awsPsmMetamodel),
                                readTarget)),
                overwriteOutput,
                captureOutput);
    }
}
