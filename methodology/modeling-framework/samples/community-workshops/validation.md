# Verification evidence and reproduction

The reference CIM was verified on 2026-10-18 against the repository's existing combined CIM metamodel, complete CIM semantic profile, XMI import/export service, and CIM-to-PIM transformation. No metamodel, EVL rule, editor behavior, or transformation rule was changed to accommodate the sample.

| Check                                          | Result                                                                                                                                                              |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| EMF/Ecore structural validation                | Passed with structural validation enabled; no structural diagnostics                                                                                                |
| Complete `cim-semantic-validation.evl` profile | 0 mandatory violations and 0 optional violations, including critiques/warnings                                                                                      |
| Editor XMI conversion and graph compatibility  | Focused integration test passed; every relationship has resolvable graph endpoints and a configured CIM relationship kind                                           |
| Import → export → reimport                     | All 352 identified elements retained; semantic collections, detailed requirements, processes, privacy references, and behavior preserved; no unresolved EMF proxies |
| CIM-to-PIM ETL                                 | Executed successfully, with no execution diagnostics; a draft PIM was written under `target/`                                                                       |

The machine-readable [CIM validation report](cim-validation-report.json) includes the model SHA-256. File paths in that report are normalized relative to the repository root for portability; the counts, timings, diagnostics, and output are from the actual CLI run.

Verified model SHA-256:

```text
042595b3fd031811900e8ca09333ed70ae132e9a9d8bd67da865ee7c91133a90
```

The import check uses `XmiModelImportService`, the converter behind the native CIM import endpoint. This establishes compatibility with the editor's semantic JSON and graph representation. A live browser session, authenticated project save, cloud deployment, and runtime booking acceptance tests were not executed. The [evaluation scenarios](evaluation-scenarios.md) specify those subsequent checks. A generated draft PIM still needs review and completion; transformation success does not claim deployment readiness.

EVL was run as the explicit sample/model validation requested for this work. No EVL gate was added to chatbot apply, repair, or commit paths. No linters or formatters were run.

## Reproduce from the repository root

Java and Maven must be available. The quoted Maven properties below work in PowerShell and prevent the repository's lint/format tools from running.

Build the two standalone tools and their dependencies:

```powershell
mvn -q -pl tools/model-validator-cli,tools/model-transformer-cli -am package '-DskipTests' '-Dcheckstyle.skip=true' '-Dpmd.skip=true' '-Dspotbugs.skip=true' '-Dspotless.skip=true'
```

Run structural and full CIM semantic validation, failing on warnings as well as errors:

```powershell
java -jar tools/model-validator-cli/target/model-validator-cli-0.0.1-SNAPSHOT.jar cim --repo-root . --model methodology/modeling-framework/samples/community-workshops/community-workshops.cim.xmi --fail-on-optional-violations --log-file target/community-workshops-validation.json
```

Expected exit code: `0`; output: `Violations: 0 mandatory, 0 optional.` The default structural validation remains enabled. The `--fail-on-optional-violations` option fails for any mandatory constraint or optional critique violation.

Run the sample-specific import, graph, and semantic round-trip integration test:

```powershell
mvn -q -pl packages/java/platform-modeling-infrastructure -am test '-Dtest=CommunityWorkshopsSampleImportTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dcheckstyle.skip=true' '-Dpmd.skip=true' '-Dspotbugs.skip=true' '-Dspotless.skip=true'
```

Expected: one test, zero failures, zero errors. The [test source](../../../../packages/java/platform-modeling-infrastructure/src/test/java/io/mehdieidi/modriss/platform/modeling/xmi/CommunityWorkshopsSampleImportTest.java) preserves the import compatibility check for future changes to the metamodel or editor converter.

Run the CIM-to-PIM generation smoke check:

```powershell
java -jar tools/model-transformer-cli/target/model-transformer-cli-0.0.1-SNAPSHOT.jar cim-to-pim --repo-root . --source-model methodology/modeling-framework/samples/community-workshops/community-workshops.cim.xmi --target-model target/community-workshops.draft.pim.xmi --overwrite --log-file target/community-workshops-transformation.json
```

Expected exit code: `0`; output: `ETL execution succeeded`. This writes only a disposable draft and report under `target/`; reviewed PIM/PSM models and generated application artifacts are experiment work products, not precompleted reference answers shipped with this CIM baseline.
