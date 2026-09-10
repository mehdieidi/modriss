# SPEM 2.0 Method Content: Varka Agile MDE Methodologies

Formal method content for CIM, PIM, AWS PSM, and end-to-end modeling aligned with
[SPEM 2.0](https://www.omg.org/spec/SPEM/2.0) and executable via
`mde/methodology/process-definitions/*.json`.

## Process Engine (Agile Kernel)

The **process engine** is the iterative part of each methodology, the repeating cycle that runs
through phases/stages/tasks and produces accepted model evidence per revolution. It is not separate
metadata; it _is_ the iteration.

Each level-specific process embeds `processEngine`:

| Level      | Engine ID                 | Delivers per cycle                              |
| ---------- | ------------------------- | ----------------------------------------------- |
| CIM        | `varka.cim.engine`        | One capability / bounded-context slice          |
| PIM        | `varka.pim.engine`        | One serverless service slice                    |
| PSM        | `varka.psm.engine`        | One deployable AWS slice                        |
| End-to-end | `varka.end-to-end.engine` | Full CIM → PIM → PSM → artifacts vertical slice |

### Engine structure

```json
{
  "processEngine": {
    "id": "varka.cim.engine",
    "incrementUnit": "capability-slice",
    "deliverable": { "name": "...", "description": "..." },
    "cycle": [ { "id": "...", "type": "phase", "phaseIds": ["..."] } ],
    "loop": { "fromStepId": "...", "toStepId": "...", "condition": "...", "guidance": "..." },
    "reworkLoops": [ ... ]
  }
}
```

- **`cycle`**, phase steps executed each revolution; framing/setup phases are part of the cycle and
  contain first-cycle bootstrap tasks where needed.
- **`loop`**, engine revolution: after the phase-embedded review/adapt stage, return to framing when
  backlog remains.
- **`reworkLoops`**, internal phase-level rework paths _inside_ a cycle step (Twin Peaks, contract gaps, etc.).

Static method content (roles, stages, phases, tasks) is the executable body of the engine; the engine
repeats by revisiting the phase hierarchy for the next slice.

## SPEM Process Hierarchy

Each methodology is a **Process** with this structure (SPEM 2.0):

```text
Process
├── roles[]              (who performs work)
├── artifactKinds[]      (work product types / deliverables)
├── guidelines[]         (method guidance bound to phases or process)
├── processEngine        (iterative revolution through in-engine phases)
└── phases[]             (sequential lifecycle divisions)
    └── stages[]         (activities within a phase)
        └── subStages[]? (optional decomposition)
            └── tasks[]  (atomic work units → artifacts + metamodel elements)
```

- **Phases** run in order; each has entry/exit criteria and is repeated as part of the engine cycle
  for the current slice.
- **Stages** group related tasks; sub-stages allow finer decomposition (e.g. Information Architecture
  → taxonomy vs. items).
- **Tasks** are atomic: they name steps, produce **artifacts**, bind **workProducts** to metamodel
  EClasses, and declare **paletteFocus** for the canvas.
- **Roles** are assigned at phase, stage, and task level via `primaryRole`.
- **Guidelines** cite established practice (GQM, DDD, Twin Peaks, etc.) and apply to the whole process or a phase id.

The **process engine** is not a separate methodology, it is the repeating cycle that revolves
through `inEngine` phases until a slice is reviewed and accepted.

## CIM Process (5 phases)

| Phase     | Name                    | In engine | Stages (summary)                                                               |
| --------- | ----------------------- | --------- | ------------------------------------------------------------------------------ |
| `cim.ph1` | Increment Framing       | ✓         | Program Charter, Slice Planning, Strategic Intent                              |
| `cim.ph2` | Context Discovery       | ✓         | Participation, Capabilities, Ubiquitous Language                               |
| `cim.ph3` | Domain Exploration      | ✓         | Information Architecture, Structural Model, Behavior Surface (with sub-stages) |
| `cim.ph4` | Domain Synthesis        | ✓         | Aggregates, Process Flows, Bounded Contexts                                    |
| `cim.ph5` | Convergence & Readiness | ✓         | Requirements (Twin Peaks), Governance, EVL gate                                |

## PIM Process (6 phases)

| Phase     | Name                         | In engine | Stages (summary)                                                 |
| --------- | ---------------------------- | --------- | ---------------------------------------------------------------- |
| `pim.ph1` | Architecture & Slice Framing | ✓         | Service Slice Planning, Architecture Posture, Service Boundaries |
| `pim.ph2` | Contracts & Data             | ✓         | Contracts & Schemas, Data Architecture                           |
| `pim.ph3` | Compute & Exposure           | ✓         | Compute Units, API Surface                                       |
| `pim.ph4` | Integration & Orchestration  | ✓         | Integration Topology, Workflow Orchestration                     |
| `pim.ph5` | Assurance & Configuration    | ✓         | Security, Policies, External & Config                            |
| `pim.ph6` | Platform Readiness           | ✓         | Platform Mapping, Trace, Readiness, Increment Review             |

## PSM Process (6 phases)

| Phase     | Name                          | In engine | Stages (summary)                                                     |
| --------- | ----------------------------- | --------- | -------------------------------------------------------------------- |
| `psm.ph1` | Deployment & Slice Framing    | ✓         | Deployable Slice Planning, Account & Stage, Stack, Security Baseline |
| `psm.ph2` | Network & Identity            | ✓         | Networking, Cognito                                                  |
| `psm.ph3` | Storage & Messaging           | ✓         | DynamoDB/S3, SQS/SNS                                                 |
| `psm.ph4` | Event Fabric & Compute        | ✓         | EventBridge, Lambda                                                  |
| `psm.ph5` | API & Orchestration           | ✓         | API Gateway, Step Functions, CloudWatch                              |
| `psm.ph6` | Integration Views & Readiness | ✓         | Integration views, EVL, Increment Review                             |

## End-to-End Process

Single phase (`e2e.ph1`) whose stages mirror the vertical slice: increment planning → CIM →
CIM→PIM → PIM → PIM→PSM → PSM → M2T → artifacts. Milestones (`e2e.m0`–`e2e.m4`) mark gate
outcomes across the slice.

## Team Profiles (Roles)

| Role ID                   | Name                    | Primary levels | Responsibilities                                                |
| ------------------------- | ----------------------- | -------------- | --------------------------------------------------------------- |
| `business-modeler`        | Business Modeler        | CIM            | Intent, domain, behavior, process, transformation contracts     |
| `requirements-engineer`   | Requirements Engineer   | CIM (converge) | Requirements, acceptance criteria, governance constraints       |
| `solution-architect`      | Solution Architect      | PIM            | Service boundaries, contracts, integration, policies, readiness |
| `cloud-platform-engineer` | Cloud Platform Engineer | PSM            | AWS resources, IAM, networking, observability, deployment       |
| `process-reviewer`        | Process Reviewer        | All            | EVL gate approval, readiness assessment sign-off                |
| `method-engineer`         | Method Engineer         | Meta           | Maintains methodology when metamodels change                    |

## Work Product Kinds

| Work product                  | Metamodel | Description                                                |
| ----------------------------- | --------- | ---------------------------------------------------------- |
| Domain model elements         | CIM       | Goals, actors, capabilities, entities, behavior, processes |
| Serverless architecture model | PIM       | Services, functions, APIs, stores, workflows, policies     |
| AWS deployment model          | PSM       | SAM stacks, Lambda, API Gateway, data, messaging, IAM      |
| Trace & readiness artifacts   | Kernel    | TraceModel, ProductionReadinessAssessment across levels    |
| Generated artifacts           | M2T       | SAM/CFN, handlers, OpenAPI, ASL, CI scripts                |

## Guidance

- **GQM (Basili):** Strategic Intent anchors measurable goals before structural modeling.
- **DDD (Evans):** Ubiquitous language precedes entities; bounded contexts synthesized after behavior.
- **Event Storming (Brandolini):** Behavior Surface orders commands, queries, events.
- **Twin Peaks (Nuseibeh):** Engine rework loops alternate requirements and structure until stable.
- **Agile increments:** Engine cycle advances thin vertical slices; loop revolves while backlog remains.
- **MDA layering:** CIM → PIM → PSM with human-in-the-loop EVL gates.

## Change Management

Outside the normal engine cycle, `changeManagement.workflows` cover scope expansion, element
add/modify/remove, and post-transform propagation.

## Metrics

| Metric            | Definition                                              | Target                  |
| ----------------- | ------------------------------------------------------- | ----------------------- |
| Concept coverage  | % EClasses/enums assigned to ≥1 task                    | 100% (CI enforced)      |
| Engine cycles     | Completed frame→review/adapt revolutions per program    | ≥1 per capability slice |
| Stage completion  | All in-engine stages complete for current cycle         | Before review gate      |
| EVL pass rate     | Validation with zero blocking findings                  | Required at each gate   |
| Readiness closure | ProductionReadinessAssessment without blocking findings | Required before ETL/M2T |

## Task Catalog

- `process-definitions/cim.json`, 5 phases, 25 tasks, 122 concepts
- `process-definitions/pim.json`, 6 phases, 31 tasks, 179 concepts
- `process-definitions/psm.json`, 6 phases, 27 tasks, 297 concepts
- `process-definitions/end-to-end.json`, 1 phase, 8 stages, top-level engine

Regenerate public guide task catalogs and phase narratives:

```bash
node mde/methodology/tools/build-process-definitions.mjs
node mde/methodology/tools/generate-methodology-guides.mjs
node mde/methodology/tools/generate-methodology-narratives.mjs
```
