# Operations and Maintenance: tasks

This page documents the **SPEM TaskDefinition and TaskUse** elements used by the Operations and Maintenance process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A TaskDefinition describes reusable work. A TaskUse places that definition inside an activity and binds it to process performers and work-product uses. The same responsibility may appear in another process context with different inputs, outputs, or selected steps.

## Summary

| Task                                                  | Primary role     | Inputs | Outputs | Task uses |
| ----------------------------------------------------- | ---------------- | -----: | ------: | --------: |
| Review SLOs, telemetry, cost, and product outcomes    | Service Owner    |      6 |       3 |         1 |
| Triage and make service demand ready                  | Service Owner    |      4 |       2 |         1 |
| Replenish, pull, and manage Kanban flow               | Delivery Lead    |      4 |       3 |         1 |
| Manage incidents and emergency recovery               | Service Owner    |      5 |       2 |         1 |
| Perform problem and risk learning                     | Quality Engineer |      8 |       5 |         1 |
| Assess and route a maintenance change                 | Delivery Lead    |      7 |       3 |         1 |
| Inspect flow, quality, cost, and coordination metrics | Process Reviewer |      9 |       5 |         1 |

## Detailed tasks

## Review SLOs, telemetry, cost, and product outcomes

<small>Task definition: `task.e2e.ops.a1.t1`</small>

Review SLOs, telemetry, cost, and product outcomes This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Service Owner**. The work normally involves Product Owner, Security Engineer, FinOps and Cost Analyst. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Release, Recovery, Handover and Promotion Record
- Product and System Charter
- Operational Evidence, Incident and Change Record
- Situational Method Profile
- Kanban Service-Delivery Policy and Board
- Cost Model and Budget Guardrails

**How to perform the task**

1. Inspect service levels, errors, cost, security signals, usage, provider events, and product outcome measures.
2. Compare observations with the service, product, forecast, budget, and unit-cost hypotheses.
3. Capture actionable demand as visible service work items rather than inserting hidden work into a release plan.

**Outputs**

- Operational Evidence, Incident and Change Record
- Service Work Item
- Cost Model and Budget Guardrails

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Review SLOs, telemetry, cost, and product outcomes evidence is recorded

**Checks and evidence**

- Operational decisions are based on identified evidence and thresholds

**Uses in this process**

- `e2e.ops.a1.t1` in **Operate and Observe**

## Triage and make service demand ready

<small>Task definition: `task.e2e.ops.a2.t1`</small>

Triage and make service demand ready This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Service Owner**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Service Work Item
- Operational Evidence, Incident and Change Record
- Situational Method Profile
- Kanban Service-Delivery Policy and Board

**How to perform the task**

1. Record the demand source, impact, affected service, evidence, owner, and requested outcome on the Kanban board.
2. Where the item is maintenance, classify its purpose as corrective, preventive, adaptive, additive, or perfective; record any emergency temporary-restoration status; assign a class of service separately according to the board policy.
3. Refine the item until it meets the Ready policy and identify its likely disposition: operations-only response, the shortest safe model-driven change path, or a planned release backlog.

**Outputs**

- Service Work Item
- Kanban Service-Delivery Policy and Board

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Triage and make service demand ready evidence is recorded

**Checks and evidence**

- Maintenance purpose, emergency-temporary status, and class of service are recorded independently
- Every ready item has an accountable owner, expected outcome, evidence need, and visible disposition

**Uses in this process**

- `e2e.ops.a2.t1` in **Kanban Service-Delivery Management**

## Replenish, pull, and manage Kanban flow

<small>Task definition: `task.e2e.ops.a2.t2`</small>

Replenish, pull, and manage Kanban flow This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Delivery Lead**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Service Work Item
- Operational Evidence, Incident and Change Record
- Situational Method Profile
- Kanban Service-Delivery Policy and Board

**How to perform the task**

1. At the replenishment cadence, select eligible items into Ready according to capacity, risk, class-of-service policy, and value; pull a ready item only when the downstream WIP control permits.
2. Use the board to manage work-item age, blocked work, service-level expectations, and reserved service capacity; an expedite item may displace other work only under the explicit expedite policy and must remain visible.
3. Hold a daily flow review and periodic service-delivery review using WIP, throughput, cycle time, work-item age, SLE attainment, arrival rate, blocked time, and expedite frequency; adapt the Definition of Workflow through an explicit improvement decision.

**Outputs**

- Service Work Item
- Kanban Service-Delivery Policy and Board
- Operational Evidence, Incident and Change Record

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Replenish, pull, and manage Kanban flow evidence is recorded

**Checks and evidence**

- A team does not start ordinary service work beyond its WIP control
- The expedite class has at most one active item unless the method profile records an exceptional incident-command policy
- Requested, Ready, In Progress, Verify, and Done states and their entry/exit policies are visible on the board

**Uses in this process**

- `e2e.ops.a2.t2` in **Kanban Service-Delivery Management**

## Manage incidents and emergency recovery

<small>Task definition: `task.e2e.ops.a3.t1`</small>

Manage incidents and emergency recovery This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Service Owner**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Service Work Item
- Kanban Service-Delivery Policy and Board
- Operational Evidence, Incident and Change Record
- Release, Recovery, Handover and Promotion Record
- Situational Method Profile

**How to perform the task**

1. Triage impact, stabilize service, communicate status, and execute approved recovery actions.
2. Record incident timeline, affected scope, decisions, temporary modifications, and evidence.
3. Create permanent corrective, security, or change work for systemic causes and keep it visible after restoration.

**Outputs**

- Operational Evidence, Incident and Change Record
- Service Work Item

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Manage incidents and emergency recovery evidence is recorded

**Checks and evidence**

- Recovery and customer impact are recorded before closure
- An emergency temporary modification is not treated as the permanent corrective change

**Uses in this process**

- `e2e.ops.a3.t1` in **Incident, Problem & Risk Response**

## Perform problem and risk learning

<small>Task definition: `task.e2e.ops.a3.t2`</small>

Perform problem and risk learning This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Quality Engineer**. The work normally involves Service Owner, Security Engineer, Method Engineer. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Service Work Item
- Kanban Service-Delivery Policy and Board
- Operational Evidence, Incident and Change Record
- Increment and Integrated Plan Record
- Release, Recovery, Handover and Promotion Record
- Situational Method Profile
- Risk and Opportunity Register
- Retrospective and Improvement Record

**How to perform the task**

1. Analyze contributing causes across requirements, models, transformation, generation, deployment, and operation.
2. Update controls, tests, model patterns, risk treatment, process guidance, or method profile as appropriate.
3. Place permanent corrective work on the Kanban board or deliberately commit it to a planned release, and verify reconciliation after any emergency downstream fix.

**Outputs**

- Operational Evidence, Incident and Change Record
- Situational Method Profile
- Service Work Item
- Risk and Opportunity Register
- Retrospective and Improvement Record

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Perform problem and risk learning evidence is recorded

**Checks and evidence**

- The corrective action is routed to the earliest responsible source rather than only patched downstream

**Uses in this process**

- `e2e.ops.a3.t2` in **Incident, Problem & Risk Response**

## Assess and route a maintenance change

<small>Task definition: `task.e2e.ops.a4.t1`</small>

Assess and route a maintenance change This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Delivery Lead**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Service Work Item
- Kanban Service-Delivery Policy and Board
- Operational Evidence, Incident and Change Record
- Situational Method Profile
- Increment and Integrated Plan Record
- Product and System Charter
- Release, Recovery, Handover and Promotion Record

**How to perform the task**

1. Classify the authoritative source as product/domain, architecture, platform, generator, artifact, or operations and confirm whether the item remains a bounded Kanban service item or is committed to a planned release.
2. Use traces and dependency ownership to identify impacted downstream levels and teams.
3. Send product-changing work to the Development and Delivery Process at the smallest affected phase or activity; after release, reconcile emergency downstream fixes into the authoritative source and close the service item only when evidence returns.

**Outputs**

- Increment and Integrated Plan Record
- Operational Evidence, Incident and Change Record
- Service Work Item

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Assess and route a maintenance change evidence is recorded

**Checks and evidence**

- The change record identifies source revision, impacted levels, downstream evidence, acceptance decision, and service-work-item disposition

**Uses in this process**

- `e2e.ops.a4.t1` in **Maintenance Change and Reconciliation**

## Inspect flow, quality, cost, and coordination metrics

<small>Task definition: `task.e2e.ops.a5.t1`</small>

Inspect flow, quality, cost, and coordination metrics This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Process Reviewer**. The work normally involves Delivery Lead, Method Engineer, FinOps and Cost Analyst, Service Owner. Role names express responsibilities and may be combined or distributed according to the approved method profile.

**Inputs**

- Service Work Item
- Kanban Service-Delivery Policy and Board
- Increment and Integrated Plan Record
- Operational Evidence, Incident and Change Record
- Product and System Charter
- Situational Method Profile
- Release, Recovery, Handover and Promotion Record
- Cost Model and Budget Guardrails
- Retrospective and Improvement Record

**How to perform the task**

1. Review planned-delivery forecast, Kanban WIP, throughput, cycle time, work-item age, SLE attainment, demand arrival rate, expedite frequency, rework, trace coverage, blockers, dependency age, escaped defects, cost/unit trends, and product outcomes.
2. Look for systemic queues, starvation between planned and service work, missing work products, invalid gates, and coordination failures.
3. Approve bounded changes to the Definition of Workflow, capacity policy, DevOps interface, cost guardrails, or method profile and record their expected effect.

**Outputs**

- Operational Evidence, Incident and Change Record
- Situational Method Profile
- Kanban Service-Delivery Policy and Board
- Cost Model and Budget Guardrails
- Retrospective and Improvement Record

**Ready to start when**

- The responsible people understand the current scope and the required decision or result.

**Complete when**

- Inspect flow, quality, cost, and coordination metrics evidence is recorded

**Checks and evidence**

- Metrics lead to inspectable improvement experiments rather than individual performance rankings

**Uses in this process**

- `e2e.ops.a5.t1` in **Service and Process Improvement**

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
