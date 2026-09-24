# MODRISS Reusable Method Content Catalog

> This catalog is generated from the authoritative process definitions and
> engineered method-content sources by `../tools/build-method-package.mjs`.
> Edit the source JSON rather than this file, then rebuild the package.

## Scope and notation

The consolidated repository contains 17 RoleDefinitions, 134 TaskDefinitions, 92 WorkProductDefinitions, and 46 Guidance elements. Stable identifiers are shown because the delivery processes refer to these definitions through RoleUse, TaskUse, WorkProductUse, and ProcessParameter elements. “Provenance” identifies the source component from which an element was consolidated.

## Role definitions

### Application Engineer (`role.application-engineer`)

Reviews generated code, completes application configuration, and fixes implementation gaps.

Conceptual role mapping: `R-09`. Provenance: Artifact-readiness component.

### Business Modeler (`role.business-modeler`)

Leads CIM discovery and domain-model increments.

Conceptual role mapping: `R-06`. Provenance: End-to-end lifecycle, CIM modeling component.

### Cloud Platform Engineer (`role.cloud-platform-engineer`)

Leads AWS PSM, generation, environments, and platform automation.

Conceptual role mapping: `R-08`. Provenance: End-to-end lifecycle, PIM modeling component, AWS PSM modeling component, Artifact-readiness component.

### Delivery Lead (`role.delivery-lead`)

Maintains the integrated process run, dependency board, risks, cadence, and impediment escalation.

Conceptual role mapping: `R-05`. Provenance: End-to-end lifecycle, CIM modeling component, PIM modeling component, AWS PSM modeling component.

### Domain Expert (`role.domain-expert`)

Validates business language, rules, and operational fit.

Conceptual role mapping: `R-03`. Provenance: End-to-end lifecycle, CIM modeling component, PIM modeling component.

### FinOps and Cost Analyst (`role.finops-cost-analyst`)

Owns cost hypotheses, budgets, allocation, anomaly thresholds, and cost feedback.

Conceptual role mapping: `R-12`. Provenance: Engineered core content.

### Method Engineer (`role.method-engineer`)

Tailors the development process and maintains its alignment with the modeling framework as metamodels change.

Conceptual role mapping: `R-04`. Provenance: End-to-end lifecycle, CIM modeling component, PIM modeling component, AWS PSM modeling component.

### Process Reviewer (`role.process-reviewer`)

Reviews gates, evidence, decisions, and process improvement.

Conceptual role mapping: `R-15`. Provenance: End-to-end lifecycle, CIM modeling component, PIM modeling component, AWS PSM modeling component.

### Product Owner (`role.product-owner`)

Owns product outcomes, priority, release scope, and acceptance decisions.

Conceptual role mapping: `R-02`. Provenance: End-to-end lifecycle, CIM modeling component, PIM modeling component, AWS PSM modeling component.

### Quality Engineer (`role.quality-engineer`)

Owns verification strategy, evidence quality, and quality risks.

Conceptual role mapping: `R-10`. Provenance: End-to-end lifecycle, PIM modeling component, AWS PSM modeling component, Artifact-readiness component.

### Records and Data Steward (`role.records-data-steward`)

Owns retention, disposition, migration evidence, and lifecycle closure for records and data.

Conceptual role mapping: `R-16`. Provenance: Engineered core content.

### Release Engineer (`role.release-engineer`)

Owns release candidates, promotion, rollback, and deployment records.

Conceptual role mapping: `R-13`. Provenance: End-to-end lifecycle, AWS PSM modeling component, Artifact-readiness component.

### Requirements Engineer (`role.requirements-engineer`)

Maintains requirements, acceptance criteria, and change impact evidence.

Conceptual role mapping: `R-06`. Provenance: End-to-end lifecycle, CIM modeling component.

### Security Engineer (`role.security-engineer`)

Owns security, privacy, threat, and exception evidence.

Conceptual role mapping: `R-11`. Provenance: End-to-end lifecycle, CIM modeling component, PIM modeling component, AWS PSM modeling component, Artifact-readiness component.

### Service Owner (`role.service-owner`)

Accepts operational readiness, SLOs, support ownership, and service outcomes.

Conceptual role mapping: `R-14`. Provenance: End-to-end lifecycle, AWS PSM modeling component, Artifact-readiness component.

### Solution Architect (`role.solution-architect`)

Leads PIM architecture, contracts, integration, and cross-level decisions.

Conceptual role mapping: `R-07`. Provenance: End-to-end lifecycle, PIM modeling component, AWS PSM modeling component.

### Sponsor (`role.sponsor`)

Authorizes investment and resolves business-level escalations. Accepts continuation, redirection, and stop decisions.

Conceptual role mapping: `R-01`. Provenance: Engineered core content.

## Task definitions

### End-to-end lifecycle (32 tasks)

#### Review SLOs, telemetry, and product outcomes (`task.e2e.ops.a1.t1`)

Review SLOs, telemetry, and product outcomes. The task is performed by `role.service-owner`. It consumes `e2e-artifact.release-record`, `e2e-artifact.product-charter`, `e2e-artifact.operations-record`, `e2e-artifact.method-profile`, `e2e-artifact.service-flow-system` and produces or updates `e2e-artifact.operations-record`, `e2e-artifact.operational-work-item`.

**Work.**

- Inspect service levels, errors, cost, security signals, usage, provider events, and product outcome measures.
- Compare observations with the service and product hypotheses.
- Capture actionable demand as visible service work items rather than inserting hidden work into a release plan.

**Exit.** Review SLOs, telemetry, and product outcomes evidence is recorded.

**Checks.** Operational decisions are based on identified evidence and thresholds.

#### Triage and make service demand ready (`task.e2e.ops.a2.t1`)

Triage and make service demand ready. The task is performed by `role.service-owner`. It consumes `e2e-artifact.operational-work-item`, `e2e-artifact.operations-record`, `e2e-artifact.method-profile`, `e2e-artifact.service-flow-system` and produces or updates `e2e-artifact.operational-work-item`, `e2e-artifact.service-flow-system`.

**Work.**

- Record the demand source, impact, affected service, evidence, owner, and requested outcome on the Kanban board.
- Where the item is maintenance, classify its purpose as corrective, preventive, adaptive, additive, or perfective; record any emergency temporary-restoration status; assign a class of service separately according to the board policy.
- Refine the item until it meets the Ready policy and identify its likely disposition: operations-only response, the shortest safe model-driven change path, or a planned release backlog.

**Exit.** Triage and make service demand ready evidence is recorded.

**Checks.** Maintenance purpose, emergency-temporary status, and class of service are recorded independently. Every ready item has an accountable owner, expected outcome, evidence need, and visible disposition.

#### Replenish, pull, and manage Kanban flow (`task.e2e.ops.a2.t2`)

Replenish, pull, and manage Kanban flow. The task is performed by `role.delivery-lead`. It consumes `e2e-artifact.operational-work-item`, `e2e-artifact.operations-record`, `e2e-artifact.method-profile`, `e2e-artifact.service-flow-system` and produces or updates `e2e-artifact.operational-work-item`, `e2e-artifact.service-flow-system`, `e2e-artifact.operations-record`.

**Work.**

- At the replenishment cadence, select eligible items into Ready according to capacity, risk, class-of-service policy, and value; pull a ready item only when the downstream WIP control permits.
- Use the board to manage work-item age, blocked work, service-level expectations, and reserved service capacity; an expedite item may displace other work only under the explicit expedite policy and must remain visible.
- Hold a daily flow review and periodic service-delivery review using WIP, throughput, cycle time, work-item age, SLE attainment, arrival rate, blocked time, and expedite frequency; adapt the Definition of Workflow through an explicit improvement decision.

**Exit.** Replenish, pull, and manage Kanban flow evidence is recorded.

**Checks.** A team does not start ordinary service work beyond its WIP control. The expedite class has at most one active item unless the method profile records an exceptional incident-command policy. Requested, Ready, In Progress, Verify, and Done states and their entry/exit policies are visible on the board.

#### Manage incidents and emergency recovery (`task.e2e.ops.a3.t1`)

Manage incidents and emergency recovery. The task is performed by `role.service-owner`. It consumes `e2e-artifact.operational-work-item`, `e2e-artifact.service-flow-system`, `e2e-artifact.operations-record`, `e2e-artifact.release-record`, `e2e-artifact.method-profile` and produces or updates `e2e-artifact.operations-record`, `e2e-artifact.operational-work-item`.

**Work.**

- Triage impact, stabilize service, communicate status, and execute approved recovery actions.
- Record incident timeline, affected scope, decisions, temporary modifications, and evidence.
- Create permanent corrective, security, or change work for systemic causes and keep it visible after restoration.

**Exit.** Manage incidents and emergency recovery evidence is recorded.

**Checks.** Recovery and customer impact are recorded before closure. An emergency temporary modification is not treated as the permanent corrective change.

#### Perform problem and risk learning (`task.e2e.ops.a3.t2`)

Perform problem and risk learning. The task is performed by `role.quality-engineer`. It consumes `e2e-artifact.operational-work-item`, `e2e-artifact.service-flow-system`, `e2e-artifact.operations-record`, `e2e-artifact.increment-record`, `e2e-artifact.release-record`, `e2e-artifact.method-profile` and produces or updates `e2e-artifact.operations-record`, `e2e-artifact.method-profile`, `e2e-artifact.operational-work-item`.

**Work.**

- Analyze contributing causes across requirements, models, transformation, generation, deployment, and operation.
- Update controls, tests, model patterns, process guidance, or method profile as appropriate.
- Place permanent corrective work on the Kanban board or deliberately commit it to a planned release, and verify reconciliation after any emergency downstream fix.

**Exit.** Perform problem and risk learning evidence is recorded.

**Checks.** The corrective action is routed to the earliest responsible source rather than only patched downstream.

#### Assess and route a maintenance change (`task.e2e.ops.a4.t1`)

Assess and route a maintenance change. The task is performed by `role.delivery-lead`. It consumes `e2e-artifact.operational-work-item`, `e2e-artifact.service-flow-system`, `e2e-artifact.operations-record`, `e2e-artifact.method-profile`, `e2e-artifact.increment-record`, `e2e-artifact.product-charter`, `e2e-artifact.release-record` and produces or updates `e2e-artifact.increment-record`, `e2e-artifact.operations-record`, `e2e-artifact.operational-work-item`.

**Work.**

- Classify the authoritative source as product/domain, architecture, platform, generator, artifact, or operations and confirm whether the item remains a bounded Kanban service item or is committed to a planned release.
- Use traces and dependency ownership to identify impacted downstream levels and teams.
- Send product-changing work to the Development and Delivery Process at the smallest affected phase or activity; after release, reconcile emergency downstream fixes into the authoritative source and close the service item only when evidence returns.

**Exit.** Assess and route a maintenance change evidence is recorded.

**Checks.** The change record identifies source revision, impacted levels, downstream evidence, acceptance decision, and service-work-item disposition.

#### Inspect flow, quality, and coordination metrics (`task.e2e.ops.a5.t1`)

Inspect flow, quality, and coordination metrics. The task is performed by `role.process-reviewer`. It consumes `e2e-artifact.operational-work-item`, `e2e-artifact.service-flow-system`, `e2e-artifact.increment-record`, `e2e-artifact.operations-record`, `e2e-artifact.product-charter`, `e2e-artifact.method-profile`, `e2e-artifact.release-record` and produces or updates `e2e-artifact.operations-record`, `e2e-artifact.method-profile`, `e2e-artifact.service-flow-system`.

**Work.**

- Review planned-delivery forecast, Kanban WIP, throughput, cycle time, work-item age, SLE attainment, demand arrival rate, expedite frequency, rework, trace coverage, blockers, dependency age, escaped defects, and product outcomes.
- Look for systemic queues, starvation between planned and service work, missing work products, invalid gates, and coordination failures.
- Approve bounded changes to the Definition of Workflow, capacity policy, DevOps interface, or method profile and record their expected effect.

**Exit.** Inspect flow, quality, and coordination metrics evidence is recorded.

**Checks.** Metrics lead to inspectable improvement experiments rather than individual performance rankings.

#### Plan the vertical increment (`task.e2e.p0.increment-planning.t1`)

Plan the vertical increment. The task is performed by `role.product-owner`. It consumes `e2e-artifact.product-charter`, `e2e-artifact.method-profile`, `e2e-artifact.team-topology` and produces or updates `e2e-artifact.increment-record`.

**Work.**

- Select scope from the ordered product backlog.
- Define outcome, acceptance criteria, dependencies, risks, and expected model/artifact evidence.
- Assign scope items to owning teams and establish the increment ledger.

**Exit.** Increment scope, owners, acceptance evidence, and dependencies are agreed.

**Checks.** The slice is small enough to inspect end-to-end and large enough to demonstrate a user or operational outcome.

#### Run the CIM increment (`task.e2e.p1.cim-modeling.t1`)

Run the CIM increment. The task is performed by `role.business-modeler`. It consumes `e2e-artifact.increment-record`, `e2e-artifact.product-charter`, `e2e-artifact.method-profile`, `e2e-artifact.team-topology` and produces or updates `e2e-artifact.increment-record`.

**Work.**

- Execute the CIM process for the selected capability slice.
- Record semantic decisions, assumptions, trace links, readiness findings, and accepted CIM revision.
- Return unresolved cross-team or product decisions to the integrated decision register.

**Exit.** CIM gate evidence is accepted or explicitly reworked.

**Checks.** Assistant-applied model changes pass structural Ecore/EMF conformance; semantic validation remains an explicit user/model validation workflow.

#### Execute and inspect CIM-to-PIM transformation (`task.e2e.p2.cim-to-pim.t1`)

Execute and inspect CIM-to-PIM transformation. The task is performed by `role.solution-architect`. It consumes `e2e-artifact.increment-record`, `e2e-artifact.method-profile`, `e2e-artifact.product-charter` and produces or updates `e2e-artifact.increment-record`.

**Work.**

- Pin the source CIM revision and transformation profile.
- Run the transformation and inspect report, trace links, assumptions, and manual decisions.
- Accept, reject, or route each material decision and record the target PIM revision.

**Exit.** Execute and inspect CIM-to-PIM transformation evidence is recorded.

**Checks.** Every generated target scope item is traceable to a source or explicit transformation decision.

#### Run the PIM increment (`task.e2e.p3.pim-refinement.t1`)

Run the PIM increment. The task is performed by `role.solution-architect`. It consumes `e2e-artifact.increment-record`, `e2e-artifact.product-charter`, `e2e-artifact.method-profile` and produces or updates `e2e-artifact.increment-record`.

**Work.**

- Execute the PIM process for the transformed slice.
- Review contracts, data access, integration, policy, platform mapping, and operational assumptions.
- Record accepted PIM revision and open platform decisions.

**Exit.** PIM gate evidence is accepted or explicitly reworked.

**Checks.** Generated PIM is treated as scaffolding until the solution architect accepts material decisions.

#### Execute and inspect PIM-to-PSM transformation (`task.e2e.p4.pim-to-psm.t1`)

Execute and inspect PIM-to-PSM transformation. The task is performed by `role.cloud-platform-engineer`. It consumes `e2e-artifact.increment-record`, `e2e-artifact.method-profile`, `e2e-artifact.product-charter` and produces or updates `e2e-artifact.increment-record`.

**Work.**

- Pin the source PIM revision and AWS mapping profile.
- Run the transformation and inspect resource mappings, assumptions, security implications, and trace links.
- Record accepted target PSM revision or route mapping gaps back to PIM.

**Exit.** Execute and inspect PIM-to-PSM transformation evidence is recorded.

**Checks.** Every deployable PIM scope item has an accepted AWS mapping or a documented exception.

#### Run the PSM increment (`task.e2e.p5.psm-refinement.t1`)

Run the PSM increment. The task is performed by `role.cloud-platform-engineer`. It consumes `e2e-artifact.increment-record`, `e2e-artifact.method-profile`, `e2e-artifact.product-charter` and produces or updates `e2e-artifact.increment-record`.

**Work.**

- Execute the AWS PSM process for the transformed slice.
- Review resource relationships, IAM, networking, data, eventing, APIs, workflows, and operational views.
- Record accepted PSM revision and unresolved deployment risks.

**Exit.** PSM gate evidence is accepted or explicitly reworked.

**Checks.** AWS platform choices are traceable to accepted PIM intent or explicit platform decisions.

#### Generate and fingerprint the artifact baseline (`task.e2e.p6.m2t-generation.t1`)

Generate and fingerprint the artifact baseline. The task is performed by `role.cloud-platform-engineer`. It consumes `e2e-artifact.increment-record`, `e2e-artifact.method-profile`, `e2e-artifact.team-topology` and produces or updates `e2e-artifact.increment-record`.

**Work.**

- Pin PSM revision, generator version, templates, and environment profile.
- Generate application, infrastructure, configuration, tests, and documentation artifacts.
- Record the generation manifest and route structural gaps to PSM or generator ownership.

**Exit.** Generate and fingerprint the artifact baseline evidence is recorded.

**Checks.** The generated baseline is reproducible from recorded inputs and contains no secret material.

#### Run artifact readiness and accept the increment (`task.e2e.p7.artifact-completion.t1`)

Run artifact readiness and accept the increment. The task is performed by `role.process-reviewer`. It consumes `e2e-artifact.increment-record`, `e2e-artifact.method-profile`, `e2e-artifact.product-charter` and produces or updates `e2e-artifact.increment-record`.

**Work.**

- Execute the artifact deployment-readiness process for the generated candidate.
- Review verification, security, rollback, operational, and acceptance evidence.
- Record increment outcome, open findings, deferred scope, and feedback loops for the next cycle.

**Exit.** Increment is accepted, explicitly deferred, or returned through a named rework loop.

**Checks.** No blocking finding is silently closed; risk acceptance is explicit, owned, time-bound, and traceable.

#### Define product outcomes and success measures (`task.e2e.ph0.st1.t1`)

Define product outcomes and success measures. The task is performed by `role.product-owner`. It consumes None declared and produces or updates `e2e-artifact.product-charter`.

**Work.**

- State the user or mission problem and desired outcomes.
- Define measurable product, operational, security, and quality outcomes.
- Record assumptions, constraints, non-goals, and the first release hypothesis.

**Exit.** Outcome measures and non-goals are accepted by stakeholders.

**Checks.** Every initial scope item is connected to an outcome or mandatory constraint.

#### Establish the initial release and increment hypothesis (`task.e2e.ph0.st1.t2`)

Establish the initial release and increment hypothesis. The task is performed by `role.requirements-engineer`. It consumes `e2e-artifact.product-charter` and produces or updates `e2e-artifact.product-charter`, `e2e-artifact.increment-record`.

**Work.**

- Identify the smallest useful vertical capability slice.
- Define acceptance signals and the evidence needed to call it usable.
- Record unresolved assumptions as owned decisions rather than hidden risks.

**Exit.** Establish the initial release and increment hypothesis evidence is recorded.

**Checks.** The first slice crosses the required lifecycle boundary and has observable acceptance evidence.

#### Assess context and process-tailoring risks (`task.e2e.ph0.st2.t1`)

Assess context and process-tailoring risks. The task is performed by `role.method-engineer`. It consumes `e2e-artifact.product-charter`, `e2e-artifact.increment-record` and produces or updates `e2e-artifact.method-profile`.

**Work.**

- Assess criticality, regulatory obligations, novelty, uncertainty, team distribution, system size, and delivery cadence.
- Select the required CIM, PIM, PSM, artifact, and lifecycle activities.
- Record excluded, combined, or delegated activities with rationale and compensating evidence.

**Exit.** Assess context and process-tailoring risks evidence is recorded.

**Checks.** Every tailoring decision names its context, consequence, owner, and review point.

#### Define the tailored Definition of Ready and Done (`task.e2e.ph0.st2.t2`)

Define the tailored Definition of Ready and Done. The task is performed by `role.process-reviewer`. It consumes `e2e-artifact.method-profile`, `e2e-artifact.product-charter`, `e2e-artifact.increment-record` and produces or updates `e2e-artifact.method-profile`.

**Work.**

- Define entry and exit evidence for model slices, transformations, release candidates, and operations.
- Define which findings are blocking and how time-bound risk acceptance works.
- Publish the profile version used by the process run.

**Exit.** Define the tailored Definition of Ready and Done evidence is recorded.

**Checks.** A task cannot be accepted solely because activity occurred; required evidence and gate criteria are explicit.

#### Define team ownership and interfaces (`task.e2e.ph0.st3.t1`)

Define team ownership and interfaces. The task is performed by `role.delivery-lead`. It consumes `e2e-artifact.method-profile`, `e2e-artifact.product-charter` and produces or updates `e2e-artifact.team-topology`.

**Work.**

- Partition work by bounded capability, service, platform concern, or lifecycle responsibility.
- Assign model ownership, repository ownership, decision rights, and backup owners.
- Define interface contracts and the shared dependency board.

**Exit.** Define team ownership and interfaces evidence is recorded.

**Checks.** Every owned model scope has one accountable owner and a named integration path.

#### Set coordination cadence and escalation paths (`task.e2e.ph0.st3.t2`)

Set coordination cadence and escalation paths. The task is performed by `role.delivery-lead`. It consumes `e2e-artifact.team-topology`, `e2e-artifact.method-profile`, `e2e-artifact.product-charter` and produces or updates `e2e-artifact.team-topology`, `e2e-artifact.method-profile`.

**Work.**

- Set local team syncs, cross-team integration reviews, increment reviews, release reviews, and retrospectives.
- Define escalation thresholds for aging dependencies, blocking findings, and conflicting model ownership.
- Define how concurrent model edits are merged, reviewed, and reconciled.

**Exit.** Set coordination cadence and escalation paths evidence is recorded.

**Checks.** Coordination events produce decisions, dependency status, and evidence links.

#### Define quality and security control objectives (`task.e2e.ph0.st4.t1`)

Define quality and security control objectives. The task is performed by `role.security-engineer`. It consumes `e2e-artifact.team-topology`, `e2e-artifact.method-profile`, `e2e-artifact.product-charter` and produces or updates `e2e-artifact.method-profile`.

**Work.**

- Identify privacy, threat, compliance, resilience, performance, accessibility, and audit obligations.
- Map control objectives to model evidence, generated artifact evidence, and explicit human decisions.
- Set severity and blocking policies.

**Exit.** Define quality and security control objectives evidence is recorded.

**Checks.** Critical controls have an accountable owner and verification evidence path.

#### Define operational and release strategy (`task.e2e.ph0.st4.t2`)

Define operational and release strategy. The task is performed by `role.service-owner`. It consumes `e2e-artifact.method-profile`, `e2e-artifact.team-topology`, `e2e-artifact.product-charter` and produces or updates `e2e-artifact.product-charter`, `e2e-artifact.method-profile`, `e2e-artifact.service-flow-system`.

**Work.**

- Define environments, support ownership, SLO hypotheses, observability expectations, rollback posture, and release cadence.
- Define the Kanban service-delivery system: board states and start/finish points, WIP controls, classes of service, service-level expectations, capacity allocation, replenishment and review cadences, and expedite/reconciliation policy.
- Identify data migration, compatibility, and progressive-delivery constraints and record the initial release decision policy.

**Exit.** Define operational and release strategy evidence is recorded.

**Checks.** The release strategy names promotion, rollback, and post-deployment validation evidence. The operational policy prevents unknown future maintenance demand from being represented as pre-scheduled tasks.

#### Approve retirement scope and plan (`task.e2e.ph2.st1.t1`)

Approve retirement scope and plan. The task is performed by `role.product-owner`. It consumes `e2e-artifact.operations-record`, `e2e-artifact.release-record`, `e2e-artifact.product-charter`, `e2e-artifact.method-profile` and produces or updates `e2e-artifact.retirement-record`.

**Work.**

- Confirm business, technical, legal, security, and operational reasons for retirement.
- Define replacement, user communication, compatibility, rollback, and exit criteria.
- Assign owners and schedule decision checkpoints.

**Exit.** Approve retirement scope and plan evidence is recorded.

**Checks.** The plan identifies affected stakeholders, dependencies, and irreversible actions.

#### Execute migration and data disposition (`task.e2e.ph2.st2.t1`)

Execute migration and data disposition. The task is performed by `role.cloud-platform-engineer`. It consumes `e2e-artifact.retirement-record`, `e2e-artifact.operations-record`, `e2e-artifact.release-record`, `e2e-artifact.method-profile` and produces or updates `e2e-artifact.retirement-record`.

**Work.**

- Execute migration, archival, retention, deletion, or export according to approved policy.
- Validate completeness, confidentiality, integrity, and recoverability where required.
- Record final data and integration evidence.

**Exit.** Execute migration and data disposition evidence is recorded.

**Checks.** No data or integration is silently abandoned.

#### Decommission service and access (`task.e2e.ph2.st2.t2`)

Decommission service and access. The task is performed by `role.service-owner`. It consumes `e2e-artifact.retirement-record`, `e2e-artifact.operations-record`, `e2e-artifact.release-record`, `e2e-artifact.team-topology` and produces or updates `e2e-artifact.retirement-record`.

**Work.**

- Continue required operational coverage during migration, then disable traffic, scheduled work, credentials, access paths, alerts, and environments in the approved order.
- Verify replacement ownership and customer communication.
- Retain required source, model, trace, release, incident, and decision records.

**Exit.** Decommission service and access evidence is recorded.

**Checks.** Decommission evidence covers runtime, data, access, cost, and support surfaces.

#### Complete closure review (`task.e2e.ph2.st3.t1`)

Complete closure review. The task is performed by `role.process-reviewer`. It consumes `e2e-artifact.retirement-record`, `e2e-artifact.operations-record`, `e2e-artifact.product-charter`, `e2e-artifact.increment-record`, `e2e-artifact.method-profile`, `e2e-artifact.release-record` and produces or updates `e2e-artifact.retirement-record`, `e2e-artifact.method-profile`.

**Work.**

- Confirm retirement exit criteria and records are complete.
- Review product, architecture, operational, and process outcomes.
- Publish reusable patterns, risks, and process changes for future projects.

**Exit.** Complete closure review evidence is recorded.

**Checks.** Closure identifies retained evidence, unresolved obligations, and accountable custodians.

#### Assemble the release candidate (`task.e2e.rel.a1.t1`)

Assemble the release candidate. The task is performed by `role.release-engineer`. It consumes `e2e-artifact.increment-record`, `e2e-artifact.product-charter`, `e2e-artifact.method-profile`, `e2e-artifact.team-topology` and produces or updates `e2e-artifact.release-record`.

**Work.**

- Select accepted increments and compatible model/artifact revisions.
- Resolve cross-team dependency and compatibility checks.
- Create the release record with exact inputs and promotion sequence.

**Exit.** Assemble the release candidate evidence is recorded.

**Checks.** Every release scope item maps to an accepted increment and exact artifact identity.

#### Review release evidence and go/no-go criteria (`task.e2e.rel.a1.t2`)

Review release evidence and go/no-go criteria. The task is performed by `role.process-reviewer`. It consumes `e2e-artifact.release-record`, `e2e-artifact.increment-record`, `e2e-artifact.product-charter`, `e2e-artifact.method-profile` and produces or updates `e2e-artifact.release-record`.

**Work.**

- Inspect readiness assessments, validation results, security exceptions, rollback plan, operational runbooks, and approvals.
- Confirm open blockers are zero or explicitly accepted under the tailored method profile.
- Record the go/no-go decision and decision owners.

**Exit.** Review release evidence and go/no-go criteria evidence is recorded.

**Checks.** Go/no-go is evidence-based and not inferred from task completion.

#### Deploy and validate progressively (`task.e2e.rel.a2.t1`)

Deploy and validate progressively. The task is performed by `role.release-engineer`. It consumes `e2e-artifact.release-record`, `e2e-artifact.product-charter`, `e2e-artifact.method-profile`, `e2e-artifact.team-topology` and produces or updates `e2e-artifact.release-record`.

**Work.**

- Deploy the exact candidate to the approved environment sequence.
- Run smoke, functional, security, data, observability, and compatibility checks.
- Compare observed outcomes with release acceptance signals and stop or roll back on threshold breach.

**Exit.** Deploy and validate progressively evidence is recorded.

**Checks.** Promotion evidence identifies candidate, environment, timestamp, operator, and observed result.

#### Complete handover and rollback rehearsal (`task.e2e.rel.a2.t2`)

Complete handover and rollback rehearsal. The task is performed by `role.service-owner`. It consumes `e2e-artifact.release-record`, `e2e-artifact.product-charter`, `e2e-artifact.method-profile`, `e2e-artifact.team-topology` and produces or updates `e2e-artifact.release-record`, `e2e-artifact.operations-record`.

**Work.**

- Verify dashboards, alerts, runbooks, escalation paths, support ownership, and recovery access.
- Confirm rollback and data-recovery actions are usable for the release.
- Accept operational ownership or return the release to rework.

**Exit.** Complete handover and rollback rehearsal evidence is recorded.

**Checks.** Operational acceptance names an accountable service owner and recovery evidence.

#### Review release outcome and update roadmap (`task.e2e.rel.a3.t1`)

Review release outcome and update roadmap. The task is performed by `role.product-owner`. It consumes `e2e-artifact.release-record`, `e2e-artifact.operations-record`, `e2e-artifact.product-charter`, `e2e-artifact.increment-record`, `e2e-artifact.method-profile` and produces or updates `e2e-artifact.release-record`, `e2e-artifact.product-charter`.

**Work.**

- Compare product and operational outcomes with the release hypothesis.
- Accept outcomes, revise priorities, and create follow-up increments for gaps.
- Record decisions and changes to scope, measures, and assumptions.

**Exit.** Review release outcome and update roadmap evidence is recorded.

**Checks.** Outcome learning changes a backlog, product decision, or explicitly confirms the hypothesis.

### CIM modeling component (26 tasks)

#### Create CIM model root (`task.cim.ph1.st1.t1`)

Create CIM model root. The task is performed by `role.business-modeler`. It consumes None declared and produces or updates `cim-artifact.model-root`.

**Entry.** Project created or existing CIM opened for a new increment.

**Work.**

- Create or verify CIMModel with domainName and businessScope.
- Set organizationName, modelingDate, and language.
- Apply lifecycle status and annotation conventions on the root.

**Exit.** CIMModel root with domainName exists.

Indicative duration: 20m.

#### Establish shared model contract and evidence conventions (`task.cim.ph1.st1.t2`)

Establish shared model contract and evidence conventions. The task is performed by `role.method-engineer`. It consumes `cim-artifact.model-root` and produces or updates `cim-artifact.model-root`.

**Entry.** CIM model root exists.

**Work.**

- Apply the shared identity, annotation, traceability, expression, and lifecycle conventions used by every CIM element.
- Set the evidence convention for source references, review status, and model-level provenance.
- Confirm that shared support concepts are handled by inspectors and readiness records rather than mistaken for business concepts.

**Exit.** Shared model contract and evidence convention are recorded.

Indicative duration: 30m.

#### Plan capability slice (`task.cim.ph1.st2.t1`)

Plan capability slice. The task is performed by `role.business-modeler`. It consumes `cim-artifact.model-root` and produces or updates `cim-artifact.increment-plan`.

**Entry.** CIM model root exists.

**Work.**

- Select one capability or bounded-context candidate from the modeling backlog.
- Record slice objective, scope boundaries, assumptions, and definition of done.
- Identify the validation gate and review participants for the cycle.

**Exit.** Capability-slice scope and definition of done are agreed.

Indicative duration: 30m.

#### Define business goals and KPIs (`task.cim.ph1.st3.t1`)

Define business goals and KPIs. The task is performed by `role.business-modeler`. It consumes `cim-artifact.increment-plan` and produces or updates `cim-artifact.strategic-intent`.

**Entry.** Capability-slice scope agreed.

**Work.**

- Capture BusinessGoal elements with success criteria.
- Define measurable KPIs linked to each goal.

**Exit.** At least one goal with linked KPI.

**Checks.** CIM-GOAL-001. CIM-KPI-001.

Indicative duration: 45m.

#### Identify stakeholders (`task.cim.ph1.st3.t2`)

Identify stakeholders. The task is performed by `role.business-modeler`. It consumes `cim-artifact.strategic-intent`, `cim-artifact.increment-plan` and produces or updates `cim-artifact.strategic-intent`.

**Entry.** Goals drafted.

**Work.**

- Register stakeholders and their concerns linked to goals.

**Exit.** Stakeholders identified for primary outcomes.

Indicative duration: 30m.

#### Model actors and roles (`task.cim.ph2.st1.t1`)

Model actors and roles. The task is performed by `role.business-modeler`. It consumes `cim-artifact.strategic-intent`, `cim-artifact.increment-plan` and produces or updates `cim-artifact.participation-model`.

**Entry.** Strategic intent captured.

**Work.**

- Model human and system Actor elements with trust levels.
- Define Role elements and link to actors.

**Exit.** Actors exist for primary user journeys.

Indicative duration: 45m.

#### Register external systems (`task.cim.ph2.st1.t2`)

Register external systems. The task is performed by `role.business-modeler`. It consumes `cim-artifact.participation-model`, `cim-artifact.strategic-intent` and produces or updates `cim-artifact.participation-model`.

**Entry.** Actors modeled.

**Work.**

- Register ExternalSystem actors at domain boundaries.

**Exit.** External integrations at boundaries identified.

Indicative duration: 20m.

#### Map business capabilities (`task.cim.ph2.st2.t1`)

Map business capabilities. The task is performed by `role.business-modeler`. It consumes `cim-artifact.participation-model`, `cim-artifact.strategic-intent` and produces or updates `cim-artifact.capability-map`.

**Entry.** Participation model drafted.

**Work.**

- Create BusinessCapability elements linked to goals.
- Set criticality for each capability in the increment slice.

**Exit.** Capabilities cover core value streams for slice.

Indicative duration: 1h.

#### Record capability dependencies (`task.cim.ph2.st2.t2`)

Record capability dependencies. The task is performed by `role.business-modeler`. It consumes `cim-artifact.capability-map`, `cim-artifact.strategic-intent` and produces or updates `cim-artifact.capability-map`.

**Entry.** Capability drafted.

**Work.**

- Model CapabilityDependency relationships between capabilities.

**Exit.** Dependencies documented for slice.

Indicative duration: 30m.

#### Define domain glossary (`task.cim.ph2.st3.t1`)

Define domain glossary. The task is performed by `role.business-modeler`. It consumes `cim-artifact.capability-map`, `cim-artifact.strategic-intent` and produces or updates `cim-artifact.glossary`.

**Entry.** Capability drafted.

**Work.**

- Define UbiquitousLanguageTerm entries with definitions and examples.
- Link terms to capabilities where helpful.
- Resolve naming conflicts before entity modeling.

**Exit.** Glossary covers core domain nouns for slice.

Indicative duration: 45m.

#### Define data classifications (`task.cim.ph3.st1.ss1.t1`)

Define data classifications. The task is performed by `role.business-modeler`. It consumes `cim-artifact.glossary`, `cim-artifact.strategic-intent` and produces or updates `cim-artifact.information-taxonomy`.

**Entry.** Glossary established.

**Work.**

- Create DataClassification elements with confidentiality levels.

**Exit.** Classifications cover sensitive categories.

Indicative duration: 30m.

#### Create information items (`task.cim.ph3.st1.ss2.t1`)

Create information items. The task is performed by `role.business-modeler`. It consumes `cim-artifact.information-taxonomy`, `cim-artifact.glossary` and produces or updates `cim-artifact.information-taxonomy`.

**Entry.** Classifications defined.

**Work.**

- Create InformationItem elements for each planned entity attribute group.
- Set identifiability and data kind on each item.

**Exit.** Information items exist for planned entities.

**Checks.** CIM-ENTITY-001.

Indicative duration: 1h.

#### Model domain entities (`task.cim.ph3.st2.ss1.t1`)

Model domain entities. The task is performed by `role.business-modeler`. It consumes `cim-artifact.information-taxonomy`, `cim-artifact.glossary`, `cim-artifact.capability-map` and produces or updates `cim-artifact.domain-structure`.

**Entry.** Information taxonomy complete.

**Work.**

- Create DomainEntity elements referencing primary InformationItem identity.
- Define lifecycle states and business invariants per entity.

**Exit.** Core entities for slice modeled.

**Checks.** CIM-ENTITY-001.

Indicative duration: 1-2h.

#### Model value objects and relationships (`task.cim.ph3.st2.ss2.t1`)

Model value objects and relationships. The task is performed by `role.business-modeler`. It consumes `cim-artifact.domain-structure`, `cim-artifact.information-taxonomy` and produces or updates `cim-artifact.domain-structure`.

**Entry.** Entities modeled.

**Work.**

- Add ValueObject elements for descriptive data.
- Model DomainRelationship elements between concepts.

**Exit.** Relationships and VOs complete for slice.

**Checks.** CIM-RELATIONSHIP-001.

Indicative duration: 1h.

#### Model commands and outcomes (`task.cim.ph3.st3.ss1.t1`)

Model commands and outcomes. The task is performed by `role.business-modeler`. It consumes `cim-artifact.domain-structure`, `cim-artifact.participation-model`, `cim-artifact.capability-map` and produces or updates `cim-artifact.behavior-surface`.

**Entry.** Domain structure drafted.

**Work.**

- Create Command elements with actors and target capabilities.
- Define CommandOutcome and link expected/rejection events.

**Exit.** Commands cover primary write use cases.

Indicative duration: 1h.

#### Model queries (`task.cim.ph3.st3.ss2.t1`)

Model queries. The task is performed by `role.business-modeler`. It consumes `cim-artifact.behavior-surface`, `cim-artifact.information-taxonomy`, `cim-artifact.participation-model` and produces or updates `cim-artifact.behavior-surface`.

**Entry.** Commands modeled.

**Work.**

- Create Query elements linked to actors and information items.
- Set freshness requirements per query.

**Exit.** Queries cover primary read use cases.

Indicative duration: 45m.

#### Model events, errors, and conditions (`task.cim.ph3.st3.ss3.t1`)

Model events, errors, and conditions. The task is performed by `role.business-modeler`. It consumes `cim-artifact.behavior-surface`, `cim-artifact.domain-structure`, `cim-artifact.participation-model` and produces or updates `cim-artifact.behavior-surface`.

**Entry.** Commands and queries modeled.

**Work.**

- Create BusinessEvent elements emitted by commands or external systems.
- Define BusinessError and Condition elements guarding behavior.

**Exit.** Event vocabulary covers slice workflows.

Indicative duration: 1h.

#### Define aggregate candidates (`task.cim.ph4.st1.t1`)

Define aggregate candidates. The task is performed by `role.business-modeler`. It consumes `cim-artifact.behavior-surface`, `cim-artifact.domain-structure`, `cim-artifact.capability-map` and produces or updates `cim-artifact.aggregate-model`.

**Entry.** Behavior surface modeled.

**Work.**

- Create AggregateCandidate groupings with handled commands and emitted events.
- Document consistency and interaction expectations.

**Exit.** Aggregates align with command/event ownership.

Indicative duration: 1h.

#### Model business processes (`task.cim.ph4.st2.ss1.t1`)

Model business processes. The task is performed by `role.business-modeler`. It consumes `cim-artifact.behavior-surface`, `cim-artifact.domain-structure`, `cim-artifact.strategic-intent`, `cim-artifact.aggregate-model` and produces or updates `cim-artifact.process-model`.

**Entry.** Aggregates defined.

**Work.**

- Create BusinessProcess with step hierarchy and transitions.
- Connect steps to modeled commands, queries, events, and policies.

**Exit.** Key processes orchestrate modeled behavior.

Indicative duration: 2h.

#### Define policies and decision tables (`task.cim.ph4.st2.ss2.t1`)

Define policies and decision tables. The task is performed by `role.business-modeler`. It consumes `cim-artifact.process-model`, `cim-artifact.behavior-surface`, `cim-artifact.domain-structure` and produces or updates `cim-artifact.decision-model`.

**Entry.** Processes drafted.

**Work.**

- Create Policy elements guarding commands, events, and processes.
- Model DecisionTable with DecisionRule rows for branching logic.

**Exit.** Policies linked to behavior elements.

Indicative duration: 1h.

#### Synthesize bounded contexts (`task.cim.ph4.st3.t1`)

Synthesize bounded contexts. The task is performed by `role.business-modeler`. It consumes `cim-artifact.capability-map`, `cim-artifact.domain-structure`, `cim-artifact.behavior-surface`, `cim-artifact.process-model`, `cim-artifact.decision-model` and produces or updates `cim-artifact.context-map`.

**Entry.** Process and behavior complete.

**Work.**

- Group capabilities, entities, CQRS, events, and policies into contexts.
- Validate memberships reference concrete modeled elements.

**Exit.** Contexts have non-empty memberships.

Indicative duration: 1h.

#### Formalize requirements (`task.cim.ph5.st1.t1`)

Formalize requirements. The task is performed by `role.requirements-engineer`. It consumes `cim-artifact.strategic-intent`, `cim-artifact.domain-structure`, `cim-artifact.behavior-surface`, `cim-artifact.context-map`, `cim-artifact.decision-model` and produces or updates `cim-artifact.requirements-package`.

**Entry.** Bounded contexts synthesized.

**Work.**

- Create Requirement elements with constrains links to domain and behavior.
- Add AcceptanceCriterion per requirement.

**Exit.** Requirements trace to goals and model elements.

Indicative duration: 1-2h.

#### Apply governance constraints (`task.cim.ph5.st1.t2`)

Apply governance constraints. The task is performed by `role.requirements-engineer`. It consumes `cim-artifact.requirements-package`, `cim-artifact.strategic-intent`, `cim-artifact.participation-model`, `cim-artifact.information-taxonomy`, `cim-artifact.domain-structure` and produces or updates `cim-artifact.governance-package`.

**Entry.** Requirements drafted.

**Work.**

- Record security, privacy, and compliance constraints on actors and information items.

**Exit.** Governance constraints linked to targets.

Indicative duration: 1h.

#### Record transformation metadata (`task.cim.ph5.st2.t1`)

Record transformation metadata. The task is performed by `role.business-modeler`. It consumes `cim-artifact.governance-package`, `cim-artifact.requirements-package`, `cim-artifact.context-map`, `cim-artifact.domain-structure`, `cim-artifact.decision-model` and produces or updates `cim-artifact.transformation-contract`.

**Entry.** Governance captured.

**Work.**

- Record Risk, Assumption, and Hotspot elements on model hotspots.
- Define TransformationProfile for CIM→PIM with manual decisions.

**Exit.** Transformation profile ready for ETL.

Indicative duration: 45m.

#### Complete trace and readiness (`task.cim.ph5.st3.t1`)

Complete trace and readiness. The task is performed by `role.process-reviewer`. It consumes `cim-artifact.transformation-contract`, `cim-artifact.strategic-intent`, `cim-artifact.requirements-package`, `cim-artifact.domain-structure`, `cim-artifact.behavior-surface`, `cim-artifact.context-map`, `cim-artifact.governance-package`, `cim-artifact.process-model`, `cim-artifact.decision-model` and produces or updates `cim-artifact.trace-readiness`.

**Entry.** Transformation contracts recorded.

**Work.**

- Build TraceModel links across goals, requirements, and domain.
- Complete ProductionReadinessAssessment and resolve findings.

**Exit.** CIM EVL passes; readiness approved.

**Checks.** cim-semantic-validation.

Indicative duration: 1-2h.

#### Review and adapt CIM increment (`task.cim.ph5.st4.t1`)

Review and adapt CIM increment. The task is performed by `role.process-reviewer`. It consumes `cim-artifact.trace-readiness`, `cim-artifact.strategic-intent`, `cim-artifact.increment-plan`, `cim-artifact.requirements-package`, `cim-artifact.governance-package` and produces or updates `cim-artifact.increment-review`.

**Entry.** CIM EVL passes or all blocking findings are dispositioned.

**Work.**

- Review slice outcomes against goals, KPIs, and acceptance criteria.
- Record accepted scope, deferred work, and stakeholder feedback.
- Create improvement actions and backlog adjustments for the next engine cycle.

**Exit.** Increment accepted or rework loop selected; improvement actions captured.

Indicative duration: 45m.

### PIM modeling component (32 tasks)

#### Plan service slice (`task.pim.ph1.st0.t1`)

Plan service slice. The task is performed by `role.solution-architect`. It consumes None declared and produces or updates `pim-artifact.increment-plan`.

**Entry.** CIM transform complete, prior PIM increment selected, or greenfield PIM.

**Work.**

- Select one service slice traced to CIM goals, bounded contexts, and behavior.
- Record scope boundaries, architectural risks, manual transform decisions, and definition of done.
- Confirm the expected PIM EVL gate and review participants for the cycle.

**Exit.** Service-slice scope and definition of done are agreed.

Indicative duration: 30m.

#### Create PIM model root (`task.pim.ph1.st1.t1`)

Create PIM model root. The task is performed by `role.solution-architect`. It consumes `pim-artifact.increment-plan` and produces or updates `pim-artifact.architecture-posture`.

**Entry.** Service-slice scope agreed.

**Work.**

- Create or verify PIMModel with domain linkage to CIM source.
- Set modeling date, lifecycle status, and annotation conventions.

**Exit.** PIMModel root exists.

Indicative duration: 20m.

#### Set architecture posture (`task.pim.ph1.st1.t2`)

Set architecture posture. The task is performed by `role.solution-architect`. It consumes `pim-artifact.architecture-posture`, `pim-artifact.increment-plan` and produces or updates `pim-artifact.architecture-posture`.

**Entry.** PIM model root exists.

**Work.**

- Configure ImplementationProfile with serverless posture and platform assumptions.
- Select ArchitectureStyle and document key architectural decisions.

**Exit.** Architecture style and profile configured.

Indicative duration: 30m.

#### Establish shared model contract and evidence conventions (`task.pim.ph1.st1.t3`)

Establish shared model contract and evidence conventions. The task is performed by `role.method-engineer`. It consumes `pim-artifact.architecture-posture`, `pim-artifact.increment-plan` and produces or updates `pim-artifact.architecture-posture`.

**Entry.** PIM model root exists.

**Work.**

- Apply shared identity, annotation, traceability, expression, and lifecycle conventions across PIM elements.
- Record the revision, transformation provenance, and review-state convention for the service slice.
- Keep support concepts in inspectors and readiness evidence instead of exposing them as false deployable architecture nodes.

**Exit.** Shared model contract and evidence convention are recorded.

Indicative duration: 30m.

#### Define serverless services (`task.pim.ph1.st2.t1`)

Define serverless services. The task is performed by `role.solution-architect`. It consumes `pim-artifact.architecture-posture`, `pim-artifact.increment-plan` and produces or updates `pim-artifact.service-map`.

**Entry.** Architecture posture set.

**Work.**

- Create ServerlessService elements aligned to CIM bounded contexts.
- Set boundary type and ownership scope per service.

**Exit.** Services defined for increment slice.

Indicative duration: 1h.

#### Assign element memberships (`task.pim.ph1.st2.t2`)

Assign element memberships. The task is performed by `role.solution-architect`. It consumes `pim-artifact.service-map`, `pim-artifact.architecture-posture` and produces or updates `pim-artifact.service-map`.

**Entry.** Services defined.

**Work.**

- Create ServiceElementMembership links from services to planned elements.
- Set OwnershipKind for each membership.

**Exit.** Memberships cover deployable boundaries.

Indicative duration: 45m.

#### Define schemas and fields (`task.pim.ph2.st1.t1`)

Define schemas and fields. The task is performed by `role.solution-architect`. It consumes `pim-artifact.service-map`, `pim-artifact.architecture-posture`, `pim-artifact.increment-plan` and produces or updates `pim-artifact.contract-catalog`.

**Entry.** Service boundaries defined.

**Work.**

- Create Schema elements with fields and enum literals.
- Set schema kind and field types aligned to CIM information items.

**Exit.** Schemas cover API and message payloads.

Indicative duration: 1-2h.

#### Model event types and envelopes (`task.pim.ph2.st1.t2`)

Model event types and envelopes. The task is performed by `role.solution-architect`. It consumes `pim-artifact.contract-catalog`, `pim-artifact.service-map` and produces or updates `pim-artifact.contract-catalog`.

**Entry.** Schemas defined.

**Work.**

- Define EventType and EventEnvelope elements from CIM events.
- Apply validation constraints and compatibility rules.

**Exit.** Event contracts aligned with CIM behavior surface.

Indicative duration: 1h.

#### Model data stores and models (`task.pim.ph2.st2.t1`)

Model data stores and models. The task is performed by `role.solution-architect`. It consumes `pim-artifact.contract-catalog`, `pim-artifact.service-map`, `pim-artifact.architecture-posture` and produces or updates `pim-artifact.data-architecture`.

**Entry.** Contracts defined.

**Work.**

- Create DataStore and ObjectStore elements per service.
- Define DataModel and DataField structures from CIM entities.

**Exit.** Stores and models cover domain data.

Indicative duration: 1-2h.

#### Define access patterns and indexes (`task.pim.ph2.st2.t2`)

Define access patterns and indexes. The task is performed by `role.solution-architect`. It consumes `pim-artifact.data-architecture`, `pim-artifact.contract-catalog`, `pim-artifact.service-map` and produces or updates `pim-artifact.data-architecture`.

**Entry.** Data models drafted.

**Work.**

- Model AccessPattern elements from CIM queries and commands.
- Define IndexCandidate and DataAccess bindings per store.

**Exit.** Access patterns cover read/write use cases.

Indicative duration: 1h.

#### Configure change streams and notifications (`task.pim.ph2.st2.t3`)

Configure change streams and notifications. The task is performed by `role.solution-architect`. It consumes `pim-artifact.data-architecture`, `pim-artifact.contract-catalog`, `pim-artifact.service-map` and produces or updates `pim-artifact.data-architecture`.

**Entry.** Access patterns defined.

**Work.**

- Define DataChangeStream elements for event-sourced projections.
- Configure ObjectNotificationRule for object store triggers.

**Exit.** Change streams linked to event contracts.

Indicative duration: 45m.

#### Define functions and contracts (`task.pim.ph3.st1.t1`)

Define functions and contracts. The task is performed by `role.solution-architect`. It consumes `pim-artifact.data-architecture`, `pim-artifact.service-map`, `pim-artifact.contract-catalog` and produces or updates `pim-artifact.compute-catalog`.

**Entry.** Data architecture drafted.

**Work.**

- Create Function elements with FunctionContract bindings.
- Set compute profile and execution model per handler.

**Exit.** Functions mapped to CIM commands and queries.

Indicative duration: 1-2h.

#### Configure triggers and runtime (`task.pim.ph3.st1.t2`)

Configure triggers and runtime. The task is performed by `role.solution-architect`. It consumes `pim-artifact.compute-catalog`, `pim-artifact.contract-catalog` and produces or updates `pim-artifact.compute-catalog`.

**Entry.** Functions defined.

**Work.**

- Define Trigger elements linking functions to events and schedules.
- Set runtime language and package manager per function.

**Exit.** Triggers cover behavioral entry points.

Indicative duration: 1h.

#### Define APIs and routes (`task.pim.ph3.st2.t1`)

Define APIs and routes. The task is performed by `role.solution-architect`. It consumes `pim-artifact.compute-catalog`, `pim-artifact.contract-catalog`, `pim-artifact.service-map` and produces or updates `pim-artifact.api-catalog`.

**Entry.** Compute units defined.

**Work.**

- Create Api elements with routes and HTTP methods.
- Connect routes to function handlers.

**Exit.** API routes cover public endpoints.

Indicative duration: 1h.

#### Map API contracts and errors (`task.pim.ph3.st2.t2`)

Map API contracts and errors. The task is performed by `role.solution-architect`. It consumes `pim-artifact.api-catalog`, `pim-artifact.contract-catalog`, `pim-artifact.compute-catalog` and produces or updates `pim-artifact.api-catalog`.

**Entry.** API routes defined.

**Work.**

- Bind ApiContract elements to schema definitions.
- Map ErrorMapping responses to CIM business errors.

**Exit.** Contracts and error mappings complete.

Indicative duration: 45m.

#### Model event channels and buses (`task.pim.ph4.st1.t1`)

Model event channels and buses. The task is performed by `role.solution-architect`. It consumes `pim-artifact.api-catalog`, `pim-artifact.contract-catalog`, `pim-artifact.compute-catalog`, `pim-artifact.service-map` and produces or updates `pim-artifact.integration-topology`.

**Entry.** API surface defined.

**Work.**

- Create EventChannel, Queue, Topic, and EventBus elements.
- Set delivery semantics and ordering requirements.

**Exit.** Channels cover async integration points.

Indicative duration: 1h.

#### Define flows and routing rules (`task.pim.ph4.st1.t2`)

Define flows and routing rules. The task is performed by `role.solution-architect`. It consumes `pim-artifact.integration-topology`, `pim-artifact.api-catalog`, `pim-artifact.contract-catalog`, `pim-artifact.compute-catalog` and produces or updates `pim-artifact.integration-topology`.

**Entry.** Channels defined.

**Work.**

- Model Flow variants connecting producers and consumers.
- Define EventRoutingRule and Subscription bindings.
- Configure Schedule triggers for periodic integration.

**Exit.** Flows connect integration endpoints to functions.

Indicative duration: 1-2h.

#### Model workflows and states (`task.pim.ph4.st2.t1`)

Model workflows and states. The task is performed by `role.solution-architect`. It consumes `pim-artifact.integration-topology`, `pim-artifact.service-map`, `pim-artifact.api-catalog`, `pim-artifact.contract-catalog` and produces or updates `pim-artifact.workflow-model`.

**Entry.** Integration topology defined.

**Work.**

- Create Workflow elements from CIM business processes.
- Define typed workflow steps, transitions, and workflow kind.

**Exit.** Workflows cover long-running CIM processes.

Indicative duration: 1-2h.

#### Configure human tasks and error handling (`task.pim.ph4.st2.t2`)

Configure human tasks and error handling. The task is performed by `role.solution-architect`. It consumes `pim-artifact.workflow-model`, `pim-artifact.integration-topology`, `pim-artifact.service-map`, `pim-artifact.api-catalog`, `pim-artifact.contract-catalog` and produces or updates `pim-artifact.workflow-model`.

**Entry.** Workflows drafted.

**Work.**

- Add HumanTask and ApprovalTask elements with escalation policies.
- Define CompensationPolicy and ErrorHandler for failure paths.

**Exit.** Human steps and error paths orchestrated.

Indicative duration: 1h.

#### Configure identity providers and principals (`task.pim.ph5.st1.t1`)

Configure identity providers and principals. The task is performed by `role.solution-architect`. It consumes `pim-artifact.architecture-posture`, `pim-artifact.service-map`, `pim-artifact.contract-catalog` and produces or updates `pim-artifact.security-model`.

**Entry.** Workflow orchestration drafted.

**Work.**

- Define IdentityProvider elements aligned to CIM actors.
- Register Principal elements for human and service identities.

**Exit.** Identity model covers all actor types.

Indicative duration: 45m.

#### Define permissions and security policies (`task.pim.ph5.st1.t2`)

Define permissions and security policies. The task is performed by `role.solution-architect`. It consumes `pim-artifact.security-model`, `pim-artifact.architecture-posture`, `pim-artifact.service-map`, `pim-artifact.api-catalog`, `pim-artifact.compute-catalog`, `pim-artifact.contract-catalog` and produces or updates `pim-artifact.security-model`.

**Entry.** Principals configured.

**Work.**

- Create Permission elements with effect and action kind.
- Apply AuthPolicy and AuthorizationPolicy to APIs and functions.

**Exit.** Auth model covers all public endpoints.

Indicative duration: 1h.

#### Apply resilience policies (`task.pim.ph5.st2.ss1.t1`)

Apply resilience policies. The task is performed by `role.solution-architect`. It consumes `pim-artifact.security-model`, `pim-artifact.compute-catalog`, `pim-artifact.integration-topology`, `pim-artifact.service-map` and produces or updates `pim-artifact.policy-catalog`.

**Entry.** Security and identity configured.

**Work.**

- Define ResiliencePolicy on functions and integration flows.
- Configure RetryPolicy, DeadLetterPolicy, and TimeoutPolicy per target.
- Set IdempotencyPolicy for state-changing handlers.

**Exit.** Resilience policies applied to critical paths.

Indicative duration: 1h.

#### Configure throughput and ordering policies (`task.pim.ph5.st2.ss1.t2`)

Configure throughput and ordering policies. The task is performed by `role.solution-architect`. It consumes `pim-artifact.policy-catalog`, `pim-artifact.data-architecture`, `pim-artifact.api-catalog`, `pim-artifact.compute-catalog`, `pim-artifact.integration-topology` and produces or updates `pim-artifact.policy-catalog`.

**Entry.** Resilience policies applied.

**Work.**

- Set ConcurrencyPolicy and RateLimitPolicy on APIs and functions.
- Configure BatchPolicy, OrderingPolicy, and CachePolicy where needed.
- Apply BackupPolicy, RetentionPolicy, and CostPolicy to stores.

**Exit.** Throughput and cost policies configured.

Indicative duration: 45m.

#### Configure observability policies (`task.pim.ph5.st2.ss2.t1`)

Configure observability policies. The task is performed by `role.solution-architect`. It consumes `pim-artifact.policy-catalog`, `pim-artifact.compute-catalog`, `pim-artifact.api-catalog`, `pim-artifact.integration-topology`, `pim-artifact.workflow-model`, `pim-artifact.service-map` and produces or updates `pim-artifact.policy-catalog`.

**Entry.** Resilience policies applied.

**Work.**

- Define ObservabilityConfig per service.
- Apply LoggingPolicy, MetricPolicy, and TracingPolicy to functions and APIs.

**Exit.** Observability baseline configured.

Indicative duration: 45m.

#### Define alerts, SLOs, and CORS (`task.pim.ph5.st2.ss2.t2`)

Define alerts, SLOs, and CORS. The task is performed by `role.solution-architect`. It consumes `pim-artifact.policy-catalog`, `pim-artifact.api-catalog`, `pim-artifact.workflow-model`, `pim-artifact.service-map` and produces or updates `pim-artifact.policy-catalog`.

**Entry.** Observability baseline configured.

**Work.**

- Create AlertPolicy and Slo elements for critical endpoints.
- Apply CorsPolicy to public API routes.

**Exit.** Alerts and SLOs cover critical paths.

Indicative duration: 30m.

#### Apply governance and compliance policies (`task.pim.ph5.st2.ss3.t1`)

Apply governance and compliance policies. The task is performed by `role.solution-architect`. It consumes `pim-artifact.policy-catalog`, `pim-artifact.security-model`, `pim-artifact.data-architecture` and produces or updates `pim-artifact.policy-catalog`.

**Entry.** Observability policies configured.

**Work.**

- Define ArchitecturePolicy and PolicySetting elements.
- Apply DataProtectionPolicy and CompliancePolicy from CIM governance.

**Exit.** Governance policies linked to data and APIs.

Indicative duration: 45m.

#### Map business rules and decision models (`task.pim.ph5.st2.ss3.t2`)

Map business rules and decision models. The task is performed by `role.solution-architect`. It consumes `pim-artifact.policy-catalog`, `pim-artifact.workflow-model`, `pim-artifact.api-catalog`, `pim-artifact.service-map` and produces or updates `pim-artifact.policy-catalog`.

**Entry.** Governance policies applied.

**Work.**

- Create BusinessRule elements from CIM policies.
- Model DecisionModel with DecisionRule rows for branching logic.

**Exit.** Business rules linked to functions and workflows.

Indicative duration: 45m.

#### Model external endpoints and adapters (`task.pim.ph5.st3.t1`)

Model external endpoints and adapters. The task is performed by `role.solution-architect`. It consumes `pim-artifact.policy-catalog`, `pim-artifact.api-catalog`, `pim-artifact.integration-topology`, `pim-artifact.contract-catalog`, `pim-artifact.service-map`, `pim-artifact.architecture-posture` and produces or updates `pim-artifact.config-package`.

**Entry.** Architecture policies applied.

**Work.**

- Create ExternalEndpoint elements from CIM external systems.
- Define ExternalAdapter bindings to integration flows.

**Exit.** External integrations modeled.

Indicative duration: 45m.

#### Configure environments and deployment units (`task.pim.ph5.st3.t2`)

Configure environments and deployment units. The task is performed by `role.solution-architect`. It consumes `pim-artifact.config-package`, `pim-artifact.architecture-posture`, `pim-artifact.security-model`, `pim-artifact.compute-catalog`, `pim-artifact.api-catalog`, `pim-artifact.integration-topology` and produces or updates `pim-artifact.config-package`.

**Entry.** External integrations modeled.

**Work.**

- Define Environment and DeploymentUnit elements per stage.
- Configure ConfigurationSet, secrets, and credential requirements.
- Set ConfigParameter and EnvironmentVariable values per environment.

**Exit.** Environments and config complete for slice.

Indicative duration: 1h.

#### Assess platform capabilities (`task.pim.ph6.st1.t1`)

Assess platform capabilities. The task is performed by `role.process-reviewer`. It consumes `pim-artifact.config-package`, `pim-artifact.architecture-posture`, `pim-artifact.service-map`, `pim-artifact.data-architecture`, `pim-artifact.compute-catalog`, `pim-artifact.api-catalog`, `pim-artifact.integration-topology`, `pim-artifact.security-model`, `pim-artifact.policy-catalog` and produces or updates `pim-artifact.platform-readiness`.

**Entry.** External and config complete.

**Work.**

- Review PlatformCapability coverage against modeled elements.
- Complete PlatformMappingAssessment with gap analysis.

**Exit.** Platform mapping assessment documented.

Indicative duration: 1h.

#### Complete trace and readiness (`task.pim.ph6.st2.t1`)

Complete trace and readiness. The task is performed by `role.process-reviewer`. It consumes `pim-artifact.platform-readiness`, `pim-artifact.increment-plan`, `pim-artifact.architecture-posture`, `pim-artifact.service-map`, `pim-artifact.contract-catalog`, `pim-artifact.data-architecture`, `pim-artifact.compute-catalog`, `pim-artifact.api-catalog`, `pim-artifact.integration-topology`, `pim-artifact.workflow-model`, `pim-artifact.security-model`, `pim-artifact.policy-catalog`, `pim-artifact.config-package` and produces or updates `pim-artifact.platform-readiness`.

**Entry.** Platform mapping assessment complete.

**Work.**

- Build TraceModel links across CIM and PIM elements.
- Complete ProductionReadinessAssessment and resolve findings.

**Exit.** PIM EVL passes; readiness gate approved.

**Checks.** pim-semantic-validation.

Indicative duration: 1-2h.

#### Review and adapt PIM increment (`task.pim.ph6.st3.t1`)

Review and adapt PIM increment. The task is performed by `role.process-reviewer`. It consumes `pim-artifact.platform-readiness`, `pim-artifact.increment-plan`, `pim-artifact.architecture-posture`, `pim-artifact.security-model`, `pim-artifact.policy-catalog`, `pim-artifact.config-package` and produces or updates `pim-artifact.increment-review`.

**Entry.** PIM EVL passes or all blocking findings are dispositioned.

**Work.**

- Review architecture outcomes against CIM trace links and PIM definition of done.
- Record accepted scope, deferred architecture decisions, and platform mapping feedback.
- Create improvement actions and backlog adjustments for the next service slice.

**Exit.** Increment accepted or rework loop selected; improvement actions captured.

Indicative duration: 45m.

### AWS PSM modeling component (28 tasks)

#### Plan deployable slice (`task.psm.ph1.st0.t1`)

Plan deployable slice. The task is performed by `role.cloud-platform-engineer`. It consumes None declared and produces or updates `psm-artifact.increment-plan`.

**Entry.** PIM transform complete, prior PSM increment selected, or greenfield PSM.

**Work.**

- Select one PIM service slice or deployment unit to materialize on AWS.
- Record AWS account, region, stage, networking, IAM, and deployment assumptions.
- Define the PSM EVL gate, artifact-generation readiness criteria, and review participants.

**Exit.** Deployable-slice scope and definition of done are agreed.

Indicative duration: 30m.

#### Create AWS PSM model root (`task.psm.ph1.st1.t1`)

Create AWS PSM model root. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.increment-plan` and produces or updates `psm-artifact.deployment-strategy`.

**Entry.** Deployable-slice scope agreed.

**Work.**

- Create or verify AwsPsmModel with partition and region defaults.
- Link to PIM source model and set lifecycle metadata.

**Exit.** AwsPsmModel root exists.

Indicative duration: 20m.

#### Define stage and naming policies (`task.psm.ph1.st1.t2`)

Define stage and naming policies. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.deployment-strategy`, `psm-artifact.increment-plan` and produces or updates `psm-artifact.deployment-strategy`.

**Entry.** AWS PSM root exists.

**Work.**

- Configure AwsStage elements for dev, staging, and production.
- Define AwsNamingPolicy and AwsTaggingPolicy conventions.

**Exit.** Stage strategy and naming policies configured.

Indicative duration: 30m.

#### Establish shared model contract and evidence conventions (`task.psm.ph1.st1.t3`)

Establish shared model contract and evidence conventions. The task is performed by `role.method-engineer`. It consumes `psm-artifact.deployment-strategy`, `psm-artifact.increment-plan` and produces or updates `psm-artifact.deployment-strategy`.

**Entry.** AWS PSM root exists.

**Work.**

- Apply shared identity, annotation, traceability, expression, lifecycle, and release-provenance conventions across AWS resources.
- Record the source PIM revision, provider mapping revision, and review-state convention for the deployable slice.
- Keep support and generated relationship concepts in detail views and evidence records rather than treating them as independent resources.

**Exit.** Shared model contract and evidence convention are recorded.

Indicative duration: 30m.

#### Create SAM stack and globals (`task.psm.ph1.st2.t1`)

Create SAM stack and globals. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.deployment-strategy`, `psm-artifact.increment-plan` and produces or updates `psm-artifact.stack-scaffold`.

**Entry.** Stage strategy set.

**Work.**

- Create SamStack elements per deployment unit.
- Configure SamGlobals for shared function and API defaults.

**Exit.** Stack scaffolding ready for resources.

Indicative duration: 45m.

#### Define CFN parameters and outputs (`task.psm.ph1.st2.t2`)

Define CFN parameters and outputs. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.stack-scaffold`, `psm-artifact.deployment-strategy` and produces or updates `psm-artifact.stack-scaffold`.

**Entry.** SAM stack created.

**Work.**

- Define CfnParameter, CfnMapping, and CfnCondition elements.
- Configure CfnOutput and resource lifecycle policies.

**Exit.** CFN scaffolding complete per stack.

Indicative duration: 30m.

#### Configure IAM roles and policies (`task.psm.ph1.st3.t1`)

Configure IAM roles and policies. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.stack-scaffold`, `psm-artifact.deployment-strategy` and produces or updates `psm-artifact.security-baseline`.

**Entry.** Stack scaffolding complete.

**Work.**

- Establish AwsSecurityBaseline with least-privilege defaults.
- Create IamRole elements with inline and managed policies.

**Exit.** IAM roles cover function and service principals.

Indicative duration: 1-2h.

#### Provision KMS, secrets, and SSM (`task.psm.ph1.st3.t2`)

Provision KMS, secrets, and SSM. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.security-baseline`, `psm-artifact.stack-scaffold`, `psm-artifact.deployment-strategy` and produces or updates `psm-artifact.security-baseline`.

**Entry.** IAM roles configured.

**Work.**

- Configure KmsKey and KmsAlias for encryption at rest.
- Create SecretsManagerSecret with rotation rules.
- Define SsmParameter elements for non-secret configuration.

**Exit.** Security baseline applied.

Indicative duration: 1h.

#### Configure VPC and subnets (`task.psm.ph2.st1.t1`)

Configure VPC and subnets. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.security-baseline`, `psm-artifact.deployment-strategy`, `psm-artifact.stack-scaffold`, `psm-artifact.increment-plan` and produces or updates `psm-artifact.network-identity`.

**Entry.** Security baseline applied.

**Work.**

- Create Vpc and Subnet elements when VPC-attached resources are required.
- Configure VpcAttachmentConfig for cross-stack references.

**Exit.** VPC topology defined for increment slice.

Indicative duration: 1h.

#### Define endpoints and security groups (`task.psm.ph2.st1.t2`)

Define endpoints and security groups. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.network-identity`, `psm-artifact.security-baseline`, `psm-artifact.deployment-strategy` and produces or updates `psm-artifact.network-identity`.

**Entry.** VPC topology defined.

**Work.**

- Configure VpcEndpoint elements for AWS service access.
- Define SecurityGroup and SecurityGroupRule for workload isolation.

**Exit.** Network posture defined for workloads.

Indicative duration: 45m.

#### Configure Cognito user pools (`task.psm.ph2.st2.t1`)

Configure Cognito user pools. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.network-identity`, `psm-artifact.security-baseline`, `psm-artifact.deployment-strategy` and produces or updates `psm-artifact.network-identity`.

**Entry.** Networking configured.

**Work.**

- Create CognitoUserPool elements from PIM identity providers.
- Configure password policy, schema attributes, and recovery settings.

**Exit.** User pools match PIM principal model.

Indicative duration: 1h.

#### Configure clients, groups, and identity pools (`task.psm.ph2.st2.t2`)

Configure clients, groups, and identity pools. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.network-identity`, `psm-artifact.security-baseline`, `psm-artifact.deployment-strategy` and produces or updates `psm-artifact.network-identity`.

**Entry.** User pools configured.

**Work.**

- Define CognitoUserPoolClient with OAuth configuration.
- Create CognitoUserPoolGroup and CognitoIdentityPool for federated access.

**Exit.** Identity resources match PIM auth model.

Indicative duration: 45m.

#### Provision DynamoDB tables (`task.psm.ph3.st1.t1`)

Provision DynamoDB tables. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.network-identity`, `psm-artifact.stack-scaffold`, `psm-artifact.security-baseline`, `psm-artifact.deployment-strategy` and produces or updates `psm-artifact.storage-layer`.

**Entry.** Identity configured.

**Work.**

- Create DynamoDbTable elements from PIM DataStore definitions.
- Configure key schema, GSIs/LSIs, streams, and TTL.

**Exit.** DynamoDB tables match PIM data models.

Indicative duration: 1-2h.

#### Configure S3 buckets and policies (`task.psm.ph3.st1.t2`)

Configure S3 buckets and policies. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.storage-layer`, `psm-artifact.network-identity`, `psm-artifact.security-baseline`, `psm-artifact.deployment-strategy`, `psm-artifact.stack-scaffold` and produces or updates `psm-artifact.storage-layer`.

**Entry.** DynamoDB tables provisioned.

**Work.**

- Create S3Bucket elements from PIM ObjectStore definitions.
- Configure encryption, lifecycle, notifications, and bucket policies.

**Exit.** S3 buckets match PIM object stores.

Indicative duration: 1-2h.

#### Create SQS queues (`task.psm.ph3.st2.t1`)

Create SQS queues. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.storage-layer`, `psm-artifact.network-identity`, `psm-artifact.security-baseline`, `psm-artifact.deployment-strategy` and produces or updates `psm-artifact.messaging-layer`.

**Entry.** Durable storage provisioned.

**Work.**

- Create SqsQueue elements from PIM Queue definitions.
- Configure redrive policies and queue policies.

**Exit.** SQS queues match PIM integration topology.

Indicative duration: 45m.

#### Configure SNS topics and subscriptions (`task.psm.ph3.st2.t2`)

Configure SNS topics and subscriptions. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.messaging-layer`, `psm-artifact.storage-layer`, `psm-artifact.network-identity`, `psm-artifact.security-baseline`, `psm-artifact.deployment-strategy` and produces or updates `psm-artifact.messaging-layer`.

**Entry.** SQS queues created.

**Work.**

- Create SnsTopic elements from PIM Topic definitions.
- Define SnsSubscription with filter rules and topic policies.

**Exit.** Messaging matches PIM event channels.

Indicative duration: 45m.

#### Configure EventBridge buses and rules (`task.psm.ph4.st1.t1`)

Configure EventBridge buses and rules. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.messaging-layer`, `psm-artifact.network-identity`, `psm-artifact.security-baseline`, `psm-artifact.deployment-strategy` and produces or updates `psm-artifact.event-fabric`.

**Entry.** Messaging configured.

**Work.**

- Create EventBridgeBus and EventBridgeRule elements from PIM EventBus.
- Configure targets, input transformers, and retry policies.

**Exit.** EventBridge rules match PIM routing rules.

Indicative duration: 1-2h.

#### Configure schedules, pipes, and connections (`task.psm.ph4.st1.t2`)

Configure schedules, pipes, and connections. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.event-fabric`, `psm-artifact.messaging-layer`, `psm-artifact.network-identity`, `psm-artifact.security-baseline`, `psm-artifact.deployment-strategy` and produces or updates `psm-artifact.event-fabric`.

**Entry.** EventBridge rules configured.

**Work.**

- Define EventBridgeSchedule elements from PIM Schedule triggers.
- Configure EventBridgePipe and API destinations for external integration.

**Exit.** Event fabric matches PIM integration.

Indicative duration: 1h.

#### Deploy Lambda functions (`task.psm.ph4.st2.t1`)

Deploy Lambda functions. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.event-fabric`, `psm-artifact.stack-scaffold`, `psm-artifact.security-baseline`, `psm-artifact.network-identity`, `psm-artifact.deployment-strategy` and produces or updates `psm-artifact.compute-layer`.

**Entry.** Event fabric configured.

**Work.**

- Create AwsLambdaFunction elements from PIM Function definitions.
- Configure code config, layers, aliases, and environment variables.

**Exit.** Lambda functions match PIM compute catalog.

Indicative duration: 2-3h.

#### Configure event sources and permissions (`task.psm.ph4.st2.t2`)

Configure event sources and permissions. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.compute-layer`, `psm-artifact.event-fabric`, `psm-artifact.security-baseline`, `psm-artifact.network-identity` and produces or updates `psm-artifact.compute-layer`.

**Entry.** Lambda functions deployed.

**Work.**

- Configure LambdaEventSourceMapping for SQS, DynamoDB streams, and EventBridge.
- Set LambdaPermission, dead-letter config, and tracing/logging.

**Exit.** Compute matches PIM functions.

Indicative duration: 1-2h.

#### Configure APIs and routes (`task.psm.ph5.st1.t1`)

Configure APIs and routes. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.compute-layer`, `psm-artifact.network-identity`, `psm-artifact.security-baseline`, `psm-artifact.deployment-strategy` and produces or updates `psm-artifact.api-layer`.

**Entry.** Compute deployed.

**Work.**

- Create HttpApi, RestApi, and WebSocketApi from PIM Api definitions.
- Define routes, resources, and methods per API style.

**Exit.** API routes match PIM API catalog.

Indicative duration: 1-2h.

#### Configure integrations and authorizers (`task.psm.ph5.st1.t2`)

Configure integrations and authorizers. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.api-layer`, `psm-artifact.compute-layer`, `psm-artifact.event-fabric`, `psm-artifact.network-identity`, `psm-artifact.security-baseline` and produces or updates `psm-artifact.api-layer`.

**Entry.** API routes defined.

**Work.**

- Configure ApiGatewayIntegration to Lambda functions.
- Set up authorizers, stages, and WAF associations.

**Exit.** API Gateway matches PIM APIs.

Indicative duration: 1-2h.

#### Deploy state machines (`task.psm.ph5.st2.ss1.t1`)

Deploy state machines. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.api-layer`, `psm-artifact.compute-layer`, `psm-artifact.event-fabric`, `psm-artifact.network-identity`, `psm-artifact.security-baseline` and produces or updates `psm-artifact.workflow-observability`.

**Entry.** API Gateway configured.

**Work.**

- Create StepFunctionStateMachine from PIM Workflow definitions.
- Select the concrete AslPassState, AslTaskState, AslChoiceState, AslWaitState, AslSucceedState, AslFailState, AslParallelState, or AslMapState classifier for every state.
- Configure ASL transitions, retry/catch rules, branches, map processors, and logging.

**Exit.** State machines match PIM workflows.

Indicative duration: 1-2h.

#### Configure CloudWatch logs and metrics (`task.psm.ph5.st2.ss2.t1`)

Configure CloudWatch logs and metrics. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.workflow-observability`, `psm-artifact.compute-layer`, `psm-artifact.api-layer`, `psm-artifact.event-fabric`, `psm-artifact.security-baseline` and produces or updates `psm-artifact.workflow-observability`.

**Entry.** State machines deployed.

**Work.**

- Create CloudWatchLogGroup elements for functions and APIs.
- Configure metric filters and subscription filters.

**Exit.** Logging and metrics configured.

Indicative duration: 45m.

#### Configure alarms and dashboards (`task.psm.ph5.st2.ss2.t2`)

Configure alarms and dashboards. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.workflow-observability`, `psm-artifact.compute-layer`, `psm-artifact.api-layer`, `psm-artifact.event-fabric`, `psm-artifact.network-identity` and produces or updates `psm-artifact.workflow-observability`.

**Entry.** Logging and metrics configured.

**Work.**

- Define CloudWatchAlarm and composite alarms from PIM SLOs.
- Create CloudWatchDashboard for increment slice.

**Exit.** Workflows and observability complete.

Indicative duration: 45m.

#### Create integration relationship views (`task.psm.ph6.st1.t1`)

Create integration relationship views. The task is performed by `role.cloud-platform-engineer`. It consumes `psm-artifact.workflow-observability`, `psm-artifact.deployment-strategy`, `psm-artifact.network-identity`, `psm-artifact.storage-layer`, `psm-artifact.messaging-layer`, `psm-artifact.event-fabric`, `psm-artifact.compute-layer`, `psm-artifact.api-layer` and produces or updates `psm-artifact.integration-views`.

**Entry.** Workflow and observability complete.

**Work.**

- Generate AwsRelationshipView elements for Lambda integrations.
- Validate event source, API, and notification wiring views.
- Apply AwsTag and ResourceImport for cross-stack references.

**Exit.** Integration views cover all wiring paths.

Indicative duration: 1h.

#### Complete trace and readiness (`task.psm.ph6.st2.t1`)

Complete trace and readiness. The task is performed by `role.process-reviewer`. It consumes `psm-artifact.integration-views`, `psm-artifact.increment-plan`, `psm-artifact.deployment-strategy`, `psm-artifact.stack-scaffold`, `psm-artifact.security-baseline`, `psm-artifact.network-identity`, `psm-artifact.storage-layer`, `psm-artifact.messaging-layer`, `psm-artifact.event-fabric`, `psm-artifact.compute-layer`, `psm-artifact.api-layer`, `psm-artifact.workflow-observability` and produces or updates `psm-artifact.deployment-readiness`.

**Entry.** Integration views complete.

**Work.**

- Build TraceModel links across PIM and PSM resources.
- Complete ProductionReadinessAssessment and resolve findings.

**Exit.** PSM EVL passes; readiness gate approved.

**Checks.** psm-semantic-validation.

Indicative duration: 1-2h.

#### Review and adapt PSM increment (`task.psm.ph6.st3.t1`)

Review and adapt PSM increment. The task is performed by `role.process-reviewer`. It consumes `psm-artifact.deployment-readiness`, `psm-artifact.increment-plan`, `psm-artifact.deployment-strategy`, `psm-artifact.integration-views`, `psm-artifact.security-baseline`, `psm-artifact.workflow-observability` and produces or updates `psm-artifact.increment-review`.

**Entry.** PSM EVL passes or all blocking findings are dispositioned.

**Work.**

- Review AWS resource wiring, least-privilege posture, and artifact-generation readiness.
- Record accepted scope, deferred deployment decisions, and operational feedback.
- Create improvement actions and backlog adjustments for the next deployable slice.

**Exit.** Increment accepted or rework loop selected; improvement actions captured.

Indicative duration: 45m.

### Artifact-readiness component (16 tasks)

#### Review project structure and generated templates (`task.artifact.ph1.st1.t1`)

Review project structure and generated templates. The task is performed by `role.application-engineer`. It consumes None declared and produces or updates `artifact.generated-baseline`.

**Entry.** Artifact explorer contains generated files.

**Work.**

- Inspect template, source, configuration, documentation, and pipeline files in the artifact explorer.
- Identify entry points, Lambda handlers, infrastructure resources, parameters, outputs, and generated deployment instructions.
- Compare the generated structure with the PSM deployable slice and record missing or unexpected components.

**Exit.** Project inventory and PSM-to-file trace are recorded.

**Checks.** Every deployable resource has an identifiable template or source owner.

Indicative duration: 30–60 min.

#### Establish source-control and release baseline (`task.artifact.ph1.st1.t2`)

Establish source-control and release baseline. The task is performed by `role.release-engineer`. It consumes `artifact.generated-baseline` and produces or updates `artifact.generated-baseline`.

**Entry.** Generated output has been reviewed.

**Work.**

- Create a dedicated branch or repository according to team policy.
- Commit the unmodified generated baseline with generator, PSM revision, and generation timestamp recorded.
- Tag or otherwise identify the baseline so later refinements are reviewable.

**Exit.** Baseline is versioned and reproducible.

**Checks.** No unreviewed local-only changes remain.

Indicative duration: 15–30 min.

#### Route structural gaps back to the PSM (`task.artifact.ph1.st2.t1`)

Route structural gaps back to the PSM. The task is performed by `role.cloud-platform-engineer`. It consumes `artifact.generated-baseline` and produces or updates `artifact.generated-baseline`.

**Entry.** Inventory differences are known.

**Work.**

- Classify each gap as a PSM modeling defect, generator limitation, or legitimate implementation refinement.
- For missing resources, permissions, integrations, or policies, update and validate the PSM before regenerating artifacts.
- Record the reason for any approved hand-maintained template extension.

**Exit.** PSM feedback and local refinements have named owners.

**Checks.** Architecture decisions are not hidden only in generated files.

Indicative duration: 30–90 min.

#### Complete repository hygiene (`task.artifact.ph1.st2.t2`)

Complete repository hygiene. The task is performed by `role.application-engineer`. It consumes `artifact.generated-baseline` and produces or updates `artifact.generated-baseline`.

**Entry.** Baseline branch exists.

**Work.**

- Add repository documentation, ownership, license, ignore rules, and contribution instructions required by the organization.
- Confirm generated documentation names the service, supported environments, and deployment prerequisites.
- Ensure build outputs and local credentials are excluded from source control.

**Exit.** Repository can be safely shared and reviewed.

**Checks.** Repository contains no generated build output or credential material.

Indicative duration: 30 min.

#### Define parameter and environment contract (`task.artifact.ph2.st1.t1`)

Define parameter and environment contract. The task is performed by `role.cloud-platform-engineer`. It consumes `artifact.generated-baseline` and produces or updates `artifact.environment-contract`.

**Entry.** Deployment templates and runtime configuration are known.

**Work.**

- List required parameters, outputs, domains, regions, account IDs, tags, feature flags, and external endpoints.
- Provide validated values or references for development, test, staging, and production.
- Document defaults, allowed ranges, and the owner for every environment-specific value.

**Exit.** All environment values have a documented source and owner.

**Checks.** Production values are not copied into non-production configuration.

Indicative duration: 45–90 min.

#### Configure secret references and encryption (`task.artifact.ph2.st1.t2`)

Configure secret references and encryption. The task is performed by `role.security-engineer`. It consumes `artifact.generated-baseline`, `artifact.environment-contract` and produces or updates `artifact.environment-contract`, `artifact.security-record`.

**Entry.** Parameter contract is drafted.

**Work.**

- Replace inline credentials, tokens, and sensitive defaults with approved secret-manager or parameter-store references.
- Verify encryption keys, rotation expectations, and access paths for each secret or protected data store.
- Document bootstrap steps that create secrets before deployment.

**Exit.** Secrets are externalized and bootstrap steps are reproducible.

**Checks.** No secret value is committed to source control or template defaults.

Indicative duration: 30–60 min.

#### Review IAM and deployment permissions (`task.artifact.ph2.st2.t1`)

Review IAM and deployment permissions. The task is performed by `role.security-engineer`. It consumes `artifact.environment-contract`, `artifact.security-record`, `artifact.generated-baseline` and produces or updates `artifact.security-record`.

**Entry.** Infrastructure and delivery identities are defined.

**Work.**

- Review execution roles, resource policies, and CI/CD identities against least-privilege requirements.
- Separate deployment permissions from application runtime permissions.
- Record exceptions, approval owners, and expiration dates for elevated access.

**Exit.** Required permissions are approved and bounded.

**Checks.** No wildcard permission remains without documented justification.

Indicative duration: 45–90 min.

#### Implement repeatable build and deployment pipeline (`task.artifact.ph2.st2.t2`)

Implement repeatable build and deployment pipeline. The task is performed by `role.release-engineer`. It consumes `artifact.security-record`, `artifact.environment-contract`, `artifact.generated-baseline` and produces or updates `artifact.environment-contract`.

**Entry.** Environment contract and deployment identities are available.

**Work.**

- Configure the approved CI/CD workflow to restore dependencies, build, package, validate, and deploy the artifact.
- Pin or record runtime, build-image, and dependency-resolution versions.
- Require review and controlled promotion between environments.

**Exit.** A pipeline can produce a uniquely identifiable release candidate.

**Checks.** Build and deploy steps do not depend on a developer workstation.

Indicative duration: 60–120 min.

#### Run reproducible build and infrastructure validation (`task.artifact.ph3.st1.t1`)

Run reproducible build and infrastructure validation. The task is performed by `role.quality-engineer`. It consumes `artifact.environment-contract`, `artifact.security-record`, `artifact.generated-baseline` and produces or updates `artifact.verification-evidence`.

**Entry.** Versioned release candidate is available.

**Work.**

- Build and package the project using the delivery pipeline.
- Run the project's infrastructure/template validation and capture the results.
- Verify generated deployment artifacts match the tagged source revision.

**Exit.** Build and template validation evidence is retained.

**Checks.** Build succeeds from a clean environment.

Indicative duration: 30–60 min.

#### Assess dependencies and security findings (`task.artifact.ph3.st1.t2`)

Assess dependencies and security findings. The task is performed by `role.security-engineer`. It consumes `artifact.verification-evidence`, `artifact.security-record`, `artifact.environment-contract`, `artifact.generated-baseline` and produces or updates `artifact.security-record`, `artifact.verification-evidence`.

**Entry.** Packaged release candidate exists.

**Work.**

- Run approved dependency, secret, and infrastructure security scans.
- Triage findings by severity and exploitability; fix blockers or obtain time-bound risk acceptance.
- Verify logging, encryption, retention, and data-handling controls meet the PSM intent.

**Exit.** Security findings are resolved or formally accepted.

**Checks.** No unresolved critical finding proceeds without explicit risk acceptance.

Indicative duration: 45–90 min.

#### Deploy to staging and execute acceptance tests (`task.artifact.ph3.st2.t1`)

Deploy to staging and execute acceptance tests. The task is performed by `role.quality-engineer`. It consumes `artifact.security-record`, `artifact.verification-evidence`, `artifact.environment-contract`, `artifact.generated-baseline` and produces or updates `artifact.verification-evidence`.

**Entry.** Build and security checks pass.

**Work.**

- Deploy the exact candidate through the pipeline to a controlled non-production environment.
- Execute smoke, integration, contract, and business acceptance tests for the deployable slice.
- Record test data constraints, results, defects, and any accepted deviations.

**Exit.** Acceptance evidence covers critical user and integration paths.

**Checks.** Tests target the deployed candidate as well as local code.

Indicative duration: 60–180 min.

#### Verify observability and operational signals (`task.artifact.ph3.st2.t2`)

Verify observability and operational signals. The task is performed by `role.cloud-platform-engineer`. It consumes `artifact.verification-evidence`, `artifact.environment-contract`, `artifact.security-record` and produces or updates `artifact.verification-evidence`, `artifact.operations-pack`.

**Entry.** Candidate is deployed to staging.

**Work.**

- Confirm logs, metrics, traces, dashboards, and alarms are emitted for critical paths.
- Trigger representative failure and recovery scenarios to verify alert routing and diagnostic context.
- Set practical thresholds and identify the team that responds to each alert.

**Exit.** Critical operational signals and alert ownership are verified.

**Checks.** An operator can detect and diagnose a failed critical path.

Indicative duration: 45–90 min.

#### Create release, rollback, and communication plan (`task.artifact.ph4.st1.t1`)

Create release, rollback, and communication plan. The task is performed by `role.release-engineer`. It consumes `artifact.verification-evidence`, `artifact.operations-pack`, `artifact.security-record`, `artifact.environment-contract` and produces or updates `artifact.release-plan`.

**Entry.** Staging evidence is accepted.

**Work.**

- Define release scope, version, deployment sequence, approvals, maintenance window, and stakeholder communications.
- Document rollback triggers, steps, data considerations, decision owner, and expected recovery time.
- Rehearse rollback or recovery in non-production when the change affects data, interfaces, or critical availability.

**Exit.** Release and rollback plan are approved by accountable owners.

**Checks.** Rollback does not rely on undocumented manual knowledge.

Indicative duration: 45–90 min.

#### Obtain production readiness approval (`task.artifact.ph4.st1.t2`)

Obtain production readiness approval. The task is performed by `role.service-owner`. It consumes `artifact.release-plan`, `artifact.verification-evidence`, `artifact.security-record`, `artifact.operations-pack` and produces or updates `artifact.release-plan`, `artifact.security-record`.

**Entry.** Release plan and evidence are available.

**Work.**

- Review release scope, risk, verification evidence, security findings, operational readiness, and rollback posture.
- Capture go/no-go decision, approvers, constraints, and follow-up actions.
- Confirm support and business stakeholders understand the release window and success criteria.

**Exit.** Go/no-go decision is recorded.

**Checks.** Production deployment has an accountable approver.

Indicative duration: 30 min.

#### Complete operations handover (`task.artifact.ph4.st2.t1`)

Complete operations handover. The task is performed by `role.service-owner`. It consumes `artifact.release-plan`, `artifact.security-record`, `artifact.verification-evidence`, `artifact.environment-contract`, `artifact.generated-baseline` and produces or updates `artifact.operations-pack`.

**Entry.** Release approval exists.

**Work.**

- Publish runbooks for deployment, rollback, common incidents, access requests, and escalation.
- Assign service ownership, on-call coverage, dashboards, alerts, service objectives, and support contacts.
- Transfer repository, pipeline, and environment access according to the operating model.

**Exit.** Operations team can support the deployed service.

**Checks.** Runbooks identify owners and escalation paths.

Indicative duration: 45–90 min.

#### Execute production validation and close release (`task.artifact.ph4.st2.t2`)

Execute production validation and close release. The task is performed by `role.release-engineer`. It consumes `artifact.operations-pack`, `artifact.release-plan`, `artifact.verification-evidence`, `artifact.security-record`, `artifact.environment-contract` and produces or updates `artifact.verification-evidence`, `artifact.operations-pack`.

**Entry.** Approved release window is open.

**Work.**

- Deploy the approved release candidate using the documented pipeline and plan.
- Perform post-deployment smoke checks, monitor health signals, and confirm business and technical success criteria.
- Close the release or invoke rollback; record lessons and feed recurring issues to the PSM or generator backlog.

**Exit.** Production outcome and follow-up actions are recorded.

**Checks.** Production health is checked before the release is closed.

Indicative duration: 30–90 min.

## Work-product definitions

### Environment Configuration Contract (`artifact.environment-contract`)

Per-environment parameters, secrets references, account/region bindings, and ownership. Kind: Artifact. Provenance: Artifact-readiness component.

### Generated Project Baseline (`artifact.generated-baseline`)

Reviewed, version-controlled output generated from the approved PSM. Kind: Artifact. Provenance: Artifact-readiness component.

### Operations Handover Pack (`artifact.operations-pack`)

Runbooks, dashboards, alarms, support ownership, and post-release checks. Kind: Artifact. Provenance: Artifact-readiness component.

### Release & Rollback Plan (`artifact.release-plan`)

Version, deployment sequence, approvals, rollback steps, and communications. Kind: Artifact. Provenance: Artifact-readiness component.

### Security Review Record (`artifact.security-record`)

Least-privilege, secret handling, dependency, and data-protection evidence. Kind: Artifact. Provenance: Artifact-readiness component.

### Verification Evidence (`artifact.verification-evidence`)

Build, validation, test, and quality-gate results. Kind: Artifact. Provenance: Artifact-readiness component.

### Aggregate Boundary Model (`cim-artifact.aggregate-model`)

Aggregate candidates and consistency rules. Kind: Artifact. Provenance: CIM modeling component.

### CQRS Behavior Surface (`cim-artifact.behavior-surface`)

Commands, queries, events, conditions. Kind: Artifact. Provenance: CIM modeling component.

### Capability (`cim-artifact.capability-map`)

Business capabilities and dependencies. Kind: Artifact. Provenance: CIM modeling component.

### Bounded Context Map (`cim-artifact.context-map`)

Context boundary assignments. Kind: Artifact. Provenance: CIM modeling component.

### Decision & Policy Model (`cim-artifact.decision-model`)

Policies and decision tables. Kind: Artifact. Provenance: CIM modeling component.

### Domain Structure Model (`cim-artifact.domain-structure`)

Entities, value objects, relationships, invariants. Kind: Artifact. Provenance: CIM modeling component.

### Ubiquitous Language Glossary (`cim-artifact.glossary`)

Domain term definitions. Kind: Artifact. Provenance: CIM modeling component.

### Governance Constraint Package (`cim-artifact.governance-package`)

Security, privacy, compliance constraints. Kind: Artifact. Provenance: CIM modeling component.

### CIM Increment Plan (`cim-artifact.increment-plan`)

Selected capability slice, working agreements, and definition of done. Kind: Artifact. Provenance: CIM modeling component.

### CIM Increment Review Record (`cim-artifact.increment-review`)

Review outcomes, accepted slice scope, and improvement actions. Kind: Artifact. Provenance: CIM modeling component.

### Information Taxonomy (`cim-artifact.information-taxonomy`)

Data classifications and information items. Kind: Artifact. Provenance: CIM modeling component.

### CIM Model Root (`cim-artifact.model-root`)

CIMModel container and program metadata. Kind: Artifact. Provenance: CIM modeling component.

### Participation Model (`cim-artifact.participation-model`)

Actors, roles, and external systems. Kind: Artifact. Provenance: CIM modeling component.

### Business Process Model (`cim-artifact.process-model`)

Processes, steps, transitions. Kind: Artifact. Provenance: CIM modeling component.

### Requirements Package (`cim-artifact.requirements-package`)

Functional and non-functional requirements. Kind: Artifact. Provenance: CIM modeling component.

### Strategic Intent Package (`cim-artifact.strategic-intent`)

Goals, KPIs, and stakeholder map. Kind: Artifact. Provenance: CIM modeling component.

### Trace & Readiness Record (`cim-artifact.trace-readiness`)

TraceModel and production readiness assessment. Kind: Artifact. Provenance: CIM modeling component.

### Transformation Contract (`cim-artifact.transformation-contract`)

Risks, assumptions, hotspots, profile. Kind: Artifact. Provenance: CIM modeling component.

### Increment Record (`e2e-artifact.increment-record`)

Slice goal, scope, acceptance evidence, model revisions, transformation runs, and retrospective results. Kind: Artifact. Provenance: End-to-end lifecycle.

### Situational Method Profile (`e2e-artifact.method-profile`)

Tailored lifecycle, roles, work products, gates, evidence rules, and metrics for the project context. Kind: Artifact. Provenance: End-to-end lifecycle.

### Service Work Item (`e2e-artifact.operational-work-item`)

A production, maintenance, service, or improvement demand item with source, maintenance purpose where applicable, emergency-temporary status, class of service, severity, owner, service-level expectation, state, age, evidence, and disposition. Kind: Artifact. Provenance: End-to-end lifecycle.

### Operations and Learning Record (`e2e-artifact.operations-record`)

SLOs, incidents, changes, product outcomes, and method-improvement actions. Kind: Artifact. Provenance: End-to-end lifecycle.

### Product and System Charter (`e2e-artifact.product-charter`)

Product goal, outcome hypothesis, scope boundaries, constraints, and success measures. Kind: Artifact. Provenance: End-to-end lifecycle.

### Release Record (`e2e-artifact.release-record`)

Release scope, exact model and artifact revisions, approvals, deployment evidence, rollback identity, and outcome. Kind: Artifact. Provenance: End-to-end lifecycle.

### Retirement and Closure Record (`e2e-artifact.retirement-record`)

Retirement decision, migration/data disposition, decommission evidence, and retained knowledge. Kind: Artifact. Provenance: End-to-end lifecycle.

### Kanban Service-Delivery Policy and Board (`e2e-artifact.service-flow-system`)

The explicit Definition of Workflow and visible Kanban board: requested/ready/started/finished points, workflow states, WIP controls, classes of service, service-level expectations, replenishment and review cadences, capacity policy, and flow metrics. Kind: Artifact. Provenance: End-to-end lifecycle.

### Team Topology and Dependency Map (`e2e-artifact.team-topology`)

Team ownership, interfaces, dependency board, decision rights, and coordination cadence. Kind: Artifact. Provenance: End-to-end lifecycle.

### API Catalog (`pim-artifact.api-catalog`)

APIs, routes, error mappings. Kind: Artifact. Provenance: PIM modeling component.

### Architecture Posture (`pim-artifact.architecture-posture`)

PIMModel root and implementation profile. Kind: Artifact. Provenance: PIM modeling component.

### Compute Catalog (`pim-artifact.compute-catalog`)

Functions, contracts, triggers. Kind: Artifact. Provenance: PIM modeling component.

### Configuration Package (`pim-artifact.config-package`)

Environments, secrets, external adapters. Kind: Artifact. Provenance: PIM modeling component.

### Contract Catalog (`pim-artifact.contract-catalog`)

Schemas, event types, envelopes. Kind: Artifact. Provenance: PIM modeling component.

### Data Architecture (`pim-artifact.data-architecture`)

Stores, models, access patterns. Kind: Artifact. Provenance: PIM modeling component.

### PIM Increment Plan (`pim-artifact.increment-plan`)

Selected service slice, trace baseline, and architecture definition of done. Kind: Artifact. Provenance: PIM modeling component.

### PIM Increment Review Record (`pim-artifact.increment-review`)

Architecture review outcomes, accepted service slice, and improvement actions. Kind: Artifact. Provenance: PIM modeling component.

### Integration Topology (`pim-artifact.integration-topology`)

Channels, flows, schedules. Kind: Artifact. Provenance: PIM modeling component.

### Platform Readiness Record (`pim-artifact.platform-readiness`)

Mapping assessment, trace, readiness. Kind: Artifact. Provenance: PIM modeling component.

### Architecture Policy Catalog (`pim-artifact.policy-catalog`)

Operational and compliance policies. Kind: Artifact. Provenance: PIM modeling component.

### Security Model (`pim-artifact.security-model`)

Identity, principals, permissions. Kind: Artifact. Provenance: PIM modeling component.

### Service Boundary Map (`pim-artifact.service-map`)

Serverless services and memberships. Kind: Artifact. Provenance: PIM modeling component.

### Workflow Model (`pim-artifact.workflow-model`)

Workflows, human tasks, escalation. Kind: Artifact. Provenance: PIM modeling component.

### API Gateway Layer (`psm-artifact.api-layer`)

HTTP/REST/WebSocket APIs. Kind: Artifact. Provenance: AWS PSM modeling component.

### Lambda Compute Layer (`psm-artifact.compute-layer`)

Functions, mappings, permissions. Kind: Artifact. Provenance: AWS PSM modeling component.

### Deployment Readiness Record (`psm-artifact.deployment-readiness`)

Trace and readiness closure. Kind: Artifact. Provenance: AWS PSM modeling component.

### Deployment Strategy (`psm-artifact.deployment-strategy`)

Account, region, naming, tagging policies. Kind: Artifact. Provenance: AWS PSM modeling component.

### Event Fabric (`psm-artifact.event-fabric`)

EventBridge buses, rules, pipes. Kind: Artifact. Provenance: AWS PSM modeling component.

### PSM Increment Plan (`psm-artifact.increment-plan`)

Selected deployable slice, AWS assumptions, and deployment definition of done. Kind: Artifact. Provenance: AWS PSM modeling component.

### PSM Increment Review Record (`psm-artifact.increment-review`)

Deployment review outcomes, accepted AWS slice, and improvement actions. Kind: Artifact. Provenance: AWS PSM modeling component.

### Integration View Catalog (`psm-artifact.integration-views`)

Cross-resource relationship views. Kind: Artifact. Provenance: AWS PSM modeling component.

### Messaging Layer (`psm-artifact.messaging-layer`)

SQS and SNS resources. Kind: Artifact. Provenance: AWS PSM modeling component.

### Network & Identity (`psm-artifact.network-identity`)

VPC, Cognito resources. Kind: Artifact. Provenance: AWS PSM modeling component.

### Security Baseline (`psm-artifact.security-baseline`)

IAM, KMS, secrets, SSM. Kind: Artifact. Provenance: AWS PSM modeling component.

### SAM Stack Scaffold (`psm-artifact.stack-scaffold`)

Stacks, globals, CFN parameters. Kind: Artifact. Provenance: AWS PSM modeling component.

### Durable Storage Layer (`psm-artifact.storage-layer`)

DynamoDB and S3 resources. Kind: Artifact. Provenance: AWS PSM modeling component.

### Workflow & Observability (`psm-artifact.workflow-observability`)

Step Functions and CloudWatch. Kind: Artifact. Provenance: AWS PSM modeling component.

### Architecture Decision Record Set (`wp.architecture-decisions`)

Decision context, alternatives, decision, consequences, evidence, and review triggers. Kind: Artifact. Provenance: Engineered core content.

### AWS PSM Revision (`wp.aws-psm-revision`)

Provider resources, configuration, relationships, IAM, networking, observability, and deployment intent. Kind: Model. Provenance: Engineered core content.

### Change and Impact Record (`wp.change-impact-record`)

Request, authoritative re-entry point, affected traces, risk, plan, and evidence. Kind: Artifact. Provenance: Engineered core content.

### CIM to PIM Transformation Run (`wp.cim-pim-transformation-run`)

Source, baseline, working and target revisions, transformation version, trace, conflicts, and manual decisions. Kind: Artifact. Provenance: Engineered core content.

### CIM Review and Readiness Record (`wp.cim-readiness-record`)

Structural and semantic evidence, trace, findings, decisions, and accepted CIM revision. Kind: Artifact. Provenance: Engineered core content.

### CIM Revision (`wp.cim-revision`)

Strategic, organizational, domain, behavior, process, policy, governance, and transformation intent. Kind: Model. Provenance: Engineered core content.

### Initial Cost Model and Budget Guardrails (`wp.cost-model-budget-guardrails`)

Demand assumptions, service cost drivers, scenarios, budgets, and uncertainty. Kind: Artifact. Provenance: Engineered core content.

### Threat, Privacy, Failure, and Cost Analysis (`wp.crosscutting-analysis`)

Scenarios, controls and treatments, residual risk, tests, and monitors. Kind: Artifact. Provenance: Engineered core content.

### Deployment and Promotion Record (`wp.deployment-promotion-record`)

Candidate, target, timestamps, checks, observations, decisions, and outcome. Kind: Artifact. Provenance: Engineered core content.

### Generated Artifact Baseline and Manifest (`wp.generated-artifact-baseline`)

Output files and hashes, model and generator revisions, warnings, manual actions, and artifact trace. Kind: Artifact. Provenance: Engineered core content.

### Immutable Release Candidate (`wp.immutable-release-candidate`)

Artifact digests, configuration and schema versions, dependencies, provenance, and selected SBOM evidence. Kind: Artifact. Provenance: Engineered core content.

### Incident and Problem Record (`wp.incident-problem-record`)

Timeline, impact, recovery, causes and system conditions, actions, and learning. Kind: Artifact. Provenance: Engineered core content.

### Integrated Roadmap, Release, and Increment Plan (`wp.integrated-roadmap-plan`)

Outcomes, increments, dependencies, release hypotheses, and forecasts. Kind: Artifact. Provenance: Engineered core content.

### Operational Evidence Set (`wp.operational-evidence-set`)

SLI and SLO evidence, incidents, capacity, cost, security, product outcomes, and provider events. Kind: Artifact. Provenance: Engineered core content.

### Operational Readiness and Handover Pack (`wp.operational-readiness-pack`)

SLOs, dashboards, alerts, runbooks, support, access, backup and recovery, and known risks. Kind: Artifact. Provenance: Engineered core content.

### Service Work Item (`wp.operational-work-item`)

Demand source, affected service, maintenance purpose, emergency-temporary status, service class, severity, owner, SLE, state, age, evidence, authoritative re-entry point, and disposition. Kind: Artifact. Provenance: Engineered core content.

### Kanban Service-Delivery Policy and Board (`wp.operations-flow-policy-board`)

Kanban board states and start/finish points, WIP controls, pull and replenishment rules, classes of service, SLEs, capacity and expedite policies, reconciliation rules, feedback cadences, and flow measures. Kind: Artifact. Provenance: Engineered core content.

### PIM to PSM Transformation Run (`wp.pim-psm-transformation-run`)

Source, baseline, working and target revisions, mapping profile, trace, conflicts, and manual decisions. Kind: Artifact. Provenance: Engineered core content.

### PIM Review and Readiness Record (`wp.pim-readiness-record`)

Validation, platform mapping, trace, findings, decisions, and accepted PIM revision. Kind: Artifact. Provenance: Engineered core content.

### PIM Revision (`wp.pim-revision`)

Provider-independent services, contracts, data, compute, APIs, events, workflows, integration, policy, security, and deployment intent. Kind: Model. Provenance: Engineered core content.

### Product and System Charter (`wp.product-system-charter`)

Problem, outcomes, stakeholders, boundaries, non-goals, constraints, and the first release hypothesis. Kind: Artifact. Provenance: Engineered core content.

### PSM Review and Readiness Record (`wp.psm-readiness-record`)

Validation, trace, quota, cost, security, and generation-readiness decisions. Kind: Artifact. Provenance: Engineered core content.

### Release and Recovery Plan (`wp.release-recovery-plan`)

Promotion strategy, thresholds, migrations, rollback or roll-forward, communications, and approvals. Kind: Artifact. Provenance: Engineered core content.

### Retirement, Migration, and Closure Record (`wp.retirement-closure-record`)

Stakeholders, data, integrations, access, resources, migration, verification, and retained evidence. Kind: Artifact. Provenance: Engineered core content.

### Retrospective and Improvement Record (`wp.retrospective-improvement-record`)

Observations, measures, decisions, actions, reusable candidates, and no-op rationale. Kind: Artifact. Provenance: Engineered core content.

### Risk and Opportunity Register (`wp.risk-opportunity-register`)

Cause, event, effect, exposure, owner, treatment, trigger, status, and evidence. Kind: Artifact. Provenance: Engineered core content.

### Feasibility and Serverless Suitability Record (`wp.serverless-suitability-record`)

Alternatives, serverless fit criteria, assumptions, experiments, and recommendation. Kind: Artifact. Provenance: Engineered core content.

### Situational Method Profile (`wp.situational-method-profile`)

Situational factors, selected packages, substitutions or omissions, evidence depth, and review triggers. Kind: Artifact. Provenance: Engineered core content.

### Source and Test Baseline (`wp.source-test-baseline`)

Completed logic, adapters, client code, unit, contract and component tests, and provenance. Kind: Artifact. Provenance: Engineered core content.

### Team Topology and Responsibility Assignment (`wp.team-topology-responsibility`)

Scopes, roles, accountable owners, decision rights, dependencies, and escalation paths. Kind: Artifact. Provenance: Engineered core content.

### Verification and Validation Record (`wp.verification-validation-record`)

Test scope, environments, inputs, results, findings, coverage, and limitations. Kind: Artifact. Provenance: Engineered core content.

## Guidance definitions

### Environment parity with explicit differences (`artifact.guid.environment-parity`)

Keep development, test, staging, and production configuration structurally equivalent. Record intentional differences as parameters or approved environment exceptions.

Guidance kind: Practice guidance. Applies to `artifact.ph2`. Provenance: Artifact-readiness component.

### Evidence before promotion (`artifact.guid.evidence`)

Promotion requires reproducible build, validation, test, and security evidence for the exact release candidate.

Guidance kind: Practice guidance. Applies to `artifact.ph3`. Provenance: Artifact-readiness component.

### Generated output is a controlled baseline (`artifact.guid.generated-baseline`)

Treat generated files as a PSM-derived baseline: review every generated change, commit it to source control, and feed structural defects back to the PSM instead of repeatedly patching generated output.

Guidance kind: Practice guidance. Applies to `process`. Provenance: Artifact-readiness component.

### No secrets in artifacts (`artifact.guid.no-secrets`)

Store secret values only in the approved secret manager or CI/CD secret store; generated templates and repositories contain references, never secret material.

Guidance kind: Practice guidance. Applies to `artifact.ph2`. Provenance: Artifact-readiness component.

### Rehearse recovery (`artifact.guid.rollback`)

A release is not ready until rollback, data protection, alerting, and ownership are demonstrably usable by the on-call team.

Guidance kind: Practice guidance. Applies to `artifact.ph4`. Provenance: Artifact-readiness component.

### Ubiquitous language first (Evans) (`cim.guid.ddd-language`)

Agree domain vocabulary before modeling entities. Renaming later is expensive. Use glossary tasks early.

Guidance kind: Practice guidance. Applies to `cim.ph2`. Provenance: CIM modeling component.

### Event storming surface (Brandolini) (`cim.guid.event-storming`)

Model commands, queries, and events on a behavior surface linked to actors and capabilities before aggregates.

Guidance kind: Practice guidance. Applies to `cim.ph3`. Provenance: CIM modeling component.

### Goal-Question-Metric (Basili) (`cim.guid.gqm`)

Anchor every modeling increment with measurable business goals before structural or behavioral elements.

Guidance kind: Practice guidance. Applies to `cim.ph1`. Provenance: CIM modeling component.

### Thin capability slices (`cim.guid.increment-cycle`)

Run CIM as an empirical increment cycle: select one valuable capability slice, model only enough breadth to satisfy the slice definition of done, review, then adapt the backlog.

Guidance kind: Practice guidance. Applies to `cim.ph1`. Provenance: CIM modeling component.

### Information before entity (CIM-ENTITY-001) (`cim.guid.information-before-entity`)

Create InformationItem elements before DomainEntity. Primary identity must reference an information item.

Guidance kind: Practice guidance. Applies to `cim.ph3`. Provenance: CIM modeling component.

### Computation-independent intent (`cim.guid.mda-intent`)

CIM captures business meaning without platform or implementation choices. Defer technology decisions to PIM/PSM.

Guidance kind: Practice guidance. Applies to `process`. Provenance: CIM modeling component.

### Twin Peaks requirements (Nuseibeh) (`cim.guid.twin-peaks`)

Backfill formal requirements after domain structure exists. Use engine rework loops when requirements expose gaps.

Guidance kind: Practice guidance. Applies to `cim.ph5`. Provenance: CIM modeling component.

### Empirical process control (`e2e.guid.empirical-control`)

Plan a small vertical slice, inspect executable model evidence at each gate, and adapt the backlog after artifact review.

Guidance kind: Practice guidance. Applies to `process`. Provenance: End-to-end lifecycle.

### Human-in-the-loop transforms (`e2e.guid.human-in-loop`)

ETL and M2T are assistive. ManualDecision and readiness gates block promotion when automation is uncertain.

Guidance kind: Practice guidance. Applies to `process`. Provenance: End-to-end lifecycle.

### Thin vertical increments (`e2e.guid.incremental-vertical`)

Deliver one capability slice through CIM → PIM → PSM → artifact readiness per engine revolution. The engine repeats inside release, operations, and retirement lifecycle governance; avoid big-bang modeling.

Guidance kind: Practice guidance. Applies to `process`. Provenance: End-to-end lifecycle.

### MF-01 — Opportunity and feasibility (`guidance.fragment.mf-01`)

A project can commit to a solution before value, feasibility, constraints, and alternatives are understood. Result: The endeavor is pursued, explored, redirected, or stopped with explicit evidence.

Guidance kind: Process Pattern. Pattern source: `MF-01`. Provenance: Engineered fragment catalog.

### MF-02 — Serverless suitability, cost, risk, and provider decision (`guidance.fragment.mf-02`)

Serverless and provider commitments can be made without workload, cost, risk, or exit analysis. Result: A serverless/hybrid/non-serverless and provider decision or bounded experiment is authorized.

Guidance kind: Process Pattern. Pattern source: `MF-02`. Provenance: Engineered fragment catalog.

### MF-03 — Situational method tailoring (`guidance.fragment.mf-03`)

One fixed process either overburdens a project or omits necessary controls. Result: A versioned method profile selects content, evidence depth, roles, and review triggers.

Guidance kind: Process Pattern. Pattern source: `MF-03`. Provenance: Engineered fragment catalog.

### MF-04 — Product, release, and increment framing (`guidance.fragment.mf-04`)

Modeling can become an end in itself without a bounded outcome and acceptance evidence. Result: A valuable vertical slice is ready with owners, risks, dependencies, and evidence.

Guidance kind: Process Pattern. Pattern source: `MF-04`. Provenance: Engineered fragment catalog.

### MF-05 — Event-driven domain discovery and CIM modeling (`guidance.fragment.mf-05`)

Business meaning can be lost if software/provider structure is chosen before domain and requirement understanding. Result: An accepted computation-independent model expresses intent, behavior, policy, and governance.

Guidance kind: Process Pattern. Pattern source: `MF-05`. Provenance: Engineered fragment catalog.

### MF-06 — CIM assurance and readiness (`guidance.fragment.mf-06`)

An incomplete or inconsistent CIM can propagate false certainty downstream. Result: The revision is accepted, reworked, or deferred with trace and findings.

Guidance kind: Process Pattern. Pattern source: `MF-06`. Provenance: Engineered fragment catalog.

### MF-07 — CIM to PIM transformation and reconciliation (`guidance.fragment.mf-07`)

Automated refinement can overwrite human decisions or hide unresolved mappings. Result: A traceable PIM draft is merged or conflicts/manual decisions are explicit.

Guidance kind: Process Pattern. Pattern source: `MF-07`. Provenance: Engineered fragment catalog.

### MF-08 — Platform-independent serverless architecture (`guidance.fragment.mf-08`)

Serverless architecture needs explicit provider-independent services, contracts, state, integration, workflow, security, and operability. Result: An accepted provider-independent architecture is ready for capability mapping.

Guidance kind: Process Pattern. Pattern source: `MF-08`. Provenance: Engineered fragment catalog.

### MF-09 — PIM to PSM transformation and reconciliation (`guidance.fragment.mf-09`)

Provider mapping can silently introduce unsafe defaults or lose refinements. Result: A traceable PSM draft is merged and mapping gaps/conflicts are explicit.

Guidance kind: Process Pattern. Pattern source: `MF-09`. Provenance: Engineered fragment catalog.

### MF-10 — AWS PSM refinement and assurance (`guidance.fragment.mf-10`)

Generated provider resources require explicit security, networking, quota, observability, recovery, cost, and deployment decisions. Result: An accepted AWS PSM is ready for reproducible generation.

Guidance kind: Process Pattern. Pattern source: `MF-10`. Provenance: Engineered fragment catalog.

### MF-11 — Reproducible model-to-text generation (`guidance.fragment.mf-11`)

Generated output cannot be trusted or reproduced without exact source, template, configuration, and manifest provenance. Result: A generated-draft baseline and traceable manifest exist.

Guidance kind: Process Pattern. Pattern source: `MF-11`. Provenance: Engineered fragment catalog.

### MF-12 — Artifact completion and test in the small (`guidance.fragment.mf-12`)

Generated scaffolds can be mistaken for complete, correct software. Result: Business logic and extensions are complete and component evidence is available.

Guidance kind: Process Pattern. Pattern source: `MF-12`. Provenance: Engineered fragment catalog.

### MF-13 — Test in the large and release qualification (`guidance.fragment.mf-13`)

Locally correct functions and templates may fail as an integrated serverless service. Result: System, acceptance, NFR, security, resilience, recovery, and operational evidence support a release decision.

Guidance kind: Process Pattern. Pattern source: `MF-13`. Provenance: Engineered fragment catalog.

### MF-14 — Progressive CI/CD and transition (`guidance.fragment.mf-14`)

A technically valid candidate can still fail during promotion or handover. Result: The release is progressively promoted, observed, recorded, and accepted for operation.

Guidance kind: Process Pattern. Pattern source: `MF-14`. Provenance: Engineered fragment catalog.

### MF-15 — Operate, observe, control cost, and learn (`guidance.fragment.mf-15`)

A deployed serverless service needs operational ownership and feedback to remain valuable, reliable, secure, and economical. Result: Service/product outcomes, SLOs, risk, security, and cost are controlled and feed prioritized change.

Guidance kind: Process Pattern. Pattern source: `MF-15`. Provenance: Engineered fragment catalog.

### MF-16 — Incident, problem, and controlled change propagation (`guidance.fragment.mf-16`)

Emergency or evolutionary changes can diverge models, generators, code, and the running service. Result: Service is restored where necessary and change is routed to the earliest authoritative source and re-evidenced.

Guidance kind: Process Pattern. Pattern source: `MF-16`. Provenance: Engineered fragment catalog.

### MF-17 — Retirement, migration, and closure (`guidance.fragment.mf-17`)

A service can be switched off while data, consumers, access, resources, costs, or legal obligations remain. Result: Users, data, integrations, access, resources, cost, and evidence are migrated or closed and accepted.

Guidance kind: Process Pattern. Pattern source: `MF-17`. Provenance: Engineered fragment catalog.

### MF-18 — Kanban service-delivery and maintenance system (`guidance.fragment.mf-18`)

Production, maintenance, and improvement demand competes with planned delivery, while invisible queues or emergency speed can bypass model, trace, and release controls and create permanent divergence. Result: Service demand is visualized on a Kanban board, made ready and pulled within WIP controls, resolved or routed, measured, and reconciled with authoritative sources and release evidence.

Guidance kind: Process Pattern. Pattern source: `MF-18`. Provenance: Engineered fragment catalog.

### MF-19 — DevOps cross-process coordination (`guidance.fragment.mf-19`)

Development and operations can optimize locally while release readiness, production feedback, change ownership, and retirement obligations fall between processes. Result: G7 handover, CI/CD evidence, telemetry, incident and maintenance change routing, learning, and G8 closure synchronize the two processes without merging their control systems.

Guidance kind: Process Pattern. Pattern source: `MF-19`. Provenance: Engineered fragment catalog.

### UF-01 — Integrated management, assurance, and evidence (`guidance.fragment.uf-01`)

Lifecycle tasks become disconnected without continuous planning, risk, quality, security, change, trace, and evidence management. Result: Decisions, dependencies, risks, findings, revisions, evidence, and improvement remain controlled throughout the run.

Guidance kind: Continuous Practice Pattern. Pattern source: `UF-01`. Provenance: Engineered fragment catalog.

### Contracts decouple producers (`pim.guid.contracts-first`)

Define schemas and event types before wiring functions and routes. Contracts are the integration currency.

Guidance kind: Practice guidance. Applies to `pim.ph2`. Provenance: PIM modeling component.

### Access patterns drive stores (`pim.guid.data-access`)

Model read/write access patterns from CIM queries/commands before choosing store topology.

Guidance kind: Practice guidance. Applies to `pim.ph2`. Provenance: PIM modeling component.

### Generated PIM is a draft (`pim.guid.generated-is-draft`)

Treat CIM→PIM output as architectural scaffolding. Every generated service slice must pass through framing, refinement, readiness, and review before PIM→PSM.

Guidance kind: Practice guidance. Applies to `pim.ph1`. Provenance: PIM modeling component.

### Policy attachment discipline (`pim.guid.policy-as-code`)

Attach resilience, observability, and compliance policies to concrete PolicyTarget elements, not free-floating rules.

Guidance kind: Practice guidance. Applies to `pim.ph5`. Provenance: PIM modeling component.

### Readiness is part of delivery (`pim.guid.readiness-in-cycle`)

Platform mapping and EVL validation are not after-the-fact audits. They are the review gate of each service-slice cycle.

Guidance kind: Practice guidance. Applies to `pim.ph6`. Provenance: PIM modeling component.

### Service as deployable boundary (`pim.guid.serverless-boundary`)

Align ServerlessService boundaries to CIM bounded contexts. One increment typically maps to one service slice.

Guidance kind: Practice guidance. Applies to `process`. Provenance: PIM modeling component.

### Deployable slice discipline (`psm.guid.deployable-slice`)

A PSM increment is done only when AWS wiring, least-privilege posture, observability, traceability, and artifact-generation readiness are reviewed together.

Guidance kind: Practice guidance. Applies to `psm.ph6`. Provenance: AWS PSM modeling component.

### Generated PSM is a draft (`psm.guid.generated-is-draft`)

Treat PIM→PSM output as AWS scaffolding. Refine IAM, networking, resource settings, integration views, and readiness before M2T generation.

Guidance kind: Practice guidance. Applies to `psm.ph1`. Provenance: AWS PSM modeling component.

### Integration views for traceability (`psm.guid.integration-views`)

Relationship views denormalize cross-resource wiring for M2T and human review, create them before M2T generation.

Guidance kind: Practice guidance. Applies to `psm.ph6`. Provenance: AWS PSM modeling component.

### Least-privilege IAM (`psm.guid.least-privilege`)

Security baseline before compute. Lambda permissions and resource policies reference roles from the baseline.

Guidance kind: Practice guidance. Applies to `psm.ph1`. Provenance: AWS PSM modeling component.

### SAM stack containment (`psm.guid.sam-containment`)

Most AWS resources live under SamStack. Establish stack scaffolding before resource provisioning tasks.

Guidance kind: Practice guidance. Applies to `process`. Provenance: AWS PSM modeling component.
