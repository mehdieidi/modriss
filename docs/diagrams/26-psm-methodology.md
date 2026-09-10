# AWS PSM Modeling Methodology

AWS PSM modeling follows `varka.psm.modeling` (`mde/methodology/process-definitions/psm.json`). **Six SPEM phases** (`psm.ph1`–`psm.ph6`) with **25 atomic tasks**. **Deployment Foundation** and **Network & Identity** run once; data/compute/orchestration phases repeat in the engine cycle.

## Phase Flow

```mermaid
flowchart TB
    ph1["psm.ph1, Deployment Foundation<br/>once"]
    ph2["psm.ph2, Network & Identity<br/>once"]
    ph3["psm.ph3, Storage & Messaging<br/>engine"]
    ph4["psm.ph4, Event Fabric & Compute<br/>engine"]
    ph5["psm.ph5, API & Orchestration<br/>engine"]
    ph6["psm.ph6, Integration Views & Readiness<br/>EVL gate"]
    gate["psm-semantic-validation"]

    ph1 --> ph2 --> ph3 --> ph4 --> ph5 --> ph6 --> gate
```

Primary role: **cloud-platform-engineer**; **process-reviewer** signs off `psm.ph6`.

## PIM→PSM ETL Alignment

After PIM EVL, `e2e.p4.pim-to-psm` materializes draft AWS resources. Refinement follows SPEM task order within each phase, not a separate ad-hoc checklist.

```mermaid
flowchart LR
    PIM["PIM (EVL passed)"]
    ETL["pim-to-awspsm ETL"]
    PH1["psm.ph1, stacks & IAM"]
    PH3["psm.ph3–ph5, engine phases"]
    PH6["psm.ph6, readiness"]
    M2T["awspsm-to-artifacts"]

    PIM --> ETL --> PH1 --> PH3 --> PH6 --> M2T
```

| Concept / artifact           | Phase     | Example task ID  |
| ---------------------------- | --------- | ---------------- |
| `AwsPsmModel`                | `psm.ph1` | `psm.ph1.st1.t1` |
| `SamStack`, `IamRole`        | `psm.ph1` | `psm.ph1.st2.t1` |
| `Vpc`, `CognitoUserPool`     | `psm.ph2` | `psm.ph2.st1.t1` |
| `DynamoDbTable`, `SqsQueue`  | `psm.ph3` | `psm.ph3.st1.t1` |
| `LambdaFunction`             | `psm.ph4` | `psm.ph4.st1.t1` |
| `ApiGateway`, `StateMachine` | `psm.ph5` | `psm.ph5.st1.t1` |

All **297** PSM concepts map to tasks via `spem-psm.mjs`.
