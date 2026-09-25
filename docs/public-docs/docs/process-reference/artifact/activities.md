# Generated-artifact readiness: phases and activities

This page documents the **SPEM process-structure Activity, Phase, and TaskUse** elements used by the Generated-artifact readiness process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A phase establishes a significant lifecycle period and normally ends at a major checkpoint. An activity groups related work within a phase or process component. A TaskUse places reusable task guidance into that process context. Entry and exit conditions describe evidence states; they are not calendar dates.

## Summary

| Phase or activity                           | SPEM type | Contained by                                | Task uses | Execution        |
| ------------------------------------------- | --------- | ------------------------------------------- | --------: | ---------------- |
| Inspect & Baseline Generated Project        | Activity  | Process                                     |         0 | defined sequence |
| Inventory Generated Output                  | Activity  | Inspect & Baseline Generated Project        |         2 | defined sequence |
| Classify Refinements                        | Activity  | Inspect & Baseline Generated Project        |         2 | defined sequence |
| Harden Environment & Delivery Configuration | Activity  | Process                                     |         0 | defined sequence |
| Externalize Environment Configuration       | Activity  | Harden Environment & Delivery Configuration |         2 | defined sequence |
| Harden Delivery Automation                  | Activity  | Harden Environment & Delivery Configuration |         2 | defined sequence |
| Verify Release Candidate                    | Activity  | Process                                     |         0 | defined sequence |
| Build & Static Verification                 | Activity  | Verify Release Candidate                    |         2 | defined sequence |
| Deploy & Exercise Non-Production            | Activity  | Verify Release Candidate                    |         2 | defined sequence |
| Approve Release & Operational Handover      | Activity  | Process                                     |         0 | defined sequence |
| Plan Release & Recovery                     | Activity  | Approve Release & Operational Handover      |         2 | defined sequence |
| Handover & Post-Deployment Validation       | Activity  | Approve Release & Operational Handover      |         2 | defined sequence |

## Detailed activities

## Inspect & Baseline Generated Project

<small>Activity · MODRISS::Stage: `artifact.ph1`</small>

Turn the PSM-generated project into a traceable, reviewed, version-controlled release baseline and identify model changes that must return to PSM.

**Entry conditions**

- Artifacts were generated from an approved PSM
- A target repository and release owner are available

**Exit conditions**

- Generated files are inventoried and committed
- Implementation gaps are classified as artifact refinements or PSM feedback

**Participating roles**

- Application Engineer

### Inventory Generated Output

<small>Activity · MODRISS::SubStage: `artifact.ph1.st1` · contained by **Inspect & Baseline Generated Project**</small>

Understand exactly what was generated and how it maps to the intended deployment.

**Participating roles**

- Application Engineer
- Release Engineer

**Task uses in this activity**

- **Review project structure and generated templates** (TaskUse `artifact.ph1.st1.t1`)
- **Establish source-control and release baseline** (TaskUse `artifact.ph1.st1.t2`)

### Classify Refinements

<small>Activity · MODRISS::SubStage: `artifact.ph1.st2` · contained by **Inspect & Baseline Generated Project**</small>

Keep architecture changes in the model and implementation-specific refinements in the project.

**Participating roles**

- Cloud Platform Engineer
- Application Engineer

**Task uses in this activity**

- **Route structural gaps back to the PSM** (TaskUse `artifact.ph1.st2.t1`)
- **Complete repository hygiene** (TaskUse `artifact.ph1.st2.t2`)

## Harden Environment & Delivery Configuration

<small>Activity · MODRISS::Stage: `artifact.ph2`</small>

Make the generated project safe and repeatable across target environments without embedding secrets or account-specific assumptions.

**Entry conditions**

- Generated baseline is version-controlled
- Target environments and account ownership are known

**Exit conditions**

- Environment contract, least-privilege access, and CI/CD configuration are reviewed

**Participating roles**

- Cloud Platform Engineer

### Externalize Environment Configuration

<small>Activity · MODRISS::SubStage: `artifact.ph2.st1` · contained by **Harden Environment & Delivery Configuration**</small>

Separate deploy-time configuration and secret references from source and templates.

**Participating roles**

- Cloud Platform Engineer
- Security Engineer

**Task uses in this activity**

- **Define parameter and environment contract** (TaskUse `artifact.ph2.st1.t1`)
- **Configure secret references and encryption** (TaskUse `artifact.ph2.st1.t2`)

### Harden Delivery Automation

<small>Activity · MODRISS::SubStage: `artifact.ph2.st2` · contained by **Harden Environment & Delivery Configuration**</small>

Create a controlled path for building and deploying the exact release candidate.

**Participating roles**

- Release Engineer
- Security Engineer

**Task uses in this activity**

- **Review IAM and deployment permissions** (TaskUse `artifact.ph2.st2.t1`)
- **Implement repeatable build and deployment pipeline** (TaskUse `artifact.ph2.st2.t2`)

## Verify Release Candidate

<small>Activity · MODRISS::Stage: `artifact.ph3`</small>

Prove that the exact release candidate is buildable, secure, functional, observable, and deployable before production approval.

**Entry conditions**

- Pipeline and environment contract are available
- Release candidate is versioned

**Exit conditions**

- Required verification evidence is attached to the release candidate
- Defects are resolved or explicitly accepted

**Participating roles**

- Quality Engineer

### Build & Static Verification

<small>Activity · MODRISS::SubStage: `artifact.ph3.st1` · contained by **Verify Release Candidate**</small>

Check packaging, template validity, dependencies, and security posture.

**Participating roles**

- Quality Engineer
- Security Engineer

**Task uses in this activity**

- **Run reproducible build and infrastructure validation** (TaskUse `artifact.ph3.st1.t1`)
- **Assess dependencies and security findings** (TaskUse `artifact.ph3.st1.t2`)

### Deploy & Exercise Non-Production

<small>Activity · MODRISS::SubStage: `artifact.ph3.st2` · contained by **Verify Release Candidate**</small>

Demonstrate the release candidate in a production-like environment.

**Participating roles**

- Quality Engineer
- Cloud Platform Engineer

**Task uses in this activity**

- **Deploy to staging and execute acceptance tests** (TaskUse `artifact.ph3.st2.t1`)
- **Verify observability and operational signals** (TaskUse `artifact.ph3.st2.t2`)

## Approve Release & Operational Handover

<small>Activity · MODRISS::Stage: `artifact.ph4`</small>

Prepare a controlled production release with recovery, ownership, communications, and post-deployment verification defined.

**Entry conditions**

- Verification evidence is complete
- Production deployment window and approvers are known

**Exit conditions**

- Deployment package is approved, recoverable, and handed to operations

**Participating roles**

- Release Engineer

### Plan Release & Recovery

<small>Activity · MODRISS::SubStage: `artifact.ph4.st1` · contained by **Approve Release & Operational Handover**</small>

Make production deployment and failure recovery deliberate and executable.

**Participating roles**

- Release Engineer
- Service Owner

**Task uses in this activity**

- **Create release, rollback, and communication plan** (TaskUse `artifact.ph4.st1.t1`)
- **Obtain production readiness approval** (TaskUse `artifact.ph4.st1.t2`)

### Handover & Post-Deployment Validation

<small>Activity · MODRISS::SubStage: `artifact.ph4.st2` · contained by **Approve Release & Operational Handover**</small>

Ensure the service can be supported after deployment and verify production health immediately after release.

**Participating roles**

- Service Owner
- Release Engineer

**Task uses in this activity**

- **Complete operations handover** (TaskUse `artifact.ph4.st2.t1`)
- **Execute production validation and close release** (TaskUse `artifact.ph4.st2.t2`)

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
