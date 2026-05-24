package io.mehdieidi.modless.mde.etl;

import java.nio.file.Path;
import java.util.List;

public final class CimToPimDefaults {

    public static final List<String> SOURCE_ALIASES = List.of(
            "CIM",
            "CIMORG",
            "CIMDOMAIN",
            "CIMBEHAVIOR",
            "CIMPROCESS",
            "CIMGOV",
            "CIMTRANSFORM");

    public static final List<String> TARGET_ALIASES = List.of(
            "PIM",
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
            "KERNEL",
            "PIMTYPES");

    private CimToPimDefaults() {
    }

    public static EtlExecutionRequest request(
            Path repositoryRoot,
            Path sourceModel,
            Path targetModel,
            boolean overwriteOutput,
            boolean captureOutput) {
        Path module = repositoryRoot.resolve("mde/transformations/cim-to-pim/cim-to-pim.etl");
        Path cimMetamodel = repositoryRoot.resolve("mde/metamodels/cim/cim-combined.ecore");
        Path pimMetamodel = repositoryRoot.resolve("mde/metamodels/pim/pim-combined.ecore");
        boolean readTarget = !overwriteOutput && targetModel.toFile().isFile();

        return new EtlExecutionRequest(
                module,
                module.getParent(),
                List.of(
                        EtlModelConfiguration.source(
                                "CIM",
                                SOURCE_ALIASES,
                                sourceModel,
                                List.of(cimMetamodel)),
                        EtlModelConfiguration.target(
                                "PIM",
                                TARGET_ALIASES,
                                targetModel,
                                List.of(pimMetamodel),
                                readTarget)),
                overwriteOutput,
                captureOutput);
    }
}
