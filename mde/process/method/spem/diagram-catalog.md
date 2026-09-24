# MODRISS Process Diagram Catalog

## Purpose and authority

The diagrams form a hierarchy of views over one process definition. They are
not independent versions of the development process. The executable process source in
`../../definitions/end-to-end.json`, the reusable method-content
catalog, and the generated SPEM-logical XML are authoritative for identities,
roles, work products, dependencies, and conditions. PlantUML views explain
control flow at different levels of detail. Publication SVGs simplify the same
semantics for a thesis or paper.

## Diagram hierarchy

| Level | Diagram                                | Question answered                                                                                             |
| ----- | -------------------------------------- | ------------------------------------------------------------------------------------------------------------- |
| L0    | `views/lifecycle.puml`                 | How does the complete product/service lifecycle proceed, repeat releases, and eventually retire?              |
| L1    | `views/release-cycle.puml`             | What happens from a release hypothesis through G6, promotion, G7, operation, and the next release?            |
| L1    | `views/operations-flow.puml`           | How is unpredictable production demand classified, pulled, verified, and disposed without fictional planning? |
| L1    | `views/change-routing.puml`            | Where does operational evidence re-enter the authoritative engineering chain?                                 |
| L2    | `views/model-driven-increment.puml`    | How does one bounded Phase 1 increment cross CIM, PIM, PSM, generation, implementation, verification, and G5? |
| L3    | `views/cim-process.activity.puml`      | How is the CIM work performed?                                                                                |
| L3    | `views/pim-process.activity.puml`      | How is the PIM work performed?                                                                                |
| L3    | `views/psm-process.activity.puml`      | How is the PSM work performed?                                                                                |
| L3    | `views/artifact-process.activity.puml` | How are generated artifacts completed and made ready?                                                         |

The source-level end-to-end view is
`views/end-to-end-process.activity.puml`. It uses stable process IDs and
should remain semantically equivalent to `views/lifecycle.puml`.

## Release semantics

Two coordinated processes and their nested cadences are deliberately separated:

1. Development and Delivery has three one-time sequential phases, from Phase 0
   through the active-product Phase 1 to an authorized retirement Phase 2.
2. The release cycle is an Iteration of construction, qualification,
   promotion, handover, and review Activities inside Phase 1; no phase repeats.
3. The increment cycle repeats inside Phase 1 until the candidate has the
   accepted scope for a release.
4. The ongoing, event-driven Operations and Maintenance Process continuously captures and pulls unpredictable
   production demand under WIP and service-level policies while the accepted
   baseline runs and planned delivery may proceed.

G7 accepts an operating baseline; it does not end the lifecycle. A later
release begins only after explicit selection, by forming a new hypothesis and
returning to Phase 1 increment planning, normally
while the accepted release continues to serve users. A rejected candidate or
failed promotion also returns to Phase 1 for correction at the earliest
authoritative source. Operations-only items loop within the operations process
and do not create releases. Phase 2 and Operations and Maintenance perform
different retirement work and contribute separate evidence to the shared G8
closure criteria; no activity-flow edge joins the two processes.

## Why there is not one diagram per task

Every atomic task is already defined as a reusable SPEM `TaskDefinition` and
used through a process-specific `TaskUse`, with roles, inputs, outputs, steps,
and guidance in the method library. A separate activity diagram for each task
would duplicate that content and create a large synchronization burden.
MODRISS adds a child diagram when a scope has meaningful branching, iteration,
handoffs, or re-entry that is difficult to understand from the task definition
alone. Task steps that remain linear are read from the method-content index or
SPEM representation.

## Synchronization rules

- Change lifecycle behavior first in the process-definition JSON.
- Rebuild the method package so the method-content index, inventory, and SPEM
  XML are generated from the same sources.
- Update the L0–L3 PlantUML view affected by the change.
- Update publication HTML and re-export its SVG when the manuscript view is
  affected.
- Keep validation terminology precise: assistant-generated model changes are
  gated only by structural Ecore/EMF conformance through
  `ModelService.validateStructural(...)`; EVL semantic validation belongs to a
  separate explicit user/model validation workflow.
- Run `node ../tools/verify-method-package.mjs` and
  `node ../tools/verify-lifecycle-sync.mjs` before treating the artifacts as a
  synchronized baseline.
