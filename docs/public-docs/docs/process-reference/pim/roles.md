# PIM modeling: roles

This page documents the **SPEM RoleDefinition, RoleUse, and ProcessPerformer** elements used by the PIM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A RoleDefinition states a responsibility. RoleUse places it in an activity, while ProcessPerformer connects it to a TaskUse as a primary or supporting performer. Roles are hats worn by people or teams. They are not required organization-chart positions.

## Summary

| Role                    | Main responsibility                                                                             | Primary tasks | Supporting tasks | Role uses |
| ----------------------- | ----------------------------------------------------------------------------------------------- | ------------: | ---------------: | --------: |
| Product Owner           | Product goal, ordered backlog, value decisions, acceptance                                      |             0 |                0 |         0 |
| Delivery Lead           | Team coordination, dependencies, flow metrics, escalation, retrospectives                       |             0 |                0 |         0 |
| Domain Expert           | Validates that architecture still realizes business scenarios                                   |             0 |                0 |         0 |
| Solution Architect      | Service boundaries, contracts, integration, policies, readiness                                 |            28 |                0 |        20 |
| Process Reviewer        | EVL gate approval, readiness sign-off                                                           |             3 |                0 |         4 |
| Quality Engineer        | Contract, integration, quality-attribute, and evidence strategy                                 |             0 |                0 |         0 |
| Security Engineer       | Identity, authorization, data protection, and security review                                   |             0 |                0 |         0 |
| Cloud Platform Engineer | Provider constraints, deployability feedback, and platform handoff                              |             0 |                0 |         0 |
| Method Engineer         | Maintains alignment between the modeling framework and development process as metamodels change |             1 |                0 |         1 |

## Detailed roles

## Product Owner

<small>RoleDefinition: `role.product-owner` · 0 RoleUse occurrences</small>

**Responsibilities**

- Product goal, ordered backlog, value decisions, acceptance

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

## Domain Expert

<small>RoleDefinition: `role.domain-expert` · 0 RoleUse occurrences</small>

**Responsibilities**

- Validates that architecture still realizes business scenarios

**Primary task accountability**

- No primary task in this process scope.

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- This reusable role has no RoleUse occurrence in the current process scope.

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Solution Architect

<small>RoleDefinition: `role.solution-architect` · 20 RoleUse occurrences</small>

**Responsibilities**

- Service boundaries, contracts, integration, policies, readiness

**Primary task accountability**

- Plan service slice
- Create PIM model root
- Set architecture posture
- Define serverless services
- Assign element memberships
- Define schemas and fields
- Model event types and envelopes
- Model data stores and models
- Define access patterns and indexes
- Configure change streams and notifications
- Define functions and contracts
- Configure triggers and runtime
- Define APIs and routes
- Map API contracts and errors
- Model event channels and buses
- Define flows and routing rules
- Model workflows and states
- Configure human tasks and error handling
- Configure identity providers and principals
- Define permissions and security policies
- Apply resilience policies
- Configure throughput and ordering policies
- Configure observability policies
- Define alerts, SLOs, and CORS
- Apply governance and compliance policies
- Map business rules and decision models
- Model external endpoints and adapters
- Configure environments and deployment units

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- `ru.pim.ph1.st0.solution-architect` in `pim.ph1.st0`
- `ru.pim.ph1.st1.solution-architect` in `pim.ph1.st1`
- `ru.pim.ph1.st2.solution-architect` in `pim.ph1.st2`
- `ru.pim.ph2.st1.solution-architect` in `pim.ph2.st1`
- `ru.pim.ph2.st2.solution-architect` in `pim.ph2.st2`
- `ru.pim.ph3.st1.solution-architect` in `pim.ph3.st1`
- `ru.pim.ph3.st2.solution-architect` in `pim.ph3.st2`
- `ru.pim.ph4.st1.solution-architect` in `pim.ph4.st1`
- `ru.pim.ph4.st2.solution-architect` in `pim.ph4.st2`
- `ru.pim.ph5.st1.solution-architect` in `pim.ph5.st1`
- `ru.pim.ph5.st2.solution-architect` in `pim.ph5.st2`
- `ru.pim.ph5.st2.ss1.solution-architect` in `pim.ph5.st2.ss1`
- `ru.pim.ph5.st2.ss2.solution-architect` in `pim.ph5.st2.ss2`
- `ru.pim.ph5.st2.ss3.solution-architect` in `pim.ph5.st2.ss3`
- `ru.pim.ph5.st3.solution-architect` in `pim.ph5.st3`
- `ru.pim.ph1.solution-architect` in `pim.ph1`
- `ru.pim.ph2.solution-architect` in `pim.ph2`
- `ru.pim.ph3.solution-architect` in `pim.ph3`
- `ru.pim.ph4.solution-architect` in `pim.ph4`
- `ru.pim.ph5.solution-architect` in `pim.ph5`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Process Reviewer

<small>RoleDefinition: `role.process-reviewer` · 4 RoleUse occurrences</small>

**Responsibilities**

- EVL gate approval, readiness sign-off

**Primary task accountability**

- Assess platform capabilities
- Complete trace and readiness
- Review and adapt PIM increment

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- `ru.pim.ph6.st1.process-reviewer` in `pim.ph6.st1`
- `ru.pim.ph6.st2.process-reviewer` in `pim.ph6.st2`
- `ru.pim.ph6.st3.process-reviewer` in `pim.ph6.st3`
- `ru.pim.ph6.process-reviewer` in `pim.ph6`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Quality Engineer

<small>RoleDefinition: `role.quality-engineer` · 0 RoleUse occurrences</small>

**Responsibilities**

- Contract, integration, quality-attribute, and evidence strategy

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

- Identity, authorization, data protection, and security review

**Primary task accountability**

- No primary task in this process scope.

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- This reusable role has no RoleUse occurrence in the current process scope.

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Cloud Platform Engineer

<small>RoleDefinition: `role.cloud-platform-engineer` · 0 RoleUse occurrences</small>

**Responsibilities**

- Provider constraints, deployability feedback, and platform handoff

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

- `ru.pim.ph1.st1.method-engineer` in `pim.ph1.st1`

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
