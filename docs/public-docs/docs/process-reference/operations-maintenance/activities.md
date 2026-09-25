# Operations and Maintenance: phases and activities

This page documents the **SPEM process-structure Activity, Phase, and TaskUse** elements used by the Operations and Maintenance process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A phase establishes a significant lifecycle period and normally ends at a major checkpoint. An activity groups related work within a phase or process component. A TaskUse places reusable task guidance into that process context. Entry and exit conditions describe evidence states; they are not calendar dates.

## Summary

| Phase or activity                     | SPEM type | Contained by | Task uses | Execution |
| ------------------------------------- | --------- | ------------ | --------: | --------- |
| Operate and Observe                   | Activity  | Process      |         1 | iterative |
| Kanban Service-Delivery Management    | Activity  | Process      |         2 | iterative |
| Incident, Problem & Risk Response     | Activity  | Process      |         2 | iterative |
| Maintenance Change and Reconciliation | Activity  | Process      |         1 | iterative |
| Service and Process Improvement       | Activity  | Process      |         1 | iterative |

## Detailed activities

## Operate and Observe

<small>Activity · Activity: `e2e.ops.a1`</small>

Use operational evidence to assess service health, user outcomes, and control effectiveness.

**Participating roles**

- Service Owner
- Product Owner
- Security Engineer
- FinOps and Cost Analyst

**Task uses in this activity**

- **Review SLOs, telemetry, cost, and product outcomes** (TaskUse `e2e.ops.a1.t1`)

**Execution character:** iterative. Re-enter this work when its trigger or feedback condition applies, and retain the evidence from each material decision.

## Kanban Service-Delivery Management

<small>Activity · Activity: `e2e.ops.a2`</small>

Visualize and manage service demand through an explicit Definition of Workflow, replenishment, pull, WIP controls, service expectations, and feedback cadences.

**Participating roles**

- Delivery Lead
- Service Owner

**Task uses in this activity**

- **Triage and make service demand ready** (TaskUse `e2e.ops.a2.t1`)
- **Replenish, pull, and manage Kanban flow** (TaskUse `e2e.ops.a2.t2`)

**Execution character:** iterative. Re-enter this work when its trigger or feedback condition applies, and retain the evidence from each material decision.

## Incident, Problem & Risk Response

<small>Activity · Activity: `e2e.ops.a3`</small>

Restore service and address systemic causes while preserving traceability and learning.

**Participating roles**

- Service Owner
- Quality Engineer
- Security Engineer
- Method Engineer

**Task uses in this activity**

- **Manage incidents and emergency recovery** (TaskUse `e2e.ops.a3.t1`)
- **Perform problem and risk learning** (TaskUse `e2e.ops.a3.t2`)

**Execution character:** iterative. Re-enter this work when its trigger or feedback condition applies, and retain the evidence from each material decision.

## Maintenance Change and Reconciliation

<small>Activity · Activity: `e2e.ops.a4`</small>

Evolve the product through controlled impact analysis, authoritative-source change, release, and emergency-fix reconciliation.

**Participating roles**

- Delivery Lead

**Task uses in this activity**

- **Assess and route a maintenance change** (TaskUse `e2e.ops.a4.t1`)

**Execution character:** iterative. Re-enter this work when its trigger or feedback condition applies, and retain the evidence from each material decision.

## Service and Process Improvement

<small>Activity · Activity: `e2e.ops.a5`</small>

Improve the product and both connected processes using evidence from releases, service work, incidents, and dependencies.

**Participating roles**

- Process Reviewer
- Delivery Lead
- Method Engineer
- FinOps and Cost Analyst
- Service Owner

**Task uses in this activity**

- **Inspect flow, quality, cost, and coordination metrics** (TaskUse `e2e.ops.a5.t1`)

**Execution character:** iterative. Re-enter this work when its trigger or feedback condition applies, and retain the evidence from each material decision.

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
