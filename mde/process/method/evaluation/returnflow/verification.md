# ReturnFlow evidence index and verification record

Date: 2026-09-27. Inspected baseline commit:
`1d066f44d7c4f7be32b960dbdf5800694639fcfb`; the revised working tree is not a
published release. The evaluation is a single-agent analytical review.

## Evidence index

| File                                                                       | Evidence kind and meaning                                                                                                                                              |
| -------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [14-returnflow-enactment-audit.md](../../14-returnflow-enactment-audit.md) | Constructed project, 30 grouped task-use steps, trace/design sheets, proposed test oracles, seven findings and research rationale. No actual application test results. |
| [handoff-fixture.json](handoff-fixture.json)                               | Explicitly simulated evidence and decisions used to test the research record checker. Never a real G6/G8 approval.                                                     |
| [eidi-assessment.json](eidi-assessment.json)                               | Analytical judgments for all 78 criterion rows, with source type/page, reason and unobserved-effectiveness marker; human-readable version is document 07.              |
| [check-results.json](check-results.json)                                   | Actual commands, exit codes, stdout/stderr and SHA-256 hashes of the inspected process/evaluation sources and test instrument.                                         |

## Checks performed

| Check                                                                                                                       | Observed result                                                                                                                                           | What it does not establish                                                                                                      |
| --------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| Process-definition, method-package and public-reference generation                                                          | Completed; downstream definitions, SPEM and catalogs updated. Unchanged presentation was preserved after generation.                                      | No execution of software development tasks.                                                                                     |
| `node mde/process/method/tools/verify-method-package.mjs`                                                                   | Pass: 139 tasks, 17 roles, 96 consolidated work products, five components and 20 patterns.                                                                | Native third-party SPEM import or actual role performance.                                                                      |
| `node mde/process/method/tools/verify-lifecycle-sync.mjs`                                                                   | Pass: nine gates, lifecycle links, ten PlantUML source views and publication sources/exports.                                                             | Formal reachability proof or rendered PlantUML correctness.                                                                     |
| `node mde/process/method/tools/verify-hypothetical-case.mjs`                                                                | Pass: ColdChain inventory covers 139 tasks, 17 roles, 65 executable work-product definitions and nine gates.                                              | Instantiation, review or gate validity. The additional 31 conceptual products in the consolidated package explain 96 versus 65. |
| `node mde/process/method/tools/verify-enactment-evidence.mjs`                                                               | `record-consistent`, evidence kind `simulated`.                                                                                                           | Evidence authenticity, cryptographic approval, complete process scheduling, all tailoring rules or platform enforcement.        |
| `node --test mde/process/method/tools/verify-enactment-evidence.test.mjs tests/frontend/methodology-process-utils.test.mjs` | 16 pass: one valid hypothetical handoff, 12 adverse records, Eidi type/count check and two existing process-edge tests. Raw output in check-results.json. | Actual refund behavior, provider correctness, EMF conformance or EVL semantics.                                                 |
| `npm run build` in `apps/landing`                                                                                           | TypeScript and Vite build passed.                                                                                                                         | Production deployment; no site was published.                                                                                   |
| `python -m mkdocs build -f docs/public-docs/mkdocs.yml --site-dir <temporary directory>`                                    | Documentation build passed.                                                                                                                               | External-link availability or scientific validity of cited claims.                                                              |
| Diagram skill `scripts/self_check.py` on the new handoff HTML                                                               | Pass. SVG exported using repository exporter; browser inspection found no text outside its 960×600 canvas.                                                | General semantic verification of the process.                                                                                   |
| Playwright browser inspection of landing build                                                                              | Evaluation text/link visible; no page errors; no horizontal overflow at 390px viewport. Desktop and mobile layouts inspected.                             | Practitioner usability or task-completion evidence.                                                                             |

One intermediate synchronization check failed after an existing lifecycle caption
was replaced. The caption was restored and the new responsibility/custody labels
retained; the final check passes. No linters, formatters or migrations were run.

The handoff checker intentionally requires exact revision agreement for its
fixture. It does not implement the full normative impact-based evidence-reuse
workflow, verify signatures, inspect the truth of reports, or authorize gates.
The negative cases are regression checks for the identified record contracts,
not independent validation of the methodology or a production gate service.

## Explicitly not performed

ReturnFlow XMI construction/loading, CIM→PIM and PIM→PSM ETL execution,
EGL/EGX generation, application completion, semantic EVL review, provider
sandbox/cloud deployment, cost/latency measurement, real interviews and
independent assessor agreement were not performed. Those are required before
claiming an implemented ReturnFlow system or practical effectiveness.

Assistant-generated actions remain gated only by structural Ecore/EMF
conformance through `ModelService.validateStructural(...)`. Any future EVL
evidence must come from explicit user/model validation outside assistant
apply/repair/commit paths.
