# Generated-artifact readiness: roles

This page documents the **SPEM RoleDefinition, RoleUse, and ProcessPerformer** elements used by the Generated-artifact readiness process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A RoleDefinition states a responsibility. RoleUse places it in an activity, while ProcessPerformer connects it to a TaskUse as a primary or supporting performer. Roles are hats worn by people or teams. They are not required organization-chart positions.

## Summary

| Role                    | Main responsibility                                                                        | Primary tasks | Supporting tasks | Role uses |
| ----------------------- | ------------------------------------------------------------------------------------------ | ------------: | ---------------: | --------: |
| Release Engineer        | Owns release plan, versioning, approvals, and deployment evidence                          |             4 |                0 |         5 |
| Cloud Platform Engineer | Hardens infrastructure, IAM, environment configuration, and deployment automation          |             3 |                0 |         4 |
| Application Engineer    | Reviews generated code, completes application configuration, and fixes implementation gaps |             2 |                0 |         3 |
| Security Engineer       | Reviews secrets, access control, dependencies, and security controls                       |             3 |                0 |         3 |
| Quality Engineer        | Defines test evidence and verifies functional and non-functional acceptance                |             2 |                0 |         3 |
| Service Owner           | Accepts operational readiness, rollback posture, and production handover                   |             2 |                0 |         2 |

## Detailed roles

## Release Engineer

<small>RoleDefinition: `role.release-engineer` · 5 RoleUse occurrences</small>

**Responsibilities**

- Owns release plan, versioning, approvals, and deployment evidence

**Primary task accountability**

- Establish source-control and release baseline
- Implement repeatable build and deployment pipeline
- Create release, rollback, and communication plan
- Execute production validation and close release

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- `ru.artifact.ph1.st1.release-engineer` in `artifact.ph1.st1`
- `ru.artifact.ph2.st2.release-engineer` in `artifact.ph2.st2`
- `ru.artifact.ph4.st1.release-engineer` in `artifact.ph4.st1`
- `ru.artifact.ph4.st2.release-engineer` in `artifact.ph4.st2`
- `ru.artifact.ph4.release-engineer` in `artifact.ph4`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Cloud Platform Engineer

<small>RoleDefinition: `role.cloud-platform-engineer` · 4 RoleUse occurrences</small>

**Responsibilities**

- Hardens infrastructure, IAM, environment configuration, and deployment automation

**Primary task accountability**

- Route structural gaps back to the PSM
- Define parameter and environment contract
- Verify observability and operational signals

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- `ru.artifact.ph1.st2.cloud-platform-engineer` in `artifact.ph1.st2`
- `ru.artifact.ph2.st1.cloud-platform-engineer` in `artifact.ph2.st1`
- `ru.artifact.ph3.st2.cloud-platform-engineer` in `artifact.ph3.st2`
- `ru.artifact.ph2.cloud-platform-engineer` in `artifact.ph2`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Application Engineer

<small>RoleDefinition: `role.application-engineer` · 3 RoleUse occurrences</small>

**Responsibilities**

- Reviews generated code, completes application configuration, and fixes implementation gaps

**Primary task accountability**

- Review project structure and generated templates
- Complete repository hygiene

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- `ru.artifact.ph1.st1.application-engineer` in `artifact.ph1.st1`
- `ru.artifact.ph1.st2.application-engineer` in `artifact.ph1.st2`
- `ru.artifact.ph1.application-engineer` in `artifact.ph1`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Security Engineer

<small>RoleDefinition: `role.security-engineer` · 3 RoleUse occurrences</small>

**Responsibilities**

- Reviews secrets, access control, dependencies, and security controls

**Primary task accountability**

- Configure secret references and encryption
- Review IAM and deployment permissions
- Assess dependencies and security findings

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- `ru.artifact.ph2.st1.security-engineer` in `artifact.ph2.st1`
- `ru.artifact.ph2.st2.security-engineer` in `artifact.ph2.st2`
- `ru.artifact.ph3.st1.security-engineer` in `artifact.ph3.st1`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Quality Engineer

<small>RoleDefinition: `role.quality-engineer` · 3 RoleUse occurrences</small>

**Responsibilities**

- Defines test evidence and verifies functional and non-functional acceptance

**Primary task accountability**

- Run reproducible build and infrastructure validation
- Deploy to staging and execute acceptance tests

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- `ru.artifact.ph3.st1.quality-engineer` in `artifact.ph3.st1`
- `ru.artifact.ph3.st2.quality-engineer` in `artifact.ph3.st2`
- `ru.artifact.ph3.quality-engineer` in `artifact.ph3`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Service Owner

<small>RoleDefinition: `role.service-owner` · 2 RoleUse occurrences</small>

**Responsibilities**

- Accepts operational readiness, rollback posture, and production handover

**Primary task accountability**

- Obtain production readiness approval
- Complete operations handover

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- `ru.artifact.ph4.st1.service-owner` in `artifact.ph4.st1`
- `ru.artifact.ph4.st2.service-owner` in `artifact.ph4.st2`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

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
