# Generated-artifact readiness: tasks

This page documents the **SPEM TaskDefinition and TaskUse** elements used by the Generated-artifact readiness process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A TaskDefinition describes reusable work. A TaskUse places that definition inside an activity and binds it to process performers and work-product uses. The same responsibility may appear in another process context with different inputs, outputs, or selected steps.

## Summary

| Task                                                 | Primary role            | Inputs | Outputs | Task uses |
| ---------------------------------------------------- | ----------------------- | -----: | ------: | --------: |
| Review project structure and generated templates     | Application Engineer    |      0 |       1 |         1 |
| Establish source-control and release baseline        | Release Engineer        |      1 |       1 |         1 |
| Route structural gaps back to the PSM                | Cloud Platform Engineer |      1 |       1 |         1 |
| Complete repository hygiene                          | Application Engineer    |      1 |       1 |         1 |
| Define parameter and environment contract            | Cloud Platform Engineer |      1 |       1 |         1 |
| Configure secret references and encryption           | Security Engineer       |      2 |       2 |         1 |
| Review IAM and deployment permissions                | Security Engineer       |      3 |       1 |         1 |
| Implement repeatable build and deployment pipeline   | Release Engineer        |      3 |       1 |         1 |
| Run reproducible build and infrastructure validation | Quality Engineer        |      3 |       1 |         1 |
| Assess dependencies and security findings            | Security Engineer       |      4 |       2 |         1 |
| Deploy to staging and execute acceptance tests       | Quality Engineer        |      4 |       1 |         1 |
| Verify observability and operational signals         | Cloud Platform Engineer |      3 |       2 |         1 |
| Create release, rollback, and communication plan     | Release Engineer        |      4 |       1 |         1 |
| Obtain production readiness approval                 | Service Owner           |      4 |       2 |         1 |
| Complete operations handover                         | Service Owner           |      5 |       1 |         1 |
| Execute production validation and close release      | Release Engineer        |      5 |       2 |         1 |

## Detailed tasks

## Review project structure and generated templates

<small>Task definition: `task.artifact.ph1.st1.t1`</small>

Review project structure and generated templates This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Application Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- No formal input work product is required. The task still uses the accepted scope, decisions, and project context.

**How to perform the task**

1. Inspect template, source, configuration, documentation, and pipeline files in the artifact explorer.
2. Identify entry points, Lambda handlers, infrastructure resources, parameters, outputs, and generated deployment instructions.
3. Compare the generated structure with the PSM deployable slice and record missing or unexpected components.

**Outputs**

- Generated Project Baseline

**Ready to start when**

- Artifact explorer contains generated files

**Complete when**

- Project inventory and PSM-to-file trace are recorded

**Checks and evidence**

- Every deployable resource has an identifiable template or source owner

**Uses in this process**

- `artifact.ph1.st1.t1` in **Inventory Generated Output**

## Establish source-control and release baseline

<small>Task definition: `task.artifact.ph1.st1.t2`</small>

Establish source-control and release baseline This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Release Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Generated Project Baseline

**How to perform the task**

1. Create a dedicated branch or repository according to team policy.
2. Commit the unmodified generated baseline with generator, PSM revision, and generation timestamp recorded.
3. Tag or otherwise identify the baseline so later refinements are reviewable.

**Outputs**

- Generated Project Baseline

**Ready to start when**

- Generated output has been reviewed

**Complete when**

- Baseline is versioned and reproducible

**Checks and evidence**

- No unreviewed local-only changes remain

**Uses in this process**

- `artifact.ph1.st1.t2` in **Inventory Generated Output**

## Route structural gaps back to the PSM

<small>Task definition: `task.artifact.ph1.st2.t1`</small>

Route structural gaps back to the PSM This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Generated Project Baseline

**How to perform the task**

1. Classify each gap as a PSM modeling defect, generator limitation, or legitimate implementation refinement.
2. For missing resources, permissions, integrations, or policies, update and validate the PSM before regenerating artifacts.
3. Record the reason for any approved hand-maintained template extension.

**Outputs**

- Generated Project Baseline

**Ready to start when**

- Inventory differences are known

**Complete when**

- PSM feedback and local refinements have named owners

**Checks and evidence**

- Architecture decisions are not hidden only in generated files

**Uses in this process**

- `artifact.ph1.st2.t1` in **Classify Refinements**

## Complete repository hygiene

<small>Task definition: `task.artifact.ph1.st2.t2`</small>

Complete repository hygiene This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Application Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Generated Project Baseline

**How to perform the task**

1. Add repository documentation, ownership, license, ignore rules, and contribution instructions required by the organization.
2. Confirm generated documentation names the service, supported environments, and deployment prerequisites.
3. Ensure build outputs and local credentials are excluded from source control.

**Outputs**

- Generated Project Baseline

**Ready to start when**

- Baseline branch exists

**Complete when**

- Repository can be safely shared and reviewed

**Checks and evidence**

- Repository contains no generated build output or credential material

**Uses in this process**

- `artifact.ph1.st2.t2` in **Classify Refinements**

## Define parameter and environment contract

<small>Task definition: `task.artifact.ph2.st1.t1`</small>

Define parameter and environment contract This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Generated Project Baseline

**How to perform the task**

1. List required parameters, outputs, domains, regions, account IDs, tags, feature flags, and external endpoints.
2. Provide validated values or references for development, test, staging, and production.
3. Document defaults, allowed ranges, and the owner for every environment-specific value.

**Outputs**

- Environment Configuration Contract

**Ready to start when**

- Deployment templates and runtime configuration are known

**Complete when**

- All environment values have a documented source and owner

**Checks and evidence**

- Production values are not copied into non-production configuration

**Uses in this process**

- `artifact.ph2.st1.t1` in **Externalize Environment Configuration**

## Configure secret references and encryption

<small>Task definition: `task.artifact.ph2.st1.t2`</small>

Configure secret references and encryption This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Security Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Generated Project Baseline
- Environment Configuration Contract

**How to perform the task**

1. Replace inline credentials, tokens, and sensitive defaults with approved secret-manager or parameter-store references.
2. Verify encryption keys, rotation expectations, and access paths for each secret or protected data store.
3. Document bootstrap steps that create secrets before deployment.

**Outputs**

- Environment Configuration Contract
- Security Review Record

**Ready to start when**

- Parameter contract is drafted

**Complete when**

- Secrets are externalized and bootstrap steps are reproducible

**Checks and evidence**

- No secret value is committed to source control or template defaults

**Uses in this process**

- `artifact.ph2.st1.t2` in **Externalize Environment Configuration**

## Review IAM and deployment permissions

<small>Task definition: `task.artifact.ph2.st2.t1`</small>

Review IAM and deployment permissions This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Security Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Environment Configuration Contract
- Security Review Record
- Generated Project Baseline

**How to perform the task**

1. Review execution roles, resource policies, and CI/CD identities against least-privilege requirements.
2. Separate deployment permissions from application runtime permissions.
3. Record exceptions, approval owners, and expiration dates for elevated access.

**Outputs**

- Security Review Record

**Ready to start when**

- Infrastructure and delivery identities are defined

**Complete when**

- Required permissions are approved and bounded

**Checks and evidence**

- No wildcard permission remains without documented justification

**Uses in this process**

- `artifact.ph2.st2.t1` in **Harden Delivery Automation**

## Implement repeatable build and deployment pipeline

<small>Task definition: `task.artifact.ph2.st2.t2`</small>

Implement repeatable build and deployment pipeline This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Release Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Security Review Record
- Environment Configuration Contract
- Generated Project Baseline

**How to perform the task**

1. Configure the approved CI/CD workflow to restore dependencies, build, package, validate, and deploy the artifact.
2. Pin or record runtime, build-image, and dependency-resolution versions.
3. Require review and controlled promotion between environments.

**Outputs**

- Environment Configuration Contract

**Ready to start when**

- Environment contract and deployment identities are available

**Complete when**

- A pipeline can produce a uniquely identifiable release candidate

**Checks and evidence**

- Build and deploy steps do not depend on a developer workstation

**Uses in this process**

- `artifact.ph2.st2.t2` in **Harden Delivery Automation**

## Run reproducible build and infrastructure validation

<small>Task definition: `task.artifact.ph3.st1.t1`</small>

Run reproducible build and infrastructure validation This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Quality Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Environment Configuration Contract
- Security Review Record
- Generated Project Baseline

**How to perform the task**

1. Build and package the project using the delivery pipeline.
2. Run the project's infrastructure/template validation and capture the results.
3. Verify generated deployment artifacts match the tagged source revision.

**Outputs**

- Verification Evidence

**Ready to start when**

- Versioned release candidate is available

**Complete when**

- Build and template validation evidence is retained

**Checks and evidence**

- Build succeeds from a clean environment

**Uses in this process**

- `artifact.ph3.st1.t1` in **Build & Static Verification**

## Assess dependencies and security findings

<small>Task definition: `task.artifact.ph3.st1.t2`</small>

Assess dependencies and security findings This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Security Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Verification Evidence
- Security Review Record
- Environment Configuration Contract
- Generated Project Baseline

**How to perform the task**

1. Run approved dependency, secret, and infrastructure security scans.
2. Triage findings by severity and exploitability; fix blockers or obtain time-bound risk acceptance.
3. Verify logging, encryption, retention, and data-handling controls meet the PSM intent.

**Outputs**

- Security Review Record
- Verification Evidence

**Ready to start when**

- Packaged release candidate exists

**Complete when**

- Security findings are resolved or formally accepted

**Checks and evidence**

- No unresolved critical finding proceeds without explicit risk acceptance

**Uses in this process**

- `artifact.ph3.st1.t2` in **Build & Static Verification**

## Deploy to staging and execute acceptance tests

<small>Task definition: `task.artifact.ph3.st2.t1`</small>

Deploy to staging and execute acceptance tests This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Quality Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Security Review Record
- Verification Evidence
- Environment Configuration Contract
- Generated Project Baseline

**How to perform the task**

1. Deploy the exact candidate through the pipeline to a controlled non-production environment.
2. Execute smoke, integration, contract, and business acceptance tests for the deployable slice.
3. Record test data constraints, results, defects, and any accepted deviations.

**Outputs**

- Verification Evidence

**Ready to start when**

- Build and security checks pass

**Complete when**

- Acceptance evidence covers critical user and integration paths

**Checks and evidence**

- Tests target the deployed candidate as well as local code

**Uses in this process**

- `artifact.ph3.st2.t1` in **Deploy & Exercise Non-Production**

## Verify observability and operational signals

<small>Task definition: `task.artifact.ph3.st2.t2`</small>

Verify observability and operational signals This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Cloud Platform Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Verification Evidence
- Environment Configuration Contract
- Security Review Record

**How to perform the task**

1. Confirm logs, metrics, traces, dashboards, and alarms are emitted for critical paths.
2. Trigger representative failure and recovery scenarios to verify alert routing and diagnostic context.
3. Set practical thresholds and identify the team that responds to each alert.

**Outputs**

- Verification Evidence
- Operations Handover Pack

**Ready to start when**

- Candidate is deployed to staging

**Complete when**

- Critical operational signals and alert ownership are verified

**Checks and evidence**

- An operator can detect and diagnose a failed critical path

**Uses in this process**

- `artifact.ph3.st2.t2` in **Deploy & Exercise Non-Production**

## Create release, rollback, and communication plan

<small>Task definition: `task.artifact.ph4.st1.t1`</small>

Create release, rollback, and communication plan This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Release Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Verification Evidence
- Operations Handover Pack
- Security Review Record
- Environment Configuration Contract

**How to perform the task**

1. Define release scope, version, deployment sequence, approvals, maintenance window, and stakeholder communications.
2. Document rollback triggers, steps, data considerations, decision owner, and expected recovery time.
3. Rehearse rollback or recovery in non-production when the change affects data, interfaces, or critical availability.

**Outputs**

- Release & Rollback Plan

**Ready to start when**

- Staging evidence is accepted

**Complete when**

- Release and rollback plan are approved by accountable owners

**Checks and evidence**

- Rollback does not rely on undocumented manual knowledge

**Uses in this process**

- `artifact.ph4.st1.t1` in **Plan Release & Recovery**

## Obtain production readiness approval

<small>Task definition: `task.artifact.ph4.st1.t2`</small>

Obtain production readiness approval This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Service Owner**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Release & Rollback Plan
- Verification Evidence
- Security Review Record
- Operations Handover Pack

**How to perform the task**

1. Review release scope, risk, verification evidence, security findings, operational readiness, and rollback posture.
2. Capture go/no-go decision, approvers, constraints, and follow-up actions.
3. Confirm support and business stakeholders understand the release window and success criteria.

**Outputs**

- Release & Rollback Plan
- Security Review Record

**Ready to start when**

- Release plan and evidence are available

**Complete when**

- Go/no-go decision is recorded

**Checks and evidence**

- Production deployment has an accountable approver

**Uses in this process**

- `artifact.ph4.st1.t2` in **Plan Release & Recovery**

## Complete operations handover

<small>Task definition: `task.artifact.ph4.st2.t1`</small>

Complete operations handover This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Service Owner**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Release & Rollback Plan
- Security Review Record
- Verification Evidence
- Environment Configuration Contract
- Generated Project Baseline

**How to perform the task**

1. Publish runbooks for deployment, rollback, common incidents, access requests, and escalation.
2. Assign service ownership, on-call coverage, dashboards, alerts, service objectives, and support contacts.
3. Transfer repository, pipeline, and environment access according to the operating model.

**Outputs**

- Operations Handover Pack

**Ready to start when**

- Release approval exists

**Complete when**

- Operations team can support the deployed service

**Checks and evidence**

- Runbooks identify owners and escalation paths

**Uses in this process**

- `artifact.ph4.st2.t1` in **Handover & Post-Deployment Validation**

## Execute production validation and close release

<small>Task definition: `task.artifact.ph4.st2.t2`</small>

Execute production validation and close release This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **Release Engineer**. The approved method profile may assign this responsibility to a person who holds several roles.

**Inputs**

- Operations Handover Pack
- Release & Rollback Plan
- Verification Evidence
- Security Review Record
- Environment Configuration Contract

**How to perform the task**

1. Deploy the approved release candidate using the documented pipeline and plan.
2. Perform post-deployment smoke checks, monitor health signals, and confirm business and technical success criteria.
3. Close the release or invoke rollback; record lessons and feed recurring issues to the PSM or generator backlog.

**Outputs**

- Verification Evidence
- Operations Handover Pack

**Ready to start when**

- Approved release window is open

**Complete when**

- Production outcome and follow-up actions are recorded

**Checks and evidence**

- Production health is checked before the release is closed

**Uses in this process**

- `artifact.ph4.st2.t2` in **Handover & Post-Deployment Validation**

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
