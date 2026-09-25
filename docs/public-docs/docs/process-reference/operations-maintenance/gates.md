# Operations and Maintenance: gates and milestones

This page documents the **SPEM Milestone with MODRISS evidence and authority extensions** elements used by the Operations and Maintenance process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A milestone marks a meaningful decision in the process. MODRISS adds explicit authority and required-evidence references so a gate cannot pass only because its preceding tasks are complete. The decision record should state acceptance, conditional acceptance, redirection, rework, deferral, or closure as applicable.

## Summary

| Gate | Decision                                           | Authorities | Required evidence |
| ---- | -------------------------------------------------- | ----------: | ----------------: |
| G7   | Release transitioned to Operations and Maintenance |           2 |                 2 |
| G8   | Shared lifecycle closure accepted                  |           5 |                 4 |

## Detailed gates and milestones

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
