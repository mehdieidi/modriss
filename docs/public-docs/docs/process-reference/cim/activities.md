# CIM modeling: phases and activities

This page documents the **SPEM process-structure Activity, Phase, and TaskUse** elements used by the CIM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A phase establishes a significant lifecycle period and normally ends at a major checkpoint. An activity groups related work within a phase or process component. A TaskUse places reusable task guidance into that process context. Entry and exit conditions describe evidence states; they are not calendar dates.

## Summary

| Phase or activity             | SPEM type | Contained by             | Task uses | Execution        |
| ----------------------------- | --------- | ------------------------ | --------: | ---------------- |
| Increment Framing             | Activity  | Process                  |         0 | defined sequence |
| Program Charter               | Activity  | Increment Framing        |         2 | defined sequence |
| Capability Slice Planning     | Activity  | Increment Framing        |         1 | defined sequence |
| Strategic Framing             | Activity  | Increment Framing        |         2 | defined sequence |
| Context Discovery             | Activity  | Process                  |         0 | defined sequence |
| Participation Model           | Activity  | Context Discovery        |         2 | defined sequence |
| Capability Topology           | Activity  | Context Discovery        |         2 | defined sequence |
| Ubiquitous Language           | Activity  | Context Discovery        |         1 | defined sequence |
| Domain Exploration            | Activity  | Process                  |         0 | defined sequence |
| Information Architecture      | Activity  | Domain Exploration       |         0 | defined sequence |
| Data Classification           | Activity  | Information Architecture |         1 | defined sequence |
| Information Items             | Activity  | Information Architecture |         1 | defined sequence |
| Structural Domain Model       | Activity  | Domain Exploration       |         0 | defined sequence |
| Entities & Lifecycle          | Activity  | Structural Domain Model  |         1 | defined sequence |
| Value Objects & Relationships | Activity  | Structural Domain Model  |         1 | defined sequence |
| Behavior Surface              | Activity  | Domain Exploration       |         0 | defined sequence |
| Commands                      | Activity  | Behavior Surface         |         1 | defined sequence |
| Queries                       | Activity  | Behavior Surface         |         1 | defined sequence |
| Events & Guards               | Activity  | Behavior Surface         |         1 | defined sequence |
| Domain Synthesis              | Activity  | Process                  |         0 | defined sequence |
| Aggregate Boundaries          | Activity  | Domain Synthesis         |         1 | defined sequence |
| Process Orchestration         | Activity  | Domain Synthesis         |         0 | defined sequence |
| Business Processes            | Activity  | Process Orchestration    |         1 | defined sequence |
| Policies & Decisions          | Activity  | Process Orchestration    |         1 | defined sequence |
| Bounded Context Synthesis     | Activity  | Domain Synthesis         |         1 | defined sequence |
| Convergence & Readiness       | Activity  | Process                  |         0 | defined sequence |
| Requirements Engineering      | Activity  | Convergence & Readiness  |         2 | defined sequence |
| Transformation Contracts      | Activity  | Convergence & Readiness  |         1 | defined sequence |
| Traceability & Readiness Gate | Activity  | Convergence & Readiness  |         1 | defined sequence |
| Increment Review & Adapt      | Activity  | Convergence & Readiness  |         1 | defined sequence |

## Detailed activities

## Increment Framing

<small>Activity · MODRISS::Stage: `cim.ph1`</small>

Frame the current capability slice, establish or refresh the CIM program container, and anchor modeling in measurable intent.

**Entry conditions**

- MODRISS project created or prior CIM increment selected for evolution

**Exit conditions**

- CIMModel root exists
- Capability-slice objective and definition of done are agreed
- At least one BusinessGoal with KPI traces to the slice

**Participating roles**

- Business Modeler

### Program Charter

<small>Activity · MODRISS::SubStage: `cim.ph1.st1` · contained by **Increment Framing**</small>

Create the CIMModel root and modeling conventions on the first cycle; refresh them when the program scope changes.

**Participating roles**

- Business Modeler
- Method Engineer

**Task uses in this activity**

- **Create CIM model root** (TaskUse `cim.ph1.st1.t1`)
- **Establish shared model contract and evidence conventions** (TaskUse `cim.ph1.st1.t2`)

### Capability Slice Planning

<small>Activity · MODRISS::SubStage: `cim.ph1.st2` · contained by **Increment Framing**</small>

Select the smallest valuable capability slice and define the cycle-level definition of done.

**Participating roles**

- Business Modeler

**Task uses in this activity**

- **Plan capability slice** (TaskUse `cim.ph1.st2.t1`)

### Strategic Framing

<small>Activity · MODRISS::SubStage: `cim.ph1.st3` · contained by **Increment Framing**</small>

Capture measurable business intent for the selected slice using GQM before domain modeling.

**Participating roles**

- Business Modeler

**Task uses in this activity**

- **Define business goals and KPIs** (TaskUse `cim.ph1.st3.t1`)
- **Identify stakeholders** (TaskUse `cim.ph1.st3.t2`)

## Context Discovery

<small>Activity · MODRISS::Stage: `cim.ph2`</small>

Map who participates in the domain, what the organization can do, and shared vocabulary.

**Entry conditions**

- Increment Framing complete

**Exit conditions**

- Actors, capabilities, and glossary cover the increment slice

**Participating roles**

- Business Modeler

### Participation Model

<small>Activity · MODRISS::SubStage: `cim.ph2.st1` · contained by **Context Discovery**</small>

Model human/system actors, roles, and boundary external systems.

**Participating roles**

- Business Modeler

**Task uses in this activity**

- **Model actors and roles** (TaskUse `cim.ph2.st1.t1`)
- **Register external systems** (TaskUse `cim.ph2.st1.t2`)

### Capability Topology

<small>Activity · MODRISS::SubStage: `cim.ph2.st2` · contained by **Context Discovery**</small>

Map business capabilities to goals and record dependencies.

**Participating roles**

- Business Modeler

**Task uses in this activity**

- **Map business capabilities** (TaskUse `cim.ph2.st2.t1`)
- **Record capability dependencies** (TaskUse `cim.ph2.st2.t2`)

### Ubiquitous Language

<small>Activity · MODRISS::SubStage: `cim.ph2.st3` · contained by **Context Discovery**</small>

Build shared glossary before structural modeling (DDD).

**Participating roles**

- Business Modeler

**Task uses in this activity**

- **Define domain glossary** (TaskUse `cim.ph2.st3.t1`)

## Domain Exploration

<small>Activity · MODRISS::Stage: `cim.ph3`</small>

Explore information, structure, and behavior using Twin Peaks, iterate until CQRS surface is coherent.

**Entry conditions**

- Context Discovery complete for slice

**Exit conditions**

- Commands, queries, and events cover primary use cases

**Participating roles**

- Business Modeler

### Information Architecture

<small>Activity · MODRISS::SubStage: `cim.ph3.st1` · contained by **Domain Exploration**</small>

Classify data and name information items before entities (CIM-ENTITY-001).

**Participating roles**

- Business Modeler

#### Data Classification

<small>Activity · MODRISS::Stage: `cim.ph3.st1.ss1` · contained by **Information Architecture**</small>

Define confidentiality and handling classifications.

**Participating roles**

- Business Modeler

**Task uses in this activity**

- **Define data classifications** (TaskUse `cim.ph3.st1.ss1.t1`)

#### Information Items

<small>Activity · MODRISS::Stage: `cim.ph3.st1.ss2` · contained by **Information Architecture**</small>

Name and type the facts the domain cares about.

**Participating roles**

- Business Modeler

**Task uses in this activity**

- **Create information items** (TaskUse `cim.ph3.st1.ss2.t1`)

### Structural Domain Model

<small>Activity · MODRISS::SubStage: `cim.ph3.st2` · contained by **Domain Exploration**</small>

Model entities, value objects, relationships, lifecycle, and invariants.

**Participating roles**

- Business Modeler

#### Entities & Lifecycle

<small>Activity · MODRISS::Stage: `cim.ph3.st2.ss1` · contained by **Structural Domain Model**</small>

Model stateful domain entities with identity from information items.

**Participating roles**

- Business Modeler

**Task uses in this activity**

- **Model domain entities** (TaskUse `cim.ph3.st2.ss1.t1`)

#### Value Objects & Relationships

<small>Activity · MODRISS::Stage: `cim.ph3.st2.ss2` · contained by **Structural Domain Model**</small>

Model descriptive types and associations between domain concepts.

**Participating roles**

- Business Modeler

**Task uses in this activity**

- **Model value objects and relationships** (TaskUse `cim.ph3.st2.ss2.t1`)

### Behavior Surface

<small>Activity · MODRISS::SubStage: `cim.ph3.st3` · contained by **Domain Exploration**</small>

CQRS and event storming, commands, queries, events linked to actors and capabilities.

**Participating roles**

- Business Modeler

#### Commands

<small>Activity · MODRISS::Stage: `cim.ph3.st3.ss1` · contained by **Behavior Surface**</small>

Model state-changing operations with outcomes and preconditions.

**Participating roles**

- Business Modeler

**Task uses in this activity**

- **Model commands and outcomes** (TaskUse `cim.ph3.st3.ss1.t1`)

#### Queries

<small>Activity · MODRISS::Stage: `cim.ph3.st3.ss2` · contained by **Behavior Surface**</small>

Model read operations with freshness needs.

**Participating roles**

- Business Modeler

**Task uses in this activity**

- **Model queries** (TaskUse `cim.ph3.st3.ss2.t1`)

#### Events & Guards

<small>Activity · MODRISS::Stage: `cim.ph3.st3.ss3` · contained by **Behavior Surface**</small>

Model domain events, business errors, and guard conditions.

**Participating roles**

- Business Modeler

**Task uses in this activity**

- **Model events, errors, and conditions** (TaskUse `cim.ph3.st3.ss3.t1`)

## Domain Synthesis

<small>Activity · MODRISS::Stage: `cim.ph4`</small>

Synthesize transactional boundaries, orchestration, and bounded contexts from explored domain.

**Entry conditions**

- Domain Exploration coherent for slice

**Exit conditions**

- Bounded contexts assigned with memberships

**Participating roles**

- Business Modeler

### Aggregate Boundaries

<small>Activity · MODRISS::SubStage: `cim.ph4.st1` · contained by **Domain Synthesis**</small>

Group entities into consistency boundaries with command/event ownership.

**Participating roles**

- Business Modeler

**Task uses in this activity**

- **Define aggregate candidates** (TaskUse `cim.ph4.st1.t1`)

### Process Orchestration

<small>Activity · MODRISS::SubStage: `cim.ph4.st2` · contained by **Domain Synthesis**</small>

Model long-running business flows, policies, and decisions.

**Participating roles**

- Business Modeler

#### Business Processes

<small>Activity · MODRISS::Stage: `cim.ph4.st2.ss1` · contained by **Process Orchestration**</small>

Orchestrate commands, events, and human steps into end-to-end flows.

**Participating roles**

- Business Modeler

**Task uses in this activity**

- **Model business processes** (TaskUse `cim.ph4.st2.ss1.t1`)

#### Policies & Decisions

<small>Activity · MODRISS::Stage: `cim.ph4.st2.ss2` · contained by **Process Orchestration**</small>

Encode business rules and decision tables.

**Participating roles**

- Business Modeler

**Task uses in this activity**

- **Define policies and decision tables** (TaskUse `cim.ph4.st2.ss2.t1`)

### Bounded Context Synthesis

<small>Activity · MODRISS::SubStage: `cim.ph4.st3` · contained by **Domain Synthesis**</small>

Assign capabilities, domain, behavior, and policies into cohesive contexts.

**Participating roles**

- Business Modeler

**Task uses in this activity**

- **Synthesize bounded contexts** (TaskUse `cim.ph4.st3.t1`)

## Convergence & Readiness

<small>Activity · MODRISS::Stage: `cim.ph5`</small>

Backfill requirements, record transformation contracts, close traceability and EVL gate.

**Entry conditions**

- Domain Synthesis complete for slice

**Exit conditions**

- CIM EVL passes
- Readiness gate approved
- CIM increment reviewed and improvement actions captured

**Participating roles**

- Requirements Engineer

### Requirements Engineering

<small>Activity · MODRISS::SubStage: `cim.ph5.st1` · contained by **Convergence & Readiness**</small>

Twin Peaks backfill, formalize requirements traced to modeled elements.

**Participating roles**

- Requirements Engineer

**Task uses in this activity**

- **Formalize requirements** (TaskUse `cim.ph5.st1.t1`)
- **Apply governance constraints** (TaskUse `cim.ph5.st1.t2`)

### Transformation Contracts

<small>Activity · MODRISS::SubStage: `cim.ph5.st2` · contained by **Convergence & Readiness**</small>

Document risks, assumptions, and CIM→PIM transformation profile.

**Participating roles**

- Business Modeler

**Task uses in this activity**

- **Record transformation metadata** (TaskUse `cim.ph5.st2.t1`)

### Traceability & Readiness Gate

<small>Activity · MODRISS::SubStage: `cim.ph5.st3` · contained by **Convergence & Readiness**</small>

Close trace links and production readiness before CIM→PIM.

**Participating roles**

- Process Reviewer

**Task uses in this activity**

- **Complete trace and readiness** (TaskUse `cim.ph5.st3.t1`)

### Increment Review & Adapt

<small>Activity · MODRISS::SubStage: `cim.ph5.st4` · contained by **Convergence & Readiness**</small>

Review the CIM slice with stakeholders, accept the increment, and adapt the next cycle.

**Participating roles**

- Process Reviewer

**Task uses in this activity**

- **Review and adapt CIM increment** (TaskUse `cim.ph5.st4.t1`)

## References

These sources explain the standards and practices on which the process structure is based. They are foundations for tailoring and professional judgement, rather than substitutes for project evidence.

- [OMG Software & Systems Process Engineering Meta-Model (SPEM) 2.0](https://www.omg.org/spec/SPEM/2.0/PDF/)
- [ISO/IEC/IEEE 12207:2026, software life cycle processes](https://www.iso.org/standard/90219.html)
- [ISO/IEC/IEEE 15288:2023, system life cycle processes](https://www.iso.org/standard/81702.html)
- [SWEBOK Guide, version 4.0a](https://ieeecs-media.computer.org/media/education/swebok/swebok-v4.pdf)
- [Agile Manifesto principles](https://agilemanifesto.org/principles)
- [The Kanban Guide](https://kanbanguides.org/the-kanban-guide/)
- [FinOps Framework](https://www.finops.org/framework/)
- [AWS Well-Architected Serverless Applications Lens](https://docs.aws.amazon.com/wellarchitected/latest/serverless-applications-lens/welcome.html)
- [Brinkkemper, Method engineering](<https://doi.org/10.1016/S0950-5849(95)01059-9>)
- [Asadi, Esfahani, and Ramsin, Process patterns for MDA-based software development](https://mason.gmu.edu/~nesfaha2/Publications/SERA2010.pdf)
