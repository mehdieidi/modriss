# MODRISS Full-Lifecycle Software Development Process

## Purpose

The MODRISS methodology comprises a full-lifecycle development process and a
modeling framework. This document specifies the process component. Its three
DSMLs support work within that process at different abstraction levels:

- **CIM** expresses product, business, domain, behavior, governance, and
  transformation intent without platform detail.
- **PIM** refines that intent into platform-independent services, contracts,
  data, integration, security, and operational architecture.
- **PSM** realizes the accepted PIM as AWS-specific resources, relationships,
  policies, observability, and deployment intent.

The generated artifacts are then reviewed, verified, released, operated,
changed, and eventually retired. The lifecycle therefore has three nested or
coordinated structures:

1. a sequential, phase-based **Development and Delivery Process**;
2. a repeatable **vertical increment and release engine** within that process;
   and
3. an ongoing, event-driven **Operations and Maintenance Process** using a
   continuous Kanban service-delivery system for production, maintenance,
   incident, risk, and improvement demand.

The machine-readable definitions are in
[`process-definitions/`](process-definitions/). This document is the normative
English explanation of how those definitions are used. The Persian lifecycle
document remains available as a translation/reference, but the JSON process
definitions and this document are the maintained implementation contract.

## Why this structure is justified

The process design adapts established lifecycle and method-engineering ideas to
the serverless research context. Its activities and evidence are tailored to
project conditions.

| Foundation                               | How MODRISS uses it                                                                                                                                                                                                                                              |
| ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **SPEM 2.0**                             | Separates reusable method content from process uses. Its bounded Phase concept and ongoing/event-driven Work Breakdown Element properties justify modeling operations as a Process rather than a sequential phase.                                               |
| **RUP and Disciplined Agile lifecycles** | Distinguish sequential lifecycle phases from their contained iterations and distinguish phase-oriented projects from continuous-delivery product teams. MODRISS therefore models recurring release/transition work as Phase 1 activities, not phases.            |
| **ISO/IEC/IEEE 12207**                   | Supplies the software lifecycle scope: agreement, organizational/project enablement, technical management, technical development, operation, maintenance, and disposal. MODRISS tailors these activities while retaining evidence and decision responsibilities. |
| **ISO/IEC/IEEE 15288**                   | Supplies the system lifecycle perspective and supports concurrent, iterative, recursive, and incremental work. This is why architecture, operations, quality, security, and transition are not postponed until after modeling.                                   |
| **Situational method engineering**       | Informs the selection and tailoring of reusable method content and process activities according to project context, risk, criticality, novelty, team structure, and delivery constraints. Tailoring decisions are themselves versioned work products.            |
| **Agile principles and Scrum**           | Supplies empirical control, small usable increments, inspection, adaptation, a product backlog, explicit ownership, and a definition of done. MODRISS does not equate agility with skipping architecture, assurance, or lifecycle obligations.                   |
| **Kanban and Disciplined DevOps**        | Supplies pull/WIP/SLE controls inside Operations and Maintenance and the cross-process integration of development, operations, support, release, security, CI/CD, feedback, and improvement.                                                                     |
| **MDA/MDE process-pattern research**     | Supports the distinction between model refinement, transformation, traceability, human review of generated decisions, and feedback from later representations to earlier models.                                                                                 |

Primary references:

- [OMG SPEM 2.0](https://www.omg.org/spec/SPEM/2.0/PDF/)
- [ISO/IEC/IEEE 12207:2026](https://www.iso.org/standard/90219.html)
- [ISO/IEC/IEEE 15288:2023](https://www.iso.org/standard/81702.html)
- [Brinkkemper, “Method engineering”](<https://doi.org/10.1016/S0950-5849(95)01059-9>)
- [Brinkkemper, Saeki & Harmsen, “Assembly techniques for method engineering”](https://www.sciencedirect.com/science/article/pii/S0306437999000162)
- [Asadi, Esfahani & Ramsin, “Process patterns for MDA-based software development”](https://mason.gmu.edu/~nesfaha2/Publications/SERA2010.pdf)
- [Agile Manifesto principles](https://agilemanifesto.org/principles)
- [The 2020 Scrum Guide](https://scrumguides.org/scrum-guide.html)
- [The Kanban Guide (2025)](https://kanbanguides.org/the-kanban-guide/)
- [PMI Disciplined Agile: Starting With Iterations or Flow](https://www.pmi.org/disciplined-agile/starting-with-iterations-or-flow)
- [PMI Disciplined Agile: Designing the Kanban Board](https://www.pmi.org/disciplined-agile/designing-the-kanban-board)
- [PMI Disciplined DevOps](https://www.pmi.org/disciplined-agile/process/disciplined-devops)
- [IBM RUP project planning](https://www.ibm.com/docs/en/rational-clearquest/10.0.8?topic=settings-project-planning)
- [PMI Disciplined Agile: Full Delivery Lifecycles](https://www.pmi.org/disciplined-agile/lifecycle)
- [Ahmad et al. (2018), _Kanban in software engineering_](https://doi.org/10.1016/j.jss.2017.11.045)

The normative process endpoint is therefore a SPEM-mapped MODRISS JSON DSL,
not a native SPEM/XMI instance. Its `metamodelBindings` connect TaskDefinitions
to CIM/PIM/PSM language classifiers for coverage and guidance; they are not
SPEM WorkProductDefinitions. The exact mapping is documented in
[`spem/method-content.md`](spem/method-content.md).

These references ground the structure; they do not prove that every selected
task or metric is universally optimal. Each project must inspect and adapt the
method profile using evidence.

## Findings addressed in this revision

The repository review found that the earlier process definition was a useful
technical task catalog but did not yet specify an executable, full-lifecycle
software development process. The main defects were:

- the end-to-end definition stopped at an eight-stage modeling pipeline and had
  no explicit initiation, tailoring, release, operations, or retirement;
- several change workflows referenced obsolete stage IDs, so change impact
  could not be routed reliably;
- concept coverage was made green by silently assigning unowned concepts to a
  generic readiness task; coverage is now explicit, including shared kernel,
  shared enums, shared readiness, and PSM platform-enum ownership;
- roles and responsibilities were too narrow for product ownership, delivery
  coordination, quality, security, release, and service operation;
- progress was represented mainly as a local task checklist, without a
  versioned run, evidence, state, blocker, decision, dependency, or release
  contract;
- trace and readiness objects did not carry enough revision/provenance or
  manual-decision state to support reliable cross-level change management;
- the ownership audit treated numeric multiplicities, inherited containment,
  and valid alternative parents as defects. The audit is now
  inheritance-aware and reports those cases as review candidates.

The fixes deliberately preserve the existing CIM, PIM, PSM, transformation,
generation, and semantic-validation behavior unless a process contract required
an explicit boundary. This keeps process revision separate from unrelated
transformation changes.

## Method concepts

- The **MODRISS methodology** comprises the development process and the modeling framework.
- **Reusable method content** consists of SPEM role, task, work-product, and
  guidance definitions used by process activities. It is part of the process
  component, not a third part of the methodology.
- A **process run** is one configured enactment of the development process for
  a product, release, increment, or change. Its method profile records the
  selected process activities and method content, roles, evidence, thresholds,
  and rationale.
- A **work product** is something produced or consumed by work, such as a
  model revision, trace model, readiness assessment, release record, or
  operations pack.
- An **artifact** is an external or generated deliverable such as source code,
  infrastructure templates, tests, runbooks, or deployment configuration.
- A **gate** is an evidence-based decision. It is not merely the completion of
  the tasks that precede it.
- A **method profile** records process tailoring: selected, combined, or
  omitted activities, roles, evidence, thresholds, and rationale.
- A **phase** is a bounded sequential period in Development and Delivery ending
  at a major checkpoint. A **stage** and optional **substage** decompose a phase
  or ongoing process into Activities; a **task** is the assignable work unit.

## Lifecycle architecture

```text
Initiate / tailor / organize
        |
        v
Frame increment -> CIM -> CIM/PIM -> PIM -> PIM/PSM -> PSM -> M2T -> readiness
        ^                                                               |
        |---------------- inspect, adapt, rework, accept ----------------|
        |
        +--> assemble release -> progressive transition --G7--> Operations & Maintenance
                  ^                                             | Kanban pull flow
                  |-------- telemetry / maintenance change -----|
        |
        +--> retire / migrate / close
```

The model-driven increment cycle is one part of the development process. It can
repeat many times before one release, and the product can operate through many
releases. The cycle does not cover the full product lifecycle on its own.

## Development and Delivery phases

### Phase 0. Inception, tailoring, and organization

The product owner and sponsor define the outcome hypothesis, boundaries,
constraints, and initial release hypothesis. The Method Engineer performs a
situational assessment and publishes a method profile. The delivery lead
defines team topology, model ownership, integration ownership, dependency
boards, decision rights, escalation, and review cadence. Quality, security,
operations, and release owners define control objectives before design detail
accumulates.

Required outputs include:

- product and system charter;
- situational method profile;
- team topology and dependency map;
- quality/security/operations baseline;
- first increment goal and evidence plan.

### Phase 1. Active product construction and evolution

This phase occurs once and spans the product's active life. It contains
repeatable model-driven increment and continuous-delivery release activities;
none of those repeated units is a phase.

Each increment is a thin, valuable, testable vertical slice. The integrated
engine coordinates the child processes in this order:

1. frame the increment and its acceptance evidence;
2. run the CIM child process;
3. transform CIM → PIM and inspect traces, assumptions, and decisions;
4. run the PIM child process;
5. transform PIM → AWS PSM and inspect mappings and platform exceptions;
6. run the PSM child process;
7. generate a reproducible artifact baseline;
8. run artifact readiness and accept, defer, or rework the increment.

The steps are a logical flow, not a demand for serialized specialist work.
Teams may work concurrently on independent slices or on non-conflicting
stages, provided that revisions, ownership, dependencies, and acceptance
evidence are explicit. A downstream team must not silently accept an upstream
assumption simply because a transformation produced an object.

Each accepted increment records:

- product goal, scope items, and acceptance signals;
- participating teams and dependency decisions;
- CIM, PIM, PSM, and generated-artifact revisions;
- transformation profile/version and run report;
- trace coverage and unresolved assumptions;
- structural and semantic validation evidence;
- readiness findings, manual decisions, and risk acceptances;
- acceptance, deferral, or rework decision;
- retrospective and next-step changes.

#### Repeatable release and transition activities

Accepted increments are assembled into a release candidate. Release assembly
checks compatibility and dependency closure. The release decision reviews the
exact model and artifact revisions, verification results, security findings,
rollback, data recovery, operational readiness, and approvals. Promotion is
progressive across environments with observable stop/rollback thresholds.

Handover is complete only when the service owner accepts dashboards, alerts,
runbooks, support ownership, recovery access, and post-deployment validation.

## Operations and Maintenance Process

This is an ongoing, event-driven process, not a phase. It starts at the first
G7 handover, runs concurrently with later development and releases, and ends
at G8 only when no live release remains. The service owner
reviews SLOs, telemetry, cost, security signals, product outcomes, incidents,
and customer impact. Demand is visualized on a Kanban board and managed through
an explicit Definition of Workflow with Requested, Ready, In Progress, Verify,
and Done states; WIP controls; classes of service; service-level expectations;
replenishment; flow reviews; and flow metrics. Planned delivery retains its
release/increment commitments; the method profile defines capacity between the
two systems and an exceptional expedite policy. This implements the Kanban
Guide's core practices and Disciplined Agile's contextual use of flow and
value-stream optimization. DevOps practices supply its integration points with
Development and Delivery without merging the two control systems.

Maintenance purpose—corrective, preventive, adaptive, additive, or
perfective—emergency-temporary status, and the expedite, fixed-date, standard,
or risk-reduction class of service are separate decisions. Each operational item either completes through
an approved runbook, follows the shortest safe MDE/release path, or is
explicitly committed to a planned release. Incidents produce recovery evidence
and permanent problem/change work. A change is classified, impact-analyzed through traces and dependencies,
and routed to the earliest correct source:

- product/requirements change → CIM;
- service/contract/architecture change → PIM;
- provider/resource/security/deployment change → PSM;
- implementation, pipeline, or environment change → artifact/release process;
- operational control or support change → operations work product.

The change then re-enters the smallest affected process, propagates forward,
and receives new evidence. Patching generated output is allowed only for
implementation-specific refinements; structural changes must return to the
model or generator source.

### Phase 2. Retire, migrate, and close

Retirement covers the product decision, user communication, replacement or
migration, data retention/disposition, integration shutdown, access removal,
infrastructure decommissioning, cost closure, evidence retention, and
organizational learning. A lifecycle is not complete while data, integrations,
credentials, support obligations, or legal records remain ownerless.
Operations and Maintenance continues during migration and decommissioning and
terminates with Development and Delivery at G8. G8 is a shared lifecycle
closure condition, not an activity-flow edge between Phase 2 and the Operations
and Maintenance Process: Phase 2 owns the product-retirement decision and
closure work, while Operations owns the safe shutdown of every live release.

## Roles and team coordination

Roles are responsibilities, not mandatory job titles. One person may hold
several roles in a small project; a large project may distribute one role over
multiple people. Accountability must remain unambiguous.

The integrated process assigns responsibilities to at least these groups:

- product owner and domain experts for value and meaning;
- requirements and business modelers for intent and acceptance;
- solution architect for PIM and cross-level architectural decisions;
- cloud platform engineer for AWS PSM and platform automation;
- quality and security engineers for evidence and risk controls;
- release engineer and service owner for promotion and operation;
- delivery lead for flow, dependencies, coordination, and escalation;
- process reviewer for gates and evidence;
- method engineer for tailoring and method evolution.

For multiple teams, every model scope has one accountable owner and a named
integration path. A shared dependency record contains owner, dependency type,
affected revision, due date, status, evidence, and escalation path. Shared
model elements require an explicit ownership convention; teams should prefer
bounded ownership and published interfaces over unrestricted concurrent edits.

## Progress, state, and metrics

The process definitions now expose a progress contract. It is deliberately
separate from a UI checklist: a local checklist helps a person navigate, while
the authoritative process run is stored in a project tracker and linked to
model, transformation, validation, and release evidence.

Every run records at least:

`runId`, product goal, increment/release ID, scope item IDs, method profile,
team IDs, current phase/stage, state, owner, timestamps, evidence links, open
blockers, and acceptance timestamp.

Allowed states are `not-started`, `in-progress`, `blocked`,
`ready-for-review`, `accepted`, `rework`, and `deferred`. Important events are
increment start, evidence recorded, finding raised/resolved, decision recorded,
gate reviewed, increment accepted/reopened, and release promoted.

Core metrics are:

- eligible task completion;
- increment flow time;
- rework rate;
- trace coverage;
- open blocking findings;
- manual-decision closure;
- level-specific coverage (domain scope, contract, resource trace, release
  evidence);
- cross-team dependency age;
- release frequency and escaped-defect rate.

Metrics are for inspection and process improvement, not individual performance
ranking. Task completion never overrides a blocking finding, missing trace, or
failed acceptance outcome. Averages must not hide a single critical blocker.

## Definitions of Ready and Done

### Increment ready

The slice has an outcome hypothesis, bounded scope, accountable owners,
acceptance evidence, dependencies, risks, required controls, and a method
profile. Open assumptions have owners and due dates.

### Level ready

The level has the required work products, structural conformance, traceability,
readiness assessment, and explicit semantic review appropriate to the project
profile. Generated content is not accepted merely because the transformation
completed.

### Increment done

The exact model and artifact revisions are recorded; required structural and
semantic evidence exists; trace links and decisions are inspectable; blocking
findings are resolved or explicitly accepted; the increment is accepted,
deferred, or reworked; and the retrospective has produced a decision or no-op
rationale.

### Release done

The exact release candidate has verification, security, rollback, operational
handover, deployment, and post-deployment evidence, with a named accepting
owner and an outcome review plan.

## Validation boundary for the AI assistant

Assistant-generated model actions, patches, proposals, checkpoints, and model
outputs may be applied or committed only after **structural Ecore/EMF
conformance** through `ModelService.validateStructural(...)`. The assistant
must not call `ModelService.validate(...)`, stored validation endpoints,
`validateGeneratedXmi(...)`, `EpsilonEvlValidator`, EVL CLIs, or EVL profiles in
assistant apply/repair/commit paths.

Semantic EVL validation remains available for an explicit user/model validation
workflow outside the assistant apply path. The process records both kinds of
evidence and does not mislabel structural conformance as semantic approval.

## Tailoring rules

Tailoring may combine, delegate, or omit activities when the method profile
records:

1. the context and risk that justify the choice;
2. the preserved intent/control objective;
3. the owner and replacement evidence;
4. the review point and expiry, when the decision is temporary.

At minimum, retain product/system intent, scope ownership, traceability,
structural conformance, appropriate semantic review, security/quality
responsibility, release/rollback evidence, operational ownership, and
retirement obligations. A smaller team may combine roles; it may not make the
responsibility disappear.

## Known limits and future work

The current PSM is AWS-specific, so portability claims apply to CIM/PIM intent
and not automatically to infrastructure behavior. Progress metadata defines a
contract but does not provide a project tracker by itself. Team coordination
and release records still require integration with the project’s chosen work
and source-control systems. Future work should add a first-class persisted
process-run service, dependency-board UI, release ledger, and empirical studies
of the development process across projects.
