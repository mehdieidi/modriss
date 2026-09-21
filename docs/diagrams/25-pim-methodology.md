# PIM Modeling Process

Platform-independent modeling follows `modriss.pim.modeling` (`mde/process/process-definitions/pim.json`). **Six SPEM phases** (`pim.ph1`–`pim.ph6`) with nested stages and **29 atomic tasks**. **Architecture Establishment** runs once; engine phases repeat per service slice.

## Phase Flow

```mermaid
flowchart TB
    ph1["pim.ph1, Architecture Establishment<br/>once"]
    ph2["pim.ph2, Contracts & Data<br/>engine"]
    ph3["pim.ph3, Compute & Exposure<br/>engine"]
    ph4["pim.ph4, Integration & Orchestration<br/>engine"]
    ph5["pim.ph5, Assurance & Configuration<br/>engine"]
    ph6["pim.ph6, Platform Readiness<br/>EVL gate"]
    gate["pim-semantic-validation"]

    ph1 --> ph2 --> ph3 --> ph4 --> ph5 --> ph6 --> gate
```

Primary role: **solution-architect**; **process-reviewer** owns `pim.ph6` readiness gate.

## CIM→PIM ETL Module Alignment

After CIM EVL, `e2e.p2.cim-to-pim` runs `cim-to-pim` ETL. ETL modules seed PIM elements refined in engine phases:

```mermaid
flowchart TB
    subgraph etl["CIM→PIM ETL modules"]
        root["root-scaffolding.etl"]
        boundary["boundaries-security.etl"]
        data["domain-data.etl"]
        behavior["behavior-contracts.etl"]
        process["process-policy.etl"]
        integration["integration-deployment.etl"]
    end

    subgraph phases["PIM engine phases"]
        p1["pim.ph1, Architecture Establishment"]
        p2["pim.ph2, Contracts & Data"]
        p3["pim.ph3, Compute & Exposure"]
        p4["pim.ph4, Integration & Orchestration"]
        p5["pim.ph5, Assurance & Configuration"]
        p6["pim.ph6, Platform Readiness"]
    end

    root -.-> p1
    boundary -.-> p1
    data -.-> p2
    behavior -.-> p3
    process -.-> p4
    integration -.-> p4
    integration -.-> p5
    integration -.-> p6
```

| ETL module                   | Primary PIM phases   | Work products seeded                       |
| ---------------------------- | -------------------- | ------------------------------------------ |
| `root-scaffolding.etl`       | `pim.ph1`            | `PIMModel`, `ImplementationProfile`, trace |
| `boundaries-security.etl`    | `pim.ph1`, `pim.ph5` | Services, identity, security baseline      |
| `domain-data.etl`            | `pim.ph2`            | Schemas, events, data stores               |
| `behavior-contracts.etl`     | `pim.ph3`            | Functions, APIs, contracts                 |
| `process-policy.etl`         | `pim.ph4`, `pim.ph5` | Workflows, policies, observability         |
| `integration-deployment.etl` | `pim.ph4`–`pim.ph6`  | Flows, config, readiness closure           |

## Refinement vs. Transform

```mermaid
sequenceDiagram
    participant CIM as CIM model (EVL passed)
    participant ETL as cim-to-pim ETL
    participant PIM as PIM model (draft)
    participant SA as Solution Architect
    participant EVL as pim-semantic-validation

    CIM->>ETL: e2e.p2.cim-to-pim
    ETL->>PIM: Seed via ETL modules
    SA->>PIM: pim.ph1 – pim.ph5 engine phases
    SA->>PIM: pim.ph6 platform readiness
    PIM->>EVL: pim-semantic-validation
    EVL-->>PIM: Ready for e2e.p4.pim-to-psm
```

All **179** PIM concepts map to tasks via `spem-pim.mjs` and `concept-assignment.mjs`.
