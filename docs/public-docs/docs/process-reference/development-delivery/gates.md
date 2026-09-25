# Development and Delivery: gates and milestones

This page documents the **SPEM Milestone with MODRISS evidence and authority extensions** elements used by the Development and Delivery process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A milestone marks a meaningful decision in the process. MODRISS adds explicit authority and required-evidence references so a gate cannot pass only because its preceding tasks are complete. The decision record should state acceptance, conditional acceptance, redirection, rework, deferral, or closure as applicable.

## Summary

| Gate | Decision                                            | Authorities | Required evidence |
| ---- | --------------------------------------------------- | ----------: | ----------------: |
| G0   | Pursue, explore, redirect, or stop                  |           5 |                 4 |
| G1   | Process and organization ready                      |           4 |                 4 |
| G2   | CIM accepted for the slice                          |           3 |                 3 |
| G3   | PIM accepted and platform-mappable                  |           4 |                 3 |
| G4   | PSM accepted for generation                         |           3 |                 3 |
| G5   | Increment accepted, reworked, or deferred           |           5 |                 4 |
| G6   | Release authorized, rejected, or exception accepted |           4 |                 3 |
| G7   | Release transitioned to Operations and Maintenance  |           2 |                 2 |
| G8   | Shared lifecycle closure accepted                   |           5 |                 4 |

## Detailed gates and milestones

## G0: Pursue, explore, redirect, or stop

<small>Milestone: `e2e.g0` · phase/activity `e2e.ph0`</small>

An authorized value, feasibility, economic, and risk decision is recorded.

**Decision authorities**

- Sponsor
- Product Owner
- Solution Architect
- Security Engineer
- Service Owner

**Required evidence**

- Product and System Charter
- Feasibility and Serverless Suitability Record
- Cost Model and Budget Guardrails
- Risk and Opportunity Register

Passing this gate means the named evidence has been reviewed for this decision. It does not remove later verification, operational, compliance, or change-control obligations.

## G1: Process and organization ready

<small>Milestone: `e2e.g1` · phase/activity `e2e.ph0`</small>

The tailored way of working, ownership, controls, and first increment are authorized.

**Decision authorities**

- Product Owner
- Delivery Lead
- Method Engineer
- Process Reviewer

**Required evidence**

- Situational Method Profile
- Team Topology and Dependency Map
- Increment and Integrated Plan Record
- Kanban Service-Delivery Policy and Board

Passing this gate means the named evidence has been reviewed for this decision. It does not remove later verification, operational, compliance, or change-control obligations.

## G2: CIM accepted for the slice

<small>Milestone: `e2e.g2` · phase/activity `e2e.ph1`</small>

Meaning, requirements, behavior, acceptance, trace, and explicit semantic-validation evidence are adequate.

**Decision authorities**

- Product Owner
- Domain Expert
- Process Reviewer

**Required evidence**

- cim-artifact.trace-readiness
- cim-artifact.increment-review
- Increment and Integrated Plan Record

Passing this gate means the named evidence has been reviewed for this decision. It does not remove later verification, operational, compliance, or change-control obligations.

## G3: PIM accepted and platform-mappable

<small>Milestone: `e2e.g3` · phase/activity `e2e.ph1`</small>

Contracts, failure behavior, security, data, SLOs, trace, and provider capability needs are explicit.

**Decision authorities**

- Solution Architect
- Security Engineer
- Service Owner
- Process Reviewer

**Required evidence**

- pim-artifact.platform-readiness
- pim-artifact.increment-review
- Increment and Integrated Plan Record

Passing this gate means the named evidence has been reviewed for this decision. It does not remove later verification, operational, compliance, or change-control obligations.

## G4: PSM accepted for generation

<small>Milestone: `e2e.g4` · phase/activity `e2e.ph1`</small>

The exact PSM revision, trace, manual decisions, and explicit semantic-validation evidence are accepted.

**Decision authorities**

- Cloud Platform Engineer
- Security Engineer
- Process Reviewer

**Required evidence**

- psm-artifact.deployment-readiness
- psm-artifact.increment-review
- Increment and Integrated Plan Record

Passing this gate means the named evidence has been reviewed for this decision. It does not remove later verification, operational, compliance, or change-control obligations.

## G5: Increment accepted, reworked, or deferred

<small>Milestone: `e2e.g5` · phase/activity `e2e.ph1`</small>

Value, verification, security, operability, trace, and explicit exceptions are accepted for the exact increment.

**Decision authorities**

- Product Owner
- Quality Engineer
- Security Engineer
- Service Owner
- Process Reviewer

**Required evidence**

- Increment and Integrated Plan Record
- artifact.verification-evidence
- artifact.security-record
- artifact.operations-pack

Passing this gate means the named evidence has been reviewed for this decision. It does not remove later verification, operational, compliance, or change-control obligations.

## G6: Release authorized, rejected, or exception accepted

<small>Milestone: `e2e.g6` · phase/activity `e2e.ph1`</small>

The exact immutable candidate and recovery, assurance, operational, and economic evidence are accepted.

**Decision authorities**

- Product Owner
- Process Reviewer
- Security Engineer
- Service Owner

**Required evidence**

- Release, Recovery, Handover and Promotion Record
- Cost Model and Budget Guardrails
- Risk and Opportunity Register

Passing this gate means the named evidence has been reviewed for this decision. It does not remove later verification, operational, compliance, or change-control obligations.

## G7: Release transitioned to Operations and Maintenance

<small>Milestone: `e2e.g7` · phase/activity `e2e.ph1`</small>

The running release, support obligations, observability, recovery, and ownership are accepted.

**Decision authorities**

- Release Engineer
- Service Owner

**Required evidence**

- Release, Recovery, Handover and Promotion Record
- Operational Evidence, Incident and Change Record

Passing this gate means the named evidence has been reviewed for this decision. It does not remove later verification, operational, compliance, or change-control obligations.

## G8: Shared lifecycle closure accepted

<small>Milestone: `e2e.g8` · phase/activity `e2e.ph2`</small>

No live release, unowned data/records obligation, active access path, residual chargeable resource, or unresolved closure blocker remains.

**Decision authorities**

- Sponsor
- Product Owner
- Service Owner
- Records and Data Steward
- Process Reviewer

**Required evidence**

- Retirement, Data-Disposition and Closure Record
- Operational Evidence, Incident and Change Record
- Cost Model and Budget Guardrails
- Retrospective and Improvement Record

Passing this gate means the named evidence has been reviewed for this decision. It does not remove later verification, operational, compliance, or change-control obligations.

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
