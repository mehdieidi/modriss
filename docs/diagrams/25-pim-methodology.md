# PIM Modeling Methodology

Platform-independent modeling follows process `modless.pim.modeling` (`mde/methodology/process-definitions/pim.json`). Twelve phases (`pim.p0`–`pim.p11`) refine a PIM produced by CIM→PIM ETL or created greenfield. The PIM EVL gate (`pim.m1.evl-gate`) closes at `pim.p11.platform-mapping-readiness`.

## Phase Flow

Entry to `pim.p0` requires CIM transform completion or a greenfield PIM. Phases progress from architecture posture through services, contracts, data, compute, integration, security, and policies to platform mapping readiness.

```mermaid
flowchart TB
    p0["pim.p0.architecture-posture<br/>Architecture Posture · dashboard · 30m"]
    p1["pim.p1.service-boundaries<br/>Service Boundaries · services · 1-2h"]
    p2["pim.p2.contracts-schemas<br/>Contracts & Schemas · contracts · 2-3h"]
    p3["pim.p3.data-architecture<br/>Data Architecture · data · 2-3h"]
    p4["pim.p4.compute-units<br/>Compute Units · compute · 2-3h"]
    p5["pim.p5.api-surface<br/>API Surface · api · 1-2h"]
    p6["pim.p6.integration-topology<br/>Integration Topology · integration · 2-3h"]
    p7["pim.p7.workflow-orchestration<br/>Workflow Orchestration · workflow · 2h"]
    p8["pim.p8.security-identity<br/>Security & Identity · security · 1-2h"]
    p9["pim.p9.architecture-policies<br/>Architecture Policies · policies · 2h"]
    p10["pim.p10.external-config<br/>External & Config · config · 1-2h"]
    p11["pim.p11.platform-mapping-readiness<br/>Platform Mapping & Readiness · readiness · 1-2h"]
    gate["pim.m1.evl-gate<br/>PIM EVL validation gate"]

    p0 --> p1 --> p2 --> p3 --> p4 --> p5 --> p6 --> p7 --> p8 --> p9 --> p10 --> p11 --> gate
```

Primary role throughout modeling is **solution-architect**; **process-reviewer** leads `pim.p11` and EVL sign-off.

## CIM→PIM ETL Module Alignment

After CIM EVL, `e2e.p2.cim-to-pim` runs transformation `cim-to-pim` (`mde/transformations/cim-to-pim/`). ETL modules seed PIM elements that refinement phases then extend and validate. The diagram below shows how each ETL module maps to the PIM phases where architects continue work.

```mermaid
flowchart TB
    subgraph etl["CIM→PIM ETL modules"]
        entry["cim-to-pim.etl"]
        root["root-scaffolding.etl"]
        boundary["boundaries-security.etl"]
        data["domain-data.etl"]
        behavior["behavior-contracts.etl"]
        process["process-policy.etl"]
        integration["integration-deployment.etl"]
    end

    subgraph phases["PIM refinement phases"]
        p0["pim.p0.architecture-posture"]
        p1["pim.p1.service-boundaries"]
        p2["pim.p2.contracts-schemas"]
        p3["pim.p3.data-architecture"]
        p4["pim.p4.compute-units"]
        p5["pim.p5.api-surface"]
        p6["pim.p6.integration-topology"]
        p7["pim.p7.workflow-orchestration"]
        p8["pim.p8.security-identity"]
        p9["pim.p9.architecture-policies"]
        p10["pim.p10.external-config"]
        p11["pim.p11.platform-mapping-readiness"]
    end

    entry --> root --> boundary --> data --> behavior --> process --> integration

    root -.->|"PIMModel, ImplementationProfile, trace/readiness"| p0
    boundary -.->|"ServerlessService, ServiceElementMembership"| p1
    boundary -.->|"IdentityProvider, Principal, SecurityPolicy"| p8
    data -.->|"Schema, EventType, EventEnvelope"| p2
    data -.->|"DataStore, DataModel, AccessPattern"| p3
    behavior -.->|"Function, Trigger, FunctionContract"| p4
    behavior -.->|"Api, ApiRoute, ApiContract"| p5
    process -.->|"Workflow, WorkflowState, HumanTask"| p7
    process -.->|"ArchitecturePolicy, ResiliencePolicy, Slo"| p9
    integration -.->|"EventChannel, Queue, Topic, Flow"| p6
    integration -.->|"Environment, Secret, ExternalEndpoint, DeploymentUnit"| p10
    integration -.->|"readiness closure, PlatformCapability"| p11
```

### ETL module responsibilities

| ETL module                   | Primary PIM phases             | Work products seeded                                                           |
| ---------------------------- | ------------------------------ | ------------------------------------------------------------------------------ |
| `root-scaffolding.etl`       | `pim.p0`                       | `PIMModel`, `ImplementationProfile`, environments, trace/readiness scaffolding |
| `boundaries-security.etl`    | `pim.p1`, `pim.p8`             | `ServerlessService`, memberships, identity and security baseline               |
| `domain-data.etl`            | `pim.p2`, `pim.p3`             | Schemas, event contracts, data stores, models, protection policies             |
| `behavior-contracts.etl`     | `pim.p4`, `pim.p5`             | Functions, triggers, APIs, routes mapped from CIM CQRS surface                 |
| `process-policy.etl`         | `pim.p7`, `pim.p9`             | Workflows, decision models, resilience and observability policies              |
| `integration-deployment.etl` | `pim.p6`, `pim.p10`, `pim.p11` | Flows, triggers, deployment units, config, readiness closure                   |

Shared EOL libraries (`lib/mapping.eol`, `lib/pim-builders.eol`, `lib/trace-readiness.eol`) support deterministic naming and trace links across modules—refinement in `pim.p11` validates `PlatformMappingAssessment` and `pim-semantic-validation` before PIM→PSM transform.

## Refinement vs. Transform

```mermaid
sequenceDiagram
    participant CIM as CIM model (EVL passed)
    participant ETL as cim-to-pim ETL
    participant PIM as PIM model (draft)
    participant SA as Solution Architect
    participant EVL as pim-semantic-validation

    CIM->>ETL: e2e.p2.cim-to-pim
    ETL->>PIM: Seed via 6 ETL modules
    SA->>PIM: pim.p0 – pim.p10 refinement
    SA->>PIM: pim.p11 platform mapping
    PIM->>EVL: pim.m1.evl-gate
    EVL-->>PIM: Ready for e2e.p4.pim-to-psm
```

Architects treat ETL output as a starting point: service boundaries may split merged contexts, policies may be tightened, and platform mapping in `pim.p11` confirms AWS capability fit before transformation to PSM.
