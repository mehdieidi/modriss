# End-to-End Modeling Methodology

This guide describes the full Modless modeling lifecycle from business intent through deployable AWS
artifacts. It connects three level-specific methodologies—[CIM](cim-modeling-methodology.md),
[PIM](pim-modeling-methodology.md), and [PSM](psm-modeling-methodology.md)—with transformation
milestones, EVL gates, and human-in-the-loop refinement loops.

The machine-readable process definition lives at
`mde/methodology/process-definitions/end-to-end.json`.

## Roles Across the Pipeline

| Role                        | Primary responsibility                                          |
| --------------------------- | --------------------------------------------------------------- |
| **Business Modeler**        | CIM modeling (phases 0–12)                                      |
| **Requirements Engineer**   | CIM phase 11 governance                                         |
| **Solution Architect**      | CIM→PIM transform oversight; PIM refinement (phases 0–11)       |
| **Cloud Platform Engineer** | PIM→PSM transform; PSM refinement (phases 0–11); M2T generation |
| **Process Reviewer**        | EVL gate approval and readiness sign-off at each level          |

## End-to-End Flow

```mermaid
flowchart LR
  subgraph roles [Roles]
    BE[Business Modeler]
    RE[Requirements Engineer]
    SA[Solution Architect]
    CE[Cloud Platform Engineer]
    RV[Process Reviewer]
  end

  CIM["CIM Modeling<br/>(14 phases)"]
  T1[CIM → PIM ETL]
  PIM["PIM Refinement<br/>(12 phases)"]
  T2[PIM → AWS PSM ETL]
  PSM["PSM Refinement<br/>(12 phases)"]
  GEN[M2T Generation]
  ART[Artifact Review]

  BE --> CIM
  RE --> CIM
  CIM -->|cim-semantic-validation| T1
  T1 --> PIM
  SA --> PIM
  PIM -->|pim-semantic-validation| T2
  T2 --> PSM
  CE --> PSM
  PSM -->|psm-semantic-validation| GEN
  GEN --> ART
  RV --> CIM
  RV --> PIM
  RV --> PSM
  RV --> ART
```

## Milestones Overview

| Milestone              | ID                 | Phase                        | Gate / deliverable                                  |
| ---------------------- | ------------------ | ---------------------------- | --------------------------------------------------- |
| CIM readiness approved | `e2e.m1.cim-ready` | CIM Modeling                 | `cim-semantic-validation` passes; Phase 13 complete |
| PIM readiness approved | `e2e.m2.pim-ready` | PIM Refinement               | `pim-semantic-validation` passes; Phase 11 complete |
| PSM readiness approved | `e2e.m3.psm-ready` | PSM Refinement               | `psm-semantic-validation` passes; Phase 11 complete |
| Artifacts delivered    | `e2e.m4.artifacts` | Artifact Review & Completion | Generated project reviewed and accepted             |

## Phase Overview

| Phase | Name                         | Primary role            | Transform             | Validation gate           | Outputs                                      |
| ----- | ---------------------------- | ----------------------- | --------------------- | ------------------------- | -------------------------------------------- |
| 1     | CIM Modeling (14 phases)     | Business Modeler        | —                     | `cim-semantic-validation` | Complete CIM with traceability and readiness |
| 2     | CIM → PIM Transformation     | Solution Architect      | `cim-to-pim`          | —                         | Draft PIM scaffolding from CIM               |
| 3     | PIM Refinement (12 phases)   | Solution Architect      | —                     | `pim-semantic-validation` | Production-ready PIM                         |
| 4     | PIM → AWS PSM Transformation | Cloud Platform Engineer | `pim-to-awspsm`       | —                         | Draft AWS PSM from PIM                       |
| 5     | PSM Refinement (12 phases)   | Cloud Platform Engineer | —                     | `psm-semantic-validation` | Production-ready AWS PSM                     |
| 6     | M2T Artifact Generation      | Cloud Platform Engineer | `awspsm-to-artifacts` | —                         | SAM/CloudFormation, handlers, tests, docs    |
| 7     | Artifact Review & Completion | Process Reviewer        | —                     | —                         | Reviewed, deployable AWS project             |

---

## Phase 1 — CIM Modeling (14 phases)

**Child process:** `modless.cim.modeling` · **Guide:** [CIM Modeling Methodology](cim-modeling-methodology.md)

Model business intent, domain, behavior, governance, and transformation contracts without platform
detail. Follow all 14 CIM phases (divided into 0–13) in dependency order.

### Key activities

1. Phases 0–4: context, intent, actors, capabilities, ubiquitous language.
2. Phase 5: information taxonomy **before** domain entities (`CIM-ENTITY-001`).
3. Phases 6–9: domain structure, behavior, aggregates, processes.
4. Phase 10: bounded context synthesis (after behavior exists).
5. Phase 11: requirements and governance backfill.
6. Phases 12–13: transformation contracts and readiness gate.

### Common mistakes

| Mistake                                            | EVL rule / impact                 |
| -------------------------------------------------- | --------------------------------- |
| Skipping information taxonomy before entities      | `CIM-ENTITY-001`                  |
| Creating bounded contexts before behavior modeling | Empty context memberships         |
| Transforming before Phase 13 readiness             | `cim-semantic-validation` failure |

### Phase gate checklist

- [ ] All 14 CIM phases complete per [CIM guide](cim-modeling-methodology.md)
- [ ] `ProductionReadinessAssessment` approved
- [ ] **`cim-semantic-validation` passes**
- [ ] Milestone `e2e.m1.cim-ready` achieved

---

## Phase 2 — CIM → PIM Transformation

**Transform:** `cim-to-pim` · **Primary role:** Solution Architect

Run the semi-automated CIM-to-PIM ETL profile. The transformation creates PIM scaffolding—service
boundaries, security concepts, data structures, behaviors, contracts, integrations, deployment
concepts, traces, and readiness information—split across concern-specific ETL modules.

### Tasks

1. Confirm CIM model revision is saved and EVL-clean.
2. Execute CIM→PIM transform from the pipeline UI or API.
3. Review transform report, manual decisions, and trace links.
4. Do **not** skip PIM refinement; generated elements require human review.

### Common mistakes

| Mistake                         | Impact                                |
| ------------------------------- | ------------------------------------- |
| Transforming stale CIM revision | HTTP 409 or inconsistent PIM          |
| Ignoring manual-action report   | Unresolved decisions propagate to PSM |
| Treating generated PIM as final | Missing refinement in Phases 0–11     |

### Phase gate checklist

- [ ] Transform completed without errors
- [ ] Manual decisions and hotspots reviewed
- [ ] PIM model revision saved
- [ ] Ready to begin PIM refinement

---

## Phase 3 — PIM Refinement (12 phases)

**Child process:** `modless.pim.modeling` · **Guide:** [PIM Modeling Methodology](pim-modeling-methodology.md)

Refine generated PIM elements through 12 phases (0–11). Each phase corresponds to review tasks for
ETL-generated scaffolding plus any greenfield additions.

### Key activities

1. Phases 0–1: architecture posture and service boundaries aligned to CIM contexts.
2. Phases 2–5: contracts, data, compute, and API surface.
3. Phases 6–7: integration topology and workflow orchestration.
4. Phases 8–10: security, policies, external integrations, and config.
5. Phase 11: platform mapping assessment and PIM EVL gate.

### Common mistakes

| Mistake                                            | EVL rule / impact                 |
| -------------------------------------------------- | --------------------------------- |
| Accepting ETL service boundaries without CIM trace | Broken traceability               |
| Missing platform capability mapping                | PIM→PSM transform gaps            |
| Proceeding to PSM with open readiness findings     | `pim-semantic-validation` failure |

### Phase gate checklist

- [ ] All 12 PIM phases complete per [PIM guide](pim-modeling-methodology.md)
- [ ] `PlatformMappingAssessment` complete
- [ ] **`pim-semantic-validation` passes**
- [ ] Milestone `e2e.m2.pim-ready` achieved

---

## Phase 4 — PIM → AWS PSM Transformation

**Transform:** `pim-to-awspsm` · **Primary role:** Cloud Platform Engineer

Run the PIM-to-AWS-PSM ETL profile. Creates AWS roots, stages, stacks, compute, APIs, storage,
messaging, events, workflows, IAM, configuration, observability, and provider mappings. Post-processing
resolves relationships and validates placement.

### Tasks

1. Confirm PIM model revision is saved and EVL-clean.
2. Execute PIM→PSM transform.
3. Review relationship resolution and readiness warnings.
4. Plan PSM refinement pass through phases 0–11.

### Common mistakes

| Mistake                                        | Impact                                   |
| ---------------------------------------------- | ---------------------------------------- |
| PIM platform mapping gaps                      | Missing or placeholder AWS resources     |
| Skipping transform report                      | Unresolved IAM or networking assumptions |
| Deploying generated SAM without PSM refinement | Incomplete infrastructure model          |

### Phase gate checklist

- [ ] Transform completed without errors
- [ ] Post-processing relationship resolution reviewed
- [ ] PSM model revision saved
- [ ] Ready to begin PSM refinement

---

## Phase 5 — PSM Refinement (12 phases)

**Child process:** `modless.psm.modeling` · **Guide:** [PSM Modeling Methodology](psm-modeling-methodology.md)

Refine generated AWS resources through 12 phases (0–11), from account strategy through integration
views and readiness.

### Key activities

1. Phases 0–2: account strategy, stack scaffolding, security baseline.
2. Phases 3–4: networking and Cognito identity.
3. Phases 5–7: storage, messaging, EventBridge fabric.
4. Phases 8–10: Lambda compute, API Gateway, Step Functions, CloudWatch.
5. Phase 11: integration relationship views and PSM EVL gate.

### Common mistakes

| Mistake                                         | EVL rule / impact                         |
| ----------------------------------------------- | ----------------------------------------- |
| IAM roles too permissive vs PIM least-privilege | Security review failure at artifact stage |
| Missing integration relationship views          | `psm-semantic-validation` or M2T gaps     |
| Unvalidated Step Functions ASL                  | Deployment failure                        |

### Phase gate checklist

- [ ] All 12 PSM phases complete per [PSM guide](psm-modeling-methodology.md)
- [ ] Integration views document Lambda–API–event wiring
- [ ] **`psm-semantic-validation` passes**
- [ ] Milestone `e2e.m3.psm-ready` achieved

---

## Phase 6 — M2T Artifact Generation

**Transform:** `awspsm-to-artifacts` · **Primary role:** Cloud Platform Engineer

Generate a reviewable AWS serverless project from the validated PSM. Output may include SAM/CloudFormation
templates, Go Lambda handlers, OpenAPI and JSON Schema, ASL definitions, tests, CI workflows, and
generation reports.

See [Generated AWS Projects](generated-artifacts.md) for review expectations.

### Tasks

1. Confirm PSM EVL passes on saved revision.
2. Execute M2T generation.
3. Review `generated/reports/manual-actions.md` and `security-review.md`.
4. Inspect protected regions and external-secret placeholders.

### Common mistakes

| Mistake                         | Impact                            |
| ------------------------------- | --------------------------------- |
| Generating from unvalidated PSM | Incomplete or invalid templates   |
| Ignoring manual-actions report  | Undocumented production decisions |
| Skipping ASL validation         | Step Functions deployment failure |

### Phase gate checklist

- [ ] Generation completed successfully
- [ ] Manual-actions and security-review reports read
- [ ] Protected regions identified for application logic
- [ ] Artifact bundle saved to project

---

## Phase 7 — Artifact Review & Completion

**Primary role:** Process Reviewer

Human final gate before deployment. Generation is deliberately not the last decision—reviewers
validate infrastructure, security, operations, and traceability documentation.

### Tasks

1. Run `sam validate --lint` on generated templates.
2. Execute unit, integration, and workflow tests in generated project.
3. Verify trace links from artifacts back to CIM intent where required.
4. Sign off artifact delivery milestone.

### Common mistakes

| Mistake                                          | Impact                          |
| ------------------------------------------------ | ------------------------------- |
| Deploying without reading security-review report | Production security gaps        |
| Hard-coding secrets instead of placeholders      | Credential exposure             |
| Skipping LocalStack or staging validation        | Undetected integration failures |

### Phase gate checklist

- [ ] SAM templates lint-clean
- [ ] Generated tests pass
- [ ] Security and operations documentation reviewed
- [ ] Manual actions resolved or tracked
- [ ] Milestone `e2e.m4.artifacts` achieved

---

## Iteration and Twin Peaks

The end-to-end process supports iterative refinement:

- **CIM loop:** Requirements at Phase 11 can constrain elements modeled in earlier phases; revisit
  domain or behavior phases when governance reveals gaps.
- **Post-ETL refinement:** Transform output is a draft. Refinement phases at each level are mandatory,
  not optional cleanup.
- **Revision discipline:** Every model update requires the expected revision; stale writes return HTTP
  `409`.

```mermaid
flowchart TD
  subgraph iterate [Refinement Loops]
    CIM_R["CIM phase revisit"]
    PIM_R["PIM phase revisit"]
    PSM_R["PSM phase revisit"]
  end

  CIM_R -->|EVL pass| T1[CIM → PIM]
  PIM_R -->|EVL pass| T2[PIM → PSM]
  PSM_R -->|EVL pass| GEN[M2T]
```

## Related Documentation

- [Validation, Transformation, and Generation](../concepts/pipeline.md) — pipeline mechanics
- [Modeling Workflow](modeling-workflow.md) — editor, validation, and export
- [CIM Modeling Methodology](cim-modeling-methodology.md)
- [PIM Modeling Methodology](pim-modeling-methodology.md)
- [PSM Modeling Methodology](psm-modeling-methodology.md)
- [Generated AWS Projects](generated-artifacts.md)
