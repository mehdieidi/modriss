# PIM modeling: phases and activities

This page documents the **SPEM process-structure Activity, Phase, and TaskUse** elements used by the PIM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

A phase establishes a significant lifecycle period and normally ends at a major checkpoint. An activity groups related work within a phase or process component. A TaskUse places reusable task guidance into that process context. Entry and exit conditions describe evidence states; they are not calendar dates.

## Summary

| Phase or activity            | SPEM type | Contained by                 | Task uses | Execution        |
| ---------------------------- | --------- | ---------------------------- | --------: | ---------------- |
| Architecture & Slice Framing | Activity  | Process                      |         0 | defined sequence |
| Service Slice Planning       | Activity  | Architecture & Slice Framing |         1 | defined sequence |
| Architecture Posture         | Activity  | Architecture & Slice Framing |         3 | defined sequence |
| Service Boundaries           | Activity  | Architecture & Slice Framing |         2 | defined sequence |
| Contracts & Data             | Activity  | Process                      |         0 | defined sequence |
| Contracts & Schemas          | Activity  | Contracts & Data             |         2 | defined sequence |
| Data Architecture            | Activity  | Contracts & Data             |         3 | defined sequence |
| Compute & Exposure           | Activity  | Process                      |         0 | defined sequence |
| Compute Units                | Activity  | Compute & Exposure           |         2 | defined sequence |
| API Surface                  | Activity  | Compute & Exposure           |         2 | defined sequence |
| Integration & Orchestration  | Activity  | Process                      |         0 | defined sequence |
| Integration Topology         | Activity  | Integration & Orchestration  |         2 | defined sequence |
| Workflow Orchestration       | Activity  | Integration & Orchestration  |         2 | defined sequence |
| Assurance & Configuration    | Activity  | Process                      |         0 | defined sequence |
| Security & Identity          | Activity  | Assurance & Configuration    |         2 | defined sequence |
| Architecture Policies        | Activity  | Assurance & Configuration    |         0 | defined sequence |
| Resilience & Throughput      | Activity  | Architecture Policies        |         2 | defined sequence |
| Observability & SLOs         | Activity  | Architecture Policies        |         2 | defined sequence |
| Governance & Business Rules  | Activity  | Architecture Policies        |         2 | defined sequence |
| External & Config            | Activity  | Assurance & Configuration    |         2 | defined sequence |
| Platform Readiness           | Activity  | Process                      |         0 | defined sequence |
| Platform Mapping Assessment  | Activity  | Platform Readiness           |         1 | defined sequence |
| Trace & Readiness Gate       | Activity  | Platform Readiness           |         1 | defined sequence |
| Increment Review & Adapt     | Activity  | Platform Readiness           |         1 | defined sequence |

## Detailed activities

## Architecture & Slice Framing

<small>Activity · MODRISS::Stage: `pim.ph1`</small>

Frame the current service slice, establish or refresh PIM posture, and align serverless boundaries to CIM intent.

**Entry conditions**

- CIM transform complete, prior PIM increment selected, or greenfield PIM

**Exit conditions**

- PIM root configured
- Service-slice objective and architecture definition of done are agreed
- Services cover deployable boundaries for the slice

**Participating roles**

- Solution Architect

### Service Slice Planning

<small>Activity · MODRISS::SubStage: `pim.ph1.st0` · contained by **Architecture & Slice Framing**</small>

Select the PIM service slice and define architecture review expectations.

**Participating roles**

- Solution Architect

**Task uses in this activity**

- **Plan service slice** (TaskUse `pim.ph1.st0.t1`)

### Architecture Posture

<small>Activity · MODRISS::SubStage: `pim.ph1.st1` · contained by **Architecture & Slice Framing**</small>

Create or refresh the PIMModel root with architecture style and implementation profile.

**Participating roles**

- Solution Architect
- Method Engineer

**Task uses in this activity**

- **Create PIM model root** (TaskUse `pim.ph1.st1.t1`)
- **Set architecture posture** (TaskUse `pim.ph1.st1.t2`)
- **Establish shared model contract and evidence conventions** (TaskUse `pim.ph1.st1.t3`)

### Service Boundaries

<small>Activity · MODRISS::SubStage: `pim.ph1.st2` · contained by **Architecture & Slice Framing**</small>

Define serverless services and element memberships aligned to bounded contexts.

**Participating roles**

- Solution Architect

**Task uses in this activity**

- **Define serverless services** (TaskUse `pim.ph1.st2.t1`)
- **Assign element memberships** (TaskUse `pim.ph1.st2.t2`)

## Contracts & Data

<small>Activity · MODRISS::Stage: `pim.ph2`</small>

Define API/event contracts and persistent data architecture for the increment slice.

**Entry conditions**

- Architecture & Slice Framing complete

**Exit conditions**

- Contracts exist for APIs and events
- Persistent stores cover domain data

**Participating roles**

- Solution Architect

### Contracts & Schemas

<small>Activity · MODRISS::SubStage: `pim.ph2.st1` · contained by **Contracts & Data**</small>

Model schemas, validation constraints, and event envelopes aligned to CIM behavior.

**Participating roles**

- Solution Architect

**Task uses in this activity**

- **Define schemas and fields** (TaskUse `pim.ph2.st1.t1`)
- **Model event types and envelopes** (TaskUse `pim.ph2.st1.t2`)

### Data Architecture

<small>Activity · MODRISS::SubStage: `pim.ph2.st2` · contained by **Contracts & Data**</small>

Model data stores, access patterns, and change streams for domain persistence.

**Participating roles**

- Solution Architect

**Task uses in this activity**

- **Model data stores and models** (TaskUse `pim.ph2.st2.t1`)
- **Define access patterns and indexes** (TaskUse `pim.ph2.st2.t2`)
- **Configure change streams and notifications** (TaskUse `pim.ph2.st2.t3`)

## Compute & Exposure

<small>Activity · MODRISS::Stage: `pim.ph3`</small>

Define compute units and expose them through a coherent API surface.

**Entry conditions**

- Contracts & Data complete for slice

**Exit conditions**

- Functions cover behavioral surface
- Public API surface complete

**Participating roles**

- Solution Architect

### Compute Units

<small>Activity · MODRISS::SubStage: `pim.ph3.st1` · contained by **Compute & Exposure**</small>

Define functions with contracts and triggers mapped to CIM commands and events.

**Participating roles**

- Solution Architect

**Task uses in this activity**

- **Define functions and contracts** (TaskUse `pim.ph3.st1.t1`)
- **Configure triggers and runtime** (TaskUse `pim.ph3.st1.t2`)

### API Surface

<small>Activity · MODRISS::SubStage: `pim.ph3.st2` · contained by **Compute & Exposure**</small>

Expose functions through APIs with routes, contracts, and error mappings.

**Participating roles**

- Solution Architect

**Task uses in this activity**

- **Define APIs and routes** (TaskUse `pim.ph3.st2.t1`)
- **Map API contracts and errors** (TaskUse `pim.ph3.st2.t2`)

## Integration & Orchestration

<small>Activity · MODRISS::Stage: `pim.ph4`</small>

Wire async integration topology and long-running workflow orchestration.

**Entry conditions**

- Compute & Exposure complete for slice

**Exit conditions**

- Async integration topology complete
- Long-running processes orchestrated

**Participating roles**

- Solution Architect

### Integration Topology

<small>Activity · MODRISS::SubStage: `pim.ph4.st1` · contained by **Integration & Orchestration**</small>

Model event channels, flows, and routing rules connecting services.

**Participating roles**

- Solution Architect

**Task uses in this activity**

- **Model event channels and buses** (TaskUse `pim.ph4.st1.t1`)
- **Define flows and routing rules** (TaskUse `pim.ph4.st1.t2`)

### Workflow Orchestration

<small>Activity · MODRISS::SubStage: `pim.ph4.st2` · contained by **Integration & Orchestration**</small>

Model workflows from CIM business processes with human tasks and compensation.

**Participating roles**

- Solution Architect

**Task uses in this activity**

- **Model workflows and states** (TaskUse `pim.ph4.st2.t1`)
- **Configure human tasks and error handling** (TaskUse `pim.ph4.st2.t2`)

## Assurance & Configuration

<small>Activity · MODRISS::Stage: `pim.ph5`</small>

Apply security, operational policies, and environment configuration across the slice.

**Entry conditions**

- Integration & Orchestration complete for slice

**Exit conditions**

- Auth model covers all public endpoints
- Operational policies applied
- External integrations and config complete

**Participating roles**

- Solution Architect

### Security & Identity

<small>Activity · MODRISS::SubStage: `pim.ph5.st1` · contained by **Assurance & Configuration**</small>

Configure identity providers, principals, and authorization for APIs and functions.

**Participating roles**

- Solution Architect

**Task uses in this activity**

- **Configure identity providers and principals** (TaskUse `pim.ph5.st1.t1`)
- **Define permissions and security policies** (TaskUse `pim.ph5.st1.t2`)

### Architecture Policies

<small>Activity · MODRISS::SubStage: `pim.ph5.st2` · contained by **Assurance & Configuration**</small>

Apply resilience, observability, governance, and cost policies to architecture elements.

**Participating roles**

- Solution Architect

#### Resilience & Throughput

<small>Activity · MODRISS::Stage: `pim.ph5.st2.ss1` · contained by **Architecture Policies**</small>

Configure retry, timeout, concurrency, and throughput policies.

**Participating roles**

- Solution Architect

**Task uses in this activity**

- **Apply resilience policies** (TaskUse `pim.ph5.st2.ss1.t1`)
- **Configure throughput and ordering policies** (TaskUse `pim.ph5.st2.ss1.t2`)

#### Observability & SLOs

<small>Activity · MODRISS::Stage: `pim.ph5.st2.ss2` · contained by **Architecture Policies**</small>

Configure logging, metrics, tracing, alerts, and service level objectives.

**Participating roles**

- Solution Architect

**Task uses in this activity**

- **Configure observability policies** (TaskUse `pim.ph5.st2.ss2.t1`)
- **Define alerts, SLOs, and CORS** (TaskUse `pim.ph5.st2.ss2.t2`)

#### Governance & Business Rules

<small>Activity · MODRISS::Stage: `pim.ph5.st2.ss3` · contained by **Architecture Policies**</small>

Apply data protection, compliance, and business rule policies.

**Participating roles**

- Solution Architect

**Task uses in this activity**

- **Apply governance and compliance policies** (TaskUse `pim.ph5.st2.ss3.t1`)
- **Map business rules and decision models** (TaskUse `pim.ph5.st2.ss3.t2`)

### External & Config

<small>Activity · MODRISS::SubStage: `pim.ph5.st3` · contained by **Assurance & Configuration**</small>

Model external integrations, environments, secrets, and deployment units.

**Participating roles**

- Solution Architect

**Task uses in this activity**

- **Model external endpoints and adapters** (TaskUse `pim.ph5.st3.t1`)
- **Configure environments and deployment units** (TaskUse `pim.ph5.st3.t2`)

## Platform Readiness

<small>Activity · MODRISS::Stage: `pim.ph6`</small>

Assess platform capability mapping, close traceability, and pass PIM EVL gate.

**Entry conditions**

- Assurance & Configuration complete for slice

**Exit conditions**

- PIM EVL passes
- Readiness gate approved
- PIM increment reviewed and improvement actions captured

**Participating roles**

- Process Reviewer

### Platform Mapping Assessment

<small>Activity · MODRISS::SubStage: `pim.ph6.st1` · contained by **Platform Readiness**</small>

Evaluate platform capability coverage and mapping readiness.

**Participating roles**

- Process Reviewer

**Task uses in this activity**

- **Assess platform capabilities** (TaskUse `pim.ph6.st1.t1`)

### Trace & Readiness Gate

<small>Activity · MODRISS::SubStage: `pim.ph6.st2` · contained by **Platform Readiness**</small>

Close trace links and production readiness before PIM→PSM transform.

**Participating roles**

- Process Reviewer

**Task uses in this activity**

- **Complete trace and readiness** (TaskUse `pim.ph6.st2.t1`)

### Increment Review & Adapt

<small>Activity · MODRISS::SubStage: `pim.ph6.st3` · contained by **Platform Readiness**</small>

Review the service slice architecture, accept the increment, and adapt the next cycle.

**Participating roles**

- Process Reviewer

**Task uses in this activity**

- **Review and adapt PIM increment** (TaskUse `pim.ph6.st3.t1`)

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
