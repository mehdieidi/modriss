# Generated-artifact readiness: work products

This page documents the **SPEM WorkProductDefinition, WorkProductUse, and ProcessParameter** elements used by the Generated-artifact readiness process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A WorkProductDefinition describes maintained information or a tangible result. WorkProductUse binds that definition to an activity or task as an input or output. ProcessParameter makes the direction explicit. A work product can be stored in several physical files or systems if its identity, owner, revision, and evidence links remain clear.

## Summary

| Work product                       | Kind     | Producing tasks | Consuming tasks | Uses |
| ---------------------------------- | -------- | --------------: | --------------: | ---: |
| Generated Project Baseline         | Artifact |               4 |              11 |   15 |
| Environment Configuration Contract | Artifact |               3 |              10 |   13 |
| Security Review Record             | Artifact |               4 |              10 |   14 |
| Verification Evidence              | Artifact |               5 |               7 |   12 |
| Release & Rollback Plan            | Artifact |               2 |               3 |    5 |
| Operations Handover Pack           | Artifact |               3 |               3 |    6 |

## Detailed work products

## Generated Project Baseline

<small>Artifact: `artifact.generated-baseline` · 15 WorkProductUse occurrences</small>

Reviewed, version-controlled output generated from the approved PSM

**Produced or updated by**

- Review project structure and generated templates
- Establish source-control and release baseline
- Route structural gaps back to the PSM
- Complete repository hygiene

**Consumed by**

- Establish source-control and release baseline
- Route structural gaps back to the PSM
- Complete repository hygiene
- Define parameter and environment contract
- Configure secret references and encryption
- Review IAM and deployment permissions
- Implement repeatable build and deployment pipeline
- Run reproducible build and infrastructure validation
- Assess dependencies and security findings
- Deploy to staging and execute acceptance tests
- Complete operations handover

**Work-product uses**

- `wpu.artifact.ph1.st1.t1.output.artifact.generated-baseline` as **output** in task `artifact.ph1.st1.t1`
- `wpu.artifact.ph1.st1.t2.input.artifact.generated-baseline` as **input** in task `artifact.ph1.st1.t2`
- `wpu.artifact.ph1.st1.t2.output.artifact.generated-baseline` as **output** in task `artifact.ph1.st1.t2`
- `wpu.artifact.ph1.st2.t1.input.artifact.generated-baseline` as **input** in task `artifact.ph1.st2.t1`
- `wpu.artifact.ph1.st2.t1.output.artifact.generated-baseline` as **output** in task `artifact.ph1.st2.t1`
- `wpu.artifact.ph1.st2.t2.input.artifact.generated-baseline` as **input** in task `artifact.ph1.st2.t2`
- `wpu.artifact.ph1.st2.t2.output.artifact.generated-baseline` as **output** in task `artifact.ph1.st2.t2`
- `wpu.artifact.ph2.st1.t1.input.artifact.generated-baseline` as **input** in task `artifact.ph2.st1.t1`
- `wpu.artifact.ph2.st1.t2.input.artifact.generated-baseline` as **input** in task `artifact.ph2.st1.t2`
- `wpu.artifact.ph2.st2.t1.input.artifact.generated-baseline` as **input** in task `artifact.ph2.st2.t1`
- `wpu.artifact.ph2.st2.t2.input.artifact.generated-baseline` as **input** in task `artifact.ph2.st2.t2`
- `wpu.artifact.ph3.st1.t1.input.artifact.generated-baseline` as **input** in task `artifact.ph3.st1.t1`
- `wpu.artifact.ph3.st1.t2.input.artifact.generated-baseline` as **input** in task `artifact.ph3.st1.t2`
- `wpu.artifact.ph3.st2.t1.input.artifact.generated-baseline` as **input** in task `artifact.ph3.st2.t1`
- `wpu.artifact.ph4.st2.t1.input.artifact.generated-baseline` as **input** in task `artifact.ph4.st2.t1`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Environment Configuration Contract

<small>Artifact: `artifact.environment-contract` · 13 WorkProductUse occurrences</small>

Per-environment parameters, secrets references, account/region bindings, and ownership

**Produced or updated by**

- Define parameter and environment contract
- Configure secret references and encryption
- Implement repeatable build and deployment pipeline

**Consumed by**

- Configure secret references and encryption
- Review IAM and deployment permissions
- Implement repeatable build and deployment pipeline
- Run reproducible build and infrastructure validation
- Assess dependencies and security findings
- Deploy to staging and execute acceptance tests
- Verify observability and operational signals
- Create release, rollback, and communication plan
- Complete operations handover
- Execute production validation and close release

**Work-product uses**

- `wpu.artifact.ph2.st1.t1.output.artifact.environment-contract` as **output** in task `artifact.ph2.st1.t1`
- `wpu.artifact.ph2.st1.t2.input.artifact.environment-contract` as **input** in task `artifact.ph2.st1.t2`
- `wpu.artifact.ph2.st1.t2.output.artifact.environment-contract` as **output** in task `artifact.ph2.st1.t2`
- `wpu.artifact.ph2.st2.t1.input.artifact.environment-contract` as **input** in task `artifact.ph2.st2.t1`
- `wpu.artifact.ph2.st2.t2.input.artifact.environment-contract` as **input** in task `artifact.ph2.st2.t2`
- `wpu.artifact.ph2.st2.t2.output.artifact.environment-contract` as **output** in task `artifact.ph2.st2.t2`
- `wpu.artifact.ph3.st1.t1.input.artifact.environment-contract` as **input** in task `artifact.ph3.st1.t1`
- `wpu.artifact.ph3.st1.t2.input.artifact.environment-contract` as **input** in task `artifact.ph3.st1.t2`
- `wpu.artifact.ph3.st2.t1.input.artifact.environment-contract` as **input** in task `artifact.ph3.st2.t1`
- `wpu.artifact.ph3.st2.t2.input.artifact.environment-contract` as **input** in task `artifact.ph3.st2.t2`
- `wpu.artifact.ph4.st1.t1.input.artifact.environment-contract` as **input** in task `artifact.ph4.st1.t1`
- `wpu.artifact.ph4.st2.t1.input.artifact.environment-contract` as **input** in task `artifact.ph4.st2.t1`
- `wpu.artifact.ph4.st2.t2.input.artifact.environment-contract` as **input** in task `artifact.ph4.st2.t2`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Security Review Record

<small>Artifact: `artifact.security-record` · 14 WorkProductUse occurrences</small>

Least-privilege, secret handling, dependency, and data-protection evidence

**Produced or updated by**

- Configure secret references and encryption
- Review IAM and deployment permissions
- Assess dependencies and security findings
- Obtain production readiness approval

**Consumed by**

- Review IAM and deployment permissions
- Implement repeatable build and deployment pipeline
- Run reproducible build and infrastructure validation
- Assess dependencies and security findings
- Deploy to staging and execute acceptance tests
- Verify observability and operational signals
- Create release, rollback, and communication plan
- Obtain production readiness approval
- Complete operations handover
- Execute production validation and close release

**Work-product uses**

- `wpu.artifact.ph2.st1.t2.output.artifact.security-record` as **output** in task `artifact.ph2.st1.t2`
- `wpu.artifact.ph2.st2.t1.input.artifact.security-record` as **input** in task `artifact.ph2.st2.t1`
- `wpu.artifact.ph2.st2.t1.output.artifact.security-record` as **output** in task `artifact.ph2.st2.t1`
- `wpu.artifact.ph2.st2.t2.input.artifact.security-record` as **input** in task `artifact.ph2.st2.t2`
- `wpu.artifact.ph3.st1.t1.input.artifact.security-record` as **input** in task `artifact.ph3.st1.t1`
- `wpu.artifact.ph3.st1.t2.input.artifact.security-record` as **input** in task `artifact.ph3.st1.t2`
- `wpu.artifact.ph3.st1.t2.output.artifact.security-record` as **output** in task `artifact.ph3.st1.t2`
- `wpu.artifact.ph3.st2.t1.input.artifact.security-record` as **input** in task `artifact.ph3.st2.t1`
- `wpu.artifact.ph3.st2.t2.input.artifact.security-record` as **input** in task `artifact.ph3.st2.t2`
- `wpu.artifact.ph4.st1.t1.input.artifact.security-record` as **input** in task `artifact.ph4.st1.t1`
- `wpu.artifact.ph4.st1.t2.input.artifact.security-record` as **input** in task `artifact.ph4.st1.t2`
- `wpu.artifact.ph4.st1.t2.output.artifact.security-record` as **output** in task `artifact.ph4.st1.t2`
- `wpu.artifact.ph4.st2.t1.input.artifact.security-record` as **input** in task `artifact.ph4.st2.t1`
- `wpu.artifact.ph4.st2.t2.input.artifact.security-record` as **input** in task `artifact.ph4.st2.t2`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Verification Evidence

<small>Artifact: `artifact.verification-evidence` · 12 WorkProductUse occurrences</small>

Build, validation, test, and quality-gate results

**Produced or updated by**

- Run reproducible build and infrastructure validation
- Assess dependencies and security findings
- Deploy to staging and execute acceptance tests
- Verify observability and operational signals
- Execute production validation and close release

**Consumed by**

- Assess dependencies and security findings
- Deploy to staging and execute acceptance tests
- Verify observability and operational signals
- Create release, rollback, and communication plan
- Obtain production readiness approval
- Complete operations handover
- Execute production validation and close release

**Work-product uses**

- `wpu.artifact.ph3.st1.t1.output.artifact.verification-evidence` as **output** in task `artifact.ph3.st1.t1`
- `wpu.artifact.ph3.st1.t2.input.artifact.verification-evidence` as **input** in task `artifact.ph3.st1.t2`
- `wpu.artifact.ph3.st1.t2.output.artifact.verification-evidence` as **output** in task `artifact.ph3.st1.t2`
- `wpu.artifact.ph3.st2.t1.input.artifact.verification-evidence` as **input** in task `artifact.ph3.st2.t1`
- `wpu.artifact.ph3.st2.t1.output.artifact.verification-evidence` as **output** in task `artifact.ph3.st2.t1`
- `wpu.artifact.ph3.st2.t2.input.artifact.verification-evidence` as **input** in task `artifact.ph3.st2.t2`
- `wpu.artifact.ph3.st2.t2.output.artifact.verification-evidence` as **output** in task `artifact.ph3.st2.t2`
- `wpu.artifact.ph4.st1.t1.input.artifact.verification-evidence` as **input** in task `artifact.ph4.st1.t1`
- `wpu.artifact.ph4.st1.t2.input.artifact.verification-evidence` as **input** in task `artifact.ph4.st1.t2`
- `wpu.artifact.ph4.st2.t1.input.artifact.verification-evidence` as **input** in task `artifact.ph4.st2.t1`
- `wpu.artifact.ph4.st2.t2.input.artifact.verification-evidence` as **input** in task `artifact.ph4.st2.t2`
- `wpu.artifact.ph4.st2.t2.output.artifact.verification-evidence` as **output** in task `artifact.ph4.st2.t2`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Release & Rollback Plan

<small>Artifact: `artifact.release-plan` · 5 WorkProductUse occurrences</small>

Version, deployment sequence, approvals, rollback steps, and communications

**Produced or updated by**

- Create release, rollback, and communication plan
- Obtain production readiness approval

**Consumed by**

- Obtain production readiness approval
- Complete operations handover
- Execute production validation and close release

**Work-product uses**

- `wpu.artifact.ph4.st1.t1.output.artifact.release-plan` as **output** in task `artifact.ph4.st1.t1`
- `wpu.artifact.ph4.st1.t2.input.artifact.release-plan` as **input** in task `artifact.ph4.st1.t2`
- `wpu.artifact.ph4.st1.t2.output.artifact.release-plan` as **output** in task `artifact.ph4.st1.t2`
- `wpu.artifact.ph4.st2.t1.input.artifact.release-plan` as **input** in task `artifact.ph4.st2.t1`
- `wpu.artifact.ph4.st2.t2.input.artifact.release-plan` as **input** in task `artifact.ph4.st2.t2`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

## Operations Handover Pack

<small>Artifact: `artifact.operations-pack` · 6 WorkProductUse occurrences</small>

Runbooks, dashboards, alarms, support ownership, and post-release checks

**Produced or updated by**

- Verify observability and operational signals
- Complete operations handover
- Execute production validation and close release

**Consumed by**

- Create release, rollback, and communication plan
- Obtain production readiness approval
- Execute production validation and close release

**Work-product uses**

- `wpu.artifact.ph3.st2.t2.output.artifact.operations-pack` as **output** in task `artifact.ph3.st2.t2`
- `wpu.artifact.ph4.st1.t1.input.artifact.operations-pack` as **input** in task `artifact.ph4.st1.t1`
- `wpu.artifact.ph4.st1.t2.input.artifact.operations-pack` as **input** in task `artifact.ph4.st1.t2`
- `wpu.artifact.ph4.st2.t1.output.artifact.operations-pack` as **output** in task `artifact.ph4.st2.t1`
- `wpu.artifact.ph4.st2.t2.input.artifact.operations-pack` as **input** in task `artifact.ph4.st2.t2`
- `wpu.artifact.ph4.st2.t2.output.artifact.operations-pack` as **output** in task `artifact.ph4.st2.t2`

Keep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.

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
