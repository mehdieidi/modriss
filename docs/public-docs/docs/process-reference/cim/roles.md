# CIM modeling: roles

This page documents the **SPEM RoleDefinition, RoleUse, and ProcessPerformer** elements used by the CIM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A RoleDefinition states a responsibility. RoleUse places it in an activity, while ProcessPerformer connects it to a TaskUse as a primary or supporting performer. Roles are hats worn by people or teams. They are not required organization-chart positions.

## Summary

| Role                        | Main responsibility                                                                             | Primary tasks | Supporting tasks | Role uses |
| --------------------------- | ----------------------------------------------------------------------------------------------- | ------------: | ---------------: | --------: |
| Product Owner               | Product goal, ordered backlog, value decisions, acceptance                                      |             0 |                0 |         0 |
| Delivery Lead               | Team coordination, dependencies, flow metrics, escalation, retrospectives                       |             0 |                0 |         0 |
| Domain Expert               | Business meaning, rules, examples, language, and scenario validation                            |             0 |                0 |         0 |
| Business Modeler            | Intent, domain, behavior, process, transformation contracts                                     |            21 |                0 |        26 |
| Requirements Engineer       | Requirements, acceptance criteria, governance constraints                                       |             2 |                0 |         2 |
| Security & Privacy Engineer | Threats, privacy, compliance, risk acceptance, and control evidence                             |             0 |                0 |         0 |
| Process Reviewer            | EVL gate approval, readiness sign-off                                                           |             2 |                0 |         2 |
| Method Engineer             | Maintains alignment between the modeling framework and development process as metamodels change |             1 |                0 |         1 |

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

- Business meaning, rules, examples, language, and scenario validation

**Primary task accountability**

- No primary task in this process scope.

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- This reusable role has no RoleUse occurrence in the current process scope.

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Business Modeler

<small>RoleDefinition: `role.business-modeler` · 26 RoleUse occurrences</small>

**Responsibilities**

- Intent, domain, behavior, process, transformation contracts

**Primary task accountability**

- Create CIM model root
- Plan capability slice
- Define business goals and KPIs
- Identify stakeholders
- Model actors and roles
- Register external systems
- Map business capabilities
- Record capability dependencies
- Define domain glossary
- Define data classifications
- Create information items
- Model domain entities
- Model value objects and relationships
- Model commands and outcomes
- Model queries
- Model events, errors, and conditions
- Define aggregate candidates
- Model business processes
- Define policies and decision tables
- Synthesize bounded contexts
- Record transformation metadata

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- `ru.cim.ph1.st1.business-modeler` in `cim.ph1.st1`
- `ru.cim.ph1.st2.business-modeler` in `cim.ph1.st2`
- `ru.cim.ph1.st3.business-modeler` in `cim.ph1.st3`
- `ru.cim.ph2.st1.business-modeler` in `cim.ph2.st1`
- `ru.cim.ph2.st2.business-modeler` in `cim.ph2.st2`
- `ru.cim.ph2.st3.business-modeler` in `cim.ph2.st3`
- `ru.cim.ph3.st1.business-modeler` in `cim.ph3.st1`
- `ru.cim.ph3.st1.ss1.business-modeler` in `cim.ph3.st1.ss1`
- `ru.cim.ph3.st1.ss2.business-modeler` in `cim.ph3.st1.ss2`
- `ru.cim.ph3.st2.business-modeler` in `cim.ph3.st2`
- `ru.cim.ph3.st2.ss1.business-modeler` in `cim.ph3.st2.ss1`
- `ru.cim.ph3.st2.ss2.business-modeler` in `cim.ph3.st2.ss2`
- `ru.cim.ph3.st3.business-modeler` in `cim.ph3.st3`
- `ru.cim.ph3.st3.ss1.business-modeler` in `cim.ph3.st3.ss1`
- `ru.cim.ph3.st3.ss2.business-modeler` in `cim.ph3.st3.ss2`
- `ru.cim.ph3.st3.ss3.business-modeler` in `cim.ph3.st3.ss3`
- `ru.cim.ph4.st1.business-modeler` in `cim.ph4.st1`
- `ru.cim.ph4.st2.business-modeler` in `cim.ph4.st2`
- `ru.cim.ph4.st2.ss1.business-modeler` in `cim.ph4.st2.ss1`
- `ru.cim.ph4.st2.ss2.business-modeler` in `cim.ph4.st2.ss2`
- `ru.cim.ph4.st3.business-modeler` in `cim.ph4.st3`
- `ru.cim.ph5.st2.business-modeler` in `cim.ph5.st2`
- `ru.cim.ph1.business-modeler` in `cim.ph1`
- `ru.cim.ph2.business-modeler` in `cim.ph2`
- `ru.cim.ph3.business-modeler` in `cim.ph3`
- `ru.cim.ph4.business-modeler` in `cim.ph4`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Requirements Engineer

<small>RoleDefinition: `role.requirements-engineer` · 2 RoleUse occurrences</small>

**Responsibilities**

- Requirements, acceptance criteria, governance constraints

**Primary task accountability**

- Formalize requirements
- Apply governance constraints

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- `ru.cim.ph5.st1.requirements-engineer` in `cim.ph5.st1`
- `ru.cim.ph5.requirements-engineer` in `cim.ph5`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Security & Privacy Engineer

<small>RoleDefinition: `role.security-engineer` · 0 RoleUse occurrences</small>

**Responsibilities**

- Threats, privacy, compliance, risk acceptance, and control evidence

**Primary task accountability**

- No primary task in this process scope.

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- This reusable role has no RoleUse occurrence in the current process scope.

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Process Reviewer

<small>RoleDefinition: `role.process-reviewer` · 2 RoleUse occurrences</small>

**Responsibilities**

- EVL gate approval, readiness sign-off

**Primary task accountability**

- Complete trace and readiness
- Review and adapt CIM increment

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- `ru.cim.ph5.st3.process-reviewer` in `cim.ph5.st3`
- `ru.cim.ph5.st4.process-reviewer` in `cim.ph5.st4`

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

- `ru.cim.ph1.st1.method-engineer` in `cim.ph1.st1`

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
