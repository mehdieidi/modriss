# Full-Lifecycle Development Process

The MODRISS methodology comprises a development process and a modeling framework. This guide documents the process component. It organizes work across CIM, PIM, and AWS PSM modeling, artifact production, release, operations, change, and retirement. The modeling framework defines the languages and technical facilities used by that work.

The maintained process is described in [`mde/process/software-development-process.md`](https://github.com/mehdieidi/modriss/blob/main/mde/process/software-development-process.md). The machine-readable definitions are in [`mde/process/definitions/`](https://github.com/mehdieidi/modriss/tree/main/mde/process/definitions). Those sources define the roles, work products, dependencies, and conditions used by the process.

For operational use, continue with the complete [Development and Delivery reference](../process-reference/development-delivery.md), the [Operations and Maintenance reference](../process-reference/operations-maintenance.md), and the [reusable method-content catalogs](../process-reference/index.md). These pages give detailed instructions, responsibilities, inputs, outputs, review conditions, guidance, and gates for every canonical process item.

## Two coordinated lifecycle processes

<figure class="doc-diagram">
  <a class="doc-diagram__link" href="../../assets/diagrams/modriss-lifecycle-manuscript.svg" aria-label="Open the full-size MODRISS lifecycle diagram">
    <img src="../../assets/diagrams/modriss-lifecycle-manuscript.svg" alt="Development and Delivery proceeds through sequential phases while an ongoing Operations and Maintenance Kanban process sustains live releases; DevOps coordination connects them." />
  </a>
  <figcaption>Sequential development phases and the concurrent Operations and Maintenance process.</figcaption>
</figure>

The integrated lifecycle contains two cooperating processes. Development and
Delivery has three one-time sequential phases. Operations and Maintenance is
a distinct concurrent process: it starts at the
first G7 handover, continues while any release is live, and ends at G8.

| One-time phase                                   | Work and expected evidence                                                                                                                                                                                  |
| ------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **0. Inception, tailoring, and organization**    | Compare alternatives and serverless suitability; establish cost/risk assumptions and G0; agree outcomes, the first slice, a method profile, ownership, and quality/security/operations baselines before G1. |
| **1. Active product construction and evolution** | Repeat model-driven increment, release qualification, progressive promotion, handover, and outcome-review activities while the product remains active.                                                      |
| **2. Retire, migrate, and close**                | Authorize retirement, communicate with users, migrate or dispose of data, reconcile retained records, close integrations/access, decommission infrastructure, verify cost cessation, and retain knowledge.  |

The continuous-delivery cycle repeats activities _inside Phase 1_; no phase
repeats. Phase 2 begins only after an explicit retirement decision. Operations
and Maintenance coexists with active-product delivery and continues through
retirement until no live release remains. The two processes do not feed into
one another through a retirement activity edge. Instead, Phase 2 owns product
retirement and Operations owns live-service shutdown; both must satisfy the
shared G8 closure criteria before the lifecycle is closed.

SPEM defines a Phase as a significant period ending at a major checkpoint and
provides `isOngoing` and `isEventDriven` for continuous and occurrence-triggered
work. RUP separates sequential phases from their contained iterations. DAD
distinguishes phase-oriented projects from continuous-delivery product teams,
where transition becomes a delivery activity rather than a recurring phase
([IBM](https://www.ibm.com/docs/en/rational-clearquest/10.0.8?topic=settings-project-planning);
[PMI](https://www.pmi.org/disciplined-agile/lifecycle)). ISO/IEC/IEEE 12207 also distinguishes development, operation,
maintenance, and disposal processes and permits concurrent application. DevOps
supplies the coordination interface—shared ownership, CI/CD, operational
readiness, telemetry, change routing, and learning—without collapsing the two
control systems into one phase.

## Two connected ways of controlling work

MODRISS does not pretend that all maintenance can be scheduled. Planned product
work is committed at release and increment horizons. Production demand is
visualized when it appears and managed through a Kanban service-delivery system
with Requested, Ready, In Progress, Verify, and Done states; explicit
start/finish points; WIP controls; classes of service; service-level
expectations; replenishment and review cadences; and flow measures. The
[Kanban Guide](https://kanbanguides.org/the-kanban-guide/)
provides the minimum pull-system semantics used here.

Every operational item records its maintenance purpose—corrective, preventive,
adaptive, additive, or perfective—any emergency-temporary restoration status,
and its separate class of service—expedite, fixed-date, standard, or
risk-reduction. This follows
the maintenance distinctions in [SWEBOK v4.0a](https://ieeecs-media.computer.org/media/education/swebok/swebok-v4.pdf).

An item has three legitimate destinations:

1. finish as operations-only/runbook work with evidence;
2. take the shortest safe path through the affected CIM, PIM, PSM, generator,
   code, and release activities; or
3. be explicitly committed to a planned release backlog.

Emergency restoration may use the expedite policy, but temporary downstream
changes remain open until permanent correction or reconciliation with the
authoritative model, generator, code, configuration, or runbook source.

<figure class="doc-diagram">
  <a class="doc-diagram__link" href="../../assets/diagrams/modriss-operational-flow.svg" aria-label="Open the full-size MODRISS Kanban service-delivery board">
    <img src="../../assets/diagrams/modriss-operational-flow.svg" alt="A five-column Kanban board manages service work through Requested, Ready, In Progress, Verify, and Done, with three lifecycle dispositions." />
  </a>
  <figcaption>The Operations and Maintenance Kanban system runs alongside planned development and releases.</figcaption>
</figure>

## Capability-increment process

Phase 1 contains a repeatable engine for one capability slice. The slice should be small enough to review and test, while still producing a useful outcome.

<figure class="doc-diagram">
  <a class="doc-diagram__link" href="../../assets/diagrams/modriss-model-driven-engine.svg" aria-label="Open the full-size capability-increment process diagram">
    <img src="../../assets/diagrams/modriss-model-driven-engine.svg" alt="The delivery engine frames an increment, models it in CIM, transforms and refines it through PIM and AWS PSM, generates artifacts, and reviews readiness." />
  </a>
  <figcaption>One capability increment moves through the model-driven delivery engine.</figcaption>
</figure>

For each increment, the engine frames the scope and acceptance evidence, runs the CIM process, transforms CIM to PIM, refines PIM, transforms PIM to AWS PSM, refines PSM, generates an artifact baseline, and reviews readiness. After acceptance, the team can start another increment or leave the engine to assemble a release.

Each accepted or reworked increment also reviews outcome, flow, rework, trace,
cost, and method-friction evidence. The tailored profile receives an owned
improvement decision or a reasoned no-op; this adaptation is an activity within
Phase 1, not another lifecycle phase.

The sequence provides a shared flow for people and models. It does not require every specialist to work in isolation. Teams can proceed in parallel when scope and dependencies allow it. They record ownership and revision decisions so downstream work does not silently rely on an unresolved upstream assumption.

Feedback follows the earliest model that owns the change. A missing business concept returns from PIM work to CIM. An AWS constraint may require a PIM change. A generated artifact issue may require a PSM or generator correction. The team records the reason and affected revisions, then regenerates downstream outputs as needed.

## Release and operational change

An accepted increment is a candidate for later release assembly. The release decision reviews the exact model and artifact revisions, verification results, security findings, rollback and recovery plans, operational readiness, and required approvals. Promotion proceeds through environments with stop and rollback thresholds. Handover includes dashboards, alerts, runbooks, support ownership, recovery access, and post-deployment checks.

Operational findings enter a change workflow. Product and requirement changes return to CIM. Service, contract, or architecture changes return to PIM. Provider and resource changes return to AWS PSM. Implementation, pipeline, and environment changes go to the artifact or release process; operational control and support changes go to their operations work products. The change then moves forward through the affected transformations and receives new evidence.

<details>
  <summary>Open the compatibility copy of the lifecycle map</summary>
  <figure class="doc-diagram">
    <a class="doc-diagram__link" href="../../assets/diagrams/modriss-lifecycle-2.svg" aria-label="Open the compatibility copy of the full-size lifecycle diagram">
      <img src="../../assets/diagrams/modriss-lifecycle-2.svg" alt="Compatibility copy of the lifecycle map showing sequential Development and Delivery phases coordinated with ongoing Operations and Maintenance." />
    </a>
  </figure>
</details>

## Tailoring, roles, and evidence

The process is tailored to the project's criticality, regulation, novelty, uncertainty, team structure, and delivery constraints. A versioned method profile records selected, combined, delegated, and omitted activities with their rationale and compensating evidence. Role names describe responsibilities. A small team can assign several roles to one person, while a large team can distribute a role across people. Each model scope still needs a clear owner and integration path.

A process run records its product goal, scope, method profile, teams, active phase, owner, state, evidence, blockers, decisions, dependencies, and acceptance. A checklist helps people navigate a task. The run record connects that work to model revisions, transformation reports, validation results, release decisions, and operational evidence.

The definitions support states such as `not-started`, `in-progress`, `blocked`, `ready-for-review`, `accepted`, `rework`, and `deferred`. Useful measures include increment flow time, rework, trace coverage, blocking findings, manual-decision closure, dependency age, release frequency, and escaped defects. Completed tasks alone do not establish that a gate has passed.

## Validation boundary

Assistant-generated actions, patches, proposals, checkpoints, and model outputs are gated only by structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL semantic validation belongs to an explicit user/model validation workflow outside assistant apply, repair, and commit paths.

## Process foundations

The process documentation follows OMG SPEM 2.0 in distinguishing reusable method content, such as roles, tasks, work products, and guidance, from the process activities that use it. Its lifecycle scope is informed by ISO/IEC/IEEE 12207 and ISO/IEC/IEEE 15288. Situational method engineering informs how method content is selected and tailored for a project. Agile principles and Scrum inform inspection, adaptation, small increments, and ownership. The Kanban Guide supplies the Definition of Workflow, WIP, SLE, and flow measures; Disciplined Agile supplies the contextual iteration/flow choice and value-stream guidance. SWEBOK and ISO/IEC/IEEE 14764 supply maintenance concepts. MDE process-pattern research informs model refinement, transformation, traceability, human review, and feedback between levels. AWS Serverless Lens guidance informs serverless operations, observability, reversibility, and progressive deployment.

These references provide a basis for the structure. They do not establish that one activity sequence or metric is right for every project. Teams should review their process tailoring against project evidence.

- [OMG SPEM 2.0](https://www.omg.org/spec/SPEM/2.0/PDF/)
- [ISO/IEC/IEEE 12207:2026](https://www.iso.org/standard/90219.html)
- [ISO/IEC/IEEE 15288:2023](https://www.iso.org/standard/81702.html)
- [Brinkkemper, “Method engineering”](<https://doi.org/10.1016/S0950-5849(95)01059-9>)
- [Brinkkemper, Saeki, and Harmsen, “Assembly techniques for method engineering”](https://www.sciencedirect.com/science/article/pii/S0306437999000162)
- [Asadi, Esfahani, and Ramsin, “Process patterns for MDA-based software development”](https://mason.gmu.edu/~nesfaha2/Publications/SERA2010.pdf)
- [Agile Manifesto principles](https://agilemanifesto.org/principles)
- [The 2020 Scrum Guide](https://scrumguides.org/scrum-guide.html)
- [The Kanban Guide (2025)](https://kanbanguides.org/the-kanban-guide/)
- [PMI Disciplined Agile: Starting With Iterations or Flow](https://www.pmi.org/disciplined-agile/starting-with-iterations-or-flow)
- [PMI Disciplined Agile: Designing the Kanban Board](https://www.pmi.org/disciplined-agile/designing-the-kanban-board)
- [PMI Disciplined DevOps](https://www.pmi.org/disciplined-agile/process/disciplined-devops)
- [Ahmad et al. (2018), _Kanban in software engineering_](https://doi.org/10.1016/j.jss.2017.11.045)
- [ISO/IEC/IEEE 14764:2022 Software maintenance](https://www.iso.org/standard/80710.html)
- [FinOps Framework](https://www.finops.org/framework/)
- [FinOps planning and estimating](https://www.finops.org/framework/capabilities/planning-estimating/)
- [AWS Serverless Lens: expenditure and usage awareness](https://docs.aws.amazon.com/wellarchitected/latest/serverless-applications-lens/expenditure-and-usage-awareness.html)
- [NIST SP 800-88 Rev. 1 media sanitization](https://www.nist.gov/publications/nist-special-publication-800-88-revision-1-guidelines-media-sanitization)
