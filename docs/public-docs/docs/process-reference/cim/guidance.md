# CIM modeling: guidance

This page documents the **SPEM Guidance** elements used by the CIM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

Guidance teaches how to perform or tailor work. It supports tasks and activities without becoming an automatic gate. A project may adapt guidance when its context requires another approach, provided that the method profile records the reasoning and preserves applicable evidence obligations.

## Summary

| Guidance                                   | Applies to | Purpose                                                                                                                                                                           |
| ------------------------------------------ | ---------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Computation-independent intent             | process    | CIM captures business meaning without platform or implementation choices. Defer technology decisions to PIM/PSM.                                                                  |
| Thin capability slices                     | cim.ph1    | Run CIM as an empirical increment cycle: select one valuable capability slice, model only enough breadth to satisfy the slice definition of done, review, then adapt the backlog. |
| Goal-Question-Metric (Basili)              | cim.ph1    | Anchor every modeling increment with measurable business goals before structural or behavioral elements.                                                                          |
| Ubiquitous language first (Evans)          | cim.ph2    | Agree domain vocabulary before modeling entities. Renaming later is expensive. Use glossary tasks early.                                                                          |
| Information before entity (CIM-ENTITY-001) | cim.ph3    | Create InformationItem elements before DomainEntity. Primary identity must reference an information item.                                                                         |
| Event storming surface (Brandolini)        | cim.ph3    | Model commands, queries, and events on a behavior surface linked to actors and capabilities before aggregates.                                                                    |
| Twin Peaks requirements (Nuseibeh)         | cim.ph5    | Backfill formal requirements after domain structure exists. Use engine rework loops when requirements expose gaps.                                                                |

## Detailed guidance

## Computation-independent intent

<small>Guidance: `cim.guid.mda-intent` · applies to process</small>

CIM captures business meaning without platform or implementation choices. Defer technology decisions to PIM/PSM.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Thin capability slices

<small>Guidance: `cim.guid.increment-cycle` · applies to cim.ph1</small>

Run CIM as an empirical increment cycle: select one valuable capability slice, model only enough breadth to satisfy the slice definition of done, review, then adapt the backlog.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Goal-Question-Metric (Basili)

<small>Guidance: `cim.guid.gqm` · applies to cim.ph1</small>

Anchor every modeling increment with measurable business goals before structural or behavioral elements.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Ubiquitous language first (Evans)

<small>Guidance: `cim.guid.ddd-language` · applies to cim.ph2</small>

Agree domain vocabulary before modeling entities. Renaming later is expensive. Use glossary tasks early.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Information before entity (CIM-ENTITY-001)

<small>Guidance: `cim.guid.information-before-entity` · applies to cim.ph3</small>

Create InformationItem elements before DomainEntity. Primary identity must reference an information item.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Event storming surface (Brandolini)

<small>Guidance: `cim.guid.event-storming` · applies to cim.ph3</small>

Model commands, queries, and events on a behavior surface linked to actors and capabilities before aggregates.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Twin Peaks requirements (Nuseibeh)

<small>Guidance: `cim.guid.twin-peaks` · applies to cim.ph5</small>

Backfill formal requirements after domain structure exists. Use engine rework loops when requirements expose gaps.

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
