# AWS PSM modeling: roles

This page documents the **SPEM RoleDefinition, RoleUse, and ProcessPerformer** elements used by the AWS PSM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A RoleDefinition states a responsibility. RoleUse places it in an activity, while ProcessPerformer connects it to a TaskUse as a primary or supporting performer. Roles are hats worn by people or teams. They are not required organization-chart positions.

## Summary

| Role                    | Main responsibility                                                                             | Primary tasks | Supporting tasks | Role uses |
| ----------------------- | ----------------------------------------------------------------------------------------------- | ------------: | ---------------: | --------: |
| Product Owner           | Value, release scope, acceptance, and risk decisions                                            |             0 |                0 |         0 |
| Delivery Lead           | Team coordination, dependencies, flow metrics, escalation, retrospectives                       |             0 |                0 |         0 |
| Solution Architect      | PIM intent, provider-independent trade-offs, and architecture consistency                       |             0 |                0 |         0 |
| Cloud Platform Engineer | AWS resources, IAM, networking, observability, deployment posture                               |            25 |                0 |        20 |
| Process Reviewer        | EVL gate approval, readiness sign-off                                                           |             2 |                0 |         3 |
| Quality Engineer        | Infrastructure, contract, deployment, and operational verification evidence                     |             0 |                0 |         0 |
| Security Engineer       | IAM, network, secrets, encryption, threat findings, and exception control                       |             0 |                0 |         0 |
| Service Owner / SRE     | SLOs, observability, incident readiness, cost, recovery, and handover                           |             0 |                0 |         0 |
| Release Engineer        | Versioning, promotion, approvals, rollback, and release evidence                                |             0 |                0 |         0 |
| Method Engineer         | Maintains alignment between the modeling framework and development process as metamodels change |             1 |                0 |         1 |

## Detailed roles

## Product Owner

<small>RoleDefinition: `role.product-owner` · 0 RoleUse occurrences</small>

**Responsibilities**

- Value, release scope, acceptance, and risk decisions

**Primary task accountability**

- No primary task in this process scope.

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- This reusable role has no RoleUse occurrence in the current process scope.

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Delivery Lead

<small>RoleDefinition: `role.delivery-lead` · 0 RoleUse occurrences</small>

**Responsibilities**

- Team coordination, dependencies, flow metrics, escalation, retrospectives

**Primary task accountability**

- No primary task in this process scope.

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- This reusable role has no RoleUse occurrence in the current process scope.

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Solution Architect

<small>RoleDefinition: `role.solution-architect` · 0 RoleUse occurrences</small>

**Responsibilities**

- PIM intent, provider-independent trade-offs, and architecture consistency

**Primary task accountability**

- No primary task in this process scope.

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- This reusable role has no RoleUse occurrence in the current process scope.

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Cloud Platform Engineer

<small>RoleDefinition: `role.cloud-platform-engineer` · 20 RoleUse occurrences</small>

**Responsibilities**

- AWS resources, IAM, networking, observability, deployment posture

**Primary task accountability**

- Plan deployable slice
- Create AWS PSM model root
- Define stage and naming policies
- Create SAM stack and globals
- Define CFN parameters and outputs
- Configure IAM roles and policies
- Provision KMS, secrets, and SSM
- Configure VPC and subnets
- Define endpoints and security groups
- Configure Cognito user pools
- Configure clients, groups, and identity pools
- Provision DynamoDB tables
- Configure S3 buckets and policies
- Create SQS queues
- Configure SNS topics and subscriptions
- Configure EventBridge buses and rules
- Configure schedules, pipes, and connections
- Deploy Lambda functions
- Configure event sources and permissions
- Configure APIs and routes
- Configure integrations and authorizers
- Deploy state machines
- Configure CloudWatch logs and metrics
- Configure alarms and dashboards
- Create integration relationship views

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- `ru.psm.ph1.st0.cloud-platform-engineer` in `psm.ph1.st0`
- `ru.psm.ph1.st1.cloud-platform-engineer` in `psm.ph1.st1`
- `ru.psm.ph1.st2.cloud-platform-engineer` in `psm.ph1.st2`
- `ru.psm.ph1.st3.cloud-platform-engineer` in `psm.ph1.st3`
- `ru.psm.ph2.st1.cloud-platform-engineer` in `psm.ph2.st1`
- `ru.psm.ph2.st2.cloud-platform-engineer` in `psm.ph2.st2`
- `ru.psm.ph3.st1.cloud-platform-engineer` in `psm.ph3.st1`
- `ru.psm.ph3.st2.cloud-platform-engineer` in `psm.ph3.st2`
- `ru.psm.ph4.st1.cloud-platform-engineer` in `psm.ph4.st1`
- `ru.psm.ph4.st2.cloud-platform-engineer` in `psm.ph4.st2`
- `ru.psm.ph5.st1.cloud-platform-engineer` in `psm.ph5.st1`
- `ru.psm.ph5.st2.cloud-platform-engineer` in `psm.ph5.st2`
- `ru.psm.ph5.st2.ss1.cloud-platform-engineer` in `psm.ph5.st2.ss1`
- `ru.psm.ph5.st2.ss2.cloud-platform-engineer` in `psm.ph5.st2.ss2`
- `ru.psm.ph6.st1.cloud-platform-engineer` in `psm.ph6.st1`
- `ru.psm.ph1.cloud-platform-engineer` in `psm.ph1`
- `ru.psm.ph2.cloud-platform-engineer` in `psm.ph2`
- `ru.psm.ph3.cloud-platform-engineer` in `psm.ph3`
- `ru.psm.ph4.cloud-platform-engineer` in `psm.ph4`
- `ru.psm.ph5.cloud-platform-engineer` in `psm.ph5`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Process Reviewer

<small>RoleDefinition: `role.process-reviewer` · 3 RoleUse occurrences</small>

**Responsibilities**

- EVL gate approval, readiness sign-off

**Primary task accountability**

- Complete trace and readiness
- Review and adapt PSM increment

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- `ru.psm.ph6.st2.process-reviewer` in `psm.ph6.st2`
- `ru.psm.ph6.st3.process-reviewer` in `psm.ph6.st3`
- `ru.psm.ph6.process-reviewer` in `psm.ph6`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Quality Engineer

<small>RoleDefinition: `role.quality-engineer` · 0 RoleUse occurrences</small>

**Responsibilities**

- Infrastructure, contract, deployment, and operational verification evidence

**Primary task accountability**

- No primary task in this process scope.

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- This reusable role has no RoleUse occurrence in the current process scope.

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Security Engineer

<small>RoleDefinition: `role.security-engineer` · 0 RoleUse occurrences</small>

**Responsibilities**

- IAM, network, secrets, encryption, threat findings, and exception control

**Primary task accountability**

- No primary task in this process scope.

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- This reusable role has no RoleUse occurrence in the current process scope.

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Service Owner / SRE

<small>RoleDefinition: `role.service-owner` · 0 RoleUse occurrences</small>

**Responsibilities**

- SLOs, observability, incident readiness, cost, recovery, and handover

**Primary task accountability**

- No primary task in this process scope.

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- This reusable role has no RoleUse occurrence in the current process scope.

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Release Engineer

<small>RoleDefinition: `role.release-engineer` · 0 RoleUse occurrences</small>

**Responsibilities**

- Versioning, promotion, approvals, rollback, and release evidence

**Primary task accountability**

- No primary task in this process scope.

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- This reusable role has no RoleUse occurrence in the current process scope.

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Method Engineer

<small>RoleDefinition: `role.method-engineer` · 1 RoleUse occurrence</small>

**Responsibilities**

- Maintains alignment between the modeling framework and development process as metamodels change

**Primary task accountability**

- Establish shared model contract and evidence conventions

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- `ru.psm.ph1.st1.method-engineer` in `psm.ph1.st1`

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
