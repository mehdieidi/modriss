# AWS PSM modeling: guidance

This page documents the **SPEM Guidance** elements used by the AWS PSM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

Guidance teaches how to perform or tailor work. It supports tasks and activities without becoming an automatic gate. A project may adapt guidance when its context requires another approach, provided that the method profile records the reasoning and preserves applicable evidence obligations.

## Summary

| Guidance                           | Applies to | Purpose                                                                                                                                                      |
| ---------------------------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| SAM stack containment              | process    | Most AWS resources live under SamStack. Establish stack scaffolding before resource provisioning tasks.                                                      |
| Generated PSM is a draft           | psm.ph1    | Treat PIM→PSM output as AWS scaffolding. Refine IAM, networking, resource settings, integration views, and readiness before M2T generation.                  |
| Least-privilege IAM                | psm.ph1    | Security baseline before compute. Lambda permissions and resource policies reference roles from the baseline.                                                |
| Integration views for traceability | psm.ph6    | Relationship views denormalize cross-resource wiring for M2T and human review, create them before M2T generation.                                            |
| Deployable slice discipline        | psm.ph6    | A PSM increment is done only when AWS wiring, least-privilege posture, observability, traceability, and artifact-generation readiness are reviewed together. |

## Detailed guidance

## SAM stack containment

<small>Guidance: `psm.guid.sam-containment` · applies to process</small>

Most AWS resources live under SamStack. Establish stack scaffolding before resource provisioning tasks.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Generated PSM is a draft

<small>Guidance: `psm.guid.generated-is-draft` · applies to psm.ph1</small>

Treat PIM→PSM output as AWS scaffolding. Refine IAM, networking, resource settings, integration views, and readiness before M2T generation.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Least-privilege IAM

<small>Guidance: `psm.guid.least-privilege` · applies to psm.ph1</small>

Security baseline before compute. Lambda permissions and resource policies reference roles from the baseline.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Integration views for traceability

<small>Guidance: `psm.guid.integration-views` · applies to psm.ph6</small>

Relationship views denormalize cross-resource wiring for M2T and human review, create them before M2T generation.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Deployable slice discipline

<small>Guidance: `psm.guid.deployable-slice` · applies to psm.ph6</small>

A PSM increment is done only when AWS wiring, least-privilege posture, observability, traceability, and artifact-generation readiness are reviewed together.

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
