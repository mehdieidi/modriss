# Development and Delivery: work products

This page documents the **SPEM WorkProductDefinition, WorkProductUse, and ProcessParameter** elements used by the Development and Delivery process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A WorkProductDefinition describes maintained information or a tangible result. WorkProductUse binds that definition to an activity or task as an input or output. ProcessParameter makes the direction explicit. A work product can be stored in several physical files or systems if its identity, owner, revision, and evidence links remain clear.

## Summary

| Work product                                     | Kind     | Producing tasks | Consuming tasks | Uses |
| ------------------------------------------------ | -------- | --------------: | --------------: | ---: |
| Product and System Charter                       | Artifact |               5 |              24 |   29 |
| Feasibility and Serverless Suitability Record    | Artifact |               2 |               2 |    4 |
| Cost Model and Budget Guardrails                 | Artifact |               7 |              10 |   17 |
| Risk and Opportunity Register                    | Artifact |              10 |               8 |   18 |
| Situational Method Profile                       | Artifact |               7 |              23 |   30 |
| Team Topology and Dependency Map                 | Artifact |               2 |              10 |   12 |
| Increment and Integrated Plan Record             | Artifact |              11 |              14 |   25 |
| Release, Recovery, Handover and Promotion Record | Artifact |               5 |               8 |   13 |
| Operational Evidence, Incident and Change Record | Artifact |               1 |               6 |    7 |
| Retrospective and Improvement Record             | Artifact |               3 |               3 |    6 |
| Service Work Item                                | Artifact |               0 |               0 |    0 |
| Kanban Service-Delivery Policy and Board         | Artifact |               1 |               0 |    1 |
| Retirement, Data-Disposition and Closure Record  | Artifact |               5 |               4 |    9 |

## Detailed work products

## Product and System Charter

<small>Artifact: `e2e-artifact.product-charter` · 29 WorkProductUse occurrences</small>

Product goal, outcome hypothesis, scope boundaries, constraints, and success measures.

**Produced or updated by**

- Define product outcomes and success measures
- Establish the initial release and increment hypothesis
- Authorize pursue, explore, redirect, or stop at G0
- Define operational and release strategy
- Review release outcome and update roadmap

**Consumed by**

- Establish the initial release and increment hypothesis
- Assess feasibility and serverless suitability
- Establish cost model and budget guardrails
- Authorize pursue, explore, redirect, or stop at G0
- Assess context and process-tailoring risks
- Define the tailored Definition of Ready and Done
- Define team ownership and interfaces
- Set coordination cadence and escalation paths
- Define quality and security control objectives
- Define operational and release strategy
- Plan the vertical increment
- Run the CIM increment
- Execute and inspect CIM-to-PIM transformation
- Run the PIM increment
- Execute and inspect PIM-to-PSM transformation
- Run the PSM increment
- Run artifact readiness and accept the increment
- Assemble the release candidate
- Review release evidence and go/no-go criteria
- Deploy and validate progressively
- Complete handover and rollback rehearsal
- Review release outcome and update roadmap
- Approve retirement scope and plan
- Complete closure review

**Work-product uses**

- `wpu.e2e.ph0.st1.t1.output.e2e-artifact.product-charter` as **output** in task `e2e.ph0.st1.t1`
- `wpu.e2e.ph0.st1.t2.input.e2e-artifact.product-charter` as **input** in task `e2e.ph0.st1.t2`
- `wpu.e2e.ph0.st1.t2.output.e2e-artifact.product-charter` as **output** in task `e2e.ph0.st1.t2`
- `wpu.e2e.ph0.st1a.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.ph0.st1a.t1`
- `wpu.e2e.ph0.st1a.t2.input.e2e-artifact.product-charter` as **input** in task `e2e.ph0.st1a.t2`
- `wpu.e2e.ph0.st1a.t3.input.e2e-artifact.product-charter` as **input** in task `e2e.ph0.st1a.t3`
- `wpu.e2e.ph0.st1a.t3.output.e2e-artifact.product-charter` as **output** in task `e2e.ph0.st1a.t3`
- `wpu.e2e.ph0.st2.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.ph0.st2.t1`
- `wpu.e2e.ph0.st2.t2.input.e2e-artifact.product-charter` as **input** in task `e2e.ph0.st2.t2`
- `wpu.e2e.ph0.st3.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.ph0.st3.t1`
- `wpu.e2e.ph0.st3.t2.input.e2e-artifact.product-charter` as **input** in task `e2e.ph0.st3.t2`
- `wpu.e2e.ph0.st4.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.ph0.st4.t1`
- `wpu.e2e.ph0.st4.t2.input.e2e-artifact.product-charter` as **input** in task `e2e.ph0.st4.t2`
- `wpu.e2e.ph0.st4.t2.output.e2e-artifact.product-charter` as **output** in task `e2e.ph0.st4.t2`
- `wpu.e2e.p0.increment-planning.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.p0.increment-planning.t1`
- `wpu.e2e.p1.cim-modeling.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.p1.cim-modeling.t1`
- `wpu.e2e.p2.cim-to-pim.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.p2.cim-to-pim.t1`
- `wpu.e2e.p3.pim-refinement.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.p3.pim-refinement.t1`
- `wpu.e2e.p4.pim-to-psm.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.p4.pim-to-psm.t1`
- `wpu.e2e.p5.psm-refinement.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.p5.psm-refinement.t1`
- `wpu.e2e.p7.artifact-completion.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.p7.artifact-completion.t1`
- `wpu.e2e.rel.a1.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.rel.a1.t1`
- `wpu.e2e.rel.a1.t2.input.e2e-artifact.product-charter` as **input** in task `e2e.rel.a1.t2`
- `wpu.e2e.rel.a2.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.rel.a2.t1`
- `wpu.e2e.rel.a2.t2.input.e2e-artifact.product-charter` as **input** in task `e2e.rel.a2.t2`
- `wpu.e2e.rel.a3.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.rel.a3.t1`
- `wpu.e2e.rel.a3.t1.output.e2e-artifact.product-charter` as **output** in task `e2e.rel.a3.t1`
- `wpu.e2e.ph2.st1.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.ph2.st1.t1`
- `wpu.e2e.ph2.st3.t1.input.e2e-artifact.product-charter` as **input** in task `e2e.ph2.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Feasibility and Serverless Suitability Record

<small>Artifact: `e2e-artifact.serverless-suitability` · 4 WorkProductUse occurrences</small>

Compared alternatives, workload fit, assumptions, experiments, provider decision, and recommendation.

**Produced or updated by**

- Assess feasibility and serverless suitability
- Authorize pursue, explore, redirect, or stop at G0

**Consumed by**

- Establish cost model and budget guardrails
- Authorize pursue, explore, redirect, or stop at G0

**Work-product uses**

- `wpu.e2e.ph0.st1a.t1.output.e2e-artifact.serverless-suitability` as **output** in task `e2e.ph0.st1a.t1`
- `wpu.e2e.ph0.st1a.t2.input.e2e-artifact.serverless-suitability` as **input** in task `e2e.ph0.st1a.t2`
- `wpu.e2e.ph0.st1a.t3.input.e2e-artifact.serverless-suitability` as **input** in task `e2e.ph0.st1a.t3`
- `wpu.e2e.ph0.st1a.t3.output.e2e-artifact.serverless-suitability` as **output** in task `e2e.ph0.st1a.t3`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Cost Model and Budget Guardrails

<small>Artifact: `e2e-artifact.cost-model` · 17 WorkProductUse occurrences</small>

Demand drivers, scenario ranges, allocation tags, unit economics, budgets, anomaly thresholds, confidence, and review cadence.

**Produced or updated by**

- Establish cost model and budget guardrails
- Authorize pursue, explore, redirect, or stop at G0
- Run the PSM increment
- Review release evidence and go/no-go criteria
- Review release outcome and update roadmap
- Decommission service, access, and cost surfaces
- Complete closure review

**Consumed by**

- Authorize pursue, explore, redirect, or stop at G0
- Define operational and release strategy
- Plan the vertical increment
- Run artifact readiness and accept the increment
- Review the increment and adapt the way of working
- Review release evidence and go/no-go criteria
- Review release outcome and update roadmap
- Approve retirement scope and plan
- Decommission service, access, and cost surfaces
- Complete closure review

**Work-product uses**

- `wpu.e2e.ph0.st1a.t2.output.e2e-artifact.cost-model` as **output** in task `e2e.ph0.st1a.t2`
- `wpu.e2e.ph0.st1a.t3.input.e2e-artifact.cost-model` as **input** in task `e2e.ph0.st1a.t3`
- `wpu.e2e.ph0.st1a.t3.output.e2e-artifact.cost-model` as **output** in task `e2e.ph0.st1a.t3`
- `wpu.e2e.ph0.st4.t2.input.e2e-artifact.cost-model` as **input** in task `e2e.ph0.st4.t2`
- `wpu.e2e.p0.increment-planning.t1.input.e2e-artifact.cost-model` as **input** in task `e2e.p0.increment-planning.t1`
- `wpu.e2e.p5.psm-refinement.t1.output.e2e-artifact.cost-model` as **output** in task `e2e.p5.psm-refinement.t1`
- `wpu.e2e.p7.artifact-completion.t1.input.e2e-artifact.cost-model` as **input** in task `e2e.p7.artifact-completion.t1`
- `wpu.e2e.p7.artifact-completion.t2.input.e2e-artifact.cost-model` as **input** in task `e2e.p7.artifact-completion.t2`
- `wpu.e2e.rel.a1.t2.input.e2e-artifact.cost-model` as **input** in task `e2e.rel.a1.t2`
- `wpu.e2e.rel.a1.t2.output.e2e-artifact.cost-model` as **output** in task `e2e.rel.a1.t2`
- `wpu.e2e.rel.a3.t1.input.e2e-artifact.cost-model` as **input** in task `e2e.rel.a3.t1`
- `wpu.e2e.rel.a3.t1.output.e2e-artifact.cost-model` as **output** in task `e2e.rel.a3.t1`
- `wpu.e2e.ph2.st1.t1.input.e2e-artifact.cost-model` as **input** in task `e2e.ph2.st1.t1`
- `wpu.e2e.ph2.st2.t2.input.e2e-artifact.cost-model` as **input** in task `e2e.ph2.st2.t2`
- `wpu.e2e.ph2.st2.t2.output.e2e-artifact.cost-model` as **output** in task `e2e.ph2.st2.t2`
- `wpu.e2e.ph2.st3.t1.input.e2e-artifact.cost-model` as **input** in task `e2e.ph2.st3.t1`
- `wpu.e2e.ph2.st3.t1.output.e2e-artifact.cost-model` as **output** in task `e2e.ph2.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Risk and Opportunity Register

<small>Artifact: `e2e-artifact.risk-register` · 18 WorkProductUse occurrences</small>

Cause-event-effect statements, exposure, owner, treatment, trigger, review date, and evidence.

**Produced or updated by**

- Assess feasibility and serverless suitability
- Establish cost model and budget guardrails
- Authorize pursue, explore, redirect, or stop at G0
- Assess context and process-tailoring risks
- Define quality and security control objectives
- Plan the vertical increment
- Run the PIM increment
- Run the PSM increment
- Review release evidence and go/no-go criteria
- Approve retirement scope and plan

**Consumed by**

- Authorize pursue, explore, redirect, or stop at G0
- Define quality and security control objectives
- Define operational and release strategy
- Plan the vertical increment
- Run artifact readiness and accept the increment
- Review the increment and adapt the way of working
- Review release evidence and go/no-go criteria
- Approve retirement scope and plan

**Work-product uses**

- `wpu.e2e.ph0.st1a.t1.output.e2e-artifact.risk-register` as **output** in task `e2e.ph0.st1a.t1`
- `wpu.e2e.ph0.st1a.t2.output.e2e-artifact.risk-register` as **output** in task `e2e.ph0.st1a.t2`
- `wpu.e2e.ph0.st1a.t3.input.e2e-artifact.risk-register` as **input** in task `e2e.ph0.st1a.t3`
- `wpu.e2e.ph0.st1a.t3.output.e2e-artifact.risk-register` as **output** in task `e2e.ph0.st1a.t3`
- `wpu.e2e.ph0.st2.t1.output.e2e-artifact.risk-register` as **output** in task `e2e.ph0.st2.t1`
- `wpu.e2e.ph0.st4.t1.input.e2e-artifact.risk-register` as **input** in task `e2e.ph0.st4.t1`
- `wpu.e2e.ph0.st4.t1.output.e2e-artifact.risk-register` as **output** in task `e2e.ph0.st4.t1`
- `wpu.e2e.ph0.st4.t2.input.e2e-artifact.risk-register` as **input** in task `e2e.ph0.st4.t2`
- `wpu.e2e.p0.increment-planning.t1.input.e2e-artifact.risk-register` as **input** in task `e2e.p0.increment-planning.t1`
- `wpu.e2e.p0.increment-planning.t1.output.e2e-artifact.risk-register` as **output** in task `e2e.p0.increment-planning.t1`
- `wpu.e2e.p3.pim-refinement.t1.output.e2e-artifact.risk-register` as **output** in task `e2e.p3.pim-refinement.t1`
- `wpu.e2e.p5.psm-refinement.t1.output.e2e-artifact.risk-register` as **output** in task `e2e.p5.psm-refinement.t1`
- `wpu.e2e.p7.artifact-completion.t1.input.e2e-artifact.risk-register` as **input** in task `e2e.p7.artifact-completion.t1`
- `wpu.e2e.p7.artifact-completion.t2.input.e2e-artifact.risk-register` as **input** in task `e2e.p7.artifact-completion.t2`
- `wpu.e2e.rel.a1.t2.input.e2e-artifact.risk-register` as **input** in task `e2e.rel.a1.t2`
- `wpu.e2e.rel.a1.t2.output.e2e-artifact.risk-register` as **output** in task `e2e.rel.a1.t2`
- `wpu.e2e.ph2.st1.t1.input.e2e-artifact.risk-register` as **input** in task `e2e.ph2.st1.t1`
- `wpu.e2e.ph2.st1.t1.output.e2e-artifact.risk-register` as **output** in task `e2e.ph2.st1.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Situational Method Profile

<small>Artifact: `e2e-artifact.method-profile` · 30 WorkProductUse occurrences</small>

Tailored lifecycle, roles, work products, gates, evidence rules, and metrics for the project context.

**Produced or updated by**

- Assess context and process-tailoring risks
- Define the tailored Definition of Ready and Done
- Set coordination cadence and escalation paths
- Define quality and security control objectives
- Define operational and release strategy
- Review the increment and adapt the way of working
- Complete closure review

**Consumed by**

- Define the tailored Definition of Ready and Done
- Define team ownership and interfaces
- Set coordination cadence and escalation paths
- Define quality and security control objectives
- Define operational and release strategy
- Plan the vertical increment
- Run the CIM increment
- Execute and inspect CIM-to-PIM transformation
- Run the PIM increment
- Execute and inspect PIM-to-PSM transformation
- Run the PSM increment
- Generate and fingerprint the artifact baseline
- Run artifact readiness and accept the increment
- Review the increment and adapt the way of working
- Assemble the release candidate
- Review release evidence and go/no-go criteria
- Deploy and validate progressively
- Complete handover and rollback rehearsal
- Review release outcome and update roadmap
- Approve retirement scope and plan
- Execute migration and data disposition
- Verify records retention and data disposition
- Complete closure review

**Work-product uses**

- `wpu.e2e.ph0.st2.t1.output.e2e-artifact.method-profile` as **output** in task `e2e.ph0.st2.t1`
- `wpu.e2e.ph0.st2.t2.input.e2e-artifact.method-profile` as **input** in task `e2e.ph0.st2.t2`
- `wpu.e2e.ph0.st2.t2.output.e2e-artifact.method-profile` as **output** in task `e2e.ph0.st2.t2`
- `wpu.e2e.ph0.st3.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.ph0.st3.t1`
- `wpu.e2e.ph0.st3.t2.input.e2e-artifact.method-profile` as **input** in task `e2e.ph0.st3.t2`
- `wpu.e2e.ph0.st3.t2.output.e2e-artifact.method-profile` as **output** in task `e2e.ph0.st3.t2`
- `wpu.e2e.ph0.st4.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.ph0.st4.t1`
- `wpu.e2e.ph0.st4.t1.output.e2e-artifact.method-profile` as **output** in task `e2e.ph0.st4.t1`
- `wpu.e2e.ph0.st4.t2.input.e2e-artifact.method-profile` as **input** in task `e2e.ph0.st4.t2`
- `wpu.e2e.ph0.st4.t2.output.e2e-artifact.method-profile` as **output** in task `e2e.ph0.st4.t2`
- `wpu.e2e.p0.increment-planning.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.p0.increment-planning.t1`
- `wpu.e2e.p1.cim-modeling.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.p1.cim-modeling.t1`
- `wpu.e2e.p2.cim-to-pim.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.p2.cim-to-pim.t1`
- `wpu.e2e.p3.pim-refinement.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.p3.pim-refinement.t1`
- `wpu.e2e.p4.pim-to-psm.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.p4.pim-to-psm.t1`
- `wpu.e2e.p5.psm-refinement.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.p5.psm-refinement.t1`
- `wpu.e2e.p6.m2t-generation.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.p6.m2t-generation.t1`
- `wpu.e2e.p7.artifact-completion.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.p7.artifact-completion.t1`
- `wpu.e2e.p7.artifact-completion.t2.input.e2e-artifact.method-profile` as **input** in task `e2e.p7.artifact-completion.t2`
- `wpu.e2e.p7.artifact-completion.t2.output.e2e-artifact.method-profile` as **output** in task `e2e.p7.artifact-completion.t2`
- `wpu.e2e.rel.a1.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.rel.a1.t1`
- `wpu.e2e.rel.a1.t2.input.e2e-artifact.method-profile` as **input** in task `e2e.rel.a1.t2`
- `wpu.e2e.rel.a2.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.rel.a2.t1`
- `wpu.e2e.rel.a2.t2.input.e2e-artifact.method-profile` as **input** in task `e2e.rel.a2.t2`
- `wpu.e2e.rel.a3.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.rel.a3.t1`
- `wpu.e2e.ph2.st1.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.ph2.st1.t1`
- `wpu.e2e.ph2.st2.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.ph2.st2.t1`
- `wpu.e2e.ph2.st2.t3.input.e2e-artifact.method-profile` as **input** in task `e2e.ph2.st2.t3`
- `wpu.e2e.ph2.st3.t1.input.e2e-artifact.method-profile` as **input** in task `e2e.ph2.st3.t1`
- `wpu.e2e.ph2.st3.t1.output.e2e-artifact.method-profile` as **output** in task `e2e.ph2.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Team Topology and Dependency Map

<small>Artifact: `e2e-artifact.team-topology` · 12 WorkProductUse occurrences</small>

Team ownership, interfaces, dependency board, decision rights, and coordination cadence.

**Produced or updated by**

- Define team ownership and interfaces
- Set coordination cadence and escalation paths

**Consumed by**

- Set coordination cadence and escalation paths
- Define quality and security control objectives
- Define operational and release strategy
- Plan the vertical increment
- Run the CIM increment
- Generate and fingerprint the artifact baseline
- Assemble the release candidate
- Deploy and validate progressively
- Complete handover and rollback rehearsal
- Decommission service, access, and cost surfaces

**Work-product uses**

- `wpu.e2e.ph0.st3.t1.output.e2e-artifact.team-topology` as **output** in task `e2e.ph0.st3.t1`
- `wpu.e2e.ph0.st3.t2.input.e2e-artifact.team-topology` as **input** in task `e2e.ph0.st3.t2`
- `wpu.e2e.ph0.st3.t2.output.e2e-artifact.team-topology` as **output** in task `e2e.ph0.st3.t2`
- `wpu.e2e.ph0.st4.t1.input.e2e-artifact.team-topology` as **input** in task `e2e.ph0.st4.t1`
- `wpu.e2e.ph0.st4.t2.input.e2e-artifact.team-topology` as **input** in task `e2e.ph0.st4.t2`
- `wpu.e2e.p0.increment-planning.t1.input.e2e-artifact.team-topology` as **input** in task `e2e.p0.increment-planning.t1`
- `wpu.e2e.p1.cim-modeling.t1.input.e2e-artifact.team-topology` as **input** in task `e2e.p1.cim-modeling.t1`
- `wpu.e2e.p6.m2t-generation.t1.input.e2e-artifact.team-topology` as **input** in task `e2e.p6.m2t-generation.t1`
- `wpu.e2e.rel.a1.t1.input.e2e-artifact.team-topology` as **input** in task `e2e.rel.a1.t1`
- `wpu.e2e.rel.a2.t1.input.e2e-artifact.team-topology` as **input** in task `e2e.rel.a2.t1`
- `wpu.e2e.rel.a2.t2.input.e2e-artifact.team-topology` as **input** in task `e2e.rel.a2.t2`
- `wpu.e2e.ph2.st2.t2.input.e2e-artifact.team-topology` as **input** in task `e2e.ph2.st2.t2`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Increment and Integrated Plan Record

<small>Artifact: `e2e-artifact.increment-record` · 25 WorkProductUse occurrences</small>

Roadmap/release hypothesis, slice goal, scope, acceptance evidence, model revisions, transformation runs, forecast, and retrospective results.

**Produced or updated by**

- Establish the initial release and increment hypothesis
- Plan the vertical increment
- Run the CIM increment
- Execute and inspect CIM-to-PIM transformation
- Run the PIM increment
- Execute and inspect PIM-to-PSM transformation
- Run the PSM increment
- Generate and fingerprint the artifact baseline
- Run artifact readiness and accept the increment
- Review the increment and adapt the way of working
- Review release outcome and update roadmap

**Consumed by**

- Assess context and process-tailoring risks
- Define the tailored Definition of Ready and Done
- Run the CIM increment
- Execute and inspect CIM-to-PIM transformation
- Run the PIM increment
- Execute and inspect PIM-to-PSM transformation
- Run the PSM increment
- Generate and fingerprint the artifact baseline
- Run artifact readiness and accept the increment
- Review the increment and adapt the way of working
- Assemble the release candidate
- Review release evidence and go/no-go criteria
- Review release outcome and update roadmap
- Complete closure review

**Work-product uses**

- `wpu.e2e.ph0.st1.t2.output.e2e-artifact.increment-record` as **output** in task `e2e.ph0.st1.t2`
- `wpu.e2e.ph0.st2.t1.input.e2e-artifact.increment-record` as **input** in task `e2e.ph0.st2.t1`
- `wpu.e2e.ph0.st2.t2.input.e2e-artifact.increment-record` as **input** in task `e2e.ph0.st2.t2`
- `wpu.e2e.p0.increment-planning.t1.output.e2e-artifact.increment-record` as **output** in task `e2e.p0.increment-planning.t1`
- `wpu.e2e.p1.cim-modeling.t1.input.e2e-artifact.increment-record` as **input** in task `e2e.p1.cim-modeling.t1`
- `wpu.e2e.p1.cim-modeling.t1.output.e2e-artifact.increment-record` as **output** in task `e2e.p1.cim-modeling.t1`
- `wpu.e2e.p2.cim-to-pim.t1.input.e2e-artifact.increment-record` as **input** in task `e2e.p2.cim-to-pim.t1`
- `wpu.e2e.p2.cim-to-pim.t1.output.e2e-artifact.increment-record` as **output** in task `e2e.p2.cim-to-pim.t1`
- `wpu.e2e.p3.pim-refinement.t1.input.e2e-artifact.increment-record` as **input** in task `e2e.p3.pim-refinement.t1`
- `wpu.e2e.p3.pim-refinement.t1.output.e2e-artifact.increment-record` as **output** in task `e2e.p3.pim-refinement.t1`
- `wpu.e2e.p4.pim-to-psm.t1.input.e2e-artifact.increment-record` as **input** in task `e2e.p4.pim-to-psm.t1`
- `wpu.e2e.p4.pim-to-psm.t1.output.e2e-artifact.increment-record` as **output** in task `e2e.p4.pim-to-psm.t1`
- `wpu.e2e.p5.psm-refinement.t1.input.e2e-artifact.increment-record` as **input** in task `e2e.p5.psm-refinement.t1`
- `wpu.e2e.p5.psm-refinement.t1.output.e2e-artifact.increment-record` as **output** in task `e2e.p5.psm-refinement.t1`
- `wpu.e2e.p6.m2t-generation.t1.input.e2e-artifact.increment-record` as **input** in task `e2e.p6.m2t-generation.t1`
- `wpu.e2e.p6.m2t-generation.t1.output.e2e-artifact.increment-record` as **output** in task `e2e.p6.m2t-generation.t1`
- `wpu.e2e.p7.artifact-completion.t1.input.e2e-artifact.increment-record` as **input** in task `e2e.p7.artifact-completion.t1`
- `wpu.e2e.p7.artifact-completion.t1.output.e2e-artifact.increment-record` as **output** in task `e2e.p7.artifact-completion.t1`
- `wpu.e2e.p7.artifact-completion.t2.input.e2e-artifact.increment-record` as **input** in task `e2e.p7.artifact-completion.t2`
- `wpu.e2e.p7.artifact-completion.t2.output.e2e-artifact.increment-record` as **output** in task `e2e.p7.artifact-completion.t2`
- `wpu.e2e.rel.a1.t1.input.e2e-artifact.increment-record` as **input** in task `e2e.rel.a1.t1`
- `wpu.e2e.rel.a1.t2.input.e2e-artifact.increment-record` as **input** in task `e2e.rel.a1.t2`
- `wpu.e2e.rel.a3.t1.input.e2e-artifact.increment-record` as **input** in task `e2e.rel.a3.t1`
- `wpu.e2e.rel.a3.t1.output.e2e-artifact.increment-record` as **output** in task `e2e.rel.a3.t1`
- `wpu.e2e.ph2.st3.t1.input.e2e-artifact.increment-record` as **input** in task `e2e.ph2.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Release, Recovery, Handover and Promotion Record

<small>Artifact: `e2e-artifact.release-record` · 13 WorkProductUse occurrences</small>

Immutable candidate, exact model/artifact revisions, recovery plan, readiness/handover pack, approvals, deployment evidence, rollback identity, and outcome.

**Produced or updated by**

- Assemble the release candidate
- Review release evidence and go/no-go criteria
- Deploy and validate progressively
- Complete handover and rollback rehearsal
- Review release outcome and update roadmap

**Consumed by**

- Review release evidence and go/no-go criteria
- Deploy and validate progressively
- Complete handover and rollback rehearsal
- Review release outcome and update roadmap
- Approve retirement scope and plan
- Execute migration and data disposition
- Decommission service, access, and cost surfaces
- Complete closure review

**Work-product uses**

- `wpu.e2e.rel.a1.t1.output.e2e-artifact.release-record` as **output** in task `e2e.rel.a1.t1`
- `wpu.e2e.rel.a1.t2.input.e2e-artifact.release-record` as **input** in task `e2e.rel.a1.t2`
- `wpu.e2e.rel.a1.t2.output.e2e-artifact.release-record` as **output** in task `e2e.rel.a1.t2`
- `wpu.e2e.rel.a2.t1.input.e2e-artifact.release-record` as **input** in task `e2e.rel.a2.t1`
- `wpu.e2e.rel.a2.t1.output.e2e-artifact.release-record` as **output** in task `e2e.rel.a2.t1`
- `wpu.e2e.rel.a2.t2.input.e2e-artifact.release-record` as **input** in task `e2e.rel.a2.t2`
- `wpu.e2e.rel.a2.t2.output.e2e-artifact.release-record` as **output** in task `e2e.rel.a2.t2`
- `wpu.e2e.rel.a3.t1.input.e2e-artifact.release-record` as **input** in task `e2e.rel.a3.t1`
- `wpu.e2e.rel.a3.t1.output.e2e-artifact.release-record` as **output** in task `e2e.rel.a3.t1`
- `wpu.e2e.ph2.st1.t1.input.e2e-artifact.release-record` as **input** in task `e2e.ph2.st1.t1`
- `wpu.e2e.ph2.st2.t1.input.e2e-artifact.release-record` as **input** in task `e2e.ph2.st2.t1`
- `wpu.e2e.ph2.st2.t2.input.e2e-artifact.release-record` as **input** in task `e2e.ph2.st2.t2`
- `wpu.e2e.ph2.st3.t1.input.e2e-artifact.release-record` as **input** in task `e2e.ph2.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Operational Evidence, Incident and Change Record

<small>Artifact: `e2e-artifact.operations-record` · 7 WorkProductUse occurrences</small>

SLOs, incidents/problems, changes, capacity, cost, security, provider events, product outcomes, and learning.

**Produced or updated by**

- Complete handover and rollback rehearsal

**Consumed by**

- Review release outcome and update roadmap
- Approve retirement scope and plan
- Execute migration and data disposition
- Decommission service, access, and cost surfaces
- Verify records retention and data disposition
- Complete closure review

**Work-product uses**

- `wpu.e2e.rel.a2.t2.output.e2e-artifact.operations-record` as **output** in task `e2e.rel.a2.t2`
- `wpu.e2e.rel.a3.t1.input.e2e-artifact.operations-record` as **input** in task `e2e.rel.a3.t1`
- `wpu.e2e.ph2.st1.t1.input.e2e-artifact.operations-record` as **input** in task `e2e.ph2.st1.t1`
- `wpu.e2e.ph2.st2.t1.input.e2e-artifact.operations-record` as **input** in task `e2e.ph2.st2.t1`
- `wpu.e2e.ph2.st2.t2.input.e2e-artifact.operations-record` as **input** in task `e2e.ph2.st2.t2`
- `wpu.e2e.ph2.st2.t3.input.e2e-artifact.operations-record` as **input** in task `e2e.ph2.st2.t3`
- `wpu.e2e.ph2.st3.t1.input.e2e-artifact.operations-record` as **input** in task `e2e.ph2.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Retrospective and Improvement Record

<small>Artifact: `e2e-artifact.improvement-record` · 6 WorkProductUse occurrences</small>

Observed outcomes, process/model friction, measures, decisions, owners, experiments, reusable candidates, and no-op rationale.

**Produced or updated by**

- Review the increment and adapt the way of working
- Review release outcome and update roadmap
- Complete closure review

**Consumed by**

- Review the increment and adapt the way of working
- Review release outcome and update roadmap
- Complete closure review

**Work-product uses**

- `wpu.e2e.p7.artifact-completion.t2.input.e2e-artifact.improvement-record` as **input** in task `e2e.p7.artifact-completion.t2`
- `wpu.e2e.p7.artifact-completion.t2.output.e2e-artifact.improvement-record` as **output** in task `e2e.p7.artifact-completion.t2`
- `wpu.e2e.rel.a3.t1.input.e2e-artifact.improvement-record` as **input** in task `e2e.rel.a3.t1`
- `wpu.e2e.rel.a3.t1.output.e2e-artifact.improvement-record` as **output** in task `e2e.rel.a3.t1`
- `wpu.e2e.ph2.st3.t1.input.e2e-artifact.improvement-record` as **input** in task `e2e.ph2.st3.t1`
- `wpu.e2e.ph2.st3.t1.output.e2e-artifact.improvement-record` as **output** in task `e2e.ph2.st3.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Service Work Item

<small>Artifact: `e2e-artifact.operational-work-item` · 0 WorkProductUse occurrences</small>

A production, maintenance, service, or improvement demand item with source, maintenance purpose where applicable, emergency-temporary status, class of service, severity, owner, service-level expectation, state, age, evidence, and disposition.

**Produced or updated by**

- None declared

**Consumed by**

- No task in this scope declares this item as an input.

**Work-product uses**

- This reusable work product has no WorkProductUse occurrence in the current process scope.

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Kanban Service-Delivery Policy and Board

<small>Artifact: `e2e-artifact.service-flow-system` · 1 WorkProductUse occurrence</small>

The explicit Definition of Workflow and visible Kanban board: requested/ready/started/finished points, workflow states, WIP controls, classes of service, service-level expectations, replenishment and review cadences, capacity policy, and flow metrics.

**Produced or updated by**

- Define operational and release strategy

**Consumed by**

- No task in this scope declares this item as an input.

**Work-product uses**

- `wpu.e2e.ph0.st4.t2.output.e2e-artifact.service-flow-system` as **output** in task `e2e.ph0.st4.t2`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Retirement, Data-Disposition and Closure Record

<small>Artifact: `e2e-artifact.retirement-record` · 9 WorkProductUse occurrences</small>

Retirement decision, migration, retention/export/deletion evidence, decommission evidence, financial closure, and retained knowledge.

**Produced or updated by**

- Approve retirement scope and plan
- Execute migration and data disposition
- Decommission service, access, and cost surfaces
- Verify records retention and data disposition
- Complete closure review

**Consumed by**

- Execute migration and data disposition
- Decommission service, access, and cost surfaces
- Verify records retention and data disposition
- Complete closure review

**Work-product uses**

- `wpu.e2e.ph2.st1.t1.output.e2e-artifact.retirement-record` as **output** in task `e2e.ph2.st1.t1`
- `wpu.e2e.ph2.st2.t1.input.e2e-artifact.retirement-record` as **input** in task `e2e.ph2.st2.t1`
- `wpu.e2e.ph2.st2.t1.output.e2e-artifact.retirement-record` as **output** in task `e2e.ph2.st2.t1`
- `wpu.e2e.ph2.st2.t2.input.e2e-artifact.retirement-record` as **input** in task `e2e.ph2.st2.t2`
- `wpu.e2e.ph2.st2.t2.output.e2e-artifact.retirement-record` as **output** in task `e2e.ph2.st2.t2`
- `wpu.e2e.ph2.st2.t3.input.e2e-artifact.retirement-record` as **input** in task `e2e.ph2.st2.t3`
- `wpu.e2e.ph2.st2.t3.output.e2e-artifact.retirement-record` as **output** in task `e2e.ph2.st2.t3`
- `wpu.e2e.ph2.st3.t1.input.e2e-artifact.retirement-record` as **input** in task `e2e.ph2.st3.t1`
- `wpu.e2e.ph2.st3.t1.output.e2e-artifact.retirement-record` as **output** in task `e2e.ph2.st3.t1`

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
