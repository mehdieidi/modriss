# PIM modeling: guidance

This page documents the **SPEM Guidance** elements used by the PIM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

Guidance teaches how to perform or tailor work. It supports tasks and activities without becoming an automatic gate. A project may adapt guidance when its context requires another approach, provided that the method profile records the reasoning and preserves applicable evidence obligations.

## Summary

| Guidance                       | Applies to | Purpose                                                                                                                                                       |
| ------------------------------ | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Service as deployable boundary | process    | Align ServerlessService boundaries to CIM bounded contexts. One increment typically maps to one service slice.                                                |
| Generated PIM is a draft       | pim.ph1    | Treat CIM→PIM output as architectural scaffolding. Every generated service slice must pass through framing, refinement, readiness, and review before PIM→PSM. |
| Contracts decouple producers   | pim.ph2    | Define schemas and event types before wiring functions and routes. Contracts are the integration currency.                                                    |
| Access patterns drive stores   | pim.ph2    | Model read/write access patterns from CIM queries/commands before choosing store topology.                                                                    |
| Policy attachment discipline   | pim.ph5    | Attach resilience, observability, and compliance policies to concrete PolicyTarget elements, not free-floating rules.                                         |
| Readiness is part of delivery  | pim.ph6    | Platform mapping and EVL validation are not after-the-fact audits. They are the review gate of each service-slice cycle.                                      |

## Detailed guidance

## Service as deployable boundary

<small>Guidance: `pim.guid.serverless-boundary` · applies to process</small>

Align ServerlessService boundaries to CIM bounded contexts. One increment typically maps to one service slice.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Generated PIM is a draft

<small>Guidance: `pim.guid.generated-is-draft` · applies to pim.ph1</small>

Treat CIM→PIM output as architectural scaffolding. Every generated service slice must pass through framing, refinement, readiness, and review before PIM→PSM.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Contracts decouple producers

<small>Guidance: `pim.guid.contracts-first` · applies to pim.ph2</small>

Define schemas and event types before wiring functions and routes. Contracts are the integration currency.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Access patterns drive stores

<small>Guidance: `pim.guid.data-access` · applies to pim.ph2</small>

Model read/write access patterns from CIM queries/commands before choosing store topology.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Policy attachment discipline

<small>Guidance: `pim.guid.policy-as-code` · applies to pim.ph5</small>

Attach resilience, observability, and compliance policies to concrete PolicyTarget elements, not free-floating rules.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Readiness is part of delivery

<small>Guidance: `pim.guid.readiness-in-cycle` · applies to pim.ph6</small>

Platform mapping and EVL validation are not after-the-fact audits. They are the review gate of each service-slice cycle.

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
