# MODRISS Full-Lifecycle Serverless Software Development Process

## Purpose and operating model

MODRISS is a product and service lifecycle with a model-driven delivery engine
inside it. The lifecycle may last for years and span many releases. The engine
creates one bounded, testable vertical increment at a time and can repeat
several times within a release.

The default lifecycle is iterative, incremental, risk-driven,
architecture-conscious, requirements-based, and evidence-gated. Phases express
dominant objectives rather than hard departmental handoffs. Activities from
different phases may overlap when inputs, ownership, and decision authority are
explicit.

## Lifecycle at a glance

| Phase                                          | Objective                                                                                                               | Exit outcome                                                      |
| ---------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| 0. Initiate, Tailor, and Organize              | Establish value, feasibility, fit, method profile, controls, teams, and first release hypothesis.                       | Authorized product/service endeavor and enactable method profile. |
| 1. Iterative-Incremental Model-Driven Delivery | Turn a valuable slice into accepted CIM, PIM, PSM, generated, and verified artifacts.                                   | Accepted vertical increment or explicit rework/defer decision.    |
| 2. Release and Transition                      | Assemble compatible increments, qualify a release, promote progressively, and hand over operation.                      | Deployed release accepted by product and service owners.          |
| 3. Operate, Evolve, and Learn                  | Run the service, meet outcomes/SLOs, control cost/risk, respond to incidents, and propagate change.                     | Stable service plus prioritized evidence-driven changes.          |
| 4. Retire, Migrate, and Close                  | End or replace the service without leaving users, data, integrations, access, infrastructure, or obligations ownerless. | Verified decommissioning and retained organizational knowledge.   |

### Nested lifecycle cadence

The five phases are not one pass from left to right. MODRISS separates four
cadences that are easy to confuse in a single overview figure:

1. **Product/service lifecycle:** Phase 0 establishes the endeavor once;
   Phase 4 closes it only after an explicit retirement decision.
2. **Release cycle:** Phases 1, 2, and 3 repeat for each release. Operational
   evidence and roadmap demand form the hypothesis for a later release and
   return work to Phase 1 at the earliest authoritative source.
3. **Increment cycle:** Phase 1 repeats one or more bounded vertical increments
   before a release candidate is assembled. Each increment crosses the
   required CIM, PIM, PSM, generation, implementation, and verification work.
4. **Continuous operation:** The last accepted release normally remains in
   operation while the next release is engineered. Beginning a later release
   does not restart the product lifecycle and does not imply downtime.

Consequently, G7 is not the end of the process. After transition, the team may
continue observing the current release without immediately changing it, start
a new release from planned or operationally discovered work, or initiate
Phase 4 when retirement is authorized. If G6 rejects a candidate, or promotion
crosses a stop threshold, the team retains the last accepted operating
baseline and returns corrective work to Phase 1 before requalification.

## Phase 0 — Initiate, Tailor, and Organize

### 0.1 Frame the opportunity and product system

The Product Owner, Sponsor, Domain Expert, and Service Owner define the user or
mission problem, stakeholders, measurable outcomes, non-goals, product/system
boundary, expected lifetime, initial release hypothesis, and operational
expectations. Existing systems, suppliers, data, legal obligations, and exit
conditions are identified early.

Outputs: product/system charter, stakeholder map, outcome measures, initial
scope/backlog, and product decision log.

### 0.2 Assess feasibility and serverless suitability

The Solution Architect leads feasibility analysis with product, platform,
security, quality, finance/FinOps, and operations roles. The team compares
serverless, hybrid, and non-serverless alternatives using workload shape,
latency, state, integration, availability, compliance, skills, cost, portability,
operations, and migration/exit constraints. Uncertainty is handled with a
bounded architecture or cost experiment rather than an unsupported assumption.

Outputs: feasibility study, serverless suitability record, initial cost model,
risk register, provider/target decision or decision plan, and approved
experiments.

Gate G0 — **Pursue / explore / redirect / stop**. The Sponsor and Product Owner
accept the value case; Architecture, Security, and Service Owners accept that
risks are understood enough for the next commitment.

### 0.3 Engineer the situational method

The Method Engineer evaluates the factors in `02-method-requirements.md`,
selects a reference configuration, adds conditional packages, records omitted
or substituted content, assigns evidence depth, and sets review triggers. The
profile is versioned; it is not an informal team convention.

Outputs: situational assessment, method profile, tailoring decisions, gate and
evidence plan, training plan.

### 0.4 Organize teams, ownership, and flow

The Delivery Lead defines stream-aligned scope, model ownership, integration
ownership, platform enablement, dependency management, decision rights,
communication cadence, escalation, source-control strategy, and process-run
tracking. Small teams combine roles; large programs distribute roles without
splitting accountability.

Outputs: team topology, responsibility assignment, model/repository ownership,
dependency board, integrated plan, communication/escalation plan.

### 0.5 Establish quality, security, operations, and delivery baselines

Quality, Security, Platform, Release, and Service Owners define acceptance
evidence, threat/privacy approach, environment and secret strategy, SLO/SLI
intent, observability, backup/recovery, CI/CD controls, artifact provenance,
and definitions of Ready and Done.

Gate G1 — **Method and organization ready**. The first increment has a bounded
outcome, owners, dependencies, risk treatment, evidence expectations, and a
valid method profile.

## Phase 1 — Iterative-Incremental Model-Driven Delivery

### The vertical increment loop

One increment implements a coherent outcome slice through all necessary levels.
It is not “finish all CIM, then all PIM, then all PSM.” Earlier levels may be
broader for context, but accepted scope and revisions are explicit.

#### 1.1 Frame the increment

Select a valuable capability/event journey, outcome signal, requirements,
quality scenarios, dependencies, risks, and acceptance evidence. Define the
affected bounded contexts and anticipated deployment slice. Reassess the method
profile if novelty or criticality changed.

Exit: Increment Ready.

#### 1.2 Discover and model the CIM

Apply the reusable CIM component to model strategic intent, stakeholders,
capabilities, language, information, entities/value objects, commands, queries,
events, policies, processes, decisions, functional and non-functional
requirements, governance, and transformation metadata. The CIM remains
computation- and provider-independent.

Quality activities include structured review, trace completion, explicit
semantic model validation, and readiness assessment. In assistant workflows,
only structural Ecore/EMF conformance gates assistant-generated changes;
semantic EVL validation is a separate explicit user/model validation workflow.

Gate G2 — **CIM accepted for this slice**. Stakeholder meaning, requirements,
acceptance, domain behavior, trace, and open decisions are adequate to design a
solution.

#### 1.3 Transform CIM to PIM and reconcile

Run the versioned ETL profile into an empty generated target. Compare the new
generated result with the previous generated baseline and the user-refined
working PIM. Apply independent changes, retain independent refinements, and
surface overlapping edits as conflicts. Review the transformation report,
trace links, assumptions, placeholders, and manual decisions.

The generated PIM is a draft. Completion of ETL is not acceptance.

#### 1.4 Refine the PIM serverless architecture

Define services and boundaries; contracts and schemas; events and envelopes;
functions and triggers; APIs; data stores, access patterns, state, retention,
and recovery; event channels and routing; orchestration and choreography;
identity, permissions, security, privacy, resilience, throughput, ordering,
observability and SLO policies; external adapters; configuration; deployment
units; portability; and platform capability expectations.

Record architectural decisions for granularity, orchestration versus
choreography, state, consistency, retries/idempotency, failure isolation,
multi-tenancy, cold starts, cost, and lock-in. Threat, failure-mode, and cost
reviews are part of architecture, not postponed until PSM.

Gate G3 — **PIM accepted and platform-mappable**. Contracts, failure behavior,
security, data, SLOs, traceability, and provider capability needs are explicit;
open mapping gaps are owned.

#### 1.5 Transform PIM to PSM and reconcile

Confirm or revisit provider selection. Run the versioned PIM→PSM transform and
perform the same baseline/working/new-generated reconciliation. Inspect every
generated provider resource, IAM implication, network assumption, workflow
mapping, unsupported capability, and manual decision.

#### 1.6 Refine the AWS PSM

For the current profile, refine account/stage and naming strategy, stacks and
parameters, IAM/KMS/secrets/SSM, VPC/endpoints/security groups, identity,
DynamoDB/S3, SQS/SNS, EventBridge, Lambda and event sources, API Gateway,
Step Functions, logging, metrics, alarms, dashboards, and integration views.
Review quotas, concurrency, reserved/provisioned capacity, encryption,
retention, deletion policy, recovery, regional behavior, and cost controls.

Gate G4 — **PSM accepted for generation**. The exact PSM revision passes
structural conformance and the explicit model-validation workflow required by
the profile; traces and blocking manual decisions are closed or accepted by an
authorized owner.

#### 1.7 Generate a reproducible artifact baseline

Run EGX/EGL from the accepted PSM and record model revision, generator and
template versions, configuration, environment, output manifest, hashes, trace,
warnings, protected regions, and manual actions. Generated output remains
replaceable; custom code belongs in supported extension/protected regions or in
the generator/model source.

#### 1.8 Complete implementation and test in the small

Developers complete business logic, UI/client work where applicable, adapters,
fixtures, and documented manual actions. Use unit, contract, component,
workflow, policy, permission, and failure-path tests. Static checks and build
verification run in CI. A model-derived test remains subject to human review;
generation is not proof of correctness.

#### 1.9 Integrate and test in the large

Deploy to an appropriate non-production environment and execute integration,
end-to-end, acceptance, performance/load, concurrency, cold-start, resilience,
recovery, security, compatibility, usability/accessibility, and operational
tests selected by risk. Validate dashboards, alerts, tracing, runbooks, and cost
signals during testing.

Gate G5 — **Increment accepted / rework / defer**. The Product Owner accepts
value and behavior; Quality and Security accept required evidence or approved
exceptions; the Service Owner accepts operability; unresolved structural
changes are routed back to the authoritative model/generator source.

### Increment review and adaptation

At the end of each increment, inspect outcome evidence, flow time, rework,
trace gaps, findings, dependency age, cost forecasts, and method friction.
Update backlog, risks, architecture decisions, method profile, and reusable
assets. A retrospective may produce an improvement action or a reasoned no-op.

## Phase 2 — Release and Transition

### 2.1 Assemble the release candidate

Select accepted increments and freeze exact model revisions, transformation
profiles, generator versions, source revisions, artifact digests, schemas,
configuration, database changes, and dependencies. Check backward/forward
compatibility, event/API evolution, consumer readiness, data migration, quota
capacity, and feature-control strategy.

### 2.2 Qualify release and recovery

Re-run risk-proportional verification against the immutable candidate. Review
security and privacy findings, software supply chain, infrastructure change,
cost forecast, support readiness, observability, SLOs, capacity, backup,
restore, rollback/roll-forward, data recovery, incident communications, and
approvals.

Gate G6 — **Release authorized / rejected / exception accepted**. Approval
identifies the exact candidate and evidence; task completion alone cannot
override a blocker.

### 2.3 Promote progressively

Promote through environments using the selected strategy: canary, weighted
alias, blue/green, feature control, or carefully governed direct promotion for
low-risk situations. Automate checks and define stop, rollback, and escalation
thresholds. Observe business, technical, security, and cost signals.

### 2.4 Operational handover and outcome review

The Service Owner accepts dashboards, alerts, traces, runbooks, on-call/support
ownership, access, recovery procedures, known errors, risk acceptances, and
post-deployment checks. The Product Owner schedules outcome review against the
release hypothesis.

Gate G7 — **Release transitioned**. Deployment is not complete until the
running service and its support obligations have owners and evidence.

G7 establishes a new operating baseline; it does not terminate development.
Unless retirement has been authorized, roadmap work, telemetry, incidents,
risks, cost evidence, and user feedback are assessed for a subsequent release.
That release receives a new hypothesis, increment set, immutable candidate,
G6 decision, and G7 transition record.

## Phase 3 — Operate, Evolve, and Learn

### 3.1 Operate and observe

Operate against SLOs and product outcomes. Monitor latency, availability,
errors, throttling, concurrency, retries, dead letters, workflow failures,
security signals, quota usage, cold starts, cost/unit economics, customer
impact, and provider health. Execute routine recovery, patching, access review,
certificate/key rotation, dependency update, and continuity work.

### 3.2 Respond to incidents and problems

Restore service first under the incident process, preserve an evidence
timeline, communicate, and track temporary changes. Problem analysis identifies
systemic causes and produces model, generator, artifact, process, or operational
changes. Blameless learning does not remove accountable follow-through.

### 3.3 Classify and propagate change

Route changes to the earliest authoritative source:

| Change                                                                     | Re-entry point              |
| -------------------------------------------------------------------------- | --------------------------- |
| outcome, requirement, domain rule, event meaning                           | CIM                         |
| service boundary, contract, data, workflow, provider-independent policy    | PIM                         |
| AWS resource, IAM, network, runtime, deployment, observability realization | PSM                         |
| generator defect or reusable scaffold concern                              | EGL/EGX/generator source    |
| business logic or supported extension implementation                       | artifact/code workflow      |
| promotion, environment, rollback, support control                          | release/operations workflow |
| method friction or missing guidance                                        | method-engineering backlog  |

Perform impact analysis through traces, create a bounded change increment,
propagate forward, and obtain new evidence. Emergency downstream changes must
be reconciled into the authoritative source after stabilization.

### 3.4 Learn and generalize

Compare actual outcomes, SLOs, cost, risks, estimates, and process measures with
hypotheses. Generalize reusable models, patterns, transformations, tests,
runbooks, and method fragments only after review. Update training and the
method library through versioned change.

## Phase 4 — Retire, Migrate, and Close

### 4.1 Decide and plan retirement

Define reason, scope, successor, users/consumers, contractual and legal
obligations, data retention/disposition, integrations, access, infrastructure,
communications, rollback window, and evidence retention. Treat retirement as a
release with acceptance criteria.

### 4.2 Migrate and decommission

Migrate or export data, users, events, and integrations; validate completeness;
notify consumers; stop traffic and schedules; revoke credentials and access;
remove subscriptions, queues, endpoints, domains, alarms, and resources in a
controlled sequence; and verify billing/cost closure. Preserve required models,
code, logs, decisions, and audit evidence.

### 4.3 Close and learn

Confirm that no data, integration, account, infrastructure, support obligation,
or legal record is ownerless. Archive evidence, review outcomes, publish
lessons, and return reusable assets to the library.

Gate G8 — **Lifecycle closed**. Product, Service, Security/Privacy, and Records
owners accept closure.

## Process-run state model

Every increment, release, major change, and retirement run records:

- run, product, increment/release/change identifiers;
- method-profile version and selected configuration packages;
- scope and exact input/output revisions;
- owner, participating roles/teams, dependencies, risks, and decisions;
- state: `not-started`, `in-progress`, `blocked`, `ready-for-review`,
  `accepted`, `rework`, `deferred`, or `closed`;
- evidence and finding links; and
- timestamps and acceptance authority.

Progress percentage is informative only. A single blocking finding, missing
trace, failed control, or unowned decision prevents gate acceptance regardless
of task completion.
