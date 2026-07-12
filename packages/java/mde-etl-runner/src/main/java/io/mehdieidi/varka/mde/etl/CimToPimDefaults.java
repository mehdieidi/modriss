package io.mehdieidi.varka.mde.etl;

import java.nio.file.Path;
import java.util.List;

/**
 * Supplies the repository-relative defaults used to execute the standard CIM-to-PIM ETL
 * transformation.
 */
public final class CimToPimDefaults {

  /** Model aliases expected by the CIM input side of the ETL module. */
  public static final List<String> SOURCE_ALIASES =
      List.of("CIM", "CIMORG", "CIMDOMAIN", "CIMBEHAVIOR", "CIMPROCESS", "CIMGOV", "CIMTRANSFORM");

  /** Model aliases expected by the generated PIM output side of the ETL module. */
  public static final List<String> TARGET_ALIASES =
      List.of(
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

  /** Prevents construction of this constants-and-factory holder. */
  private CimToPimDefaults() {}

  /**
   * Builds an execution request for the bundled CIM-to-PIM module and metamodels.
   *
   * @param repositoryRoot repository root containing the {@code mde} assets
   * @param sourceModel source CIM XMI file
   * @param targetModel target PIM XMI file
   * @param overwriteOutput whether an existing target should be replaced
   * @param captureOutput whether Epsilon output streams should be captured
   * @return configured ETL execution request
   */
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
            EtlModelConfiguration.source("CIM", SOURCE_ALIASES, sourceModel, List.of(cimMetamodel)),
            EtlModelConfiguration.target(
                "PIM", TARGET_ALIASES, targetModel, List.of(pimMetamodel), readTarget)),
        overwriteOutput,
        captureOutput);
  }
}
