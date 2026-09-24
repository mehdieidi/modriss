# Method Traceability and Residual Gaps

## Trace model

The process design is justified through four linked layers:

`method requirement → reusable fragment → lifecycle Activity/TaskUse → MODRISS
repository asset or project evidence`.

The direction is deliberately many-to-many. A requirement such as fault
modeling needs domain, PIM, PSM, test, release, and operations work. A fragment
such as PIM refinement realizes several requirements at once.

## Requirement-to-fragment trace

| Requirement family                                                       | Primary fragments                       | Process phases        |
| ------------------------------------------------------------------------ | --------------------------------------- | --------------------- |
| Full lifecycle and gates (`MR-LC-*`)                                     | MF-01, MF-03, MF-04, MF-13–MF-18, UF-01 | 0–4                   |
| Requirements and user involvement (`MR-RE-*`)                            | MF-04–MF-06, MF-15–MF-16                | 0, 1, 3               |
| MDE boundaries, transformation, synchronization, validation (`MR-MDE-*`) | MF-05–MF-12, UF-01                      | 1 and continuous      |
| Serverless exploration (`MR-SL-01`–`04`)                                 | MF-01–MF-03                             | 0, revisited at 1/2/3 |
| Serverless modeling/design/security (`MR-SL-05`–`12`)                    | MF-05, MF-08–MF-10                      | 1                     |
| Generation, testing, deployment (`MR-SL-13`–`15`)                        | MF-11–MF-14                             | 1–2                   |
| Observability, cold start, feedback, lock-in (`MR-SL-16`–`18`)           | MF-02, MF-08, MF-10, MF-13–MF-16, MF-18 | 0–3                   |
| Planned/interrupt flow and maintenance (`MR-LC-08`–`10`, `MR-MG-08`)     | MF-16, MF-18, UF-01                     | 3 and continuous      |
| Management and scale (`MR-MG-*`)                                         | MF-03–MF-04, MF-13–MF-18, UF-01         | continuous            |
| Usability/configurability (`MR-Q-*`)                                     | MF-03, all documented components, UF-01 | method-wide           |

Detailed requirement arrays are stored on every entry in
`method-library/method-fragments.json`.

## Process-to-repository trace

| Method element                         | Implemented source/evidence                                                                                                                                       |
| -------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| CIM abstract syntax                    | `mde/metamodels/cim/cim-combined.ecore` and modular `.emf` sources                                                                                                |
| CIM semantics                          | `mde/validation/cim/cim-semantic-validation.evl` and rules                                                                                                        |
| CIM method component                   | `mde/process/process-definitions/cim.json` (26 TaskDefinitions)                                                                                                   |
| CIM coverage                           | `mde/process/coverage-matrix/cim-coverage.json`                                                                                                                   |
| CIM→PIM                                | `mde/transformations/cim-to-pim/` with trace/readiness and synchronization contract                                                                               |
| PIM abstract syntax                    | `mde/metamodels/pim/pim-combined.ecore` and modular `.emf` sources                                                                                                |
| PIM semantics                          | `mde/validation/pim/pim-semantic-validation.evl` and rules                                                                                                        |
| PIM method component                   | `mde/process/process-definitions/pim.json` (32 TaskDefinitions)                                                                                                   |
| PIM coverage                           | `mde/process/coverage-matrix/pim-coverage.json`                                                                                                                   |
| PIM→AWS PSM                            | `mde/transformations/pim-to-awspsm/` with trace/readiness and synchronization contract                                                                            |
| AWS PSM abstract syntax                | `mde/metamodels/psm/psm-combined.ecore` and modular `.emf` sources                                                                                                |
| PSM semantics                          | `mde/validation/psm/psm-semantic-validation.evl`, AWS profile, and rules                                                                                          |
| PSM method component                   | `mde/process/process-definitions/psm.json` (28 TaskDefinitions)                                                                                                   |
| PSM coverage                           | `mde/process/coverage-matrix/psm-coverage.json`                                                                                                                   |
| PSM→artifacts                          | `mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx` and EGL templates                                                                                       |
| Artifact completion/readiness          | `mde/process/process-definitions/artifact.json` (16 TaskDefinitions)                                                                                              |
| Integrated executable process          | `mde/process/process-definitions/end-to-end.json`                                                                                                                 |
| Nested lifecycle and release semantics | `mde/process/engineered-method/spem/lifecycle.puml`, `release-cycle.puml`, and generated conditional `WorkSequence` elements in `modriss-method-library.spem.xml` |
| Interrupt-driven maintenance semantics | `processEngine.maintenanceFlow` in `end-to-end.json`, WP-30/WP-31, MF-18, and `templates/operations-flow-policy.md`                                               |
| Human modeling guidance                | `docs/public-docs/docs/guides/{cim,pim,psm,end-to-end}-modeling-methodology.md`                                                                                   |
| Operations/release guidance            | `docs/devops-sre/` and public operations documentation                                                                                                            |

## Lifecycle work-product trace

| Upstream authority     | Transformation or decision   | Downstream evidence                             | Feedback rule                                                                                            |
| ---------------------- | ---------------------------- | ----------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| Product/System Charter | increment selection          | accepted scope and outcome signal               | outcome failure updates charter/backlog, not only implementation                                         |
| Requirements and CIM   | CIM→PIM ETL                  | traceable PIM draft and report                  | domain/requirement mismatch returns to CIM                                                               |
| Accepted PIM           | PIM→PSM ETL                  | traceable AWS PSM draft and report              | provider constraint may revise PIM decision or provider choice                                           |
| Accepted PSM           | EGX/EGL                      | generated baseline, manifest, artifact trace    | structural realization defect returns to PSM/generator                                                   |
| Generated baseline     | implementation/test          | candidate and verification record               | reusable scaffold defect returns to generator; business logic stays in extension code                    |
| Qualified candidate    | promotion                    | deployment and handover record                  | release finding returns to earliest affected model/code/release control                                  |
| Running service        | telemetry/incidents/outcomes | WP-30 item, WP-31 board, evidence/change record | pull within WIP; finish operations-only, traverse bounded MDE/release path, or commit to planned release |
| Retirement decision    | migration/decommission       | closure record                                  | unresolved consumer/data/access/resource blocks closure                                                  |

## Assistant-validation boundary trace

The MODRISS process design distinguishes two workflows:

- **assistant apply/repair/commit:** structural Ecore/EMF conformance only,
  through `ModelService.validateStructural(...)`; and
- **explicit model validation:** semantic EVL validation initiated as a
  user/model-validation workflow outside assistant application paths.

No methodology gate, document, or SPEM task may imply that assistant output was
semantically approved merely because it was structurally accepted.

## Residual gaps and research backlog

| Gap                                                     | Effect on claims                                                                                                                             | Proposed evidence/work                                                                                                                              |
| ------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| No empirical enactment reported by this package         | Effectiveness, effort, usability, and scalability are not proven.                                                                            | Conduct at least two contrasting case studies and practitioner review.                                                                              |
| AWS-only PSM and generator                              | Portability applies strongly to CIM/PIM intent, not to realized infrastructure behavior.                                                     | Add another provider PSM/transformation/generator and compare semantic deltas.                                                                      |
| No general reverse transformation                       | Round-trip engineering is partial despite robust forward three-way reconciliation.                                                           | Define authoritative reverse mappings or explicitly bounded bidirectional synchronization.                                                          |
| Cost analysis not generated quantitatively from models  | Cost criterion has process support but partial automation.                                                                                   | Add workload/cost profile and provider pricing estimator with uncertainty ranges.                                                                   |
| Deployment-strategy automation is incomplete            | Canary/blue-green are method requirements but not uniformly generated.                                                                       | Add PSM concepts/templates and automated policy checks.                                                                                             |
| Cold-start and resilience tests vary by project         | Serverless-specific test support is not uniformly executable.                                                                                | Provide reusable experiment/test profiles and thresholds.                                                                                           |
| Process-run persistence/UI incomplete                   | Project management evidence relies on external tracker integration.                                                                          | Implement process-run, dependency, gate, exception, and release ledger services.                                                                    |
| SPEM exchange is not certified against vendor importers | Standard alignment must not be reported as certified native interchange.                                                                     | Validate generated logical XMI against normative CMOF and selected tools; publish fidelity report.                                                  |
| Domain coverage inferred from metamodel and samples     | Metamodel adequacy across domains remains an external-validity question.                                                                     | Model diverse cases and record unsupported concepts/workarounds.                                                                                    |
| One stable role ID has divergent display names          | `role.security-engineer` is “Security Engineer” in the integrated process and “Security & Privacy Engineer” in at least one child component. | Keep the integrated name canonical, record variants in the consolidated index, and align source specifications in a later executable-method change. |

## Thesis claim boundary

The defensible current claim is: **MODRISS provides a systematically engineered,
criteria-evaluated, SPEM-structured method design aligned with an implemented
three-level serverless MDE framework.** The evidence supports completeness and
traceability of the design and concrete realization of the core modeling chain.
It does not yet establish that the process is more effective than alternatives;
that requires empirical study.
