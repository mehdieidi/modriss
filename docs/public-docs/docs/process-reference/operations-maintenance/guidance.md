# Operations and Maintenance: guidance

This page documents the **SPEM Guidance** elements used by the Operations and Maintenance process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

Guidance teaches how to perform or tailor work. It supports tasks and activities without becoming an automatic gate. A project may adapt guidance when its context requires another approach, provided that the method profile records the reasoning and preserves applicable evidence obligations.

## Summary

| Guidance                     | Applies to | Purpose                                                                                                                                                                                                       |
| ---------------------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Thin vertical increments     | process    | Deliver one capability slice through CIM → PIM → PSM → artifact readiness per engine revolution. The engine repeats inside release, operations, and retirement lifecycle governance; avoid big-bang modeling. |
| Human-in-the-loop transforms | process    | ETL and M2T are assistive. ManualDecision and readiness gates block promotion when automation is uncertain.                                                                                                   |
| Empirical process control    | process    | Plan a small vertical slice, inspect executable model evidence at each gate, and adapt the backlog after artifact review.                                                                                     |

## Detailed guidance

## Thin vertical increments

<small>Guidance: `e2e.guid.incremental-vertical` · applies to process</small>

Deliver one capability slice through CIM → PIM → PSM → artifact readiness per engine revolution. The engine repeats inside release, operations, and retirement lifecycle governance; avoid big-bang modeling.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Human-in-the-loop transforms

<small>Guidance: `e2e.guid.human-in-loop` · applies to process</small>

ETL and M2T are assistive. ManualDecision and readiness gates block promotion when automation is uncertain.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Empirical process control

<small>Guidance: `e2e.guid.empirical-control` · applies to process</small>

Plan a small vertical slice, inspect executable model evidence at each gate, and adapt the backlog after artifact review.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

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
