# End-to-End Modeling Methodology

The full MDE lifecycle is `varka.end-to-end.modeling` (`mde/methodology/process-definitions/end-to-end.json`). One SPEM phase (`e2e.ph1`) contains **eight stages** that orchestrate child process engines, ETL transforms, EVL gates, M2T, and cross-level rework loops.

## Pipeline Overview

```mermaid
flowchart LR
    p0["e2e.p0.increment-planning"]
    p1["e2e.p1.cim-modeling<br/>varka.cim.modeling · 5 phases"]
    evl1["cim-semantic-validation<br/>e2e.m1.cim-ready"]
    p2["e2e.p2.cim-to-pim"]
    p3["e2e.p3.pim-refinement<br/>varka.pim.modeling · 6 phases"]
    evl2["pim-semantic-validation<br/>e2e.m2.pim-ready"]
    p4["e2e.p4.pim-to-psm"]
    p5["e2e.p5.psm-refinement<br/>varka.psm.modeling · 6 phases"]
    evl3["psm-semantic-validation<br/>e2e.m3.psm-ready"]
    p6["e2e.p6.m2t-generation"]
    p7["e2e.p7.artifact-completion<br/>e2e.m4.artifacts"]

    p0 --> p1 --> evl1 --> p2 --> p3 --> evl2 --> p4 --> p5 --> evl3 --> p6 --> p7
```

The top-level **process engine** loops: plan → run child engines → transform → review → deliver → retrospect → ↻ while backlog remains.

## Roles Across the Pipeline

| Role                      | Stages / child process                   | Responsibility                  |
| ------------------------- | ---------------------------------------- | ------------------------------- |
| `business-modeler`        | `e2e.p1` → `cim.ph1`–`cim.ph5`           | CIM modeling                    |
| `requirements-engineer`   | within CIM `cim.ph5`                     | Twin Peaks requirements         |
| `solution-architect`      | `e2e.p2`, `e2e.p3` → `pim.ph1`–`pim.ph6` | CIM→PIM, PIM refinement         |
| `cloud-platform-engineer` | `e2e.p4`–`e2e.p6` → `psm.ph1`–`psm.ph6`  | PIM→PSM, PSM, M2T               |
| `process-reviewer`        | EVL gates, `e2e.p7`                      | Readiness and artifact sign-off |

## EVL and ETL Gates

```mermaid
flowchart TB
    subgraph cim["CIM track"]
        CIM["CIM model"]
        EVL_CIM["cim-semantic-validation<br/>@ cim.ph5"]
        ETL_CP["ETL: cim-to-pim<br/>e2e.p2"]
    end

    subgraph pim["PIM track"]
        PIM["PIM model"]
        EVL_PIM["pim-semantic-validation<br/>@ pim.ph6"]
        ETL_PP["ETL: pim-to-awspsm<br/>e2e.p4"]
    end

    subgraph psm["PSM track"]
        PSM["AWS PSM model"]
        EVL_PSM["psm-semantic-validation<br/>@ psm.ph6"]
        M2T["EGX: awspsm-to-artifacts<br/>e2e.p6"]
        ART["e2e.p7, artifact review"]
    end

    CIM --> EVL_CIM --> ETL_CP --> PIM
    PIM --> EVL_PIM --> ETL_PP --> PSM
    PSM --> EVL_PSM --> M2T --> ART
```

### Gate semantics

| Milestone          | Stage / phase           | Gate / action             | On failure                    |
| ------------------ | ----------------------- | ------------------------- | ----------------------------- |
| `e2e.m1.cim-ready` | `e2e.p1.cim-modeling`   | `cim-semantic-validation` | Rework in CIM (`cim.ph5`)     |
| :                  | `e2e.p2.cim-to-pim`     | ETL (no EVL)              | Fix CIM or profile; re-run    |
| `e2e.m2.pim-ready` | `e2e.p3.pim-refinement` | `pim-semantic-validation` | Rework in PIM (`pim.ph6`)     |
| :                  | `e2e.p4.pim-to-psm`     | ETL (no EVL)              | Fix PIM; re-run               |
| `e2e.m3.psm-ready` | `e2e.p5.psm-refinement` | `psm-semantic-validation` | Rework in PSM (`psm.ph6`)     |
| `e2e.m4.artifacts` | `e2e.p7`                | Manual review             | Fix PSM; regenerate artifacts |

## Sequence: Full Lifecycle

```mermaid
sequenceDiagram
    autonumber
    participant BM as Business Modeler
    participant SA as Solution Architect
    participant CPE as Cloud Platform Engineer
    participant PR as Process Reviewer
    participant EVL as EVL runner
    participant ETL as ETL / EGX runner

    BM->>BM: e2e.p1, cim.ph1..ph5
    PR->>EVL: cim-semantic-validation @ cim.ph5
    EVL-->>PR: e2e.m1.cim-ready

    SA->>ETL: e2e.p2, cim-to-pim
    ETL-->>SA: PIM draft
    SA->>SA: e2e.p3, pim.ph1..ph6
    PR->>EVL: pim-semantic-validation
    EVL-->>PR: e2e.m2.pim-ready

    CPE->>ETL: e2e.p4, pim-to-awspsm
    ETL-->>CPE: AWS PSM draft
    CPE->>CPE: e2e.p5, psm.ph1..ph6
    PR->>EVL: psm-semantic-validation
    EVL-->>PR: e2e.m3.psm-ready

    CPE->>ETL: e2e.p6, awspsm-to-artifacts
    PR->>PR: e2e.p7, artifact review
```

## Child Process Reference

| E2E stage ID                 | Child / transform     | SPEM phases             |
| ---------------------------- | --------------------- | ----------------------- |
| `e2e.p1.cim-modeling`        | `varka.cim.modeling`  | 5 (`cim.ph1`–`cim.ph5`) |
| `e2e.p2.cim-to-pim`          | `cim-to-pim` ETL      | :                       |
| `e2e.p3.pim-refinement`      | `varka.pim.modeling`  | 6 (`pim.ph1`–`pim.ph6`) |
| `e2e.p4.pim-to-psm`          | `pim-to-awspsm` ETL   | :                       |
| `e2e.p5.psm-refinement`      | `varka.psm.modeling`  | 6 (`psm.ph1`–`psm.ph6`) |
| `e2e.p6.m2t-generation`      | `awspsm-to-artifacts` | :                       |
| `e2e.p7.artifact-completion` | Manual review         | :                       |

See [24-cim-methodology.md](24-cim-methodology.md), [25-pim-methodology.md](25-pim-methodology.md), and [26-psm-methodology.md](26-psm-methodology.md).
