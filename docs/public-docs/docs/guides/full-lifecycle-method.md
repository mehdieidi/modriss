# Full-Lifecycle Development Process

The MODRISS methodology comprises a development process and a modeling framework. This guide documents the process component. It organizes work across CIM, PIM, and AWS PSM modeling, artifact production, release, operations, change, and retirement. The modeling framework defines the languages and technical facilities used by that work.

The maintained process is described in [`mde/process/software-development-process.md`](https://github.com/mehdieidi/modriss/blob/main/mde/process/software-development-process.md). The machine-readable definitions are in [`mde/process/process-definitions/`](https://github.com/mehdieidi/modriss/tree/main/mde/process/process-definitions). Those sources define the roles, work products, dependencies, and conditions used by the process.

## Lifecycle phases

<figure class="doc-diagram">
  <a class="doc-diagram__link" href="../../assets/diagrams/modriss-lifecycle-manuscript.svg" aria-label="Open the full-size MODRISS lifecycle diagram">
    <img src="../../assets/diagrams/modriss-lifecycle-manuscript.svg" alt="Five lifecycle phases: initiation and tailoring, iterative model-driven delivery, release and transition, operation and learning, and authorized retirement." />
  </a>
  <figcaption>The five lifecycle phases and their evidence-driven return paths.</figcaption>
</figure>

The lifecycle has five phases. Each has an outcome and evidence that supports the next decision.

| Phase                                  | Work and expected evidence                                                                                                                                                                             |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **0. Initiate, tailor, and organize**  | Agree on product outcomes, the first release and increment hypotheses, a method profile, team ownership, dependencies, and quality, security, and operations baselines.                                |
| **1. Iterative model-driven delivery** | Deliver capability increments through CIM, PIM, AWS PSM, generation, and artifact review. Record model and artifact revisions, decisions, risks, validation, and the accept, defer, or rework outcome. |
| **2. Release and transition**          | Assemble accepted increments into a release candidate, review its evidence, promote it through environments, rehearse rollback, and transfer operational ownership.                                    |
| **3. Operate, evolve, and learn**      | Review service and product outcomes, SLOs, cost, security signals, and incidents. Use this evidence to plan changes and improve the development process.                                               |
| **4. Retire, migrate, and close**      | Authorize retirement, communicate with users, migrate or dispose of data, close integrations and access, decommission infrastructure, and retain required records and knowledge.                       |

The release cycle repeats while the product remains active. Phase 4 begins only after an explicit retirement decision. A release or an accepted increment does not end the product lifecycle.

## Capability-increment process

Phase 1 contains a repeatable engine for one capability slice. The slice should be small enough to review and test, while still producing a useful outcome.

<figure class="doc-diagram">
  <a class="doc-diagram__link" href="../../assets/diagrams/modriss-model-driven-engine.svg" aria-label="Open the full-size capability-increment process diagram">
    <img src="../../assets/diagrams/modriss-model-driven-engine.svg" alt="The delivery engine frames an increment, models it in CIM, transforms and refines it through PIM and AWS PSM, generates artifacts, and reviews readiness." />
  </a>
  <figcaption>One capability increment moves through the model-driven delivery engine.</figcaption>
</figure>

For each increment, the engine frames the scope and acceptance evidence, runs the CIM process, transforms CIM to PIM, refines PIM, transforms PIM to AWS PSM, refines PSM, generates an artifact baseline, and reviews readiness. After acceptance, the team can start another increment or leave the engine to assemble a release.

The sequence provides a shared flow for people and models. It does not require every specialist to work in isolation. Teams can proceed in parallel when scope and dependencies allow it. They record ownership and revision decisions so downstream work does not silently rely on an unresolved upstream assumption.

Feedback follows the earliest model that owns the change. A missing business concept returns from PIM work to CIM. An AWS constraint may require a PIM change. A generated artifact issue may require a PSM or generator correction. The team records the reason and affected revisions, then regenerates downstream outputs as needed.

## Release and operational change

An accepted increment is a candidate for later release assembly. The release decision reviews the exact model and artifact revisions, verification results, security findings, rollback and recovery plans, operational readiness, and required approvals. Promotion proceeds through environments with stop and rollback thresholds. Handover includes dashboards, alerts, runbooks, support ownership, recovery access, and post-deployment checks.

Operational findings enter a change workflow. Product and requirement changes return to CIM. Service, contract, or architecture changes return to PIM. Provider and resource changes return to AWS PSM. Implementation, pipeline, and environment changes go to the artifact or release process; operational control and support changes go to their operations work products. The change then moves forward through the affected transformations and receives new evidence.

<details>
  <summary>View the alternative phase-oriented lifecycle map</summary>
  <figure class="doc-diagram">
    <a class="doc-diagram__link" href="../../assets/diagrams/modriss-lifecycle-2.svg" aria-label="Open the full-size phase-oriented lifecycle diagram">
      <img src="../../assets/diagrams/modriss-lifecycle-2.svg" alt="Alternative lifecycle map with the model-driven construction phase shown in greater detail." />
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

The process documentation follows OMG SPEM 2.0 in distinguishing reusable method content, such as roles, tasks, work products, and guidance, from the process activities that use it. Its lifecycle scope is informed by ISO/IEC/IEEE 12207 and ISO/IEC/IEEE 15288. Situational method engineering informs how method content is selected and tailored for a project. Agile principles and Scrum inform inspection, adaptation, small increments, and ownership. MDE process-pattern research informs model refinement, transformation, traceability, human review, and feedback between levels.

These references provide a basis for the structure. They do not establish that one activity sequence or metric is right for every project. Teams should review their process tailoring against project evidence.

- [OMG SPEM 2.0](https://www.omg.org/spec/SPEM/2.0/About-SPEM)
- [ISO/IEC/IEEE 12207:2026](https://www.iso.org/standard/90219.html)
- [ISO/IEC/IEEE 15288:2023](https://www.iso.org/standard/81702.html)
- [Brinkkemper, “Method engineering”](<https://doi.org/10.1016/S0950-5849(95)01059-9>)
- [Brinkkemper, Saeki, and Harmsen, “Assembly techniques for method engineering”](https://www.sciencedirect.com/science/article/pii/S0306437999000162)
- [Asadi, Esfahani, and Ramsin, “Process patterns for MDA-based software development”](https://mason.gmu.edu/~nesfaha2/Publications/SERA2010.pdf)
- [Agile Manifesto principles](https://agilemanifesto.org/principles)
- [The 2020 Scrum Guide](https://scrumguides.org/scrum-guide.html)
