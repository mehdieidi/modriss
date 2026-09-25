# Operations and Maintenance: work products

This page documents the **SPEM WorkProductDefinition, WorkProductUse, and ProcessParameter** elements used by the Operations and Maintenance process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A WorkProductDefinition describes maintained information or a tangible result. WorkProductUse binds that definition to an activity or task as an input or output. ProcessParameter makes the direction explicit. A work product can be stored in several physical files or systems if its identity, owner, revision, and evidence links remain clear.

## Summary

| Work product                                     | Kind     | Producing tasks | Consuming tasks | Uses |
| ------------------------------------------------ | -------- | --------------: | --------------: | ---: |
| Product and System Charter                       | Artifact |               0 |               3 |    3 |
| Cost Model and Budget Guardrails                 | Artifact |               2 |               2 |    4 |
| Risk and Opportunity Register                    | Artifact |               1 |               1 |    2 |
| Situational Method Profile                       | Artifact |               2 |               7 |    9 |
| Increment and Integrated Plan Record             | Artifact |               1 |               3 |    4 |
| Release, Recovery, Handover and Promotion Record | Artifact |               0 |               5 |    5 |
| Operational Evidence, Incident and Change Record | Artifact |               6 |               7 |   13 |
| Retrospective and Improvement Record             | Artifact |               2 |               2 |    4 |
| Service Work Item                                | Artifact |               6 |               6 |   12 |
| Kanban Service-Delivery Policy and Board         | Artifact |               3 |               7 |   10 |

## Detailed work products

## Product and System Charter

<small>Artifact: `e2e-artifact.product-charter` · 3 WorkProductUse occurrences</small>

Product goal, outcome hypothesis, scope boundaries, constraints, and success measures.

**Produced or updated by**

- None declared

**Consumed by**

- Review SLOs, telemetry, cost, and product outcomes
- Assess and route a maintenance change
- Inspect flow, quality, cost, and coordination metrics

**Work-product uses**

- `wpu.e2e.ops.a1.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.ops.a1.t1`
- `wpu.e2e.ops.a4.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.ops.a4.t1`
- `wpu.e2e.ops.a5.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.ops.a5.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Cost Model and Budget Guardrails

<small>Artifact: `e2e-artifact.cost-model` · 4 WorkProductUse occurrences</small>

Demand drivers, scenario ranges, allocation tags, unit economics, budgets, anomaly thresholds, confidence, and review cadence.

**Produced or updated by**

- Review SLOs, telemetry, cost, and product outcomes
- Inspect flow, quality, cost, and coordination metrics

**Consumed by**

- Review SLOs, telemetry, cost, and product outcomes
- Inspect flow, quality, cost, and coordination metrics

**Work-product uses**

- `wpu.e2e.ops.a1.t1.input.e2e-artifact.cost-model` as **input** in task `e2e.ops.a1.t1`
- `wpu.e2e.ops.a1.t1.output.e2e-artifact.cost-model` as **output** in task `e2e.ops.a1.t1`
- `wpu.e2e.ops.a5.t1.input.e2e-artifact.cost-model` as **input** in task `e2e.ops.a5.t1`
- `wpu.e2e.ops.a5.t1.output.e2e-artifact.cost-model` as **output** in task `e2e.ops.a5.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Risk and Opportunity Register

<small>Artifact: `e2e-artifact.risk-register` · 2 WorkProductUse occurrences</small>

Cause-event-effect statements, exposure, owner, treatment, trigger, review date, and evidence.

**Produced or updated by**

- Perform problem and risk learning

**Consumed by**

- Perform problem and risk learning

**Work-product uses**

- `wpu.e2e.ops.a3.t2.input.e2e-artifact.risk-register` as **input** in task `e2e.ops.a3.t2`
- `wpu.e2e.ops.a3.t2.output.e2e-artifact.risk-register` as **output** in task `e2e.ops.a3.t2`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Situational Method Profile

<small>Artifact: `e2e-artifact.method-profile` · 9 WorkProductUse occurrences</small>

Tailored lifecycle, roles, work products, gates, evidence rules, and metrics for the project context.

**Produced or updated by**

- Perform problem and risk learning
- Inspect flow, quality, cost, and coordination metrics

**Consumed by**

- Review SLOs, telemetry, cost, and product outcomes
- Triage and make service demand ready
- Replenish, pull, and manage Kanban flow
- Manage incidents and emergency recovery
- Perform problem and risk learning
- Assess and route a maintenance change
- Inspect flow, quality, cost, and coordination metrics

**Work-product uses**

- `wpu.e2e.ops.a1.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.ops.a1.t1`
- `wpu.e2e.ops.a2.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.ops.a2.t1`
- `wpu.e2e.ops.a2.t2.input.e2e-artifact.method-profile` as **input** in task `e2e.ops.a2.t2`
- `wpu.e2e.ops.a3.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.ops.a3.t1`
- `wpu.e2e.ops.a3.t2.input.e2e-artifact.method-profile` as **input** in task `e2e.ops.a3.t2`
- `wpu.e2e.ops.a3.t2.output.e2e-artifact.method-profile` as **output** in task `e2e.ops.a3.t2`
- `wpu.e2e.ops.a4.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.ops.a4.t1`
- `wpu.e2e.ops.a5.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.ops.a5.t1`
- `wpu.e2e.ops.a5.t1.output.e2e-artifact.method-profile` as **output** in task `e2e.ops.a5.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Increment and Integrated Plan Record

<small>Artifact: `e2e-artifact.increment-record` · 4 WorkProductUse occurrences</small>

Roadmap/release hypothesis, slice goal, scope, acceptance evidence, model revisions, transformation runs, forecast, and retrospective results.

**Produced or updated by**

- Assess and route a maintenance change

**Consumed by**

- Perform problem and risk learning
- Assess and route a maintenance change
- Inspect flow, quality, cost, and coordination metrics

**Work-product uses**

- `wpu.e2e.ops.a3.t2.input.e2e-artifact.increment-record` as **input** in task `e2e.ops.a3.t2`
- `wpu.e2e.ops.a4.t1.input.e2e-artifact.increment-record` as **input** in task `e2e.ops.a4.t1`
- `wpu.e2e.ops.a4.t1.output.e2e-artifact.increment-record` as **output** in task `e2e.ops.a4.t1`
- `wpu.e2e.ops.a5.t1.input.e2e-artifact.increment-record` as **input** in task `e2e.ops.a5.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Release, Recovery, Handover and Promotion Record

<small>Artifact: `e2e-artifact.release-record` · 5 WorkProductUse occurrences</small>

Immutable candidate, exact model/artifact revisions, recovery plan, readiness/handover pack, approvals, deployment evidence, rollback identity, and outcome.

**Produced or updated by**

- None declared

**Consumed by**

- Review SLOs, telemetry, cost, and product outcomes
- Manage incidents and emergency recovery
- Perform problem and risk learning
- Assess and route a maintenance change
- Inspect flow, quality, cost, and coordination metrics

**Work-product uses**

- `wpu.e2e.ops.a1.t1.input.e2e-artifact.release-record` as **input** in task `e2e.ops.a1.t1`
- `wpu.e2e.ops.a3.t1.input.e2e-artifact.release-record` as **input** in task `e2e.ops.a3.t1`
- `wpu.e2e.ops.a3.t2.input.e2e-artifact.release-record` as **input** in task `e2e.ops.a3.t2`
- `wpu.e2e.ops.a4.t1.input.e2e-artifact.release-record` as **input** in task `e2e.ops.a4.t1`
- `wpu.e2e.ops.a5.t1.input.e2e-artifact.release-record` as **input** in task `e2e.ops.a5.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Operational Evidence, Incident and Change Record

<small>Artifact: `e2e-artifact.operations-record` · 13 WorkProductUse occurrences</small>

SLOs, incidents/problems, changes, capacity, cost, security, provider events, product outcomes, and learning.

**Produced or updated by**

- Review SLOs, telemetry, cost, and product outcomes
- Replenish, pull, and manage Kanban flow
- Manage incidents and emergency recovery
- Perform problem and risk learning
- Assess and route a maintenance change
- Inspect flow, quality, cost, and coordination metrics

**Consumed by**

- Review SLOs, telemetry, cost, and product outcomes
- Triage and make service demand ready
- Replenish, pull, and manage Kanban flow
- Manage incidents and emergency recovery
- Perform problem and risk learning
- Assess and route a maintenance change
- Inspect flow, quality, cost, and coordination metrics

**Work-product uses**

- `wpu.e2e.ops.a1.t1.input.e2e-artifact.operations-record` as **input** in task `e2e.ops.a1.t1`
- `wpu.e2e.ops.a1.t1.output.e2e-artifact.operations-record` as **output** in task `e2e.ops.a1.t1`
- `wpu.e2e.ops.a2.t1.input.e2e-artifact.operations-record` as **input** in task `e2e.ops.a2.t1`
- `wpu.e2e.ops.a2.t2.input.e2e-artifact.operations-record` as **input** in task `e2e.ops.a2.t2`
- `wpu.e2e.ops.a2.t2.output.e2e-artifact.operations-record` as **output** in task `e2e.ops.a2.t2`
- `wpu.e2e.ops.a3.t1.input.e2e-artifact.operations-record` as **input** in task `e2e.ops.a3.t1`
- `wpu.e2e.ops.a3.t1.output.e2e-artifact.operations-record` as **output** in task `e2e.ops.a3.t1`
- `wpu.e2e.ops.a3.t2.input.e2e-artifact.operations-record` as **input** in task `e2e.ops.a3.t2`
- `wpu.e2e.ops.a3.t2.output.e2e-artifact.operations-record` as **output** in task `e2e.ops.a3.t2`
- `wpu.e2e.ops.a4.t1.input.e2e-artifact.operations-record` as **input** in task `e2e.ops.a4.t1`
- `wpu.e2e.ops.a4.t1.output.e2e-artifact.operations-record` as **output** in task `e2e.ops.a4.t1`
- `wpu.e2e.ops.a5.t1.input.e2e-artifact.operations-record` as **input** in task `e2e.ops.a5.t1`
- `wpu.e2e.ops.a5.t1.output.e2e-artifact.operations-record` as **output** in task `e2e.ops.a5.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Retrospective and Improvement Record

<small>Artifact: `e2e-artifact.improvement-record` · 4 WorkProductUse occurrences</small>

Observed outcomes, process/model friction, measures, decisions, owners, experiments, reusable candidates, and no-op rationale.

**Produced or updated by**

- Perform problem and risk learning
- Inspect flow, quality, cost, and coordination metrics

**Consumed by**

- Perform problem and risk learning
- Inspect flow, quality, cost, and coordination metrics

**Work-product uses**

- `wpu.e2e.ops.a3.t2.input.e2e-artifact.improvement-record` as **input** in task `e2e.ops.a3.t2`
- `wpu.e2e.ops.a3.t2.output.e2e-artifact.improvement-record` as **output** in task `e2e.ops.a3.t2`
- `wpu.e2e.ops.a5.t1.input.e2e-artifact.improvement-record` as **input** in task `e2e.ops.a5.t1`
- `wpu.e2e.ops.a5.t1.output.e2e-artifact.improvement-record` as **output** in task `e2e.ops.a5.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Service Work Item

<small>Artifact: `e2e-artifact.operational-work-item` · 12 WorkProductUse occurrences</small>

A production, maintenance, service, or improvement demand item with source, maintenance purpose where applicable, emergency-temporary status, class of service, severity, owner, service-level expectation, state, age, evidence, and disposition.

**Produced or updated by**

- Review SLOs, telemetry, cost, and product outcomes
- Triage and make service demand ready
- Replenish, pull, and manage Kanban flow
- Manage incidents and emergency recovery
- Perform problem and risk learning
- Assess and route a maintenance change

**Consumed by**

- Triage and make service demand ready
- Replenish, pull, and manage Kanban flow
- Manage incidents and emergency recovery
- Perform problem and risk learning
- Assess and route a maintenance change
- Inspect flow, quality, cost, and coordination metrics

**Work-product uses**

- `wpu.e2e.ops.a1.t1.output.e2e-artifact.operational-work-item` as **output** in task `e2e.ops.a1.t1`
- `wpu.e2e.ops.a2.t1.input.e2e-artifact.operational-work-item` as **input** in task `e2e.ops.a2.t1`
- `wpu.e2e.ops.a2.t1.output.e2e-artifact.operational-work-item` as **output** in task `e2e.ops.a2.t1`
- `wpu.e2e.ops.a2.t2.input.e2e-artifact.operational-work-item` as **input** in task `e2e.ops.a2.t2`
- `wpu.e2e.ops.a2.t2.output.e2e-artifact.operational-work-item` as **output** in task `e2e.ops.a2.t2`
- `wpu.e2e.ops.a3.t1.input.e2e-artifact.operational-work-item` as **input** in task `e2e.ops.a3.t1`
- `wpu.e2e.ops.a3.t1.output.e2e-artifact.operational-work-item` as **output** in task `e2e.ops.a3.t1`
- `wpu.e2e.ops.a3.t2.input.e2e-artifact.operational-work-item` as **input** in task `e2e.ops.a3.t2`
- `wpu.e2e.ops.a3.t2.output.e2e-artifact.operational-work-item` as **output** in task `e2e.ops.a3.t2`
- `wpu.e2e.ops.a4.t1.input.e2e-artifact.operational-work-item` as **input** in task `e2e.ops.a4.t1`
- `wpu.e2e.ops.a4.t1.output.e2e-artifact.operational-work-item` as **output** in task `e2e.ops.a4.t1`
- `wpu.e2e.ops.a5.t1.input.e2e-artifact.operational-work-item` as **input** in task `e2e.ops.a5.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Kanban Service-Delivery Policy and Board

<small>Artifact: `e2e-artifact.service-flow-system` · 10 WorkProductUse occurrences</small>

The explicit Definition of Workflow and visible Kanban board: requested/ready/started/finished points, workflow states, WIP controls, classes of service, service-level expectations, replenishment and review cadences, capacity policy, and flow metrics.

**Produced or updated by**

- Triage and make service demand ready
- Replenish, pull, and manage Kanban flow
- Inspect flow, quality, cost, and coordination metrics

**Consumed by**

- Review SLOs, telemetry, cost, and product outcomes
- Triage and make service demand ready
- Replenish, pull, and manage Kanban flow
- Manage incidents and emergency recovery
- Perform problem and risk learning
- Assess and route a maintenance change
- Inspect flow, quality, cost, and coordination metrics

**Work-product uses**

- `wpu.e2e.ops.a1.t1.input.e2e-artifact.service-flow-system` as **input** in task `e2e.ops.a1.t1`
- `wpu.e2e.ops.a2.t1.input.e2e-artifact.service-flow-system` as **input** in task `e2e.ops.a2.t1`
- `wpu.e2e.ops.a2.t1.output.e2e-artifact.service-flow-system` as **output** in task `e2e.ops.a2.t1`
- `wpu.e2e.ops.a2.t2.input.e2e-artifact.service-flow-system` as **input** in task `e2e.ops.a2.t2`
- `wpu.e2e.ops.a2.t2.output.e2e-artifact.service-flow-system` as **output** in task `e2e.ops.a2.t2`
- `wpu.e2e.ops.a3.t1.input.e2e-artifact.service-flow-system` as **input** in task `e2e.ops.a3.t1`
- `wpu.e2e.ops.a3.t2.input.e2e-artifact.service-flow-system` as **input** in task `e2e.ops.a3.t2`
- `wpu.e2e.ops.a4.t1.input.e2e-artifact.service-flow-system` as **input** in task `e2e.ops.a4.t1`
- `wpu.e2e.ops.a5.t1.input.e2e-artifact.service-flow-system` as **input** in task `e2e.ops.a5.t1`
- `wpu.e2e.ops.a5.t1.output.e2e-artifact.service-flow-system` as **output** in task `e2e.ops.a5.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

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
