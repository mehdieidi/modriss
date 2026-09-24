# Hypothetical Enactment and Analytical Process Validation

## Status and purpose

This document reports a complete **analytical hypothetical enactment** of the
MODRISS method. It is a design walkthrough and coverage test, not an empirical
case study. It can establish internal consistency, role/product coverage, and
the plausibility of the way of working. It cannot establish usability,
efficiency, comparative advantage, or project outcomes; those claims require
the multiple-case protocol in
[`09-empirical-validation-protocol.md`](09-empirical-validation-protocol.md).

The walkthrough exercised all five executable process components, every
TaskDefinition through a TaskUse, every RoleDefinition, every executable work
product, the three one-time phases, the repeatable increment and release
activities, the ongoing Operations and Maintenance process, all DevOps
interfaces, and gates G0–G8. The generated evidence is in
[`evaluation/coldchain-sentinel/`](evaluation/coldchain-sentinel/).

## Case context: ColdChain Sentinel

ColdChain Sentinel is a fictional service that receives medicine-shipment
temperature readings, identifies excursions, coordinates acknowledgement and
escalation, and retains evidence for compliance review. The proposed AWS
solution uses API Gateway, Lambda, DynamoDB, S3, EventBridge, SQS with a DLQ,
SNS, Step Functions, Cognito, KMS/Secrets Manager, Systems Manager parameters,
CloudWatch, and a private ERP integration.

The case deliberately includes uncertainty and change:

- a private-ERP connectivity experiment and an explicit provider-exit
  assumption;
- two planned increments and two releases;
- one failed progressive promotion caused by duplicate alerts;
- an expedite incident followed by authoritative-source reconciliation;
- an operations-only alarm-threshold change;
- a bounded PIM→PSM→artifact retry/idempotency correction;
- a compliance-analytics feature committed to a planned release; and
- retirement with migration, records retention, access removal, and residual
  cost verification.

Seventeen fictional assignees cover product, domain, method, delivery,
modeling, architecture, platform, application, quality, security, FinOps,
release, service, assurance, sponsorship, and data/records responsibilities.
One person may hold more than one role in a tailored real project; the
responsibilities remain distinct.

## Enactment path

### Phase 0 — one-time inception, tailoring, and organization

The Sponsor and Product Owner first frame the outcome: detect excursions fast
enough to protect medicine while producing auditable evidence at an acceptable
cost per monitored shipment. The team compares serverless, container, and
managed-IoT alternatives against workload shape, latency, state, integration,
availability, compliance, skills, operations, portability, and exit needs.
It selects a serverless event-driven approach conditionally, with a bounded
experiment for private ERP connectivity.

The FinOps and Cost Analyst estimates request volume, Lambda memory-duration,
workflow transitions, data storage, transfer, logging, and external-service
charges; defines cost per monitored shipment; and sets forecast and anomaly
thresholds. G0 records **pursue**, conditional on the experiment and pilot
budget ceiling. The Method Engineer then tailors the method, the Delivery Lead
assigns ownership and dependencies, and quality, security, operational,
records, and release controls are defined. G1 authorizes the active-product
phase only after the first vertical slice, evidence rules, owners, and Kanban
service policy are ready.

This ordering proved important: tailoring before feasibility would optimize a
method for an initiative that might properly stop, while provider commitment
before the suitability decision would make the PIM boundary fictitious.

### Phase 1 — active product construction and evolution

#### Increment 1 and gates G2–G5

The first slice covers sensor ingestion, excursion detection, notification,
acknowledgement, and audit evidence. The CIM process captures stakeholders,
goals, domain vocabulary, commands, events, processes, policies, quality
scenarios, governance, trace, and readiness. G2 accepts the business intent.

The CIM→PIM transformation creates architecture scaffolding. Human review
finds that idempotency and late-event semantics require explicit design rather
than a silent transformation default. PIM work defines provider-independent
services, contracts, event flows, workflows, data, security, resilience,
configuration, and observability. G3 accepts the corrected PIM.

The PIM→PSM transformation maps accepted intent to AWS resources. The private
ERP connection remains a reviewed, owned manual mapping instead of pretending
complete automation. PSM work resolves IAM, networking, quotas, alarms,
allocation tags, retry/DLQ behavior, deployment, and resource relationships;
G4 accepts it. EGL/EGX produces the reproducible baseline. Application logic,
tests, pipeline, security checks, operational evidence, rollback, and readiness
are completed and reviewed at G5.

The increment retrospective identifies review-queue delay and duplicated
evidence. The Method Engineer changes the tailored profile by adding a
gate-ready signal, a review service expectation, and links to authoritative
evidence instead of copied attachments. This is method adaptation within
Phase 1, not another phase.

#### Release 1 and gates G6–G7

The Release Engineer assembles an immutable candidate. G6 reviews traceable
model/artifact revisions, tests, security exceptions, recovery, forecast/unit
cost, runbooks, and operational readiness. Progressive promotion uses a
weighted Lambda alias and explicit stop thresholds. At G7 the Service Owner
accepts dashboards, alerts, runbooks, escalation, recovery access, cost views,
and ownership. Operations and Maintenance begins here while Phase 1 remains
active for later delivery.

#### Concurrent operations and Increment 2

The development lane prepares compliance analytics while Operations sustains
Release 1. Operational demand is pulled through Requested → Ready → In
Progress → Verify → Done with WIP 2 for standard work and one visible expedite
slot. The case exercises all three dispositions:

1. alarm-threshold tuning is completed operations-only with evidence;
2. retry/idempotency correction follows the shortest safe PIM→PSM→artifact→
   release path; and
3. analytics export is explicitly committed to the next planned release.

An alert-delivery incident uses the expedite policy. Restoration temporarily
disables a faulty retry path, but the item cannot close until the accepted PIM,
PSM, generated configuration, tests, and live release are reconciled. This
preserves urgent recovery without allowing production to become an
untraceable source of truth.

Release 2 pauses at 10% promotion when duplicate-alert thresholds are
breached. The accepted Release 1 baseline remains live. The team routes the
defect to the authoritative PIM retry/idempotency policy, propagates the
correction, repeats affected assurance work, and re-enters G6. The corrected
candidate is promoted and handed over at G7. Thus rejection loops over release
and affected increment activities, never over a lifecycle phase.

### Phase 2 — one-time retirement, migration, and closure

After an authorized product decision, Phase 2 plans replacement, stakeholder
communication, compatibility, data disposition, rollback, records, security,
financial closure, and irreversible actions. Operations continues to sustain
the remaining live release during migration; Phase 2 is not entered from the
Kanban board and no activity-flow edge is required between the two processes.

The team migrates required records, reconciles DynamoDB tables, S3 archives,
backups, exports, log retention, derived copies, and legal holds, and assigns
custody. It then disables traffic, schedules, credentials, alerts, and
environments in the approved order and verifies billing cessation and removal
of residual chargeable resources. G8 is a **shared closure condition**:
Development and Delivery supplies retirement evidence and Operations supplies
shutdown evidence. It is accepted only when no live release, unowned data or
records obligation, access path, residual chargeable resource, or closure
blocker remains.

## Exhaustive coverage result

| Coverage object                                                           |    Result | Reproducible evidence                                                                                  |
| ------------------------------------------------------------------------- | --------: | ------------------------------------------------------------------------------------------------------ |
| Executable tasks across end-to-end, CIM, PIM, PSM, and artifact processes | 139 / 139 | [`task-enactment-ledger.csv`](evaluation/coldchain-sentinel/task-enactment-ledger.csv)                 |
| Defined roles with at least one primary or supporting task binding        |   17 / 17 | [`role-enactment-ledger.csv`](evaluation/coldchain-sentinel/role-enactment-ledger.csv)                 |
| Executable work-product definitions instantiated and reviewed             |   65 / 65 | [`work-product-enactment-ledger.csv`](evaluation/coldchain-sentinel/work-product-enactment-ledger.csv) |
| Gate decisions with authority, evidence, condition, and result            |     9 / 9 | [`gate-decision-ledger.csv`](evaluation/coldchain-sentinel/gate-decision-ledger.csv)                   |

The generator and verifier are
[`build-hypothetical-case.mjs`](tools/build-hypothetical-case.mjs) and
[`verify-hypothetical-case.mjs`](tools/verify-hypothetical-case.mjs). Exact set
comparison prevents a narrative claim of completeness when a definition is
missing from the case ledger.

## Defects exposed and method-engineering corrections

| Observed defect before correction                                                                         | Why it mattered                                                              | Corrected method fragment/content                                                                   |
| --------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Sponsor, FinOps/Cost Analyst, and Records/Data Steward existed conceptually but had no executable TaskUse | Decisions and evidence could be described but not assigned or enacted        | MF-01/MF-02/MF-15/MF-17 and UF-01 now bind them as primary or supporting performers                 |
| Canonical lifecycle products were collapsed into generic executable records without explicit mappings     | Product ownership and audit trace could disappear at compilation             | Executable definitions now retain one or more `methodWorkProductIds`                                |
| Generic milestones did not encode all G0–G8 authorities, evidence, and acceptance conditions              | A completed activity could be mistaken for authorization                     | Explicit nine-gate register compiled into JSON and SPEM                                             |
| No explicit increment-level way-of-working adaptation                                                     | The method could collect metrics without changing practice                   | MF-03/UF-01 gains an increment retrospective and owned improvement decision or reasoned no-op       |
| Cost feasibility, forecast, unit economics, and anomaly feedback were mostly narrative                    | Serverless consumption risk was not executable across the lifecycle          | MF-02/MF-13/MF-15 now create and update a cost model at G0, increments, G6, operations, and closure |
| Records and data disposition were not an explicit executable closure task                                 | G8 could close with an unowned legal hold, backup, or retained copy          | MF-17 adds data/records reconciliation and makes unresolved obligations G8 blockers                 |
| Supporting collaborators were lost during process compilation                                             | A single primary performer understated cross-functional serverless decisions | TaskDefinitions and TaskUses now distinguish primary and supporting performers                      |

These are method-content corrections, not ad hoc case exceptions. They were
made through the same requirement → fragment → method content → process use →
evidence chain used by the rest of MODRISS.

## Scientific rationale

The corrections follow the supplied method-engineering literature. Ramsin and
Paige require iterative, criteria-based stabilization of method requirements;
Asadi and Ramsin describe process patterns through initial/result context,
roles, work products, and tasks; and Asadi, Esfahani, and Ramsin supply
model-driven process patterns for refinement, transformation, review, and
feedback. Eidi and Ramsin's criteria make project management, maintenance,
MDD, and serverless-specific cost/risk concerns assessable rather than
implicit. Full bibliographic details for the supplied papers are in
[`01-research-synthesis.md`](01-research-synthesis.md).

The external fragments are also traceable:

- SPEM distinguishes reusable method content from its process uses and
  provides `isOngoing`/`isEventDriven`, supporting the concurrent operations
  component and explicit gates ([OMG SPEM 2.0](https://www.omg.org/spec/SPEM/2.0/PDF/)).
- Disciplined Agile distinguishes phase-oriented project lifecycles from
  continuous-delivery/product-team lifecycles and supports choosing iteration
  or flow by context ([PMI full lifecycles](https://www.pmi.org/disciplined-agile/process/introduction-to-dad/full-delivery-lifecycles-introduction),
  [PMI continuous delivery](https://www.pmi.org/disciplined-agile/lifecycle/dad-lifecycle-continuous-delivery-agile)).
- The Kanban Guide requires an explicit Definition of Workflow, WIP control,
  pull, an SLE, and flow measures; these govern unpredictable service demand
  rather than turning it into a fictional planned phase
  ([Kanban Guide 2025](https://kanbanguides.org/the-kanban-guide/)).
- ISO/IEC/IEEE 12207 covers development, operation, maintenance, and disposal
  processes and permits concurrent and iterative application
  ([ISO 12207:2026](https://www.iso.org/standard/90219.html)).
- FinOps makes allocation, forecasting, budgets, anomaly response, and unit
  economics shared engineering concerns
  ([FinOps Framework](https://www.finops.org/framework/),
  [planning and estimating](https://www.finops.org/framework/capabilities/planning-estimating/),
  [unit economics](https://www.finops.org/framework/capabilities/unit-economics/)).
- AWS Serverless Lens connects operations, observability, safe change, and
  expenditure awareness to serverless workloads
  ([operational excellence](https://docs.aws.amazon.com/wellarchitected/latest/serverless-applications-lens/operational-excellence-pillar.html),
  [expenditure awareness](https://docs.aws.amazon.com/wellarchitected/latest/serverless-applications-lens/expenditure-and-usage-awareness.html)).
- Records/data disposition at retirement is strengthened by risk-based media
  sanitization guidance ([NIST SP 800-88 Rev. 1](https://www.nist.gov/publications/nist-special-publication-800-88-revision-1-guidelines-media-sanitization)).
- Blameless post-incident learning supports systemic correction rather than
  individual-performance attribution
  ([Google SRE Workbook](https://sre.google/workbook/postmortem-culture/)).

## Evaluation of the way of working

The corrected process is **sound and internally coherent for this analytical
case**:

- It starts with value, alternatives, feasibility, cost, risk, and authority;
  it does not assume serverless or AWS before G0.
- It maintains three sequential one-time phases while placing repeated
  increments and releases inside Phase 1.
- It lets Operations coexist with later development under a different, explicit
  pull control system.
- It retains an accepted live baseline when a candidate fails and routes
  correction to the earliest authoritative source.
- It connects Dev and Ops through evidence-bearing interfaces—G7 handover,
  CI/CD, telemetry, product-change routing, reconciliation, learning, and
  shared G8—without collapsing their work-control mechanisms.
- It closes deliberately across runtime, data, records, access, cost, support,
  and knowledge rather than ending at the last deployment.

The way of working is necessarily configurable. Applying all tasks with equal
weight to every increment would be burdensome; the method profile must select
depth according to criticality, novelty, regulation, uncertainty, and change
impact. A supporting-role binding means required participation or review, not
that every role attends every working session.

## Residual limitations and empirical questions

The analytical run does not show actual elapsed time, cognitive load, model
quality, transformation defect rate, cost-estimate accuracy, incident outcomes,
or practitioner acceptance. It also uses one AWS-oriented scenario, so it
cannot establish portability or fit for every team size and regulatory domain.
The next evidence step remains the multiple-case protocol. In particular, it
should measure gate-decision time, forecast variance, review queues, model and
release rework, operational cycle time/SLE attainment, expedite frequency,
escaped defects, trace coverage, and closure-obligation count, while recording
method-profile changes and negative cases.

Consequently, the defensible conclusion is: the corrected process is complete
enough to enact without an obvious unowned lifecycle gap and is logically
consistent with its scientific sources; practical effectiveness remains an
empirical research question.
