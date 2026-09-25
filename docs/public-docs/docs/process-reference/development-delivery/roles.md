# Development and Delivery: roles

This page documents the **SPEM RoleDefinition, RoleUse, and ProcessPerformer** elements used by the Development and Delivery process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A RoleDefinition states a responsibility. RoleUse places it in an activity, while ProcessPerformer connects it to a TaskUse as a primary or supporting performer. Roles are hats worn by people or teams. They are not required organization-chart positions.

## Summary

| Role                     | Main responsibility                                                                                          | Primary tasks | Supporting tasks | Role uses |
| ------------------------ | ------------------------------------------------------------------------------------------------------------ | ------------: | ---------------: | --------: |
| Sponsor                  | Authorizes investment and accepts pursue, redirect, stop, and final closure decisions                        |             1 |                3 |         4 |
| Product Owner            | Owns product outcomes, priority, release scope, and acceptance decisions                                     |             4 |               10 |        11 |
| Delivery Lead            | Maintains the integrated process run, dependency board, risks, cadence, and impediment escalation            |             2 |                5 |         7 |
| Business Modeler         | Leads CIM discovery and domain-model increments                                                              |             1 |                0 |         1 |
| Requirements Engineer    | Maintains requirements, acceptance criteria, and change impact evidence                                      |             1 |                2 |         3 |
| Domain Expert            | Validates business language, rules, and operational fit                                                      |             0 |                5 |         4 |
| Solution Architect       | Leads PIM architecture, contracts, integration, and cross-level decisions                                    |             3 |                4 |         5 |
| Cloud Platform Engineer  | Leads AWS PSM, generation, environments, and platform automation                                             |             4 |                5 |         6 |
| Quality Engineer         | Owns verification strategy, evidence quality, and quality risks                                              |             0 |                8 |         8 |
| Security Engineer        | Owns security, privacy, threat, and exception evidence                                                       |             1 |               15 |        11 |
| Release Engineer         | Owns release candidates, promotion, rollback, and deployment records                                         |             2 |                3 |         4 |
| Service Owner            | Accepts operational readiness, SLOs, support ownership, and service outcomes                                 |             3 |               16 |        14 |
| Process Reviewer         | Reviews gates, evidence, decisions, and process improvement                                                  |             4 |                2 |         4 |
| Method Engineer          | Tailors the development process and maintains its alignment with the modeling framework as metamodels change |             2 |                4 |         5 |
| FinOps and Cost Analyst  | Owns cost hypotheses, budgets, allocation, anomaly thresholds, forecasts, and unit economics                 |             1 |               14 |        12 |
| Records and Data Steward | Owns retention, export, deletion, disposition evidence, and records custody through closure                  |             1 |                6 |         4 |

## Detailed roles

## Sponsor

<small>RoleDefinition: `role.sponsor` · 4 RoleUse occurrences</small>

**Responsibilities**

- Authorizes investment and accepts pursue, redirect, stop, and final closure decisions

**Primary task accountability**

- Authorize pursue, explore, redirect, or stop at G0

**Supporting participation**

- Define product outcomes and success measures
- Approve retirement scope and plan
- Complete closure review

**Role uses**

- `ru.e2e.ph0.st1.sponsor` in `e2e.ph0.st1`
- `ru.e2e.ph0.st1a.sponsor` in `e2e.ph0.st1a`
- `ru.e2e.ph2.st1.sponsor` in `e2e.ph2.st1`
- `ru.e2e.ph2.st3.sponsor` in `e2e.ph2.st3`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Product Owner

<small>RoleDefinition: `role.product-owner` · 11 RoleUse occurrences</small>

**Responsibilities**

- Owns product outcomes, priority, release scope, and acceptance decisions

**Primary task accountability**

- Define product outcomes and success measures
- Plan the vertical increment
- Review release outcome and update roadmap
- Approve retirement scope and plan

**Supporting participation**

- Establish the initial release and increment hypothesis
- Assess feasibility and serverless suitability
- Establish cost model and budget guardrails
- Authorize pursue, explore, redirect, or stop at G0
- Define team ownership and interfaces
- Run the CIM increment
- Run artifact readiness and accept the increment
- Review the increment and adapt the way of working
- Review release evidence and go/no-go criteria
- Complete closure review

**Role uses**

- `ru.e2e.ph0.st1.product-owner` in `e2e.ph0.st1`
- `ru.e2e.ph0.st1a.product-owner` in `e2e.ph0.st1a`
- `ru.e2e.ph0.st3.product-owner` in `e2e.ph0.st3`
- `ru.e2e.p0.increment-planning.product-owner` in `e2e.p0.increment-planning`
- `ru.e2e.p1.cim-modeling.product-owner` in `e2e.p1.cim-modeling`
- `ru.e2e.p7.artifact-completion.product-owner` in `e2e.p7.artifact-completion`
- `ru.e2e.rel.a1.product-owner` in `e2e.rel.a1`
- `ru.e2e.rel.a3.product-owner` in `e2e.rel.a3`
- `ru.e2e.ph2.st1.product-owner` in `e2e.ph2.st1`
- `ru.e2e.ph2.st3.product-owner` in `e2e.ph2.st3`
- `ru.e2e.ph0.product-owner` in `e2e.ph0`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Delivery Lead

<small>RoleDefinition: `role.delivery-lead` · 7 RoleUse occurrences</small>

**Responsibilities**

- Maintains the integrated process run, dependency board, risks, cadence, and impediment escalation

**Primary task accountability**

- Define team ownership and interfaces
- Set coordination cadence and escalation paths

**Supporting participation**

- Establish the initial release and increment hypothesis
- Assess context and process-tailoring risks
- Plan the vertical increment
- Review the increment and adapt the way of working
- Review release outcome and update roadmap

**Role uses**

- `ru.e2e.ph0.st1.delivery-lead` in `e2e.ph0.st1`
- `ru.e2e.ph0.st2.delivery-lead` in `e2e.ph0.st2`
- `ru.e2e.ph0.st3.delivery-lead` in `e2e.ph0.st3`
- `ru.e2e.p0.increment-planning.delivery-lead` in `e2e.p0.increment-planning`
- `ru.e2e.p7.artifact-completion.delivery-lead` in `e2e.p7.artifact-completion`
- `ru.e2e.rel.a3.delivery-lead` in `e2e.rel.a3`
- `ru.e2e.ph1.delivery-lead` in `e2e.ph1`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Business Modeler

<small>RoleDefinition: `role.business-modeler` · 1 RoleUse occurrence</small>

**Responsibilities**

- Leads CIM discovery and domain-model increments

**Primary task accountability**

- Run the CIM increment

**Supporting participation**

- No supporting task in this process scope.

**Role uses**

- `ru.e2e.p1.cim-modeling.business-modeler` in `e2e.p1.cim-modeling`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Requirements Engineer

<small>RoleDefinition: `role.requirements-engineer` · 3 RoleUse occurrences</small>

**Responsibilities**

- Maintains requirements, acceptance criteria, and change impact evidence

**Primary task accountability**

- Establish the initial release and increment hypothesis

**Supporting participation**

- Plan the vertical increment
- Run the CIM increment

**Role uses**

- `ru.e2e.ph0.st1.requirements-engineer` in `e2e.ph0.st1`
- `ru.e2e.p0.increment-planning.requirements-engineer` in `e2e.p0.increment-planning`
- `ru.e2e.p1.cim-modeling.requirements-engineer` in `e2e.p1.cim-modeling`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Domain Expert

<small>RoleDefinition: `role.domain-expert` · 4 RoleUse occurrences</small>

**Responsibilities**

- Validates business language, rules, and operational fit

**Primary task accountability**

- No primary task in this process scope.

**Supporting participation**

- Define product outcomes and success measures
- Establish the initial release and increment hypothesis
- Assess feasibility and serverless suitability
- Plan the vertical increment
- Run the CIM increment

**Role uses**

- `ru.e2e.ph0.st1.domain-expert` in `e2e.ph0.st1`
- `ru.e2e.ph0.st1a.domain-expert` in `e2e.ph0.st1a`
- `ru.e2e.p0.increment-planning.domain-expert` in `e2e.p0.increment-planning`
- `ru.e2e.p1.cim-modeling.domain-expert` in `e2e.p1.cim-modeling`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Solution Architect

<small>RoleDefinition: `role.solution-architect` · 5 RoleUse occurrences</small>

**Responsibilities**

- Leads PIM architecture, contracts, integration, and cross-level decisions

**Primary task accountability**

- Assess feasibility and serverless suitability
- Execute and inspect CIM-to-PIM transformation
- Run the PIM increment

**Supporting participation**

- Establish the initial release and increment hypothesis
- Establish cost model and budget guardrails
- Authorize pursue, explore, redirect, or stop at G0
- Run the PSM increment

**Role uses**

- `ru.e2e.ph0.st1.solution-architect` in `e2e.ph0.st1`
- `ru.e2e.ph0.st1a.solution-architect` in `e2e.ph0.st1a`
- `ru.e2e.p2.cim-to-pim.solution-architect` in `e2e.p2.cim-to-pim`
- `ru.e2e.p3.pim-refinement.solution-architect` in `e2e.p3.pim-refinement`
- `ru.e2e.p5.psm-refinement.solution-architect` in `e2e.p5.psm-refinement`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Cloud Platform Engineer

<small>RoleDefinition: `role.cloud-platform-engineer` · 6 RoleUse occurrences</small>

**Responsibilities**

- Leads AWS PSM, generation, environments, and platform automation

**Primary task accountability**

- Execute and inspect PIM-to-PSM transformation
- Run the PSM increment
- Generate and fingerprint the artifact baseline
- Execute migration and data disposition

**Supporting participation**

- Assess feasibility and serverless suitability
- Establish cost model and budget guardrails
- Run the PIM increment
- Decommission service, access, and cost surfaces
- Verify records retention and data disposition

**Role uses**

- `ru.e2e.ph0.st1a.cloud-platform-engineer` in `e2e.ph0.st1a`
- `ru.e2e.p3.pim-refinement.cloud-platform-engineer` in `e2e.p3.pim-refinement`
- `ru.e2e.p4.pim-to-psm.cloud-platform-engineer` in `e2e.p4.pim-to-psm`
- `ru.e2e.p5.psm-refinement.cloud-platform-engineer` in `e2e.p5.psm-refinement`
- `ru.e2e.p6.m2t-generation.cloud-platform-engineer` in `e2e.p6.m2t-generation`
- `ru.e2e.ph2.st2.cloud-platform-engineer` in `e2e.ph2.st2`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Quality Engineer

<small>RoleDefinition: `role.quality-engineer` · 8 RoleUse occurrences</small>

**Responsibilities**

- Owns verification strategy, evidence quality, and quality risks

**Primary task accountability**

- No primary task in this process scope.

**Supporting participation**

- Assess feasibility and serverless suitability
- Define the tailored Definition of Ready and Done
- Define quality and security control objectives
- Run the PIM increment
- Run the PSM increment
- Run artifact readiness and accept the increment
- Review release evidence and go/no-go criteria
- Deploy and validate progressively

**Role uses**

- `ru.e2e.ph0.st1a.quality-engineer` in `e2e.ph0.st1a`
- `ru.e2e.ph0.st2.quality-engineer` in `e2e.ph0.st2`
- `ru.e2e.ph0.st4.quality-engineer` in `e2e.ph0.st4`
- `ru.e2e.p3.pim-refinement.quality-engineer` in `e2e.p3.pim-refinement`
- `ru.e2e.p5.psm-refinement.quality-engineer` in `e2e.p5.psm-refinement`
- `ru.e2e.p7.artifact-completion.quality-engineer` in `e2e.p7.artifact-completion`
- `ru.e2e.rel.a1.quality-engineer` in `e2e.rel.a1`
- `ru.e2e.rel.a2.quality-engineer` in `e2e.rel.a2`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Security Engineer

<small>RoleDefinition: `role.security-engineer` · 11 RoleUse occurrences</small>

**Responsibilities**

- Owns security, privacy, threat, and exception evidence

**Primary task accountability**

- Define quality and security control objectives

**Supporting participation**

- Assess feasibility and serverless suitability
- Authorize pursue, explore, redirect, or stop at G0
- Define the tailored Definition of Ready and Done
- Define operational and release strategy
- Run the CIM increment
- Run the PIM increment
- Run the PSM increment
- Run artifact readiness and accept the increment
- Review release evidence and go/no-go criteria
- Deploy and validate progressively
- Complete handover and rollback rehearsal
- Approve retirement scope and plan
- Execute migration and data disposition
- Decommission service, access, and cost surfaces
- Verify records retention and data disposition

**Role uses**

- `ru.e2e.ph0.st1a.security-engineer` in `e2e.ph0.st1a`
- `ru.e2e.ph0.st2.security-engineer` in `e2e.ph0.st2`
- `ru.e2e.ph0.st4.security-engineer` in `e2e.ph0.st4`
- `ru.e2e.p1.cim-modeling.security-engineer` in `e2e.p1.cim-modeling`
- `ru.e2e.p3.pim-refinement.security-engineer` in `e2e.p3.pim-refinement`
- `ru.e2e.p5.psm-refinement.security-engineer` in `e2e.p5.psm-refinement`
- `ru.e2e.p7.artifact-completion.security-engineer` in `e2e.p7.artifact-completion`
- `ru.e2e.rel.a1.security-engineer` in `e2e.rel.a1`
- `ru.e2e.rel.a2.security-engineer` in `e2e.rel.a2`
- `ru.e2e.ph2.st1.security-engineer` in `e2e.ph2.st1`
- `ru.e2e.ph2.st2.security-engineer` in `e2e.ph2.st2`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Release Engineer

<small>RoleDefinition: `role.release-engineer` · 4 RoleUse occurrences</small>

**Responsibilities**

- Owns release candidates, promotion, rollback, and deployment records

**Primary task accountability**

- Assemble the release candidate
- Deploy and validate progressively

**Supporting participation**

- Define operational and release strategy
- Run the PSM increment
- Complete handover and rollback rehearsal

**Role uses**

- `ru.e2e.ph0.st4.release-engineer` in `e2e.ph0.st4`
- `ru.e2e.p5.psm-refinement.release-engineer` in `e2e.p5.psm-refinement`
- `ru.e2e.rel.a1.release-engineer` in `e2e.rel.a1`
- `ru.e2e.rel.a2.release-engineer` in `e2e.rel.a2`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Service Owner

<small>RoleDefinition: `role.service-owner` · 14 RoleUse occurrences</small>

**Responsibilities**

- Accepts operational readiness, SLOs, support ownership, and service outcomes

**Primary task accountability**

- Define operational and release strategy
- Complete handover and rollback rehearsal
- Decommission service, access, and cost surfaces

**Supporting participation**

- Define product outcomes and success measures
- Assess feasibility and serverless suitability
- Establish cost model and budget guardrails
- Authorize pursue, explore, redirect, or stop at G0
- Define the tailored Definition of Ready and Done
- Define quality and security control objectives
- Run the PIM increment
- Run the PSM increment
- Run artifact readiness and accept the increment
- Review release evidence and go/no-go criteria
- Deploy and validate progressively
- Review release outcome and update roadmap
- Approve retirement scope and plan
- Execute migration and data disposition
- Verify records retention and data disposition
- Complete closure review

**Role uses**

- `ru.e2e.ph0.st1.service-owner` in `e2e.ph0.st1`
- `ru.e2e.ph0.st1a.service-owner` in `e2e.ph0.st1a`
- `ru.e2e.ph0.st2.service-owner` in `e2e.ph0.st2`
- `ru.e2e.ph0.st4.service-owner` in `e2e.ph0.st4`
- `ru.e2e.p3.pim-refinement.service-owner` in `e2e.p3.pim-refinement`
- `ru.e2e.p5.psm-refinement.service-owner` in `e2e.p5.psm-refinement`
- `ru.e2e.p7.artifact-completion.service-owner` in `e2e.p7.artifact-completion`
- `ru.e2e.rel.a1.service-owner` in `e2e.rel.a1`
- `ru.e2e.rel.a2.service-owner` in `e2e.rel.a2`
- `ru.e2e.rel.a3.service-owner` in `e2e.rel.a3`
- `ru.e2e.ph2.st1.service-owner` in `e2e.ph2.st1`
- `ru.e2e.ph2.st2.service-owner` in `e2e.ph2.st2`
- `ru.e2e.ph2.st3.service-owner` in `e2e.ph2.st3`
- `ru.e2e.ph2.service-owner` in `e2e.ph2`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Process Reviewer

<small>RoleDefinition: `role.process-reviewer` · 4 RoleUse occurrences</small>

**Responsibilities**

- Reviews gates, evidence, decisions, and process improvement

**Primary task accountability**

- Define the tailored Definition of Ready and Done
- Run artifact readiness and accept the increment
- Review release evidence and go/no-go criteria
- Complete closure review

**Supporting participation**

- Assess context and process-tailoring risks
- Review the increment and adapt the way of working

**Role uses**

- `ru.e2e.ph0.st2.process-reviewer` in `e2e.ph0.st2`
- `ru.e2e.p7.artifact-completion.process-reviewer` in `e2e.p7.artifact-completion`
- `ru.e2e.rel.a1.process-reviewer` in `e2e.rel.a1`
- `ru.e2e.ph2.st3.process-reviewer` in `e2e.ph2.st3`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Method Engineer

<small>RoleDefinition: `role.method-engineer` · 5 RoleUse occurrences</small>

**Responsibilities**

- Tailors the development process and maintains its alignment with the modeling framework as metamodels change

**Primary task accountability**

- Assess context and process-tailoring risks
- Review the increment and adapt the way of working

**Supporting participation**

- Define the tailored Definition of Ready and Done
- Define team ownership and interfaces
- Review release outcome and update roadmap
- Complete closure review

**Role uses**

- `ru.e2e.ph0.st2.method-engineer` in `e2e.ph0.st2`
- `ru.e2e.ph0.st3.method-engineer` in `e2e.ph0.st3`
- `ru.e2e.p7.artifact-completion.method-engineer` in `e2e.p7.artifact-completion`
- `ru.e2e.rel.a3.method-engineer` in `e2e.rel.a3`
- `ru.e2e.ph2.st3.method-engineer` in `e2e.ph2.st3`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## FinOps and Cost Analyst

<small>RoleDefinition: `role.finops-cost-analyst` · 12 RoleUse occurrences</small>

**Responsibilities**

- Owns cost hypotheses, budgets, allocation, anomaly thresholds, forecasts, and unit economics

**Primary task accountability**

- Establish cost model and budget guardrails

**Supporting participation**

- Assess feasibility and serverless suitability
- Authorize pursue, explore, redirect, or stop at G0
- Define operational and release strategy
- Plan the vertical increment
- Run the PIM increment
- Run the PSM increment
- Run artifact readiness and accept the increment
- Review release evidence and go/no-go criteria
- Deploy and validate progressively
- Complete handover and rollback rehearsal
- Review release outcome and update roadmap
- Approve retirement scope and plan
- Decommission service, access, and cost surfaces
- Complete closure review

**Role uses**

- `ru.e2e.ph0.st1a.finops-cost-analyst` in `e2e.ph0.st1a`
- `ru.e2e.ph0.st4.finops-cost-analyst` in `e2e.ph0.st4`
- `ru.e2e.p0.increment-planning.finops-cost-analyst` in `e2e.p0.increment-planning`
- `ru.e2e.p3.pim-refinement.finops-cost-analyst` in `e2e.p3.pim-refinement`
- `ru.e2e.p5.psm-refinement.finops-cost-analyst` in `e2e.p5.psm-refinement`
- `ru.e2e.p7.artifact-completion.finops-cost-analyst` in `e2e.p7.artifact-completion`
- `ru.e2e.rel.a1.finops-cost-analyst` in `e2e.rel.a1`
- `ru.e2e.rel.a2.finops-cost-analyst` in `e2e.rel.a2`
- `ru.e2e.rel.a3.finops-cost-analyst` in `e2e.rel.a3`
- `ru.e2e.ph2.st1.finops-cost-analyst` in `e2e.ph2.st1`
- `ru.e2e.ph2.st2.finops-cost-analyst` in `e2e.ph2.st2`
- `ru.e2e.ph2.st3.finops-cost-analyst` in `e2e.ph2.st3`

The method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.

## Records and Data Steward

<small>RoleDefinition: `role.records-data-steward` · 4 RoleUse occurrences</small>

**Responsibilities**

- Owns retention, export, deletion, disposition evidence, and records custody through closure

**Primary task accountability**

- Verify records retention and data disposition

**Supporting participation**

- Define quality and security control objectives
- Define operational and release strategy
- Approve retirement scope and plan
- Execute migration and data disposition
- Decommission service, access, and cost surfaces
- Complete closure review

**Role uses**

- `ru.e2e.ph0.st4.records-data-steward` in `e2e.ph0.st4`
- `ru.e2e.ph2.st1.records-data-steward` in `e2e.ph2.st1`
- `ru.e2e.ph2.st2.records-data-steward` in `e2e.ph2.st2`
- `ru.e2e.ph2.st3.records-data-steward` in `e2e.ph2.st3`

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
