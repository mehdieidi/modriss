# Operations and Maintenance reference

Operations and Maintenance is an ongoing, event-driven process for every accepted live release. It begins at the first G7 handover and closes only when G8 is accepted and no release remains live. Its Kanban system controls production demand while Development and Delivery continues to engineer planned or product-changing work.

Operate the service and manage production, maintenance, and improvement demand through a continuous Kanban service-delivery system while planned releases continue through the increment engine; restore service, reconcile authoritative sources, and learn.

**Entry conditions**

- G7 operational handover has accepted at least one live release

**Exit conditions**

- G8 lifecycle closure is accepted and no live release remains

An operational item can finish as runbook or service work, follow the shortest safe model-driven change path, or enter a planned release backlog. Emergency restoration remains temporary until the authoritative source and downstream outputs are reconciled.

## Activity summary

| Activity                              | Purpose                                                                                                                                                         | Task uses | Iterative |
| ------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------: | --------- |
| Operate and Observe                   | Use operational evidence to assess service health, user outcomes, and control effectiveness.                                                                    |         1 | Yes       |
| Kanban Service-Delivery Management    | Visualize and manage service demand through an explicit Definition of Workflow, replenishment, pull, WIP controls, service expectations, and feedback cadences. |         2 | Yes       |
| Incident, Problem & Risk Response     | Restore service and address systemic causes while preserving traceability and learning.                                                                         |         2 | Yes       |
| Maintenance Change and Reconciliation | Evolve the product through controlled impact analysis, authoritative-source change, release, and emergency-fix reconciliation.                                  |         1 | Yes       |
| Service and Process Improvement       | Improve the product and both connected processes using evidence from releases, service work, incidents, and dependencies.                                       |         1 | Yes       |

### Operate and Observe

<small>Activity: `e2e.ops.a1`</small>

Use operational evidence to assess service health, user outcomes, and control effectiveness.

This activity is iterative. Repeat it when evidence changes, a review finds rework, or the current item needs another controlled refinement.

#### Review SLOs, telemetry, cost, and product outcomes

<small>Task definition: `e2e.ops.a1.t1`</small>

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

### Kanban Service-Delivery Management

<small>Activity: `e2e.ops.a2`</small>

Visualize and manage service demand through an explicit Definition of Workflow, replenishment, pull, WIP controls, service expectations, and feedback cadences.

This activity is iterative. Repeat it when evidence changes, a review finds rework, or the current item needs another controlled refinement.

#### Triage and make service demand ready

<small>Task definition: `e2e.ops.a2.t1`</small>

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

#### Replenish, pull, and manage Kanban flow

<small>Task definition: `e2e.ops.a2.t2`</small>

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

### Incident, Problem & Risk Response

<small>Activity: `e2e.ops.a3`</small>

Restore service and address systemic causes while preserving traceability and learning.

This activity is iterative. Repeat it when evidence changes, a review finds rework, or the current item needs another controlled refinement.

#### Manage incidents and emergency recovery

<small>Task definition: `e2e.ops.a3.t1`</small>

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

#### Perform problem and risk learning

<small>Task definition: `e2e.ops.a3.t2`</small>

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

### Maintenance Change and Reconciliation

<small>Activity: `e2e.ops.a4`</small>

Evolve the product through controlled impact analysis, authoritative-source change, release, and emergency-fix reconciliation.

This activity is iterative. Repeat it when evidence changes, a review finds rework, or the current item needs another controlled refinement.

#### Assess and route a maintenance change

<small>Task definition: `e2e.ops.a4.t1`</small>

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

### Service and Process Improvement

<small>Activity: `e2e.ops.a5`</small>

Improve the product and both connected processes using evidence from releases, service work, incidents, and dependencies.

This activity is iterative. Repeat it when evidence changes, a review finds rework, or the current item needs another controlled refinement.

#### Inspect flow, quality, cost, and coordination metrics

<small>Task definition: `e2e.ops.a5.t1`</small>

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
