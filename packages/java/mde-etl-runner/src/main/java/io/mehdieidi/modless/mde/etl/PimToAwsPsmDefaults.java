package io.mehdieidi.modless.mde.etl;

import java.nio.file.Path;
import java.util.List;

/**
 * Supplies the repository-relative defaults used to execute the standard PIM-to-AWS-PSM ETL
 * transformation.
 */
public final class PimToAwsPsmDefaults {

    /**
     * Model aliases expected by the PIM input side of the ETL module.
     */
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

    /**
     * Model aliases expected by the generated AWS PSM output side of the ETL module.
     */
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

    /**
     * Prevents construction of this constants-and-factory holder.
     */
    private PimToAwsPsmDefaults() {
    }

    /**
     * Builds an execution request for the bundled PIM-to-AWS-PSM module and metamodels.
     *
     * @param repositoryRoot  repository root containing the {@code mde} assets
     * @param sourceModel     source PIM XMI file
     * @param targetModel     target AWS PSM XMI file
     * @param overwriteOutput whether an existing target should be replaced
     * @param captureOutput   whether Epsilon output streams should be captured
     * @return configured ETL execution request
     */
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
