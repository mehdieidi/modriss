# SPEM 2.0 Method Content — Modless MDE Methodologies

Formal method content for CIM, PIM, AWS PSM, and end-to-end modeling aligned with [SPEM 2.0](https://www.omg.org/spec/SPEM/2.0) and executable via `mde/methodology/process-definitions/*.json`.

## Team Profiles (Roles)

| Role ID                   | Name                    | Primary levels | Responsibilities                                                |
| ------------------------- | ----------------------- | -------------- | --------------------------------------------------------------- |
| `business-modeler`        | Business Modeler        | CIM            | Intent, domain, behavior, process, transformation contracts     |
| `requirements-engineer`   | Requirements Engineer   | CIM (phase 11) | Requirements, acceptance criteria, governance constraints       |
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

- **GQM (Basili):** Phase 1 anchors measurable intent before structural modeling.
- **DDD (Evans):** Ubiquitous language (phase 4) precedes entities; bounded contexts synthesized after behavior (phase 10).
- **Event Storming (Brandolini):** Phase 7 orders commands, queries, events on the behavior surface.
- **Twin Peaks:** Requirements at phase 11 backfill traceability to concrete modeled elements.
- **MDA layering:** CIM → PIM → PSM with human-in-the-loop EVL gates (ADR 0001, ADR 0002).

## Metrics

| Metric            | Definition                                              | Target                  |
| ----------------- | ------------------------------------------------------- | ----------------------- |
| Concept coverage  | % EClasses/enums assigned to ≥1 task                    | 100% (CI enforced)      |
| Phase completion  | Tasks marked complete in guided panel                   | All before transform    |
| EVL pass rate     | Validation with zero blocking findings                  | Required at each gate   |
| Readiness closure | ProductionReadinessAssessment without blocking findings | Required before ETL/M2T |

## Task Catalog

Executable task definitions with work products, entry/exit criteria, and EVL rule crosswalks live in:

- `process-definitions/cim.json` — 14 phases, 122 concepts
- `process-definitions/pim.json` — 12 phases, 179 concepts
- `process-definitions/psm.json` — 11 phases, 297 concepts
- `process-definitions/end-to-end.json` — lifecycle with transform milestones

UML activity diagrams in this directory express phase flow and decision gates per level.
