# Situational Tailoring, Governance, and Continuous Disciplines

## Tailoring procedure

Tailoring is performed before the first increment and revisited when the
product, risk, platform, team, compliance context, or release strategy changes.

1. Record situational factors and evidence.
2. Select the closest reference configuration.
3. Add conditional method packages triggered by risk or context.
4. Combine roles only after preserving accountability and competence.
5. Adjust work-product detail and reviewer independence proportionally.
6. Record every omission, replacement, and addition with its preserved control
   objective and replacement evidence.
7. Review the profile at increments, releases, major incidents, and major
   changes.

The profile is versioned as a project work product. It configures TaskUses and
Guidance; it does not fork reusable TaskDefinitions without a method-library
change.

## Reference configurations

### Profile A — Exploration

Use for high-uncertainty discovery before a production commitment.

- one small cross-functional team;
- short experiments and thin CIM/PIM slices;
- explicit suitability, architecture, cold-start, cost, and integration
  hypotheses;
- disposable PSM/artifact prototypes where useful;
- no production deployment without re-tailoring to another profile.

Required evidence is concise, but risks, assumptions, data handling, and
experiment results remain recorded.

### Profile B — Standard product delivery (default)

Use for ordinary business applications with one or a few teams.

- full lifecycle and vertical increment engine;
- peer model/code review;
- automated CI, contract/integration tests, progressive non-production and
  production promotion;
- explicit service ownership, SLOs, cost signals, and rollback;
- lightweight but complete decision, risk, and trace records.

### Profile C — Multi-team product/platform

Adds to Profile B:

- bounded model repositories or ownership partitions;
- architecture and contract integration owners;
- platform enablement team and reusable paved-road assets;
- dependency board, compatibility policy, release-train coordination, and
  consumer-driven contract evidence;
- communities of practice and method-library contribution workflow.

### Profile D — Regulated or high-criticality

Adds to B or C:

- independent assurance/security/privacy review;
- formal approval authority and segregation of duties;
- strengthened trace, change control, records retention, supplier evidence,
  threat/failure analysis, recovery rehearsal, and audit trail;
- higher model/test coverage and explicit acceptance of every deviation;
- qualified toolchain or compensating verification where required by the
  applicable regime.

MODRISS does not claim that this profile alone makes a system compliant or
safety-certified. Domain-specific standards and assurance cases must extend it.

## Conditional configuration packages

| Package                                   | Trigger                                                                  | Adds or strengthens                                                                                                                                    |
| ----------------------------------------- | ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| CP-01 Multi-team scale                    | more than one independently planning team                                | bounded ownership, contract governance, dependencies, integration ownership, release coordination                                                      |
| CP-02 Sensitive/regulated data            | confidential/restricted data or external obligation                      | privacy analysis, residency, encryption, access evidence, retention/disposition, independent review                                                    |
| CP-03 High availability/disaster recovery | stringent SLO or recovery objective                                      | failure analysis, multi-zone/region decisions, backup/restore, failover rehearsal, chaos/fault tests                                                   |
| CP-04 Portability/multi-cloud             | provider undecided, exit requirement, or multiple providers              | provider-neutral contracts, capability intersection, adapter boundaries, portability tests, separate PSM profiles                                      |
| CP-05 Legacy migration                    | coexistence, strangler, replacement, or data migration                   | baseline discovery, compatibility, dual-run, reconciliation, cutover, decommissioning                                                                  |
| CP-06 High release risk                   | irreversible data change, broad blast radius, or critical service        | independent authorization, rehearsal, canary/blue-green, extended observation, rollback/roll-forward                                                   |
| CP-07 External suppliers                  | SaaS, partner, or managed dependency                                     | supplier responsibility, SLA/security review, sandbox/contract tests, exit and incident coordination                                                   |
| CP-08 AI-assisted modeling                | assistant enabled                                                        | structural-only assistant gate, human review, provenance, budgets, privacy, prompt/model policy; EVL remains separate explicit validation              |
| CP-09 Sustained operational demand        | production service has intermittent/sustained interrupts or 24×7 support | operational Kanban flow, maintenance/service classification, capacity and preemption policy, WIP/SLE tuning, on-call hand-off, service-delivery review |

## Dual-flow governance

MODRISS uses two connected control systems. Planned product work is committed
at release and increment horizons, then refined adaptively. Production demand
is captured when it emerges and pulled under the Operations Flow Policy and
Board. The Product Owner orders planned value; the Service Owner owns service
impact and urgency; the Delivery Lead protects system flow and makes capacity
and preemption visible.

The Method Profile must define:

- the intake sources and `ready`/`done` criteria for operational work;
- WIP limits by active state and an explicit expedite policy (default active
  expedite WIP: one);
- standard, fixed-date, expedite, and risk-reduction service-class policies and
  service-level expectations;
- the initial operational-capacity allocation, who may change it, and the
  evidence/cadence for doing so;
- replenishment, daily flow review, service-delivery review, and release
  planning cadences;
- what may finish as operations-only work, what must use a bounded MDE/release
  path, and what enters the next planned release; and
- how temporary emergency changes are reconciled with the earliest
  authoritative model, generator, code, configuration, or runbook source.

These rules operationalize the Kanban Guide's workflow, WIP, pull, explicit
policy, SLE, and flow-measure requirements
([Kanban Guides, 2025](https://kanbanguides.org/the-kanban-guide/)). They do not
turn maintenance into a second sprint backlog or reserve a universal percentage
of capacity; those are situational decisions reviewed from observed demand.

## Release management

### Release structure

- An **increment** is the smallest accepted vertical slice.
- A **release candidate** is an immutable, compatible assembly of accepted
  increments and exact configuration.
- A **release** is a candidate authorized and promoted to an environment.
- A **deployment** is one promotion event; multiple deployments may implement
  one release strategy.

Version models, transformations, generators, contracts/schemas, code,
infrastructure, configuration, and runbooks together. A release record must be
able to reconstruct what was intended, generated, built, and deployed.

### Branching and integration

Prefer short-lived change branches or trunk-based integration with protected
mainline, automated checks, and review. Long-lived generated branches are
discouraged because they obscure authoritative sources. Model changes and code
changes for one increment share a traceable change identifier.

### CI/CD control flow

1. validate repository structure and build inputs;
2. run structural model checks and explicit model-validation workflows;
3. verify transformations and trace/readiness reports;
4. generate from the accepted PSM in a clean environment;
5. compare manifest/provenance and compile/package outputs;
6. run unit, contract, component, security, and static verification;
7. publish immutable candidate artifacts;
8. deploy to non-production and run integration/system/operational tests;
9. authorize promotion against candidate-specific evidence;
10. promote progressively with observation and rollback controls; and
11. record post-deployment validation and handover.

Automation may stop a release. Automation does not grant risk acceptance unless
the method profile explicitly defines a machine-evaluable low-risk policy and
its accountable owner.

## Continuous disciplines

### Product and delivery management

Maintain outcome roadmap, backlog, release hypotheses, forecasts, capacity,
dependencies, decisions, blockers, and stakeholder communication. Replan based
on evidence rather than preserving an obsolete baseline.

### Risk and opportunity management

Continuously identify technical, product, security, privacy, operational,
supplier, schedule, cost, and process risks. Tie treatment to increments,
experiments, architecture, tests, controls, monitors, or contingency. Review
triggers and residual risk at gates.

### Quality assurance

Define quality objectives, review strategy, model/test evidence, defect/finding
states, independence, and acceptance thresholds. Assurance examines process and
product evidence; it is not identical to running EVL or tests.

### Security, privacy, and compliance

Maintain threat and privacy analysis, least privilege, secret/key management,
encryption, dependency/supply-chain posture, auditability, exception expiry,
incident coordination, and evidence retention from discovery through
retirement.

### Configuration and change management

Baseline every accepted model and candidate; maintain immutable provenance;
control schema/API/event compatibility; route changes to authoritative sources;
and reconcile emergency downstream fixes.

### Traceability and evidence management

Trace outcome and requirement through CIM, PIM, PSM, artifact, test, release,
runtime measure, change, and retirement evidence. Trace completeness is risk
weighted but missing critical links cannot be averaged away.

### Measurement and improvement

Recommended measures include outcome progress, increment flow time, planned
release forecast, operational WIP, throughput, cycle time, work-item age, SLE
attainment, interrupt arrival rate, expedite/preemption frequency, capacity
allocation and starvation signals, rework,
escaped defects, trace closure, open blocker age, manual-decision closure,
dependency age, deployment frequency, change failure/recovery, SLO performance,
cost per business unit, and method-profile changes. Use trends and context; do
not rank individuals.

### Knowledge and reuse

After increments, releases, incidents, and retirement, review reusable model
fragments, patterns, transformations, templates, tests, runbooks, and method
content. Store only generalized, documented, quality-reviewed assets with
ownership and versioning.

## Gates and decision semantics

Each gate has four possible outcomes:

- **accept** — criteria satisfied for the named revision and scope;
- **accept with time-bounded exception** — authorized owner accepts residual
  risk with actions, expiry, and monitoring;
- **rework** — return to the earliest affected authoritative source; or
- **defer/stop** — remove from current scope or end the endeavor with recorded
  consequences.

A gate records decision authority, date, exact revisions, criteria, evidence,
findings/exceptions, and follow-up. A meeting or checkbox is not a gate without
this record.

## Method governance

The Method Engineer owns library coherence; content-area owners maintain
specialized fragments. Proposed changes identify requirement/criterion,
affected TaskDefinitions and configurations, compatibility, migration, and
evidence. New versions are reviewed in the large before publication. Project
profiles pin a version and choose when to adopt updates.
