# SPEM 2.0 Method Content: MODRISS Agile MDE Methodologies

Formal method content for CIM, PIM, AWS PSM, and end-to-end modeling aligned with
[SPEM 2.0](https://www.omg.org/spec/SPEM/2.0) and executable via
`mde/methodology/process-definitions/*.json`.

## Process Engine (Agile Kernel)

The **process engine** is the iterative part of each methodology, the repeating cycle that runs
through phases/stages/tasks and produces accepted model evidence per revolution. It is not separate
metadata; it _is_ the iteration.

Each level-specific process embeds `processEngine`:

| Level      | Engine ID                   | Delivers per cycle                              |
| ---------- | --------------------------- | ----------------------------------------------- |
| CIM        | `modriss.cim.engine`        | One capability / bounded-context slice          |
| PIM        | `modriss.pim.engine`        | One serverless service slice                    |
| PSM        | `modriss.psm.engine`        | One deployable AWS slice                        |
| End-to-end | `modriss.end-to-end.engine` | Full CIM → PIM → PSM → artifacts vertical slice |

### Engine structure

```json
{
  "processEngine": {
    "id": "modriss.cim.engine",
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
├── progressModel        (states, events, metrics, and evidence fields)
├── governance            (entry/exit evidence and gate semantics)
└── processEngine        (iterative revolution through in-engine phases)
└── phases[]             (sequential lifecycle divisions)
    └── stages[]         (activities within a phase)
        └── subStages[]? (optional decomposition)
            └── tasks[]  (atomic work units → artifacts + metamodel elements)
```

- **Phases** run in order; each has entry/exit criteria. Only phases marked `inEngine` are repeated
  by the local engine; the end-to-end process wraps that engine with initiation, release, operation,
  and retirement phases.
- **Stages** group related tasks; sub-stages allow finer decomposition (e.g. Information Architecture
  → taxonomy vs. items).
- **Tasks** are atomic: they name steps, produce **artifacts**, bind **workProducts** to metamodel
  EClasses, and declare **paletteFocus** for the canvas.
- **Roles** are assigned at phase, stage, and task level via `primaryRole`.
- **Guidelines** cite established practice (GQM, DDD, Twin Peaks, etc.) and apply to the whole process or a phase id.

The **process engine** is not a separate methodology; it is the repeating cycle that revolves
through `inEngine` phases until a slice is reviewed and accepted. The full lifecycle adds release,
operations, change propagation, and retirement around the engine.

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

The end-to-end method is a full-lifecycle orchestration process with five phases:

| Phase     | Name                                        | Purpose                                                                                       |
| --------- | ------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `e2e.ph0` | Initiate, Tailor & Organize                 | Product/system intent, situational method profile, team topology, and cross-cutting baselines |
| `e2e.ph1` | Iterative-Incremental Model-Driven Delivery | The eight-stage vertical CIM → PIM → PSM → artifact-readiness engine cycle                    |
| `e2e.ph2` | Release & Transition                        | Release assembly, progressive promotion, rollback, and handover                               |
| `e2e.ph3` | Operate, Evolve & Learn                     | SLOs, incidents, change propagation, and product/method learning                              |
| `e2e.ph4` | Retire, Migrate & Close                     | Retirement, data disposition, decommissioning, and closure                                    |

The engine cycle repeats `e2e.p0`–`e2e.p7` for each capability slice. Release and
operations are lifecycle phases around the cycle, not hidden tasks after M2T.

## Team Profiles (Roles)

| Role ID                   | Name                    | Primary levels | Responsibilities                                                |
| ------------------------- | ----------------------- | -------------- | --------------------------------------------------------------- |
| `business-modeler`        | Business Modeler        | CIM            | Intent, domain, behavior, process, transformation contracts     |
| `requirements-engineer`   | Requirements Engineer   | CIM (converge) | Requirements, acceptance criteria, governance constraints       |
| `solution-architect`      | Solution Architect      | PIM            | Service boundaries, contracts, integration, policies, readiness |
| `cloud-platform-engineer` | Cloud Platform Engineer | PSM            | AWS resources, IAM, networking, observability, deployment       |
| `process-reviewer`        | Process Reviewer        | All            | EVL gate approval, readiness assessment sign-off                |
| `method-engineer`         | Method Engineer         | Meta           | Maintains methodology when metamodels change                    |

The full-lifecycle process additionally makes product owner, delivery lead, domain expert,
quality engineer, security engineer, release engineer, and service owner responsibilities
explicit for cross-team coordination and release/operations work.

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
- **Progress contract:** A versioned process run records state, evidence, blockers, decisions,
  dependencies, and gate timestamps; task completion alone is not acceptance.
- **Validation boundary:** Assistant apply/commit paths use structural Ecore/EMF conformance only;
  semantic EVL validation remains an explicit user/model validation workflow.

## Change Management

Outside the normal engine cycle, `changeManagement.workflows` cover scope expansion, element
add/modify/remove, and post-transform propagation.

## Metrics

| Metric                       | Definition                                                 | Target                         |
| ---------------------------- | ---------------------------------------------------------- | ------------------------------ |
| Concept coverage             | % EClasses/enums assigned to ≥1 task                       | 100% (CI enforced)             |
| Engine cycles                | Completed frame→review/adapt revolutions per program       | ≥1 per capability slice        |
| Stage completion             | All in-engine stages complete for current cycle            | Before review gate             |
| Semantic validation evidence | Explicit user/model validation with zero blocking findings | Required at each semantic gate |
| Readiness closure            | ProductionReadinessAssessment without blocking findings    | Required before ETL/M2T        |

## Task Catalog

- `process-definitions/cim.json`, 5 phases, 26 tasks, 123 concepts
- `process-definitions/pim.json`, 6 phases, 32 tasks, 188 concepts
- `process-definitions/psm.json`, 6 phases, 28 tasks, 305 concepts
- `process-definitions/end-to-end.json`, 5 lifecycle phases, 30 tasks, 8-stage increment engine

Regenerate public guide task catalogs and phase narratives:

```bash
node mde/methodology/tools/build-process-definitions.mjs
node mde/methodology/tools/generate-methodology-guides.mjs
node mde/methodology/tools/generate-methodology-narratives.mjs
```
