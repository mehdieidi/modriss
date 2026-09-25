# Operations and Maintenance: roles

This page documents the **SPEM RoleDefinition, RoleUse, and ProcessPerformer** elements used by the Operations and Maintenance process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A RoleDefinition states a responsibility. RoleUse places it in an activity, while ProcessPerformer connects it to a TaskUse as a primary or supporting performer. Roles are hats worn by people or teams. They are not required organization-chart positions.

## Summary

| Role                    | Main responsibility                                                                                          | Primary tasks | Supporting tasks | Role uses |
| ----------------------- | ------------------------------------------------------------------------------------------------------------ | ------------: | ---------------: | --------: |
| Product Owner           | Owns product outcomes, priority, release scope, and acceptance decisions                                     |             0 |                1 |         1 |
| Delivery Lead           | Maintains the integrated process run, dependency board, risks, cadence, and impediment escalation            |             2 |                1 |         3 |
| Quality Engineer        | Owns verification strategy, evidence quality, and quality risks                                              |             1 |                0 |         1 |
| Security Engineer       | Owns security, privacy, threat, and exception evidence                                                       |             0 |                2 |         2 |
| Service Owner           | Accepts operational readiness, SLOs, support ownership, and service outcomes                                 |             3 |                2 |         5 |
| Process Reviewer        | Reviews gates, evidence, decisions, and process improvement                                                  |             1 |                0 |         1 |
| Method Engineer         | Tailors the development process and maintains its alignment with the modeling framework as metamodels change |             0 |                2 |         2 |
| FinOps and Cost Analyst | Owns cost hypotheses, budgets, allocation, anomaly thresholds, forecasts, and unit economics                 |             0 |                2 |         2 |

## Detailed roles

## Product Owner

<small>RoleDefinition: `role.product-owner` · 1 RoleUse occurrence</small>

**Responsibilities**

- Owns product outcomes, priority, release scope, and acceptance decisions

**Primary task accountability**

- No primary task in this process scope.

**Supporting participation**

- Review SLOs, telemetry, cost, and product outcomes

**Role uses**

- `ru.e2e.ops.a1.product-owner` in `e2e.ops.a1`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Delivery Lead

<small>RoleDefinition: `role.delivery-lead` · 3 RoleUse occurrences</small>

**Responsibilities**

- Maintains the integrated process run, dependency board, risks, cadence, and impediment escalation

**Primary task accountability**

- Replenish, pull, and manage Kanban flow
- Assess and route a maintenance change

**Supporting participation**

- Inspect flow, quality, cost, and coordination metrics

**Role uses**

- `ru.e2e.ops.a2.delivery-lead` in `e2e.ops.a2`
- `ru.e2e.ops.a4.delivery-lead` in `e2e.ops.a4`
- `ru.e2e.ops.a5.delivery-lead` in `e2e.ops.a5`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Quality Engineer

<small>RoleDefinition: `role.quality-engineer` · 1 RoleUse occurrence</small>

**Responsibilities**

- Owns verification strategy, evidence quality, and quality risks

**Primary task accountability**

- Perform problem and risk learning

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- `ru.e2e.ops.a3.quality-engineer` in `e2e.ops.a3`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Security Engineer

<small>RoleDefinition: `role.security-engineer` · 2 RoleUse occurrences</small>

**Responsibilities**

- Owns security, privacy, threat, and exception evidence

**Primary task accountability**

- No primary task in this process scope.

**Supporting participation**

- Review SLOs, telemetry, cost, and product outcomes
- Perform problem and risk learning

**Role uses**

- `ru.e2e.ops.a1.security-engineer` in `e2e.ops.a1`
- `ru.e2e.ops.a3.security-engineer` in `e2e.ops.a3`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Service Owner

<small>RoleDefinition: `role.service-owner` · 5 RoleUse occurrences</small>

**Responsibilities**

- Accepts operational readiness, SLOs, support ownership, and service outcomes

**Primary task accountability**

- Review SLOs, telemetry, cost, and product outcomes
- Triage and make service demand ready
- Manage incidents and emergency recovery

**Supporting participation**

- Perform problem and risk learning
- Inspect flow, quality, cost, and coordination metrics

**Role uses**

- `ru.modriss.operations-maintenance.service-owner` in `modriss.operations-maintenance`
- `ru.e2e.ops.a1.service-owner` in `e2e.ops.a1`
- `ru.e2e.ops.a2.service-owner` in `e2e.ops.a2`
- `ru.e2e.ops.a3.service-owner` in `e2e.ops.a3`
- `ru.e2e.ops.a5.service-owner` in `e2e.ops.a5`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Process Reviewer

<small>RoleDefinition: `role.process-reviewer` · 1 RoleUse occurrence</small>

**Responsibilities**

- Reviews gates, evidence, decisions, and process improvement

**Primary task accountability**

- Inspect flow, quality, cost, and coordination metrics

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- `ru.e2e.ops.a5.process-reviewer` in `e2e.ops.a5`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Method Engineer

<small>RoleDefinition: `role.method-engineer` · 2 RoleUse occurrences</small>

**Responsibilities**

- Tailors the development process and maintains its alignment with the modeling framework as metamodels change

**Primary task accountability**

- No primary task in this process scope.

**Supporting participation**

- Perform problem and risk learning
- Inspect flow, quality, cost, and coordination metrics

**Role uses**

- `ru.e2e.ops.a3.method-engineer` in `e2e.ops.a3`
- `ru.e2e.ops.a5.method-engineer` in `e2e.ops.a5`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## FinOps and Cost Analyst

<small>RoleDefinition: `role.finops-cost-analyst` · 2 RoleUse occurrences</small>

**Responsibilities**

- Owns cost hypotheses, budgets, allocation, anomaly thresholds, forecasts, and unit economics

**Primary task accountability**

- No primary task in this process scope.

**Supporting participation**

- Review SLOs, telemetry, cost, and product outcomes
- Inspect flow, quality, cost, and coordination metrics

**Role uses**

- `ru.e2e.ops.a1.finops-cost-analyst` in `e2e.ops.a1`
- `ru.e2e.ops.a5.finops-cost-analyst` in `e2e.ops.a5`

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
