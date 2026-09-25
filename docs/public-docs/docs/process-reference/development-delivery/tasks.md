# Development and Delivery: tasks

This page documents the **SPEM TaskDefinition and TaskUse** elements used by the Development and Delivery process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A TaskDefinition describes reusable work. A TaskUse places that definition inside an activity and binds it to process performers and work-product uses. The same responsibility may appear in another process context with different inputs, outputs, or selected steps.

## Summary

| Task                                                   | Primary role             | Inputs | Outputs | Task uses |
| ------------------------------------------------------ | ------------------------ | -----: | ------: | --------: |
| Define product outcomes and success measures           | Product Owner            |      0 |       1 |         1 |
| Establish the initial release and increment hypothesis | Requirements Engineer    |      1 |       2 |         1 |
| Assess feasibility and serverless suitability          | Solution Architect       |      1 |       2 |         1 |
| Establish cost model and budget guardrails             | FinOps and Cost Analyst  |      2 |       2 |         1 |
| Authorize pursue, explore, redirect, or stop at G0     | Sponsor                  |      4 |       4 |         1 |
| Assess context and process-tailoring risks             | Method Engineer          |      2 |       2 |         1 |
| Define the tailored Definition of Ready and Done       | Process Reviewer         |      3 |       1 |         1 |
| Define team ownership and interfaces                   | Delivery Lead            |      2 |       1 |         1 |
| Set coordination cadence and escalation paths          | Delivery Lead            |      3 |       2 |         1 |
| Define quality and security control objectives         | Security Engineer        |      4 |       2 |         1 |
| Define operational and release strategy                | Service Owner            |      5 |       3 |         1 |
| Plan the vertical increment                            | Product Owner            |      5 |       2 |         1 |
| Run the CIM increment                                  | Business Modeler         |      4 |       1 |         1 |
| Execute and inspect CIM-to-PIM transformation          | Solution Architect       |      3 |       1 |         1 |
| Run the PIM increment                                  | Solution Architect       |      3 |       2 |         1 |
| Execute and inspect PIM-to-PSM transformation          | Cloud Platform Engineer  |      3 |       1 |         1 |
| Run the PSM increment                                  | Cloud Platform Engineer  |      3 |       3 |         1 |
| Generate and fingerprint the artifact baseline         | Cloud Platform Engineer  |      3 |       1 |         1 |
| Run artifact readiness and accept the increment        | Process Reviewer         |      5 |       1 |         1 |
| Review the increment and adapt the way of working      | Method Engineer          |      5 |       3 |         1 |
| Assemble the release candidate                         | Release Engineer         |      4 |       1 |         1 |
| Review release evidence and go/no-go criteria          | Process Reviewer         |      6 |       3 |         1 |
| Deploy and validate progressively                      | Release Engineer         |      4 |       1 |         1 |
| Complete handover and rollback rehearsal               | Service Owner            |      4 |       2 |         1 |
| Review release outcome and update roadmap              | Product Owner            |      7 |       5 |         1 |
| Approve retirement scope and plan                      | Product Owner            |      6 |       2 |         1 |
| Execute migration and data disposition                 | Cloud Platform Engineer  |      4 |       1 |         1 |
| Decommission service, access, and cost surfaces        | Service Owner            |      5 |       2 |         1 |
| Verify records retention and data disposition          | Records and Data Steward |      3 |       1 |         1 |
| Complete closure review                                | Process Reviewer         |      8 |       4 |         1 |

## Detailed tasks

## Define product outcomes and success measures

<small>Task definition: `task.e2e.ph0.st1.t1`</small>

Define product outcomes and success measures This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Product Owner**. The work normally involves Sponsor, Domain Expert, Service Owner. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- No formal input work product is required. The task still uses the accepted scope, decisions, and project context.

**How to perform the task**

1. State the user or mission problem and desired outcomes.
2. Define measurable product, operational, security, quality, and cost/value outcomes.
3. Record assumptions, constraints, non-goals, expected lifetime, exit conditions, and the first release hypothesis.

**Outputs**

- Product and System Charter

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Outcome measures and non-goals are accepted by stakeholders

**Checks and evidence**

- Every initial scope item is connected to an outcome or mandatory constraint

**Uses in this process**

- `e2e.ph0.st1.t1` in **Product and System Intent**

## Establish the initial release and increment hypothesis

<small>Task definition: `task.e2e.ph0.st1.t2`</small>

Establish the initial release and increment hypothesis This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Requirements Engineer**. The work normally involves Product Owner, Domain Expert, Solution Architect, Delivery Lead. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Product and System Charter

**How to perform the task**

1. Identify the smallest useful vertical capability slice.
2. Define acceptance signals and the evidence needed to call it usable.
3. Record unresolved assumptions as owned decisions rather than hidden risks.

**Outputs**

- Product and System Charter
- Increment and Integrated Plan Record

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Establish the initial release and increment hypothesis evidence is recorded

**Checks and evidence**

- The first slice crosses the required lifecycle boundary and has observable acceptance evidence

**Uses in this process**

- `e2e.ph0.st1.t2` in **Product and System Intent**

## Assess feasibility and serverless suitability

<small>Task definition: `task.e2e.ph0.st1a.t1`</small>

Assess feasibility and serverless suitability This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The work normally involves Product Owner, Domain Expert, Cloud Platform Engineer, Security Engineer, Quality Engineer, Service Owner, FinOps and Cost Analyst. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Product and System Charter

**How to perform the task**

1. Compare serverless, hybrid, and non-serverless alternatives against workload shape, latency, state, integration, availability, compliance, skills, operations, portability, and exit constraints.
2. Use bounded architecture experiments for material uncertainty and record provider assumptions separately from platform-independent intent.
3. Record the recommended direction, rejected alternatives, residual risks, and review triggers.

**Outputs**

- Feasibility and Serverless Suitability Record
- Risk and Opportunity Register

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Assess feasibility and serverless suitability evidence is recorded

**Checks and evidence**

- The recommendation compares credible alternatives rather than assuming serverless
- Material uncertainty has an owner and an experiment or decision deadline

**Uses in this process**

- `e2e.ph0.st1a.t1` in **Feasibility, Serverless Suitability & Economic Viability**

## Establish cost model and budget guardrails

<small>Task definition: `task.e2e.ph0.st1a.t2`</small>

Establish cost model and budget guardrails This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **FinOps and Cost Analyst**. The work normally involves Product Owner, Solution Architect, Cloud Platform Engineer, Service Owner. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Product and System Charter
- Feasibility and Serverless Suitability Record

**How to perform the task**

1. Model demand drivers and scenario ranges for requests, duration, memory, data transfer, storage, logs, workflows, and external services.
2. Define allocation tags, business unit-cost metrics, budget/forecast ranges, anomaly thresholds, and confidence assumptions.
3. Identify cost-sensitive architecture decisions and the runtime evidence needed to recalibrate estimates.

**Outputs**

- Cost Model and Budget Guardrails
- Risk and Opportunity Register

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Establish cost model and budget guardrails evidence is recorded

**Checks and evidence**

- The estimate states workload assumptions, range, confidence, owner, and review cadence
- At least one business unit-cost measure links technology spend to product value

**Uses in this process**

- `e2e.ph0.st1a.t2` in **Feasibility, Serverless Suitability & Economic Viability**

## Authorize pursue, explore, redirect, or stop at G0

<small>Task definition: `task.e2e.ph0.st1a.t3`</small>

Authorize pursue, explore, redirect, or stop at G0 This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Sponsor**. The work normally involves Product Owner, Solution Architect, Security Engineer, Service Owner, FinOps and Cost Analyst. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Product and System Charter
- Feasibility and Serverless Suitability Record
- Cost Model and Budget Guardrails
- Risk and Opportunity Register

**How to perform the task**

1. Review the product value case, suitability recommendation, cost range, material risks, and proposed experiments.
2. Record one explicit G0 decision: pursue, explore, redirect, or stop.
3. For pursue or explore, record funding/time boundaries, decision owners, and conditions that trigger reconsideration.

**Outputs**

- Product and System Charter
- Feasibility and Serverless Suitability Record
- Cost Model and Budget Guardrails
- Risk and Opportunity Register

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- An authorized G0 decision and its conditions are recorded

**Checks and evidence**

- Silence or task completion is not authorization
- A stop or redirect decision preserves the rationale and evidence

**Uses in this process**

- `e2e.ph0.st1a.t3` in **Feasibility, Serverless Suitability & Economic Viability**

## Assess context and process-tailoring risks

<small>Task definition: `task.e2e.ph0.st2.t1`</small>

Assess context and process-tailoring risks This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Method Engineer**. The work normally involves Delivery Lead, Process Reviewer. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Product and System Charter
- Increment and Integrated Plan Record

**How to perform the task**

1. Assess criticality, regulatory obligations, novelty, uncertainty, team distribution, system size, and delivery cadence.
2. Select the required CIM, PIM, PSM, artifact, and lifecycle activities.
3. Record excluded, combined, or delegated activities with rationale and compensating evidence.

**Outputs**

- Situational Method Profile
- Risk and Opportunity Register

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Assess context and process-tailoring risks evidence is recorded

**Checks and evidence**

- Every tailoring decision names its context, consequence, owner, and review point

**Uses in this process**

- `e2e.ph0.st2.t1` in **Situational Process Tailoring**

## Define the tailored Definition of Ready and Done

<small>Task definition: `task.e2e.ph0.st2.t2`</small>

Define the tailored Definition of Ready and Done This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Process Reviewer**. The work normally involves Method Engineer, Quality Engineer, Security Engineer, Service Owner. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Situational Method Profile
- Product and System Charter
- Increment and Integrated Plan Record

**How to perform the task**

1. Define entry and exit evidence for model slices, transformations, release candidates, operations, and retirement.
2. Define which findings are blocking, who may accept residual risk, and how time-bound risk acceptance works.
3. Publish the profile version used by the process run.

**Outputs**

- Situational Method Profile

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Define the tailored Definition of Ready and Done evidence is recorded

**Checks and evidence**

- A task cannot be accepted solely because activity occurred; required evidence and gate criteria are explicit

**Uses in this process**

- `e2e.ph0.st2.t2` in **Situational Process Tailoring**

## Define team ownership and interfaces

<small>Task definition: `task.e2e.ph0.st3.t1`</small>

Define team ownership and interfaces This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Delivery Lead**. The work normally involves Product Owner, Method Engineer. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Situational Method Profile
- Product and System Charter

**How to perform the task**

1. Partition work by bounded capability, service, platform concern, or lifecycle responsibility.
2. Assign model ownership, repository ownership, decision rights, and backup owners.
3. Define interface contracts and the shared dependency board.

**Outputs**

- Team Topology and Dependency Map

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Define team ownership and interfaces evidence is recorded

**Checks and evidence**

- Every owned model scope has one accountable owner and a named integration path

**Uses in this process**

- `e2e.ph0.st3.t1` in **Team Topology & Coordination**

## Set coordination cadence and escalation paths

<small>Task definition: `task.e2e.ph0.st3.t2`</small>

Set coordination cadence and escalation paths This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Delivery Lead**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Team Topology and Dependency Map
- Situational Method Profile
- Product and System Charter

**How to perform the task**

1. Set local team syncs, cross-team integration reviews, increment reviews, release reviews, and retrospectives.
2. Define escalation thresholds for aging dependencies, blocking findings, and conflicting model ownership.
3. Define how concurrent model edits are merged, reviewed, and reconciled.

**Outputs**

- Team Topology and Dependency Map
- Situational Method Profile

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Set coordination cadence and escalation paths evidence is recorded

**Checks and evidence**

- Coordination events produce decisions, dependency status, and evidence links

**Uses in this process**

- `e2e.ph0.st3.t2` in **Team Topology & Coordination**

## Define quality and security control objectives

<small>Task definition: `task.e2e.ph0.st4.t1`</small>

Define quality and security control objectives This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Security Engineer**. The work normally involves Quality Engineer, Service Owner, Records and Data Steward. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Team Topology and Dependency Map
- Situational Method Profile
- Product and System Charter
- Risk and Opportunity Register

**How to perform the task**

1. Identify privacy, threat, compliance, resilience, performance, accessibility, audit, cost, and records obligations.
2. Map control objectives to model evidence, generated artifact evidence, and explicit human decisions.
3. Set severity and blocking policies.

**Outputs**

- Situational Method Profile
- Risk and Opportunity Register

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Define quality and security control objectives evidence is recorded

**Checks and evidence**

- Critical controls have an accountable owner and verification evidence path

**Uses in this process**

- `e2e.ph0.st4.t1` in **Quality, Security & Operations Baseline**

## Define operational and release strategy

<small>Task definition: `task.e2e.ph0.st4.t2`</small>

Define operational and release strategy This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Service Owner**. The work normally involves Release Engineer, FinOps and Cost Analyst, Records and Data Steward, Security Engineer. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Situational Method Profile
- Team Topology and Dependency Map
- Product and System Charter
- Cost Model and Budget Guardrails
- Risk and Opportunity Register

**How to perform the task**

1. Define environments, support ownership, SLO hypotheses, observability expectations, cost signals, rollback posture, and release cadence.
2. Define the Kanban service-delivery system: board states and start/finish points, WIP controls, classes of service, service-level expectations, capacity allocation, replenishment and review cadences, and expedite/reconciliation policy.
3. Identify data migration, compatibility, records/retention, and progressive-delivery constraints and record the initial release decision policy.

**Outputs**

- Product and System Charter
- Situational Method Profile
- Kanban Service-Delivery Policy and Board

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Define operational and release strategy evidence is recorded

**Checks and evidence**

- The release strategy names promotion, rollback, and post-deployment validation evidence
- The operational policy prevents unknown future maintenance demand from being represented as pre-scheduled tasks

**Uses in this process**

- `e2e.ph0.st4.t2` in **Quality, Security & Operations Baseline**

## Plan the vertical increment

<small>Task definition: `task.e2e.p0.increment-planning.t1`</small>

Plan the vertical increment This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Product Owner**. The work normally involves Delivery Lead, Requirements Engineer, Domain Expert, FinOps and Cost Analyst. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Product and System Charter
- Situational Method Profile
- Team Topology and Dependency Map
- Cost Model and Budget Guardrails
- Risk and Opportunity Register

**How to perform the task**

1. Select scope from the ordered product backlog.
2. Define outcome, acceptance criteria, dependencies, risks, cost hypothesis, and expected model/artifact evidence.
3. Assign scope items to owning teams and establish the increment ledger.

**Outputs**

- Increment and Integrated Plan Record
- Risk and Opportunity Register

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Increment scope, owners, acceptance evidence, dependencies, and cost-sensitive assumptions are agreed

**Checks and evidence**

- The slice is small enough to inspect end-to-end and large enough to demonstrate a user or operational outcome

**Uses in this process**

- `e2e.p0.increment-planning.t1` in **Frame Increment**

## Run the CIM increment

<small>Task definition: `task.e2e.p1.cim-modeling.t1`</small>

Run the CIM increment This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Business Modeler**. The work normally involves Requirements Engineer, Domain Expert, Product Owner, Security Engineer. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Increment and Integrated Plan Record
- Product and System Charter
- Situational Method Profile
- Team Topology and Dependency Map

**How to perform the task**

1. Execute the CIM process for the selected capability slice.
2. Record semantic decisions, assumptions, trace links, readiness findings, and accepted CIM revision.
3. Return unresolved cross-team or product decisions to the integrated decision register.

**Outputs**

- Increment and Integrated Plan Record

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- CIM gate evidence is accepted or explicitly reworked

**Checks and evidence**

- Assistant-applied model changes pass structural Ecore/EMF conformance; semantic validation remains an explicit user/model validation workflow

**Uses in this process**

- `e2e.p1.cim-modeling.t1` in **CIM Child Process**

## Execute and inspect CIM-to-PIM transformation

<small>Task definition: `task.e2e.p2.cim-to-pim.t1`</small>

Execute and inspect CIM-to-PIM transformation This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Increment and Integrated Plan Record
- Situational Method Profile
- Product and System Charter

**How to perform the task**

1. Pin the source CIM revision and transformation profile.
2. Run the transformation and inspect report, trace links, assumptions, and manual decisions.
3. Accept, reject, or route each material decision and record the target PIM revision.

**Outputs**

- Increment and Integrated Plan Record

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Execute and inspect CIM-to-PIM transformation evidence is recorded

**Checks and evidence**

- Every generated target scope item is traceable to a source or explicit transformation decision

**Uses in this process**

- `e2e.p2.cim-to-pim.t1` in **CIM → PIM Transformation**

## Run the PIM increment

<small>Task definition: `task.e2e.p3.pim-refinement.t1`</small>

Run the PIM increment This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Solution Architect**. The work normally involves Security Engineer, Quality Engineer, Cloud Platform Engineer, Service Owner, FinOps and Cost Analyst. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Increment and Integrated Plan Record
- Product and System Charter
- Situational Method Profile

**How to perform the task**

1. Execute the PIM process for the transformed slice.
2. Review contracts, data access, integration, policy, platform mapping, cost drivers, and operational assumptions.
3. Record accepted PIM revision and open platform decisions.

**Outputs**

- Increment and Integrated Plan Record
- Risk and Opportunity Register

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- PIM gate evidence is accepted or explicitly reworked

**Checks and evidence**

- Generated PIM is treated as scaffolding until the solution architect accepts material decisions

**Uses in this process**

- `e2e.p3.pim-refinement.t1` in **PIM Child Process**

## Execute and inspect PIM-to-PSM transformation

<small>Task definition: `task.e2e.p4.pim-to-psm.t1`</small>

Execute and inspect PIM-to-PSM transformation This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Increment and Integrated Plan Record
- Situational Method Profile
- Product and System Charter

**How to perform the task**

1. Pin the source PIM revision and AWS mapping profile.
2. Run the transformation and inspect resource mappings, assumptions, security implications, and trace links.
3. Record accepted target PSM revision or route mapping gaps back to PIM.

**Outputs**

- Increment and Integrated Plan Record

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Execute and inspect PIM-to-PSM transformation evidence is recorded

**Checks and evidence**

- Every deployable PIM scope item has an accepted AWS mapping or a documented exception

**Uses in this process**

- `e2e.p4.pim-to-psm.t1` in **PIM → AWS PSM Transformation**

## Run the PSM increment

<small>Task definition: `task.e2e.p5.psm-refinement.t1`</small>

Run the PSM increment This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The work normally involves Solution Architect, Security Engineer, Quality Engineer, Release Engineer, Service Owner, FinOps and Cost Analyst. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Increment and Integrated Plan Record
- Situational Method Profile
- Product and System Charter

**How to perform the task**

1. Execute the AWS PSM process for the transformed slice.
2. Review resource relationships, IAM, networking, data, eventing, APIs, workflows, observability, quotas, allocation tags, and operational views.
3. Record accepted PSM revision and unresolved deployment risks.

**Outputs**

- Increment and Integrated Plan Record
- Cost Model and Budget Guardrails
- Risk and Opportunity Register

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- PSM gate evidence is accepted or explicitly reworked

**Checks and evidence**

- AWS platform choices are traceable to accepted PIM intent or explicit platform decisions

**Uses in this process**

- `e2e.p5.psm-refinement.t1` in **PSM Child Process**

## Generate and fingerprint the artifact baseline

<small>Task definition: `task.e2e.p6.m2t-generation.t1`</small>

Generate and fingerprint the artifact baseline This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Increment and Integrated Plan Record
- Situational Method Profile
- Team Topology and Dependency Map

**How to perform the task**

1. Pin PSM revision, generator version, templates, and environment profile.
2. Generate application, infrastructure, configuration, tests, and documentation artifacts.
3. Record the generation manifest and route structural gaps to PSM or generator ownership.

**Outputs**

- Increment and Integrated Plan Record

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Generate and fingerprint the artifact baseline evidence is recorded

**Checks and evidence**

- The generated baseline is reproducible from recorded inputs and contains no secret material

**Uses in this process**

- `e2e.p6.m2t-generation.t1` in **Model-to-Text Generation**

## Run artifact readiness and accept the increment

<small>Task definition: `task.e2e.p7.artifact-completion.t1`</small>

Run artifact readiness and accept the increment This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Process Reviewer**. The work normally involves Product Owner, Quality Engineer, Security Engineer, Service Owner, FinOps and Cost Analyst. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Increment and Integrated Plan Record
- Situational Method Profile
- Product and System Charter
- Cost Model and Budget Guardrails
- Risk and Opportunity Register

**How to perform the task**

1. Execute the artifact deployment-readiness process for the generated candidate.
2. Review verification, security, cost, rollback, operational, and acceptance evidence.
3. Record increment outcome, open findings, deferred scope, and feedback loops for the next cycle.

**Outputs**

- Increment and Integrated Plan Record

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Increment is accepted, explicitly deferred, or returned through a named rework loop

**Checks and evidence**

- No blocking finding is silently closed; risk acceptance is explicit, owned, time-bound, and traceable

**Uses in this process**

- `e2e.p7.artifact-completion.t1` in **Increment Readiness & Acceptance**

## Review the increment and adapt the way of working

<small>Task definition: `task.e2e.p7.artifact-completion.t2`</small>

Review the increment and adapt the way of working This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Method Engineer**. The work normally involves Delivery Lead, Process Reviewer, Product Owner. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Increment and Integrated Plan Record
- Situational Method Profile
- Cost Model and Budget Guardrails
- Risk and Opportunity Register
- Retrospective and Improvement Record

**How to perform the task**

1. Compare outcome, effort, queue time, rework, trace, cost, dependency, and finding evidence with the increment hypothesis.
2. Identify useful practices, avoidable burden, missing guidance, and candidate reusable assets.
3. Update the method profile, improvement record, owners, and experiment/review dates; record a reasoned no-op when no change is justified.

**Outputs**

- Increment and Integrated Plan Record
- Situational Method Profile
- Retrospective and Improvement Record

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- The increment retrospective has an owned improvement decision or a reasoned no-op

**Checks and evidence**

- A local workaround is not generalized without its context and evidence

**Uses in this process**

- `e2e.p7.artifact-completion.t2` in **Increment Readiness & Acceptance**

## Assemble the release candidate

<small>Task definition: `task.e2e.rel.a1.t1`</small>

Assemble the release candidate This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Release Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Increment and Integrated Plan Record
- Product and System Charter
- Situational Method Profile
- Team Topology and Dependency Map

**How to perform the task**

1. Select accepted increments and compatible model/artifact revisions.
2. Resolve cross-team dependency and compatibility checks.
3. Create the release record with exact inputs and promotion sequence.

**Outputs**

- Release, Recovery, Handover and Promotion Record

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Assemble the release candidate evidence is recorded

**Checks and evidence**

- Every release scope item maps to an accepted increment and exact artifact identity

**Uses in this process**

- `e2e.rel.a1.t1` in **Release Train Assembly**

## Review release evidence and go/no-go criteria

<small>Task definition: `task.e2e.rel.a1.t2`</small>

Review release evidence and go/no-go criteria This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Process Reviewer**. The work normally involves Product Owner, Quality Engineer, Security Engineer, Service Owner, FinOps and Cost Analyst. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Release, Recovery, Handover and Promotion Record
- Increment and Integrated Plan Record
- Product and System Charter
- Situational Method Profile
- Cost Model and Budget Guardrails
- Risk and Opportunity Register

**How to perform the task**

1. Inspect readiness assessments, validation results, security exceptions, rollback plan, operational runbooks, forecast/unit-cost evidence, budget guardrails, and approvals.
2. Confirm open blockers are zero or explicitly accepted under the tailored method profile.
3. Record the G6 decision and decision owners.

**Outputs**

- Release, Recovery, Handover and Promotion Record
- Cost Model and Budget Guardrails
- Risk and Opportunity Register

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Review release evidence and go/no-go criteria evidence is recorded

**Checks and evidence**

- Go/no-go is evidence-based and not inferred from task completion

**Uses in this process**

- `e2e.rel.a1.t2` in **Release Train Assembly**

## Deploy and validate progressively

<small>Task definition: `task.e2e.rel.a2.t1`</small>

Deploy and validate progressively This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Release Engineer**. The work normally involves Quality Engineer, Security Engineer, Service Owner, FinOps and Cost Analyst. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Release, Recovery, Handover and Promotion Record
- Product and System Charter
- Situational Method Profile
- Team Topology and Dependency Map

**How to perform the task**

1. Deploy the exact candidate to the approved environment sequence.
2. Run smoke, functional, security, data, observability, cost-signal, and compatibility checks.
3. Compare observed outcomes with release acceptance signals and stop or roll back on threshold breach.

**Outputs**

- Release, Recovery, Handover and Promotion Record

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Deploy and validate progressively evidence is recorded

**Checks and evidence**

- Promotion evidence identifies candidate, environment, timestamp, operator, and observed result

**Uses in this process**

- `e2e.rel.a2.t1` in **Progressive Promotion**

## Complete handover and rollback rehearsal

<small>Task definition: `task.e2e.rel.a2.t2`</small>

Complete handover and rollback rehearsal This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Service Owner**. The work normally involves Release Engineer, Security Engineer, FinOps and Cost Analyst. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Release, Recovery, Handover and Promotion Record
- Product and System Charter
- Situational Method Profile
- Team Topology and Dependency Map

**How to perform the task**

1. Verify dashboards, alerts, runbooks, escalation paths, support ownership, cost/anomaly views, and recovery access.
2. Confirm rollback and data-recovery actions are usable for the release.
3. Accept operational ownership or return the release to rework.

**Outputs**

- Release, Recovery, Handover and Promotion Record
- Operational Evidence, Incident and Change Record

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Complete handover and rollback rehearsal evidence is recorded

**Checks and evidence**

- Operational acceptance names an accountable service owner and recovery evidence

**Uses in this process**

- `e2e.rel.a2.t2` in **Progressive Promotion**

## Review release outcome and update roadmap

<small>Task definition: `task.e2e.rel.a3.t1`</small>

Review release outcome and update roadmap This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Product Owner**. The work normally involves Service Owner, FinOps and Cost Analyst, Method Engineer, Delivery Lead. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Release, Recovery, Handover and Promotion Record
- Operational Evidence, Incident and Change Record
- Product and System Charter
- Increment and Integrated Plan Record
- Situational Method Profile
- Cost Model and Budget Guardrails
- Retrospective and Improvement Record

**How to perform the task**

1. Compare product, operational, and unit-cost outcomes with the release hypothesis.
2. Accept outcomes, revise priorities, and create follow-up increments for gaps.
3. Record decisions and changes to scope, measures, estimates, and assumptions.

**Outputs**

- Release, Recovery, Handover and Promotion Record
- Product and System Charter
- Increment and Integrated Plan Record
- Cost Model and Budget Guardrails
- Retrospective and Improvement Record

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Review release outcome and update roadmap evidence is recorded

**Checks and evidence**

- Outcome learning changes a backlog, product decision, cost forecast, or explicitly confirms the hypothesis

**Uses in this process**

- `e2e.rel.a3.t1` in **Release Review**

## Approve retirement scope and plan

<small>Task definition: `task.e2e.ph2.st1.t1`</small>

Approve retirement scope and plan This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Product Owner**. The work normally involves Sponsor, Service Owner, Security Engineer, FinOps and Cost Analyst, Records and Data Steward. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Operational Evidence, Incident and Change Record
- Release, Recovery, Handover and Promotion Record
- Product and System Charter
- Situational Method Profile
- Cost Model and Budget Guardrails
- Risk and Opportunity Register

**How to perform the task**

1. Confirm business, technical, legal, security, financial, records, and operational reasons for retirement.
2. Define replacement, user communication, compatibility, rollback, retention/export/deletion, and exit criteria.
3. Assign owners and schedule decision checkpoints.

**Outputs**

- Retirement, Data-Disposition and Closure Record
- Risk and Opportunity Register

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Approve retirement scope and plan evidence is recorded

**Checks and evidence**

- The plan identifies affected stakeholders, dependencies, records obligations, and irreversible actions

**Uses in this process**

- `e2e.ph2.st1.t1` in **Retirement Decision & Plan**

## Execute migration and data disposition

<small>Task definition: `task.e2e.ph2.st2.t1`</small>

Execute migration and data disposition This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The work normally involves Records and Data Steward, Security Engineer, Service Owner. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Retirement, Data-Disposition and Closure Record
- Operational Evidence, Incident and Change Record
- Release, Recovery, Handover and Promotion Record
- Situational Method Profile

**How to perform the task**

1. Execute migration, archival, retention, deletion, or export according to approved policy.
2. Validate completeness, confidentiality, integrity, and recoverability where required.
3. Record final data and integration evidence.

**Outputs**

- Retirement, Data-Disposition and Closure Record

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Execute migration and data disposition evidence is recorded

**Checks and evidence**

- No data or integration is silently abandoned

**Uses in this process**

- `e2e.ph2.st2.t1` in **Migrate and Decommission**

## Decommission service, access, and cost surfaces

<small>Task definition: `task.e2e.ph2.st2.t2`</small>

Decommission service, access, and cost surfaces This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Service Owner**. The work normally involves Cloud Platform Engineer, Security Engineer, FinOps and Cost Analyst, Records and Data Steward. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Retirement, Data-Disposition and Closure Record
- Operational Evidence, Incident and Change Record
- Release, Recovery, Handover and Promotion Record
- Team Topology and Dependency Map
- Cost Model and Budget Guardrails

**How to perform the task**

1. Continue required operational coverage during migration, then disable traffic, scheduled work, credentials, access paths, alerts, and environments in the approved order.
2. Verify replacement ownership, customer communication, billing cessation, and removal of residual chargeable resources.
3. Retain required source, model, trace, release, incident, and decision records.

**Outputs**

- Retirement, Data-Disposition and Closure Record
- Cost Model and Budget Guardrails

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Decommission service, access, and cost surfaces evidence is recorded

**Checks and evidence**

- Decommission evidence covers runtime, data, access, cost, and support surfaces

**Uses in this process**

- `e2e.ph2.st2.t2` in **Migrate and Decommission**

## Verify records retention and data disposition

<small>Task definition: `task.e2e.ph2.st2.t3`</small>

Verify records retention and data disposition This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Records and Data Steward**. The work normally involves Security Engineer, Service Owner, Cloud Platform Engineer. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Retirement, Data-Disposition and Closure Record
- Operational Evidence, Incident and Change Record
- Situational Method Profile

**How to perform the task**

1. Reconcile every declared data class, store, backup, export, legal hold, record series, and derived copy with the approved disposition.
2. Verify deletion, sanitization, transfer, retention ownership, and evidence custody with Security and Service Owners.
3. Record unresolved obligations as blockers to G8 rather than closing them by exception without authority.

**Outputs**

- Retirement, Data-Disposition and Closure Record

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Verify records retention and data disposition evidence is recorded

**Checks and evidence**

- Every governed data set has disposition evidence and a custodian
- G8 is blocked by an unowned retention, deletion, or legal-hold obligation

**Uses in this process**

- `e2e.ph2.st2.t3` in **Migrate and Decommission**

## Complete closure review

<small>Task definition: `task.e2e.ph2.st3.t1`</small>

Complete closure review This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Process Reviewer**. The work normally involves Sponsor, Product Owner, Service Owner, FinOps and Cost Analyst, Records and Data Steward, Method Engineer. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Retirement, Data-Disposition and Closure Record
- Operational Evidence, Incident and Change Record
- Product and System Charter
- Increment and Integrated Plan Record
- Situational Method Profile
- Release, Recovery, Handover and Promotion Record
- Cost Model and Budget Guardrails
- Retrospective and Improvement Record

**How to perform the task**

1. Confirm retirement exit criteria, financial closure, records, and custodians are complete.
2. Evaluate Development and Delivery evidence together with operational shutdown evidence at shared G8; do not model this synchronization as an activity-flow edge between the processes.
3. Review product, architecture, operational, economic, and process outcomes.
4. Publish reusable patterns, risks, and process changes for future projects.

**Outputs**

- Retirement, Data-Disposition and Closure Record
- Situational Method Profile
- Cost Model and Budget Guardrails
- Retrospective and Improvement Record

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Complete closure review evidence is recorded

**Checks and evidence**

- Closure identifies retained evidence, unresolved obligations, and accountable custodians
- G8 is accepted only when no live release remains and cost, access, and data obligations are closed

**Uses in this process**

- `e2e.ph2.st3.t1` in **Closure & Organizational Learning**

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
