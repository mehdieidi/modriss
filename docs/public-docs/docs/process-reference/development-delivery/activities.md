# Development and Delivery: phases and activities

This page documents the **SPEM process-structure Activity, Phase, and TaskUse** elements used by the Development and Delivery process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A phase establishes a significant lifecycle period and normally ends at a major checkpoint. An activity groups related work within a phase or process component. A TaskUse places reusable task guidance into that process context. Entry and exit conditions describe evidence states; they are not calendar dates.

## Summary

| Phase or activity                                        | SPEM type | Contained by                            | Task uses | Execution        |
| -------------------------------------------------------- | --------- | --------------------------------------- | --------: | ---------------- |
| Inception, Tailoring & Organization                      | Activity  | Process                                 |         0 | defined sequence |
| Product and System Intent                                | Activity  | Inception, Tailoring & Organization     |         2 | defined sequence |
| Feasibility, Serverless Suitability & Economic Viability | Activity  | Inception, Tailoring & Organization     |         3 | defined sequence |
| Situational Process Tailoring                            | Activity  | Inception, Tailoring & Organization     |         2 | defined sequence |
| Team Topology & Coordination                             | Activity  | Inception, Tailoring & Organization     |         2 | defined sequence |
| Quality, Security & Operations Baseline                  | Activity  | Inception, Tailoring & Organization     |         2 | defined sequence |
| Active Product Construction & Evolution                  | Activity  | Process                                 |         0 | defined sequence |
| Frame Increment                                          | Activity  | Active Product Construction & Evolution |         1 | defined sequence |
| CIM Child Process                                        | Activity  | Active Product Construction & Evolution |         1 | defined sequence |
| CIM → PIM Transformation                                 | Activity  | Active Product Construction & Evolution |         1 | defined sequence |
| PIM Child Process                                        | Activity  | Active Product Construction & Evolution |         1 | defined sequence |
| PIM → AWS PSM Transformation                             | Activity  | Active Product Construction & Evolution |         1 | defined sequence |
| PSM Child Process                                        | Activity  | Active Product Construction & Evolution |         1 | defined sequence |
| Model-to-Text Generation                                 | Activity  | Active Product Construction & Evolution |         1 | defined sequence |
| Increment Readiness & Acceptance                         | Activity  | Active Product Construction & Evolution |         2 | defined sequence |
| Release Train Assembly                                   | Activity  | Active Product Construction & Evolution |         2 | defined sequence |
| Progressive Promotion                                    | Activity  | Active Product Construction & Evolution |         2 | defined sequence |
| Release Review                                           | Activity  | Active Product Construction & Evolution |         1 | defined sequence |
| Retire, Migrate & Close                                  | Activity  | Process                                 |         0 | defined sequence |
| Retirement Decision & Plan                               | Activity  | Retire, Migrate & Close                 |         1 | defined sequence |
| Migrate and Decommission                                 | Activity  | Retire, Migrate & Close                 |         3 | defined sequence |
| Closure & Organizational Learning                        | Activity  | Retire, Migrate & Close                 |         1 | defined sequence |

## Detailed activities

## Inception, Tailoring & Organization

<small>Activity · Phase: `e2e.ph0`</small>

Establish the product/system purpose, process profile, team topology, quality baseline, and release strategy before modeling begins.

**Entry conditions**

- A problem, opportunity, or mandated change has an accountable sponsor

**Exit conditions**

- The method profile is approved
- Teams, ownership, dependencies, and decision rights are explicit
- The first increment has a testable outcome hypothesis

**Participating roles**

- Product Owner

### Product and System Intent

<small>Activity · MODRISS::Stage: `e2e.ph0.st1` · contained by **Inception, Tailoring & Organization**</small>

Turn the opportunity into an outcome-oriented product/system charter and release hypothesis.

**Participating roles**

- Product Owner
- Sponsor
- Domain Expert
- Service Owner
- Requirements Engineer
- Solution Architect
- Delivery Lead

**Task uses in this activity**

- **Define product outcomes and success measures** (TaskUse `e2e.ph0.st1.t1`)
- **Establish the initial release and increment hypothesis** (TaskUse `e2e.ph0.st1.t2`)

### Feasibility, Serverless Suitability & Economic Viability

<small>Activity · MODRISS::Stage: `e2e.ph0.st1a` · contained by **Inception, Tailoring & Organization**</small>

Compare solution alternatives and make the G0 investment decision before committing the method and architecture.

**Participating roles**

- Solution Architect
- Product Owner
- Domain Expert
- Cloud Platform Engineer
- Security Engineer
- Quality Engineer
- Service Owner
- FinOps and Cost Analyst
- Sponsor

**Task uses in this activity**

- **Assess feasibility and serverless suitability** (TaskUse `e2e.ph0.st1a.t1`)
- **Establish cost model and budget guardrails** (TaskUse `e2e.ph0.st1a.t2`)
- **Authorize pursue, explore, redirect, or stop at G0** (TaskUse `e2e.ph0.st1a.t3`)

### Situational Process Tailoring

<small>Activity · MODRISS::Stage: `e2e.ph0.st2` · contained by **Inception, Tailoring & Organization**</small>

Select and tailor reusable method content and process activities to the project context without removing essential control objectives.

**Participating roles**

- Method Engineer
- Delivery Lead
- Process Reviewer
- Quality Engineer
- Security Engineer
- Service Owner

**Task uses in this activity**

- **Assess context and process-tailoring risks** (TaskUse `e2e.ph0.st2.t1`)
- **Define the tailored Definition of Ready and Done** (TaskUse `e2e.ph0.st2.t2`)

### Team Topology & Coordination

<small>Activity · MODRISS::Stage: `e2e.ph0.st3` · contained by **Inception, Tailoring & Organization**</small>

Make ownership and coordination explicit for multiple teams working on one integrated model and product.

**Participating roles**

- Delivery Lead
- Product Owner
- Method Engineer

**Task uses in this activity**

- **Define team ownership and interfaces** (TaskUse `e2e.ph0.st3.t1`)
- **Set coordination cadence and escalation paths** (TaskUse `e2e.ph0.st3.t2`)

### Quality, Security & Operations Baseline

<small>Activity · MODRISS::Stage: `e2e.ph0.st4` · contained by **Inception, Tailoring & Organization**</small>

Set cross-cutting quality, security, operational, and release constraints before design detail accumulates.

**Participating roles**

- Quality Engineer
- Security Engineer
- Service Owner
- Records and Data Steward
- Release Engineer
- FinOps and Cost Analyst

**Task uses in this activity**

- **Define quality and security control objectives** (TaskUse `e2e.ph0.st4.t1`)
- **Define operational and release strategy** (TaskUse `e2e.ph0.st4.t2`)

## Active Product Construction & Evolution

<small>Activity · Phase: `e2e.ph1`</small>

Construct and evolve the product through repeatable model-driven increments and releases while the independent Operations and Maintenance Process sustains accepted live baselines.

**Entry conditions**

- G1 authorizes the product, method profile, team topology, and first delivery hypothesis

**Exit conditions**

- Retirement is authorized and no development or release work remains in flight

**Participating roles**

- Delivery Lead

### Frame Increment

<small>Activity · MODRISS::Stage: `e2e.p0.increment-planning` · contained by **Active Product Construction & Evolution**</small>

Select a thin, valuable, testable capability slice and establish its evidence plan.

**Participating roles**

- Product Owner
- Delivery Lead
- Requirements Engineer
- Domain Expert
- FinOps and Cost Analyst

**Task uses in this activity**

- **Plan the vertical increment** (TaskUse `e2e.p0.increment-planning.t1`)

### CIM Child Process

<small>Activity · MODRISS::Stage: `e2e.p1.cim-modeling` · contained by **Active Product Construction & Evolution**</small>

Refine business intent and domain behavior for the selected slice.

**Participating roles**

- Business Modeler
- Requirements Engineer
- Domain Expert
- Product Owner
- Security Engineer

**Task uses in this activity**

- **Run the CIM increment** (TaskUse `e2e.p1.cim-modeling.t1`)

### CIM → PIM Transformation

<small>Activity · MODRISS::Stage: `e2e.p2.cim-to-pim` · contained by **Active Product Construction & Evolution**</small>

Transform the accepted CIM revision into PIM scaffolding while preserving traceability and surfacing manual decisions.

**Participating roles**

- Solution Architect

**Task uses in this activity**

- **Execute and inspect CIM-to-PIM transformation** (TaskUse `e2e.p2.cim-to-pim.t1`)

### PIM Child Process

<small>Activity · MODRISS::Stage: `e2e.p3.pim-refinement` · contained by **Active Product Construction & Evolution**</small>

Refine service boundaries, contracts, data, behavior, integration, assurance, and platform intent.

**Participating roles**

- Solution Architect
- Security Engineer
- Quality Engineer
- Cloud Platform Engineer
- Service Owner
- FinOps and Cost Analyst

**Task uses in this activity**

- **Run the PIM increment** (TaskUse `e2e.p3.pim-refinement.t1`)

### PIM → AWS PSM Transformation

<small>Activity · MODRISS::Stage: `e2e.p4.pim-to-psm` · contained by **Active Product Construction & Evolution**</small>

Map accepted platform-independent architecture to AWS-specific deployment intent.

**Participating roles**

- Cloud Platform Engineer

**Task uses in this activity**

- **Execute and inspect PIM-to-PSM transformation** (TaskUse `e2e.p4.pim-to-psm.t1`)

### PSM Child Process

<small>Activity · MODRISS::Stage: `e2e.p5.psm-refinement` · contained by **Active Product Construction & Evolution**</small>

Refine AWS resources, relationships, security, observability, and deployment readiness for the slice.

**Participating roles**

- Cloud Platform Engineer
- Solution Architect
- Security Engineer
- Quality Engineer
- Release Engineer
- Service Owner
- FinOps and Cost Analyst

**Task uses in this activity**

- **Run the PSM increment** (TaskUse `e2e.p5.psm-refinement.t1`)

### Model-to-Text Generation

<small>Activity · MODRISS::Stage: `e2e.p6.m2t-generation` · contained by **Active Product Construction & Evolution**</small>

Generate a reproducible artifact baseline from the accepted PSM revision.

**Participating roles**

- Cloud Platform Engineer

**Task uses in this activity**

- **Generate and fingerprint the artifact baseline** (TaskUse `e2e.p6.m2t-generation.t1`)

### Increment Readiness & Acceptance

<small>Activity · MODRISS::Stage: `e2e.p7.artifact-completion` · contained by **Active Product Construction & Evolution**</small>

Verify the exact generated candidate, capture acceptance evidence, and decide whether the increment is usable, reworkable, or deferred.

**Participating roles**

- Process Reviewer
- Product Owner
- Quality Engineer
- Security Engineer
- Service Owner
- FinOps and Cost Analyst
- Method Engineer
- Delivery Lead

**Task uses in this activity**

- **Run artifact readiness and accept the increment** (TaskUse `e2e.p7.artifact-completion.t1`)
- **Review the increment and adapt the way of working** (TaskUse `e2e.p7.artifact-completion.t2`)

### Release Train Assembly

<small>Activity · MODRISS::Stage: `e2e.rel.a1` · contained by **Active Product Construction & Evolution**</small>

Compose and verify a release from accepted increment records.

**Participating roles**

- Release Engineer
- Process Reviewer
- Product Owner
- Quality Engineer
- Security Engineer
- Service Owner
- FinOps and Cost Analyst

**Task uses in this activity**

- **Assemble the release candidate** (TaskUse `e2e.rel.a1.t1`)
- **Review release evidence and go/no-go criteria** (TaskUse `e2e.rel.a1.t2`)

### Progressive Promotion

<small>Activity · MODRISS::Stage: `e2e.rel.a2` · contained by **Active Product Construction & Evolution**</small>

Promote the release through environments with controlled observation and rollback readiness.

**Participating roles**

- Release Engineer
- Quality Engineer
- Security Engineer
- Service Owner
- FinOps and Cost Analyst

**Task uses in this activity**

- **Deploy and validate progressively** (TaskUse `e2e.rel.a2.t1`)
- **Complete handover and rollback rehearsal** (TaskUse `e2e.rel.a2.t2`)

### Release Review

<small>Activity · MODRISS::Stage: `e2e.rel.a3` · contained by **Active Product Construction & Evolution**</small>

Inspect release outcomes and feed product, process, and architecture learning back into the backlog.

**Participating roles**

- Product Owner
- Service Owner
- FinOps and Cost Analyst
- Method Engineer
- Delivery Lead

**Task uses in this activity**

- **Review release outcome and update roadmap** (TaskUse `e2e.rel.a3.t1`)

## Retire, Migrate & Close

<small>Activity · Phase: `e2e.ph2`</small>

Retire a product or service safely, preserve required knowledge and evidence, and close the lifecycle with explicit learning.

**Entry conditions**

- A retirement decision is authorized
- Replacement, migration, or end-of-life obligations are known

**Exit conditions**

- Users, data, integrations, environments, and operational ownership are safely transitioned or closed
- Required records and lessons are retained
- Shared G8 closure evidence is accepted and no live release remains

**Participating roles**

- Service Owner

### Retirement Decision & Plan

<small>Activity · MODRISS::Stage: `e2e.ph2.st1` · contained by **Retire, Migrate & Close**</small>

Define why, when, and how the service will be retired while operations continue safely.

**Participating roles**

- Product Owner
- Sponsor
- Service Owner
- Security Engineer
- FinOps and Cost Analyst
- Records and Data Steward

**Task uses in this activity**

- **Approve retirement scope and plan** (TaskUse `e2e.ph2.st1.t1`)

### Migrate and Decommission

<small>Activity · MODRISS::Stage: `e2e.ph2.st2` · contained by **Retire, Migrate & Close**</small>

Move or dispose of data, users, integrations, infrastructure, and operational obligations safely.

**Participating roles**

- Cloud Platform Engineer
- Records and Data Steward
- Security Engineer
- Service Owner
- FinOps and Cost Analyst

**Task uses in this activity**

- **Execute migration and data disposition** (TaskUse `e2e.ph2.st2.t1`)
- **Decommission service, access, and cost surfaces** (TaskUse `e2e.ph2.st2.t2`)
- **Verify records retention and data disposition** (TaskUse `e2e.ph2.st2.t3`)

### Closure & Organizational Learning

<small>Activity · MODRISS::Stage: `e2e.ph2.st3` · contained by **Retire, Migrate & Close**</small>

Assemble Development and Delivery closure evidence, evaluate it with Operations and Maintenance evidence at shared G8, and feed reusable learning into future method profiles and product planning.

**Participating roles**

- Process Reviewer
- Sponsor
- Product Owner
- Service Owner
- FinOps and Cost Analyst
- Records and Data Steward
- Method Engineer

**Task uses in this activity**

- **Complete closure review** (TaskUse `e2e.ph2.st3.t1`)

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
