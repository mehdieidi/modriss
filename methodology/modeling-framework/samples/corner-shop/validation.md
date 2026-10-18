# Verification and reproduction

The Corner Shop CIM was checked against the repository's existing metamodel, complete CIM semantic profile, native editor XMI converter and CIM-to-PIM transformation. No metamodel, EVL rule, editor implementation or transformation rule was changed for this sample.

| Check                               | Evidence                                                                                                         |
| ----------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| Structural EMF/Ecore conformance    | Passed with structural validation enabled and no structural diagnostics                                          |
| Complete CIM EVL profile            | Zero mandatory violations and zero optional critiques/warnings                                                   |
| Native editor import representation | Sample-specific integration test checks every diagram relationship endpoint and configured CIM relationship kind |
| Import → export → reimport          | All 432 identified elements retained, no unresolved proxies, and semantic collections preserved                  |
| CIM-to-PIM ETL                      | Successful execution with no execution diagnostics; disposable draft written under `target/`                     |

The [machine-readable validation report](cim-validation-report.json) contains the actual EVL results, timings and model SHA-256. Tool file paths are normalized relative to the repository for portability.

Verified XMI SHA-256:

```text
e1b06c6f8e3bae2ccffaf9e424b07dad228519edd4d30bfcd664a47df2a685cb
```

The [focused import test](../../../../packages/java/platform-modeling-infrastructure/src/test/java/io/mehdieidi/modriss/platform/modeling/xmi/CornerShopSampleImportTest.java) uses `XmiModelImportService`, the converter used by the native CIM import endpoint. It compares the semantic collections and all identifiers before/after export. EMF reconstructs the inverse event-producer association (`expectedByCommands`) in a different order; the test compares its full membership while ignoring producer order, which has no business meaning. Containment and process-step ordering remain part of the exact comparison.

This verifies editor-format compatibility, not a live browser session or authenticated project save. Runtime orders, provider integrations, refunds, deployment and the [twenty acceptance scenarios](evaluation-scenarios.md) have not been implemented/executed by delivering this reference model. Generated PIM readiness findings still require review and completion before AWS PSM and application generation.

EVL is used here as the explicit sample/model validation workflow requested for this work. No chatbot apply/repair/commit validation boundary was changed. No linters or formatters were run.

## Reproduce from the repository root

Build the standalone tools (Java and Maven required):

```powershell
mvn -q -pl tools/model-validator-cli,tools/model-transformer-cli -am package '-DskipTests' '-Dcheckstyle.skip=true' '-Dpmd.skip=true' '-Dspotbugs.skip=true' '-Dspotless.skip=true'
```

Run the full CIM validation profile with structural checks enabled and fail on warnings as well as errors:

```powershell
java -jar tools/model-validator-cli/target/model-validator-cli-0.0.1-SNAPSHOT.jar cim --repo-root . --model methodology/modeling-framework/samples/corner-shop/corner-shop.cim.xmi --fail-on-optional-violations --log-file target/corner-shop-validation.json
```

Expected exit `0`, with `Violations: 0 mandatory, 0 optional.`

Run the editor import/export compatibility check:

```powershell
mvn -q -pl packages/java/platform-modeling-infrastructure -am test '-Dtest=CornerShopSampleImportTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dcheckstyle.skip=true' '-Dpmd.skip=true' '-Dspotbugs.skip=true' '-Dspotless.skip=true'
```

Expected: one test, zero failures and zero errors. Maven properties are quoted for PowerShell and skip lint/format plugins.

Generate a disposable PIM draft:

```powershell
java -jar tools/model-transformer-cli/target/model-transformer-cli-0.0.1-SNAPSHOT.jar cim-to-pim --repo-root . --source-model methodology/modeling-framework/samples/corner-shop/corner-shop.cim.xmi --target-model target/corner-shop.draft.pim.xmi --overwrite --log-file target/corner-shop-transformation.json
```

Expected exit `0`, with `ETL execution succeeded`. The generated draft/report live under `target/`; they are not precompleted PIM/PSM/application answers supplied to experimental participants. Use the model guide and evaluation activities to review/refine the draft and record later evidence separately.
