# Generated-artifact readiness: guidance

This page documents the **SPEM Guidance** elements used by the Generated-artifact readiness process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

Guidance teaches how to perform or tailor work. It supports tasks and activities without becoming an automatic gate. A project may adapt guidance when its context requires another approach, provided that the method profile records the reasoning and preserves applicable evidence obligations.

## Summary

| Guidance                                     | Applies to   | Purpose                                                                                                                                                                                                   |
| -------------------------------------------- | ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Generated output is a controlled baseline    | process      | Treat generated files as a PSM-derived baseline: review every generated change, commit it to source control, and feed structural defects back to the PSM instead of repeatedly patching generated output. |
| Environment parity with explicit differences | artifact.ph2 | Keep development, test, staging, and production configuration structurally equivalent. Record intentional differences as parameters or approved environment exceptions.                                   |
| No secrets in artifacts                      | artifact.ph2 | Store secret values only in the approved secret manager or CI/CD secret store; generated templates and repositories contain references, never secret material.                                            |
| Evidence before promotion                    | artifact.ph3 | Promotion requires reproducible build, validation, test, and security evidence for the exact release candidate.                                                                                           |
| Rehearse recovery                            | artifact.ph4 | A release is not ready until rollback, data protection, alerting, and ownership are demonstrably usable by the on-call team.                                                                              |

## Detailed guidance

## Generated output is a controlled baseline

<small>Guidance: `artifact.guid.generated-baseline` · applies to process</small>

Treat generated files as a PSM-derived baseline: review every generated change, commit it to source control, and feed structural defects back to the PSM instead of repeatedly patching generated output.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Environment parity with explicit differences

<small>Guidance: `artifact.guid.environment-parity` · applies to artifact.ph2</small>

Keep development, test, staging, and production configuration structurally equivalent. Record intentional differences as parameters or approved environment exceptions.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## No secrets in artifacts

<small>Guidance: `artifact.guid.no-secrets` · applies to artifact.ph2</small>

Store secret values only in the approved secret manager or CI/CD secret store; generated templates and repositories contain references, never secret material.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Evidence before promotion

<small>Guidance: `artifact.guid.evidence` · applies to artifact.ph3</small>

Promotion requires reproducible build, validation, test, and security evidence for the exact release candidate.

**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.

## Rehearse recovery

<small>Guidance: `artifact.guid.rollback` · applies to artifact.ph4</small>

A release is not ready until rollback, data protection, alerting, and ownership are demonstrably usable by the on-call team.

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
