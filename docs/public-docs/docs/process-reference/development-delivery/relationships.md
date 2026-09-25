# Development and Delivery: flow and bindings

This page documents the **SPEM WorkSequence, ProcessPerformer, and ProcessParameter** elements used by the Development and Delivery process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

WorkSequence records explicit process flow. ProcessPerformer binds a role use to a task use. ProcessParameter binds a work-product use to a task use and states whether it enters or leaves the task. These relationships make dependencies inspectable and prevent document order from being treated as hidden process logic.

## Summary

| Relationship area  | Items | What it controls                                    |
| ------------------ | ----: | --------------------------------------------------- |
| Work sequences     |    37 | Ordering, iteration, feedback, and conditional flow |
| Task uses          |    30 | Placement of reusable tasks in activities           |
| Process performers |   132 | Primary and supporting role bindings                |
| Process parameters |   171 | Input and output work-product bindings              |

## Work sequences

### e2e.ph0 → e2e.ph1

<small>WorkSequence: `ws.e2e.ph0.e2e.ph1` · finishToStart</small>

Begin planned delivery only after the method profile, ownership, controls, and first increment hypothesis are accepted.

**Condition:** G1-method-and-first-increment-authorized

### e2e.ph1 → e2e.ph2

<small>WorkSequence: `ws.retirement-development.e2e.ph1.e2e.ph2` · finishToStart</small>

Begin the sequential Retirement phase only after retirement is authorized and in-flight development/release work is resolved or explicitly transferred.

**Condition:** retirement-authorized-and-no-development-or-release-work-in-flight

### e2e.ph0.st1 → e2e.ph0.st1a

<small>WorkSequence: `ws.e2e.ph0.st1.e2e.ph0.st1a` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.ph0.st1a → e2e.ph0.st2

<small>WorkSequence: `ws.e2e.ph0.st1a.e2e.ph0.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.ph0.st2 → e2e.ph0.st3

<small>WorkSequence: `ws.e2e.ph0.st2.e2e.ph0.st3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.ph0.st3 → e2e.ph0.st4

<small>WorkSequence: `ws.e2e.ph0.st3.e2e.ph0.st4` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.ph0.st1.t1 → e2e.ph0.st1.t2

<small>WorkSequence: `ws.e2e.ph0.st1.t1.e2e.ph0.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.ph0.st1a.t1 → e2e.ph0.st1a.t2

<small>WorkSequence: `ws.e2e.ph0.st1a.t1.e2e.ph0.st1a.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.ph0.st1a.t2 → e2e.ph0.st1a.t3

<small>WorkSequence: `ws.e2e.ph0.st1a.t2.e2e.ph0.st1a.t3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.ph0.st2.t1 → e2e.ph0.st2.t2

<small>WorkSequence: `ws.e2e.ph0.st2.t1.e2e.ph0.st2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.ph0.st3.t1 → e2e.ph0.st3.t2

<small>WorkSequence: `ws.e2e.ph0.st3.t1.e2e.ph0.st3.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.ph0.st4.t1 → e2e.ph0.st4.t2

<small>WorkSequence: `ws.e2e.ph0.st4.t1.e2e.ph0.st4.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.p0.increment-planning → e2e.p1.cim-modeling

<small>WorkSequence: `ws.e2e.p0.increment-planning.e2e.p1.cim-modeling` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.p1.cim-modeling → e2e.p2.cim-to-pim

<small>WorkSequence: `ws.e2e.p1.cim-modeling.e2e.p2.cim-to-pim` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.p2.cim-to-pim → e2e.p3.pim-refinement

<small>WorkSequence: `ws.e2e.p2.cim-to-pim.e2e.p3.pim-refinement` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.p3.pim-refinement → e2e.p4.pim-to-psm

<small>WorkSequence: `ws.e2e.p3.pim-refinement.e2e.p4.pim-to-psm` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.p4.pim-to-psm → e2e.p5.psm-refinement

<small>WorkSequence: `ws.e2e.p4.pim-to-psm.e2e.p5.psm-refinement` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.p5.psm-refinement → e2e.p6.m2t-generation

<small>WorkSequence: `ws.e2e.p5.psm-refinement.e2e.p6.m2t-generation` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.p6.m2t-generation → e2e.p7.artifact-completion

<small>WorkSequence: `ws.e2e.p6.m2t-generation.e2e.p7.artifact-completion` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.p7.artifact-completion → e2e.rel.a1

<small>WorkSequence: `ws.e2e.p7.artifact-completion.e2e.rel.a1` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.rel.a1 → e2e.rel.a2

<small>WorkSequence: `ws.e2e.rel.a1.e2e.rel.a2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.rel.a2 → e2e.rel.a3

<small>WorkSequence: `ws.e2e.rel.a2.e2e.rel.a3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.p7.artifact-completion.t1 → e2e.p7.artifact-completion.t2

<small>WorkSequence: `ws.e2e.p7.artifact-completion.t1.e2e.p7.artifact-completion.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.rel.a1.t1 → e2e.rel.a1.t2

<small>WorkSequence: `ws.e2e.rel.a1.t1.e2e.rel.a1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.rel.a2.t1 → e2e.rel.a2.t2

<small>WorkSequence: `ws.e2e.rel.a2.t1.e2e.rel.a2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.ph2.st1 → e2e.ph2.st2

<small>WorkSequence: `ws.e2e.ph2.st1.e2e.ph2.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.ph2.st2 → e2e.ph2.st3

<small>WorkSequence: `ws.e2e.ph2.st2.e2e.ph2.st3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.ph2.st2.t1 → e2e.ph2.st2.t2

<small>WorkSequence: `ws.e2e.ph2.st2.t1.e2e.ph2.st2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.ph2.st2.t2 → e2e.ph2.st2.t3

<small>WorkSequence: `ws.e2e.ph2.st2.t2.e2e.ph2.st2.t3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### e2e.p7.artifact-completion → e2e.p0.increment-planning

<small>WorkSequence: `ws.engine-loop.e2e.p7.artifact-completion.e2e.p0.increment-planning` · finishToStart</small>

After increment acceptance, either start the next capability slice or leave the engine for release assembly. Any downstream finding re-enters the smallest affected child process through the recorded change workflow.

**Condition:** next-increment-or-release-scope-open

### e2e.p3.pim-refinement → e2e.p1.cim-modeling

<small>WorkSequence: `ws.rework.e2e.loop.pim-feedback-cim` · finishToStart</small>

Extend CIM via change workflow; re-run CIM EVL before re-transform.

**Condition:** PIM refinement reveals missing CIM concepts for the increment.

### e2e.p5.psm-refinement → e2e.p3.pim-refinement

<small>WorkSequence: `ws.rework.e2e.loop.psm-feedback-pim` · finishToStart</small>

Adjust PIM policies or integration; re-run PIM→PSM ETL.

**Condition:** AWS constraints require PIM architecture changes.

### e2e.p7.artifact-completion → e2e.p5.psm-refinement

<small>WorkSequence: `ws.rework.e2e.loop.artifact-feedback-psm` · finishToStart</small>

Fix PSM at source stage; regenerate artifacts.

**Condition:** Generated artifacts fail validation or deployment dry-run.

### e2e.rel.a2 → modriss.operations-maintenance

<small>WorkSequence: `ws.handover.e2e.rel.a2.e2e.ops` · finishToStart</small>

At G7, the promoted release becomes an accepted live baseline owned by the Operations and Maintenance Process. This starts that ongoing process for the first release and updates its live-release set for later releases.

**Condition:** G6-authorized-and-G7-transitioned

### modriss.operations-maintenance → e2e.p0.increment-planning

<small>WorkSequence: `ws.ops-change.e2e.ops.e2e.p0` · finishToStart</small>

Route product-changing maintenance to the earliest authoritative Development and Delivery activity. Operations continues for the accepted baseline while the change is engineered, qualified, released, and returned with evidence.

**Condition:** service-item-requires-product-change

### e2e.rel.a2 → e2e.p0.increment-planning

<small>WorkSequence: `ws.release-rework.e2e.rel.a2.e2e.p0` · finishToStart</small>

Retain the last accepted operating baseline and return corrective work to the earliest affected delivery activity before requalification.

**Condition:** G6-rejected-or-promotion-failed

### e2e.rel.a3 → e2e.p0.increment-planning

<small>WorkSequence: `ws.release-cycle.e2e.rel.a3.e2e.p0` · finishToStart</small>

Start the next planned release from roadmap demand or service work deliberately committed to planned delivery; Operations and Maintenance continues for all accepted live baselines.

**Condition:** next-release-selected-and-retirement-not-authorized

## Task performer and parameter bindings

### e2e.ph0.st1.t1

<small>TaskUse of `task.e2e.ph0.st1.t1`</small>

**Process performers**

- `ppf.e2e.ph0.st1.t1.ru.e2e.ph0.st1.product-owner`: primary RoleUse `ru.e2e.ph0.st1.product-owner`
- `ppf.e2e.ph0.st1.t1.ru.e2e.ph0.st1.sponsor`: supporting RoleUse `ru.e2e.ph0.st1.sponsor`
- `ppf.e2e.ph0.st1.t1.ru.e2e.ph0.st1.domain-expert`: supporting RoleUse `ru.e2e.ph0.st1.domain-expert`
- `ppf.e2e.ph0.st1.t1.ru.e2e.ph0.st1.service-owner`: supporting RoleUse `ru.e2e.ph0.st1.service-owner`

**Process parameters**

- `pp.e2e.ph0.st1.t1.out.wpu.e2e.ph0.st1.t1.output.e2e-artifact.product-charter`: **out** WorkProductUse `wpu.e2e.ph0.st1.t1.output.e2e-artifact.product-charter`

### e2e.ph0.st1.t2

<small>TaskUse of `task.e2e.ph0.st1.t2`</small>

**Process performers**

- `ppf.e2e.ph0.st1.t2.ru.e2e.ph0.st1.requirements-engineer`: primary RoleUse `ru.e2e.ph0.st1.requirements-engineer`
- `ppf.e2e.ph0.st1.t2.ru.e2e.ph0.st1.product-owner`: supporting RoleUse `ru.e2e.ph0.st1.product-owner`
- `ppf.e2e.ph0.st1.t2.ru.e2e.ph0.st1.domain-expert`: supporting RoleUse `ru.e2e.ph0.st1.domain-expert`
- `ppf.e2e.ph0.st1.t2.ru.e2e.ph0.st1.solution-architect`: supporting RoleUse `ru.e2e.ph0.st1.solution-architect`
- `ppf.e2e.ph0.st1.t2.ru.e2e.ph0.st1.delivery-lead`: supporting RoleUse `ru.e2e.ph0.st1.delivery-lead`

**Process parameters**

- `pp.e2e.ph0.st1.t2.in.wpu.e2e.ph0.st1.t2.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.ph0.st1.t2.input.e2e-artifact.product-charter`
- `pp.e2e.ph0.st1.t2.out.wpu.e2e.ph0.st1.t2.output.e2e-artifact.product-charter`: **out** WorkProductUse `wpu.e2e.ph0.st1.t2.output.e2e-artifact.product-charter`
- `pp.e2e.ph0.st1.t2.out.wpu.e2e.ph0.st1.t2.output.e2e-artifact.increment-record`: **out** WorkProductUse `wpu.e2e.ph0.st1.t2.output.e2e-artifact.increment-record`

### e2e.ph0.st1a.t1

<small>TaskUse of `task.e2e.ph0.st1a.t1`</small>

**Process performers**

- `ppf.e2e.ph0.st1a.t1.ru.e2e.ph0.st1a.solution-architect`: primary RoleUse `ru.e2e.ph0.st1a.solution-architect`
- `ppf.e2e.ph0.st1a.t1.ru.e2e.ph0.st1a.product-owner`: supporting RoleUse `ru.e2e.ph0.st1a.product-owner`
- `ppf.e2e.ph0.st1a.t1.ru.e2e.ph0.st1a.domain-expert`: supporting RoleUse `ru.e2e.ph0.st1a.domain-expert`
- `ppf.e2e.ph0.st1a.t1.ru.e2e.ph0.st1a.cloud-platform-engineer`: supporting RoleUse `ru.e2e.ph0.st1a.cloud-platform-engineer`
- `ppf.e2e.ph0.st1a.t1.ru.e2e.ph0.st1a.security-engineer`: supporting RoleUse `ru.e2e.ph0.st1a.security-engineer`
- `ppf.e2e.ph0.st1a.t1.ru.e2e.ph0.st1a.quality-engineer`: supporting RoleUse `ru.e2e.ph0.st1a.quality-engineer`
- `ppf.e2e.ph0.st1a.t1.ru.e2e.ph0.st1a.service-owner`: supporting RoleUse `ru.e2e.ph0.st1a.service-owner`
- `ppf.e2e.ph0.st1a.t1.ru.e2e.ph0.st1a.finops-cost-analyst`: supporting RoleUse `ru.e2e.ph0.st1a.finops-cost-analyst`

**Process parameters**

- `pp.e2e.ph0.st1a.t1.in.wpu.e2e.ph0.st1a.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.ph0.st1a.t1.input.e2e-artifact.product-charter`
- `pp.e2e.ph0.st1a.t1.out.wpu.e2e.ph0.st1a.t1.output.e2e-artifact.serverless-suitability`: **out** WorkProductUse `wpu.e2e.ph0.st1a.t1.output.e2e-artifact.serverless-suitability`
- `pp.e2e.ph0.st1a.t1.out.wpu.e2e.ph0.st1a.t1.output.e2e-artifact.risk-register`: **out** WorkProductUse `wpu.e2e.ph0.st1a.t1.output.e2e-artifact.risk-register`

### e2e.ph0.st1a.t2

<small>TaskUse of `task.e2e.ph0.st1a.t2`</small>

**Process performers**

- `ppf.e2e.ph0.st1a.t2.ru.e2e.ph0.st1a.finops-cost-analyst`: primary RoleUse `ru.e2e.ph0.st1a.finops-cost-analyst`
- `ppf.e2e.ph0.st1a.t2.ru.e2e.ph0.st1a.product-owner`: supporting RoleUse `ru.e2e.ph0.st1a.product-owner`
- `ppf.e2e.ph0.st1a.t2.ru.e2e.ph0.st1a.solution-architect`: supporting RoleUse `ru.e2e.ph0.st1a.solution-architect`
- `ppf.e2e.ph0.st1a.t2.ru.e2e.ph0.st1a.cloud-platform-engineer`: supporting RoleUse `ru.e2e.ph0.st1a.cloud-platform-engineer`
- `ppf.e2e.ph0.st1a.t2.ru.e2e.ph0.st1a.service-owner`: supporting RoleUse `ru.e2e.ph0.st1a.service-owner`

**Process parameters**

- `pp.e2e.ph0.st1a.t2.in.wpu.e2e.ph0.st1a.t2.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.ph0.st1a.t2.input.e2e-artifact.product-charter`
- `pp.e2e.ph0.st1a.t2.in.wpu.e2e.ph0.st1a.t2.input.e2e-artifact.serverless-suitability`: **in** WorkProductUse `wpu.e2e.ph0.st1a.t2.input.e2e-artifact.serverless-suitability`
- `pp.e2e.ph0.st1a.t2.out.wpu.e2e.ph0.st1a.t2.output.e2e-artifact.cost-model`: **out** WorkProductUse `wpu.e2e.ph0.st1a.t2.output.e2e-artifact.cost-model`
- `pp.e2e.ph0.st1a.t2.out.wpu.e2e.ph0.st1a.t2.output.e2e-artifact.risk-register`: **out** WorkProductUse `wpu.e2e.ph0.st1a.t2.output.e2e-artifact.risk-register`

### e2e.ph0.st1a.t3

<small>TaskUse of `task.e2e.ph0.st1a.t3`</small>

**Process performers**

- `ppf.e2e.ph0.st1a.t3.ru.e2e.ph0.st1a.sponsor`: primary RoleUse `ru.e2e.ph0.st1a.sponsor`
- `ppf.e2e.ph0.st1a.t3.ru.e2e.ph0.st1a.product-owner`: supporting RoleUse `ru.e2e.ph0.st1a.product-owner`
- `ppf.e2e.ph0.st1a.t3.ru.e2e.ph0.st1a.solution-architect`: supporting RoleUse `ru.e2e.ph0.st1a.solution-architect`
- `ppf.e2e.ph0.st1a.t3.ru.e2e.ph0.st1a.security-engineer`: supporting RoleUse `ru.e2e.ph0.st1a.security-engineer`
- `ppf.e2e.ph0.st1a.t3.ru.e2e.ph0.st1a.service-owner`: supporting RoleUse `ru.e2e.ph0.st1a.service-owner`
- `ppf.e2e.ph0.st1a.t3.ru.e2e.ph0.st1a.finops-cost-analyst`: supporting RoleUse `ru.e2e.ph0.st1a.finops-cost-analyst`

**Process parameters**

- `pp.e2e.ph0.st1a.t3.in.wpu.e2e.ph0.st1a.t3.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.ph0.st1a.t3.input.e2e-artifact.product-charter`
- `pp.e2e.ph0.st1a.t3.in.wpu.e2e.ph0.st1a.t3.input.e2e-artifact.serverless-suitability`: **in** WorkProductUse `wpu.e2e.ph0.st1a.t3.input.e2e-artifact.serverless-suitability`
- `pp.e2e.ph0.st1a.t3.in.wpu.e2e.ph0.st1a.t3.input.e2e-artifact.cost-model`: **in** WorkProductUse `wpu.e2e.ph0.st1a.t3.input.e2e-artifact.cost-model`
- `pp.e2e.ph0.st1a.t3.in.wpu.e2e.ph0.st1a.t3.input.e2e-artifact.risk-register`: **in** WorkProductUse `wpu.e2e.ph0.st1a.t3.input.e2e-artifact.risk-register`
- `pp.e2e.ph0.st1a.t3.out.wpu.e2e.ph0.st1a.t3.output.e2e-artifact.product-charter`: **out** WorkProductUse `wpu.e2e.ph0.st1a.t3.output.e2e-artifact.product-charter`
- `pp.e2e.ph0.st1a.t3.out.wpu.e2e.ph0.st1a.t3.output.e2e-artifact.serverless-suitability`: **out** WorkProductUse `wpu.e2e.ph0.st1a.t3.output.e2e-artifact.serverless-suitability`
- `pp.e2e.ph0.st1a.t3.out.wpu.e2e.ph0.st1a.t3.output.e2e-artifact.cost-model`: **out** WorkProductUse `wpu.e2e.ph0.st1a.t3.output.e2e-artifact.cost-model`
- `pp.e2e.ph0.st1a.t3.out.wpu.e2e.ph0.st1a.t3.output.e2e-artifact.risk-register`: **out** WorkProductUse `wpu.e2e.ph0.st1a.t3.output.e2e-artifact.risk-register`

### e2e.ph0.st2.t1

<small>TaskUse of `task.e2e.ph0.st2.t1`</small>

**Process performers**

- `ppf.e2e.ph0.st2.t1.ru.e2e.ph0.st2.method-engineer`: primary RoleUse `ru.e2e.ph0.st2.method-engineer`
- `ppf.e2e.ph0.st2.t1.ru.e2e.ph0.st2.delivery-lead`: supporting RoleUse `ru.e2e.ph0.st2.delivery-lead`
- `ppf.e2e.ph0.st2.t1.ru.e2e.ph0.st2.process-reviewer`: supporting RoleUse `ru.e2e.ph0.st2.process-reviewer`

**Process parameters**

- `pp.e2e.ph0.st2.t1.in.wpu.e2e.ph0.st2.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.ph0.st2.t1.input.e2e-artifact.product-charter`
- `pp.e2e.ph0.st2.t1.in.wpu.e2e.ph0.st2.t1.input.e2e-artifact.increment-record`: **in** WorkProductUse `wpu.e2e.ph0.st2.t1.input.e2e-artifact.increment-record`
- `pp.e2e.ph0.st2.t1.out.wpu.e2e.ph0.st2.t1.output.e2e-artifact.method-profile`: **out** WorkProductUse `wpu.e2e.ph0.st2.t1.output.e2e-artifact.method-profile`
- `pp.e2e.ph0.st2.t1.out.wpu.e2e.ph0.st2.t1.output.e2e-artifact.risk-register`: **out** WorkProductUse `wpu.e2e.ph0.st2.t1.output.e2e-artifact.risk-register`

### e2e.ph0.st2.t2

<small>TaskUse of `task.e2e.ph0.st2.t2`</small>

**Process performers**

- `ppf.e2e.ph0.st2.t2.ru.e2e.ph0.st2.process-reviewer`: primary RoleUse `ru.e2e.ph0.st2.process-reviewer`
- `ppf.e2e.ph0.st2.t2.ru.e2e.ph0.st2.method-engineer`: supporting RoleUse `ru.e2e.ph0.st2.method-engineer`
- `ppf.e2e.ph0.st2.t2.ru.e2e.ph0.st2.quality-engineer`: supporting RoleUse `ru.e2e.ph0.st2.quality-engineer`
- `ppf.e2e.ph0.st2.t2.ru.e2e.ph0.st2.security-engineer`: supporting RoleUse `ru.e2e.ph0.st2.security-engineer`
- `ppf.e2e.ph0.st2.t2.ru.e2e.ph0.st2.service-owner`: supporting RoleUse `ru.e2e.ph0.st2.service-owner`

**Process parameters**

- `pp.e2e.ph0.st2.t2.in.wpu.e2e.ph0.st2.t2.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.ph0.st2.t2.input.e2e-artifact.method-profile`
- `pp.e2e.ph0.st2.t2.in.wpu.e2e.ph0.st2.t2.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.ph0.st2.t2.input.e2e-artifact.product-charter`
- `pp.e2e.ph0.st2.t2.in.wpu.e2e.ph0.st2.t2.input.e2e-artifact.increment-record`: **in** WorkProductUse `wpu.e2e.ph0.st2.t2.input.e2e-artifact.increment-record`
- `pp.e2e.ph0.st2.t2.out.wpu.e2e.ph0.st2.t2.output.e2e-artifact.method-profile`: **out** WorkProductUse `wpu.e2e.ph0.st2.t2.output.e2e-artifact.method-profile`

### e2e.ph0.st3.t1

<small>TaskUse of `task.e2e.ph0.st3.t1`</small>

**Process performers**

- `ppf.e2e.ph0.st3.t1.ru.e2e.ph0.st3.delivery-lead`: primary RoleUse `ru.e2e.ph0.st3.delivery-lead`
- `ppf.e2e.ph0.st3.t1.ru.e2e.ph0.st3.product-owner`: supporting RoleUse `ru.e2e.ph0.st3.product-owner`
- `ppf.e2e.ph0.st3.t1.ru.e2e.ph0.st3.method-engineer`: supporting RoleUse `ru.e2e.ph0.st3.method-engineer`

**Process parameters**

- `pp.e2e.ph0.st3.t1.in.wpu.e2e.ph0.st3.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.ph0.st3.t1.input.e2e-artifact.method-profile`
- `pp.e2e.ph0.st3.t1.in.wpu.e2e.ph0.st3.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.ph0.st3.t1.input.e2e-artifact.product-charter`
- `pp.e2e.ph0.st3.t1.out.wpu.e2e.ph0.st3.t1.output.e2e-artifact.team-topology`: **out** WorkProductUse `wpu.e2e.ph0.st3.t1.output.e2e-artifact.team-topology`

### e2e.ph0.st3.t2

<small>TaskUse of `task.e2e.ph0.st3.t2`</small>

**Process performers**

- `ppf.e2e.ph0.st3.t2.ru.e2e.ph0.st3.delivery-lead`: primary RoleUse `ru.e2e.ph0.st3.delivery-lead`

**Process parameters**

- `pp.e2e.ph0.st3.t2.in.wpu.e2e.ph0.st3.t2.input.e2e-artifact.team-topology`: **in** WorkProductUse `wpu.e2e.ph0.st3.t2.input.e2e-artifact.team-topology`
- `pp.e2e.ph0.st3.t2.in.wpu.e2e.ph0.st3.t2.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.ph0.st3.t2.input.e2e-artifact.method-profile`
- `pp.e2e.ph0.st3.t2.in.wpu.e2e.ph0.st3.t2.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.ph0.st3.t2.input.e2e-artifact.product-charter`
- `pp.e2e.ph0.st3.t2.out.wpu.e2e.ph0.st3.t2.output.e2e-artifact.team-topology`: **out** WorkProductUse `wpu.e2e.ph0.st3.t2.output.e2e-artifact.team-topology`
- `pp.e2e.ph0.st3.t2.out.wpu.e2e.ph0.st3.t2.output.e2e-artifact.method-profile`: **out** WorkProductUse `wpu.e2e.ph0.st3.t2.output.e2e-artifact.method-profile`

### e2e.ph0.st4.t1

<small>TaskUse of `task.e2e.ph0.st4.t1`</small>

**Process performers**

- `ppf.e2e.ph0.st4.t1.ru.e2e.ph0.st4.security-engineer`: primary RoleUse `ru.e2e.ph0.st4.security-engineer`
- `ppf.e2e.ph0.st4.t1.ru.e2e.ph0.st4.quality-engineer`: supporting RoleUse `ru.e2e.ph0.st4.quality-engineer`
- `ppf.e2e.ph0.st4.t1.ru.e2e.ph0.st4.service-owner`: supporting RoleUse `ru.e2e.ph0.st4.service-owner`
- `ppf.e2e.ph0.st4.t1.ru.e2e.ph0.st4.records-data-steward`: supporting RoleUse `ru.e2e.ph0.st4.records-data-steward`

**Process parameters**

- `pp.e2e.ph0.st4.t1.in.wpu.e2e.ph0.st4.t1.input.e2e-artifact.team-topology`: **in** WorkProductUse `wpu.e2e.ph0.st4.t1.input.e2e-artifact.team-topology`
- `pp.e2e.ph0.st4.t1.in.wpu.e2e.ph0.st4.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.ph0.st4.t1.input.e2e-artifact.method-profile`
- `pp.e2e.ph0.st4.t1.in.wpu.e2e.ph0.st4.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.ph0.st4.t1.input.e2e-artifact.product-charter`
- `pp.e2e.ph0.st4.t1.in.wpu.e2e.ph0.st4.t1.input.e2e-artifact.risk-register`: **in** WorkProductUse `wpu.e2e.ph0.st4.t1.input.e2e-artifact.risk-register`
- `pp.e2e.ph0.st4.t1.out.wpu.e2e.ph0.st4.t1.output.e2e-artifact.method-profile`: **out** WorkProductUse `wpu.e2e.ph0.st4.t1.output.e2e-artifact.method-profile`
- `pp.e2e.ph0.st4.t1.out.wpu.e2e.ph0.st4.t1.output.e2e-artifact.risk-register`: **out** WorkProductUse `wpu.e2e.ph0.st4.t1.output.e2e-artifact.risk-register`

### e2e.ph0.st4.t2

<small>TaskUse of `task.e2e.ph0.st4.t2`</small>

**Process performers**

- `ppf.e2e.ph0.st4.t2.ru.e2e.ph0.st4.service-owner`: primary RoleUse `ru.e2e.ph0.st4.service-owner`
- `ppf.e2e.ph0.st4.t2.ru.e2e.ph0.st4.release-engineer`: supporting RoleUse `ru.e2e.ph0.st4.release-engineer`
- `ppf.e2e.ph0.st4.t2.ru.e2e.ph0.st4.finops-cost-analyst`: supporting RoleUse `ru.e2e.ph0.st4.finops-cost-analyst`
- `ppf.e2e.ph0.st4.t2.ru.e2e.ph0.st4.records-data-steward`: supporting RoleUse `ru.e2e.ph0.st4.records-data-steward`
- `ppf.e2e.ph0.st4.t2.ru.e2e.ph0.st4.security-engineer`: supporting RoleUse `ru.e2e.ph0.st4.security-engineer`

**Process parameters**

- `pp.e2e.ph0.st4.t2.in.wpu.e2e.ph0.st4.t2.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.ph0.st4.t2.input.e2e-artifact.method-profile`
- `pp.e2e.ph0.st4.t2.in.wpu.e2e.ph0.st4.t2.input.e2e-artifact.team-topology`: **in** WorkProductUse `wpu.e2e.ph0.st4.t2.input.e2e-artifact.team-topology`
- `pp.e2e.ph0.st4.t2.in.wpu.e2e.ph0.st4.t2.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.ph0.st4.t2.input.e2e-artifact.product-charter`
- `pp.e2e.ph0.st4.t2.in.wpu.e2e.ph0.st4.t2.input.e2e-artifact.cost-model`: **in** WorkProductUse `wpu.e2e.ph0.st4.t2.input.e2e-artifact.cost-model`
- `pp.e2e.ph0.st4.t2.in.wpu.e2e.ph0.st4.t2.input.e2e-artifact.risk-register`: **in** WorkProductUse `wpu.e2e.ph0.st4.t2.input.e2e-artifact.risk-register`
- `pp.e2e.ph0.st4.t2.out.wpu.e2e.ph0.st4.t2.output.e2e-artifact.product-charter`: **out** WorkProductUse `wpu.e2e.ph0.st4.t2.output.e2e-artifact.product-charter`
- `pp.e2e.ph0.st4.t2.out.wpu.e2e.ph0.st4.t2.output.e2e-artifact.method-profile`: **out** WorkProductUse `wpu.e2e.ph0.st4.t2.output.e2e-artifact.method-profile`
- `pp.e2e.ph0.st4.t2.out.wpu.e2e.ph0.st4.t2.output.e2e-artifact.service-flow-system`: **out** WorkProductUse `wpu.e2e.ph0.st4.t2.output.e2e-artifact.service-flow-system`

### e2e.p0.increment-planning.t1

<small>TaskUse of `task.e2e.p0.increment-planning.t1`</small>

**Process performers**

- `ppf.e2e.p0.increment-planning.t1.ru.e2e.p0.increment-planning.product-owner`: primary RoleUse `ru.e2e.p0.increment-planning.product-owner`
- `ppf.e2e.p0.increment-planning.t1.ru.e2e.p0.increment-planning.delivery-lead`: supporting RoleUse `ru.e2e.p0.increment-planning.delivery-lead`
- `ppf.e2e.p0.increment-planning.t1.ru.e2e.p0.increment-planning.requirements-engineer`: supporting RoleUse `ru.e2e.p0.increment-planning.requirements-engineer`
- `ppf.e2e.p0.increment-planning.t1.ru.e2e.p0.increment-planning.domain-expert`: supporting RoleUse `ru.e2e.p0.increment-planning.domain-expert`
- `ppf.e2e.p0.increment-planning.t1.ru.e2e.p0.increment-planning.finops-cost-analyst`: supporting RoleUse `ru.e2e.p0.increment-planning.finops-cost-analyst`

**Process parameters**

- `pp.e2e.p0.increment-planning.t1.in.wpu.e2e.p0.increment-planning.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.p0.increment-planning.t1.input.e2e-artifact.product-charter`
- `pp.e2e.p0.increment-planning.t1.in.wpu.e2e.p0.increment-planning.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.p0.increment-planning.t1.input.e2e-artifact.method-profile`
- `pp.e2e.p0.increment-planning.t1.in.wpu.e2e.p0.increment-planning.t1.input.e2e-artifact.team-topology`: **in** WorkProductUse `wpu.e2e.p0.increment-planning.t1.input.e2e-artifact.team-topology`
- `pp.e2e.p0.increment-planning.t1.in.wpu.e2e.p0.increment-planning.t1.input.e2e-artifact.cost-model`: **in** WorkProductUse `wpu.e2e.p0.increment-planning.t1.input.e2e-artifact.cost-model`
- `pp.e2e.p0.increment-planning.t1.in.wpu.e2e.p0.increment-planning.t1.input.e2e-artifact.risk-register`: **in** WorkProductUse `wpu.e2e.p0.increment-planning.t1.input.e2e-artifact.risk-register`
- `pp.e2e.p0.increment-planning.t1.out.wpu.e2e.p0.increment-planning.t1.output.e2e-artifact.increment-record`: **out** WorkProductUse `wpu.e2e.p0.increment-planning.t1.output.e2e-artifact.increment-record`
- `pp.e2e.p0.increment-planning.t1.out.wpu.e2e.p0.increment-planning.t1.output.e2e-artifact.risk-register`: **out** WorkProductUse `wpu.e2e.p0.increment-planning.t1.output.e2e-artifact.risk-register`

### e2e.p1.cim-modeling.t1

<small>TaskUse of `task.e2e.p1.cim-modeling.t1`</small>

**Process performers**

- `ppf.e2e.p1.cim-modeling.t1.ru.e2e.p1.cim-modeling.business-modeler`: primary RoleUse `ru.e2e.p1.cim-modeling.business-modeler`
- `ppf.e2e.p1.cim-modeling.t1.ru.e2e.p1.cim-modeling.requirements-engineer`: supporting RoleUse `ru.e2e.p1.cim-modeling.requirements-engineer`
- `ppf.e2e.p1.cim-modeling.t1.ru.e2e.p1.cim-modeling.domain-expert`: supporting RoleUse `ru.e2e.p1.cim-modeling.domain-expert`
- `ppf.e2e.p1.cim-modeling.t1.ru.e2e.p1.cim-modeling.product-owner`: supporting RoleUse `ru.e2e.p1.cim-modeling.product-owner`
- `ppf.e2e.p1.cim-modeling.t1.ru.e2e.p1.cim-modeling.security-engineer`: supporting RoleUse `ru.e2e.p1.cim-modeling.security-engineer`

**Process parameters**

- `pp.e2e.p1.cim-modeling.t1.in.wpu.e2e.p1.cim-modeling.t1.input.e2e-artifact.increment-record`: **in** WorkProductUse `wpu.e2e.p1.cim-modeling.t1.input.e2e-artifact.increment-record`
- `pp.e2e.p1.cim-modeling.t1.in.wpu.e2e.p1.cim-modeling.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.p1.cim-modeling.t1.input.e2e-artifact.product-charter`
- `pp.e2e.p1.cim-modeling.t1.in.wpu.e2e.p1.cim-modeling.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.p1.cim-modeling.t1.input.e2e-artifact.method-profile`
- `pp.e2e.p1.cim-modeling.t1.in.wpu.e2e.p1.cim-modeling.t1.input.e2e-artifact.team-topology`: **in** WorkProductUse `wpu.e2e.p1.cim-modeling.t1.input.e2e-artifact.team-topology`
- `pp.e2e.p1.cim-modeling.t1.out.wpu.e2e.p1.cim-modeling.t1.output.e2e-artifact.increment-record`: **out** WorkProductUse `wpu.e2e.p1.cim-modeling.t1.output.e2e-artifact.increment-record`

### e2e.p2.cim-to-pim.t1

<small>TaskUse of `task.e2e.p2.cim-to-pim.t1`</small>

**Process performers**

- `ppf.e2e.p2.cim-to-pim.t1.ru.e2e.p2.cim-to-pim.solution-architect`: primary RoleUse `ru.e2e.p2.cim-to-pim.solution-architect`

**Process parameters**

- `pp.e2e.p2.cim-to-pim.t1.in.wpu.e2e.p2.cim-to-pim.t1.input.e2e-artifact.increment-record`: **in** WorkProductUse `wpu.e2e.p2.cim-to-pim.t1.input.e2e-artifact.increment-record`
- `pp.e2e.p2.cim-to-pim.t1.in.wpu.e2e.p2.cim-to-pim.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.p2.cim-to-pim.t1.input.e2e-artifact.method-profile`
- `pp.e2e.p2.cim-to-pim.t1.in.wpu.e2e.p2.cim-to-pim.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.p2.cim-to-pim.t1.input.e2e-artifact.product-charter`
- `pp.e2e.p2.cim-to-pim.t1.out.wpu.e2e.p2.cim-to-pim.t1.output.e2e-artifact.increment-record`: **out** WorkProductUse `wpu.e2e.p2.cim-to-pim.t1.output.e2e-artifact.increment-record`

### e2e.p3.pim-refinement.t1

<small>TaskUse of `task.e2e.p3.pim-refinement.t1`</small>

**Process performers**

- `ppf.e2e.p3.pim-refinement.t1.ru.e2e.p3.pim-refinement.solution-architect`: primary RoleUse `ru.e2e.p3.pim-refinement.solution-architect`
- `ppf.e2e.p3.pim-refinement.t1.ru.e2e.p3.pim-refinement.security-engineer`: supporting RoleUse `ru.e2e.p3.pim-refinement.security-engineer`
- `ppf.e2e.p3.pim-refinement.t1.ru.e2e.p3.pim-refinement.quality-engineer`: supporting RoleUse `ru.e2e.p3.pim-refinement.quality-engineer`
- `ppf.e2e.p3.pim-refinement.t1.ru.e2e.p3.pim-refinement.cloud-platform-engineer`: supporting RoleUse `ru.e2e.p3.pim-refinement.cloud-platform-engineer`
- `ppf.e2e.p3.pim-refinement.t1.ru.e2e.p3.pim-refinement.service-owner`: supporting RoleUse `ru.e2e.p3.pim-refinement.service-owner`
- `ppf.e2e.p3.pim-refinement.t1.ru.e2e.p3.pim-refinement.finops-cost-analyst`: supporting RoleUse `ru.e2e.p3.pim-refinement.finops-cost-analyst`

**Process parameters**

- `pp.e2e.p3.pim-refinement.t1.in.wpu.e2e.p3.pim-refinement.t1.input.e2e-artifact.increment-record`: **in** WorkProductUse `wpu.e2e.p3.pim-refinement.t1.input.e2e-artifact.increment-record`
- `pp.e2e.p3.pim-refinement.t1.in.wpu.e2e.p3.pim-refinement.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.p3.pim-refinement.t1.input.e2e-artifact.product-charter`
- `pp.e2e.p3.pim-refinement.t1.in.wpu.e2e.p3.pim-refinement.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.p3.pim-refinement.t1.input.e2e-artifact.method-profile`
- `pp.e2e.p3.pim-refinement.t1.out.wpu.e2e.p3.pim-refinement.t1.output.e2e-artifact.increment-record`: **out** WorkProductUse `wpu.e2e.p3.pim-refinement.t1.output.e2e-artifact.increment-record`
- `pp.e2e.p3.pim-refinement.t1.out.wpu.e2e.p3.pim-refinement.t1.output.e2e-artifact.risk-register`: **out** WorkProductUse `wpu.e2e.p3.pim-refinement.t1.output.e2e-artifact.risk-register`

### e2e.p4.pim-to-psm.t1

<small>TaskUse of `task.e2e.p4.pim-to-psm.t1`</small>

**Process performers**

- `ppf.e2e.p4.pim-to-psm.t1.ru.e2e.p4.pim-to-psm.cloud-platform-engineer`: primary RoleUse `ru.e2e.p4.pim-to-psm.cloud-platform-engineer`

**Process parameters**

- `pp.e2e.p4.pim-to-psm.t1.in.wpu.e2e.p4.pim-to-psm.t1.input.e2e-artifact.increment-record`: **in** WorkProductUse `wpu.e2e.p4.pim-to-psm.t1.input.e2e-artifact.increment-record`
- `pp.e2e.p4.pim-to-psm.t1.in.wpu.e2e.p4.pim-to-psm.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.p4.pim-to-psm.t1.input.e2e-artifact.method-profile`
- `pp.e2e.p4.pim-to-psm.t1.in.wpu.e2e.p4.pim-to-psm.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.p4.pim-to-psm.t1.input.e2e-artifact.product-charter`
- `pp.e2e.p4.pim-to-psm.t1.out.wpu.e2e.p4.pim-to-psm.t1.output.e2e-artifact.increment-record`: **out** WorkProductUse `wpu.e2e.p4.pim-to-psm.t1.output.e2e-artifact.increment-record`

### e2e.p5.psm-refinement.t1

<small>TaskUse of `task.e2e.p5.psm-refinement.t1`</small>

**Process performers**

- `ppf.e2e.p5.psm-refinement.t1.ru.e2e.p5.psm-refinement.cloud-platform-engineer`: primary RoleUse `ru.e2e.p5.psm-refinement.cloud-platform-engineer`
- `ppf.e2e.p5.psm-refinement.t1.ru.e2e.p5.psm-refinement.solution-architect`: supporting RoleUse `ru.e2e.p5.psm-refinement.solution-architect`
- `ppf.e2e.p5.psm-refinement.t1.ru.e2e.p5.psm-refinement.security-engineer`: supporting RoleUse `ru.e2e.p5.psm-refinement.security-engineer`
- `ppf.e2e.p5.psm-refinement.t1.ru.e2e.p5.psm-refinement.quality-engineer`: supporting RoleUse `ru.e2e.p5.psm-refinement.quality-engineer`
- `ppf.e2e.p5.psm-refinement.t1.ru.e2e.p5.psm-refinement.release-engineer`: supporting RoleUse `ru.e2e.p5.psm-refinement.release-engineer`
- `ppf.e2e.p5.psm-refinement.t1.ru.e2e.p5.psm-refinement.service-owner`: supporting RoleUse `ru.e2e.p5.psm-refinement.service-owner`
- `ppf.e2e.p5.psm-refinement.t1.ru.e2e.p5.psm-refinement.finops-cost-analyst`: supporting RoleUse `ru.e2e.p5.psm-refinement.finops-cost-analyst`

**Process parameters**

- `pp.e2e.p5.psm-refinement.t1.in.wpu.e2e.p5.psm-refinement.t1.input.e2e-artifact.increment-record`: **in** WorkProductUse `wpu.e2e.p5.psm-refinement.t1.input.e2e-artifact.increment-record`
- `pp.e2e.p5.psm-refinement.t1.in.wpu.e2e.p5.psm-refinement.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.p5.psm-refinement.t1.input.e2e-artifact.method-profile`
- `pp.e2e.p5.psm-refinement.t1.in.wpu.e2e.p5.psm-refinement.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.p5.psm-refinement.t1.input.e2e-artifact.product-charter`
- `pp.e2e.p5.psm-refinement.t1.out.wpu.e2e.p5.psm-refinement.t1.output.e2e-artifact.increment-record`: **out** WorkProductUse `wpu.e2e.p5.psm-refinement.t1.output.e2e-artifact.increment-record`
- `pp.e2e.p5.psm-refinement.t1.out.wpu.e2e.p5.psm-refinement.t1.output.e2e-artifact.cost-model`: **out** WorkProductUse `wpu.e2e.p5.psm-refinement.t1.output.e2e-artifact.cost-model`
- `pp.e2e.p5.psm-refinement.t1.out.wpu.e2e.p5.psm-refinement.t1.output.e2e-artifact.risk-register`: **out** WorkProductUse `wpu.e2e.p5.psm-refinement.t1.output.e2e-artifact.risk-register`

### e2e.p6.m2t-generation.t1

<small>TaskUse of `task.e2e.p6.m2t-generation.t1`</small>

**Process performers**

- `ppf.e2e.p6.m2t-generation.t1.ru.e2e.p6.m2t-generation.cloud-platform-engineer`: primary RoleUse `ru.e2e.p6.m2t-generation.cloud-platform-engineer`

**Process parameters**

- `pp.e2e.p6.m2t-generation.t1.in.wpu.e2e.p6.m2t-generation.t1.input.e2e-artifact.increment-record`: **in** WorkProductUse `wpu.e2e.p6.m2t-generation.t1.input.e2e-artifact.increment-record`
- `pp.e2e.p6.m2t-generation.t1.in.wpu.e2e.p6.m2t-generation.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.p6.m2t-generation.t1.input.e2e-artifact.method-profile`
- `pp.e2e.p6.m2t-generation.t1.in.wpu.e2e.p6.m2t-generation.t1.input.e2e-artifact.team-topology`: **in** WorkProductUse `wpu.e2e.p6.m2t-generation.t1.input.e2e-artifact.team-topology`
- `pp.e2e.p6.m2t-generation.t1.out.wpu.e2e.p6.m2t-generation.t1.output.e2e-artifact.increment-record`: **out** WorkProductUse `wpu.e2e.p6.m2t-generation.t1.output.e2e-artifact.increment-record`

### e2e.p7.artifact-completion.t1

<small>TaskUse of `task.e2e.p7.artifact-completion.t1`</small>

**Process performers**

- `ppf.e2e.p7.artifact-completion.t1.ru.e2e.p7.artifact-completion.process-reviewer`: primary RoleUse `ru.e2e.p7.artifact-completion.process-reviewer`
- `ppf.e2e.p7.artifact-completion.t1.ru.e2e.p7.artifact-completion.product-owner`: supporting RoleUse `ru.e2e.p7.artifact-completion.product-owner`
- `ppf.e2e.p7.artifact-completion.t1.ru.e2e.p7.artifact-completion.quality-engineer`: supporting RoleUse `ru.e2e.p7.artifact-completion.quality-engineer`
- `ppf.e2e.p7.artifact-completion.t1.ru.e2e.p7.artifact-completion.security-engineer`: supporting RoleUse `ru.e2e.p7.artifact-completion.security-engineer`
- `ppf.e2e.p7.artifact-completion.t1.ru.e2e.p7.artifact-completion.service-owner`: supporting RoleUse `ru.e2e.p7.artifact-completion.service-owner`
- `ppf.e2e.p7.artifact-completion.t1.ru.e2e.p7.artifact-completion.finops-cost-analyst`: supporting RoleUse `ru.e2e.p7.artifact-completion.finops-cost-analyst`

**Process parameters**

- `pp.e2e.p7.artifact-completion.t1.in.wpu.e2e.p7.artifact-completion.t1.input.e2e-artifact.increment-record`: **in** WorkProductUse `wpu.e2e.p7.artifact-completion.t1.input.e2e-artifact.increment-record`
- `pp.e2e.p7.artifact-completion.t1.in.wpu.e2e.p7.artifact-completion.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.p7.artifact-completion.t1.input.e2e-artifact.method-profile`
- `pp.e2e.p7.artifact-completion.t1.in.wpu.e2e.p7.artifact-completion.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.p7.artifact-completion.t1.input.e2e-artifact.product-charter`
- `pp.e2e.p7.artifact-completion.t1.in.wpu.e2e.p7.artifact-completion.t1.input.e2e-artifact.cost-model`: **in** WorkProductUse `wpu.e2e.p7.artifact-completion.t1.input.e2e-artifact.cost-model`
- `pp.e2e.p7.artifact-completion.t1.in.wpu.e2e.p7.artifact-completion.t1.input.e2e-artifact.risk-register`: **in** WorkProductUse `wpu.e2e.p7.artifact-completion.t1.input.e2e-artifact.risk-register`
- `pp.e2e.p7.artifact-completion.t1.out.wpu.e2e.p7.artifact-completion.t1.output.e2e-artifact.increment-record`: **out** WorkProductUse `wpu.e2e.p7.artifact-completion.t1.output.e2e-artifact.increment-record`

### e2e.p7.artifact-completion.t2

<small>TaskUse of `task.e2e.p7.artifact-completion.t2`</small>

**Process performers**

- `ppf.e2e.p7.artifact-completion.t2.ru.e2e.p7.artifact-completion.method-engineer`: primary RoleUse `ru.e2e.p7.artifact-completion.method-engineer`
- `ppf.e2e.p7.artifact-completion.t2.ru.e2e.p7.artifact-completion.delivery-lead`: supporting RoleUse `ru.e2e.p7.artifact-completion.delivery-lead`
- `ppf.e2e.p7.artifact-completion.t2.ru.e2e.p7.artifact-completion.process-reviewer`: supporting RoleUse `ru.e2e.p7.artifact-completion.process-reviewer`
- `ppf.e2e.p7.artifact-completion.t2.ru.e2e.p7.artifact-completion.product-owner`: supporting RoleUse `ru.e2e.p7.artifact-completion.product-owner`

**Process parameters**

- `pp.e2e.p7.artifact-completion.t2.in.wpu.e2e.p7.artifact-completion.t2.input.e2e-artifact.increment-record`: **in** WorkProductUse `wpu.e2e.p7.artifact-completion.t2.input.e2e-artifact.increment-record`
- `pp.e2e.p7.artifact-completion.t2.in.wpu.e2e.p7.artifact-completion.t2.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.p7.artifact-completion.t2.input.e2e-artifact.method-profile`
- `pp.e2e.p7.artifact-completion.t2.in.wpu.e2e.p7.artifact-completion.t2.input.e2e-artifact.cost-model`: **in** WorkProductUse `wpu.e2e.p7.artifact-completion.t2.input.e2e-artifact.cost-model`
- `pp.e2e.p7.artifact-completion.t2.in.wpu.e2e.p7.artifact-completion.t2.input.e2e-artifact.risk-register`: **in** WorkProductUse `wpu.e2e.p7.artifact-completion.t2.input.e2e-artifact.risk-register`
- `pp.e2e.p7.artifact-completion.t2.in.wpu.e2e.p7.artifact-completion.t2.input.e2e-artifact.improvement-record`: **in** WorkProductUse `wpu.e2e.p7.artifact-completion.t2.input.e2e-artifact.improvement-record`
- `pp.e2e.p7.artifact-completion.t2.out.wpu.e2e.p7.artifact-completion.t2.output.e2e-artifact.increment-record`: **out** WorkProductUse `wpu.e2e.p7.artifact-completion.t2.output.e2e-artifact.increment-record`
- `pp.e2e.p7.artifact-completion.t2.out.wpu.e2e.p7.artifact-completion.t2.output.e2e-artifact.method-profile`: **out** WorkProductUse `wpu.e2e.p7.artifact-completion.t2.output.e2e-artifact.method-profile`
- `pp.e2e.p7.artifact-completion.t2.out.wpu.e2e.p7.artifact-completion.t2.output.e2e-artifact.improvement-record`: **out** WorkProductUse `wpu.e2e.p7.artifact-completion.t2.output.e2e-artifact.improvement-record`

### e2e.rel.a1.t1

<small>TaskUse of `task.e2e.rel.a1.t1`</small>

**Process performers**

- `ppf.e2e.rel.a1.t1.ru.e2e.rel.a1.release-engineer`: primary RoleUse `ru.e2e.rel.a1.release-engineer`

**Process parameters**

- `pp.e2e.rel.a1.t1.in.wpu.e2e.rel.a1.t1.input.e2e-artifact.increment-record`: **in** WorkProductUse `wpu.e2e.rel.a1.t1.input.e2e-artifact.increment-record`
- `pp.e2e.rel.a1.t1.in.wpu.e2e.rel.a1.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.rel.a1.t1.input.e2e-artifact.product-charter`
- `pp.e2e.rel.a1.t1.in.wpu.e2e.rel.a1.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.rel.a1.t1.input.e2e-artifact.method-profile`
- `pp.e2e.rel.a1.t1.in.wpu.e2e.rel.a1.t1.input.e2e-artifact.team-topology`: **in** WorkProductUse `wpu.e2e.rel.a1.t1.input.e2e-artifact.team-topology`
- `pp.e2e.rel.a1.t1.out.wpu.e2e.rel.a1.t1.output.e2e-artifact.release-record`: **out** WorkProductUse `wpu.e2e.rel.a1.t1.output.e2e-artifact.release-record`

### e2e.rel.a1.t2

<small>TaskUse of `task.e2e.rel.a1.t2`</small>

**Process performers**

- `ppf.e2e.rel.a1.t2.ru.e2e.rel.a1.process-reviewer`: primary RoleUse `ru.e2e.rel.a1.process-reviewer`
- `ppf.e2e.rel.a1.t2.ru.e2e.rel.a1.product-owner`: supporting RoleUse `ru.e2e.rel.a1.product-owner`
- `ppf.e2e.rel.a1.t2.ru.e2e.rel.a1.quality-engineer`: supporting RoleUse `ru.e2e.rel.a1.quality-engineer`
- `ppf.e2e.rel.a1.t2.ru.e2e.rel.a1.security-engineer`: supporting RoleUse `ru.e2e.rel.a1.security-engineer`
- `ppf.e2e.rel.a1.t2.ru.e2e.rel.a1.service-owner`: supporting RoleUse `ru.e2e.rel.a1.service-owner`
- `ppf.e2e.rel.a1.t2.ru.e2e.rel.a1.finops-cost-analyst`: supporting RoleUse `ru.e2e.rel.a1.finops-cost-analyst`

**Process parameters**

- `pp.e2e.rel.a1.t2.in.wpu.e2e.rel.a1.t2.input.e2e-artifact.release-record`: **in** WorkProductUse `wpu.e2e.rel.a1.t2.input.e2e-artifact.release-record`
- `pp.e2e.rel.a1.t2.in.wpu.e2e.rel.a1.t2.input.e2e-artifact.increment-record`: **in** WorkProductUse `wpu.e2e.rel.a1.t2.input.e2e-artifact.increment-record`
- `pp.e2e.rel.a1.t2.in.wpu.e2e.rel.a1.t2.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.rel.a1.t2.input.e2e-artifact.product-charter`
- `pp.e2e.rel.a1.t2.in.wpu.e2e.rel.a1.t2.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.rel.a1.t2.input.e2e-artifact.method-profile`
- `pp.e2e.rel.a1.t2.in.wpu.e2e.rel.a1.t2.input.e2e-artifact.cost-model`: **in** WorkProductUse `wpu.e2e.rel.a1.t2.input.e2e-artifact.cost-model`
- `pp.e2e.rel.a1.t2.in.wpu.e2e.rel.a1.t2.input.e2e-artifact.risk-register`: **in** WorkProductUse `wpu.e2e.rel.a1.t2.input.e2e-artifact.risk-register`
- `pp.e2e.rel.a1.t2.out.wpu.e2e.rel.a1.t2.output.e2e-artifact.release-record`: **out** WorkProductUse `wpu.e2e.rel.a1.t2.output.e2e-artifact.release-record`
- `pp.e2e.rel.a1.t2.out.wpu.e2e.rel.a1.t2.output.e2e-artifact.cost-model`: **out** WorkProductUse `wpu.e2e.rel.a1.t2.output.e2e-artifact.cost-model`
- `pp.e2e.rel.a1.t2.out.wpu.e2e.rel.a1.t2.output.e2e-artifact.risk-register`: **out** WorkProductUse `wpu.e2e.rel.a1.t2.output.e2e-artifact.risk-register`

### e2e.rel.a2.t1

<small>TaskUse of `task.e2e.rel.a2.t1`</small>

**Process performers**

- `ppf.e2e.rel.a2.t1.ru.e2e.rel.a2.release-engineer`: primary RoleUse `ru.e2e.rel.a2.release-engineer`
- `ppf.e2e.rel.a2.t1.ru.e2e.rel.a2.quality-engineer`: supporting RoleUse `ru.e2e.rel.a2.quality-engineer`
- `ppf.e2e.rel.a2.t1.ru.e2e.rel.a2.security-engineer`: supporting RoleUse `ru.e2e.rel.a2.security-engineer`
- `ppf.e2e.rel.a2.t1.ru.e2e.rel.a2.service-owner`: supporting RoleUse `ru.e2e.rel.a2.service-owner`
- `ppf.e2e.rel.a2.t1.ru.e2e.rel.a2.finops-cost-analyst`: supporting RoleUse `ru.e2e.rel.a2.finops-cost-analyst`

**Process parameters**

- `pp.e2e.rel.a2.t1.in.wpu.e2e.rel.a2.t1.input.e2e-artifact.release-record`: **in** WorkProductUse `wpu.e2e.rel.a2.t1.input.e2e-artifact.release-record`
- `pp.e2e.rel.a2.t1.in.wpu.e2e.rel.a2.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.rel.a2.t1.input.e2e-artifact.product-charter`
- `pp.e2e.rel.a2.t1.in.wpu.e2e.rel.a2.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.rel.a2.t1.input.e2e-artifact.method-profile`
- `pp.e2e.rel.a2.t1.in.wpu.e2e.rel.a2.t1.input.e2e-artifact.team-topology`: **in** WorkProductUse `wpu.e2e.rel.a2.t1.input.e2e-artifact.team-topology`
- `pp.e2e.rel.a2.t1.out.wpu.e2e.rel.a2.t1.output.e2e-artifact.release-record`: **out** WorkProductUse `wpu.e2e.rel.a2.t1.output.e2e-artifact.release-record`

### e2e.rel.a2.t2

<small>TaskUse of `task.e2e.rel.a2.t2`</small>

**Process performers**

- `ppf.e2e.rel.a2.t2.ru.e2e.rel.a2.service-owner`: primary RoleUse `ru.e2e.rel.a2.service-owner`
- `ppf.e2e.rel.a2.t2.ru.e2e.rel.a2.release-engineer`: supporting RoleUse `ru.e2e.rel.a2.release-engineer`
- `ppf.e2e.rel.a2.t2.ru.e2e.rel.a2.security-engineer`: supporting RoleUse `ru.e2e.rel.a2.security-engineer`
- `ppf.e2e.rel.a2.t2.ru.e2e.rel.a2.finops-cost-analyst`: supporting RoleUse `ru.e2e.rel.a2.finops-cost-analyst`

**Process parameters**

- `pp.e2e.rel.a2.t2.in.wpu.e2e.rel.a2.t2.input.e2e-artifact.release-record`: **in** WorkProductUse `wpu.e2e.rel.a2.t2.input.e2e-artifact.release-record`
- `pp.e2e.rel.a2.t2.in.wpu.e2e.rel.a2.t2.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.rel.a2.t2.input.e2e-artifact.product-charter`
- `pp.e2e.rel.a2.t2.in.wpu.e2e.rel.a2.t2.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.rel.a2.t2.input.e2e-artifact.method-profile`
- `pp.e2e.rel.a2.t2.in.wpu.e2e.rel.a2.t2.input.e2e-artifact.team-topology`: **in** WorkProductUse `wpu.e2e.rel.a2.t2.input.e2e-artifact.team-topology`
- `pp.e2e.rel.a2.t2.out.wpu.e2e.rel.a2.t2.output.e2e-artifact.release-record`: **out** WorkProductUse `wpu.e2e.rel.a2.t2.output.e2e-artifact.release-record`
- `pp.e2e.rel.a2.t2.out.wpu.e2e.rel.a2.t2.output.e2e-artifact.operations-record`: **out** WorkProductUse `wpu.e2e.rel.a2.t2.output.e2e-artifact.operations-record`

### e2e.rel.a3.t1

<small>TaskUse of `task.e2e.rel.a3.t1`</small>

**Process performers**

- `ppf.e2e.rel.a3.t1.ru.e2e.rel.a3.product-owner`: primary RoleUse `ru.e2e.rel.a3.product-owner`
- `ppf.e2e.rel.a3.t1.ru.e2e.rel.a3.service-owner`: supporting RoleUse `ru.e2e.rel.a3.service-owner`
- `ppf.e2e.rel.a3.t1.ru.e2e.rel.a3.finops-cost-analyst`: supporting RoleUse `ru.e2e.rel.a3.finops-cost-analyst`
- `ppf.e2e.rel.a3.t1.ru.e2e.rel.a3.method-engineer`: supporting RoleUse `ru.e2e.rel.a3.method-engineer`
- `ppf.e2e.rel.a3.t1.ru.e2e.rel.a3.delivery-lead`: supporting RoleUse `ru.e2e.rel.a3.delivery-lead`

**Process parameters**

- `pp.e2e.rel.a3.t1.in.wpu.e2e.rel.a3.t1.input.e2e-artifact.release-record`: **in** WorkProductUse `wpu.e2e.rel.a3.t1.input.e2e-artifact.release-record`
- `pp.e2e.rel.a3.t1.in.wpu.e2e.rel.a3.t1.input.e2e-artifact.operations-record`: **in** WorkProductUse `wpu.e2e.rel.a3.t1.input.e2e-artifact.operations-record`
- `pp.e2e.rel.a3.t1.in.wpu.e2e.rel.a3.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.rel.a3.t1.input.e2e-artifact.product-charter`
- `pp.e2e.rel.a3.t1.in.wpu.e2e.rel.a3.t1.input.e2e-artifact.increment-record`: **in** WorkProductUse `wpu.e2e.rel.a3.t1.input.e2e-artifact.increment-record`
- `pp.e2e.rel.a3.t1.in.wpu.e2e.rel.a3.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.rel.a3.t1.input.e2e-artifact.method-profile`
- `pp.e2e.rel.a3.t1.in.wpu.e2e.rel.a3.t1.input.e2e-artifact.cost-model`: **in** WorkProductUse `wpu.e2e.rel.a3.t1.input.e2e-artifact.cost-model`
- `pp.e2e.rel.a3.t1.in.wpu.e2e.rel.a3.t1.input.e2e-artifact.improvement-record`: **in** WorkProductUse `wpu.e2e.rel.a3.t1.input.e2e-artifact.improvement-record`
- `pp.e2e.rel.a3.t1.out.wpu.e2e.rel.a3.t1.output.e2e-artifact.release-record`: **out** WorkProductUse `wpu.e2e.rel.a3.t1.output.e2e-artifact.release-record`
- `pp.e2e.rel.a3.t1.out.wpu.e2e.rel.a3.t1.output.e2e-artifact.product-charter`: **out** WorkProductUse `wpu.e2e.rel.a3.t1.output.e2e-artifact.product-charter`
- `pp.e2e.rel.a3.t1.out.wpu.e2e.rel.a3.t1.output.e2e-artifact.increment-record`: **out** WorkProductUse `wpu.e2e.rel.a3.t1.output.e2e-artifact.increment-record`
- `pp.e2e.rel.a3.t1.out.wpu.e2e.rel.a3.t1.output.e2e-artifact.cost-model`: **out** WorkProductUse `wpu.e2e.rel.a3.t1.output.e2e-artifact.cost-model`
- `pp.e2e.rel.a3.t1.out.wpu.e2e.rel.a3.t1.output.e2e-artifact.improvement-record`: **out** WorkProductUse `wpu.e2e.rel.a3.t1.output.e2e-artifact.improvement-record`

### e2e.ph2.st1.t1

<small>TaskUse of `task.e2e.ph2.st1.t1`</small>

**Process performers**

- `ppf.e2e.ph2.st1.t1.ru.e2e.ph2.st1.product-owner`: primary RoleUse `ru.e2e.ph2.st1.product-owner`
- `ppf.e2e.ph2.st1.t1.ru.e2e.ph2.st1.sponsor`: supporting RoleUse `ru.e2e.ph2.st1.sponsor`
- `ppf.e2e.ph2.st1.t1.ru.e2e.ph2.st1.service-owner`: supporting RoleUse `ru.e2e.ph2.st1.service-owner`
- `ppf.e2e.ph2.st1.t1.ru.e2e.ph2.st1.security-engineer`: supporting RoleUse `ru.e2e.ph2.st1.security-engineer`
- `ppf.e2e.ph2.st1.t1.ru.e2e.ph2.st1.finops-cost-analyst`: supporting RoleUse `ru.e2e.ph2.st1.finops-cost-analyst`
- `ppf.e2e.ph2.st1.t1.ru.e2e.ph2.st1.records-data-steward`: supporting RoleUse `ru.e2e.ph2.st1.records-data-steward`

**Process parameters**

- `pp.e2e.ph2.st1.t1.in.wpu.e2e.ph2.st1.t1.input.e2e-artifact.operations-record`: **in** WorkProductUse `wpu.e2e.ph2.st1.t1.input.e2e-artifact.operations-record`
- `pp.e2e.ph2.st1.t1.in.wpu.e2e.ph2.st1.t1.input.e2e-artifact.release-record`: **in** WorkProductUse `wpu.e2e.ph2.st1.t1.input.e2e-artifact.release-record`
- `pp.e2e.ph2.st1.t1.in.wpu.e2e.ph2.st1.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.ph2.st1.t1.input.e2e-artifact.product-charter`
- `pp.e2e.ph2.st1.t1.in.wpu.e2e.ph2.st1.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.ph2.st1.t1.input.e2e-artifact.method-profile`
- `pp.e2e.ph2.st1.t1.in.wpu.e2e.ph2.st1.t1.input.e2e-artifact.cost-model`: **in** WorkProductUse `wpu.e2e.ph2.st1.t1.input.e2e-artifact.cost-model`
- `pp.e2e.ph2.st1.t1.in.wpu.e2e.ph2.st1.t1.input.e2e-artifact.risk-register`: **in** WorkProductUse `wpu.e2e.ph2.st1.t1.input.e2e-artifact.risk-register`
- `pp.e2e.ph2.st1.t1.out.wpu.e2e.ph2.st1.t1.output.e2e-artifact.retirement-record`: **out** WorkProductUse `wpu.e2e.ph2.st1.t1.output.e2e-artifact.retirement-record`
- `pp.e2e.ph2.st1.t1.out.wpu.e2e.ph2.st1.t1.output.e2e-artifact.risk-register`: **out** WorkProductUse `wpu.e2e.ph2.st1.t1.output.e2e-artifact.risk-register`

### e2e.ph2.st2.t1

<small>TaskUse of `task.e2e.ph2.st2.t1`</small>

**Process performers**

- `ppf.e2e.ph2.st2.t1.ru.e2e.ph2.st2.cloud-platform-engineer`: primary RoleUse `ru.e2e.ph2.st2.cloud-platform-engineer`
- `ppf.e2e.ph2.st2.t1.ru.e2e.ph2.st2.records-data-steward`: supporting RoleUse `ru.e2e.ph2.st2.records-data-steward`
- `ppf.e2e.ph2.st2.t1.ru.e2e.ph2.st2.security-engineer`: supporting RoleUse `ru.e2e.ph2.st2.security-engineer`
- `ppf.e2e.ph2.st2.t1.ru.e2e.ph2.st2.service-owner`: supporting RoleUse `ru.e2e.ph2.st2.service-owner`

**Process parameters**

- `pp.e2e.ph2.st2.t1.in.wpu.e2e.ph2.st2.t1.input.e2e-artifact.retirement-record`: **in** WorkProductUse `wpu.e2e.ph2.st2.t1.input.e2e-artifact.retirement-record`
- `pp.e2e.ph2.st2.t1.in.wpu.e2e.ph2.st2.t1.input.e2e-artifact.operations-record`: **in** WorkProductUse `wpu.e2e.ph2.st2.t1.input.e2e-artifact.operations-record`
- `pp.e2e.ph2.st2.t1.in.wpu.e2e.ph2.st2.t1.input.e2e-artifact.release-record`: **in** WorkProductUse `wpu.e2e.ph2.st2.t1.input.e2e-artifact.release-record`
- `pp.e2e.ph2.st2.t1.in.wpu.e2e.ph2.st2.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.ph2.st2.t1.input.e2e-artifact.method-profile`
- `pp.e2e.ph2.st2.t1.out.wpu.e2e.ph2.st2.t1.output.e2e-artifact.retirement-record`: **out** WorkProductUse `wpu.e2e.ph2.st2.t1.output.e2e-artifact.retirement-record`

### e2e.ph2.st2.t2

<small>TaskUse of `task.e2e.ph2.st2.t2`</small>

**Process performers**

- `ppf.e2e.ph2.st2.t2.ru.e2e.ph2.st2.service-owner`: primary RoleUse `ru.e2e.ph2.st2.service-owner`
- `ppf.e2e.ph2.st2.t2.ru.e2e.ph2.st2.cloud-platform-engineer`: supporting RoleUse `ru.e2e.ph2.st2.cloud-platform-engineer`
- `ppf.e2e.ph2.st2.t2.ru.e2e.ph2.st2.security-engineer`: supporting RoleUse `ru.e2e.ph2.st2.security-engineer`
- `ppf.e2e.ph2.st2.t2.ru.e2e.ph2.st2.finops-cost-analyst`: supporting RoleUse `ru.e2e.ph2.st2.finops-cost-analyst`
- `ppf.e2e.ph2.st2.t2.ru.e2e.ph2.st2.records-data-steward`: supporting RoleUse `ru.e2e.ph2.st2.records-data-steward`

**Process parameters**

- `pp.e2e.ph2.st2.t2.in.wpu.e2e.ph2.st2.t2.input.e2e-artifact.retirement-record`: **in** WorkProductUse `wpu.e2e.ph2.st2.t2.input.e2e-artifact.retirement-record`
- `pp.e2e.ph2.st2.t2.in.wpu.e2e.ph2.st2.t2.input.e2e-artifact.operations-record`: **in** WorkProductUse `wpu.e2e.ph2.st2.t2.input.e2e-artifact.operations-record`
- `pp.e2e.ph2.st2.t2.in.wpu.e2e.ph2.st2.t2.input.e2e-artifact.release-record`: **in** WorkProductUse `wpu.e2e.ph2.st2.t2.input.e2e-artifact.release-record`
- `pp.e2e.ph2.st2.t2.in.wpu.e2e.ph2.st2.t2.input.e2e-artifact.team-topology`: **in** WorkProductUse `wpu.e2e.ph2.st2.t2.input.e2e-artifact.team-topology`
- `pp.e2e.ph2.st2.t2.in.wpu.e2e.ph2.st2.t2.input.e2e-artifact.cost-model`: **in** WorkProductUse `wpu.e2e.ph2.st2.t2.input.e2e-artifact.cost-model`
- `pp.e2e.ph2.st2.t2.out.wpu.e2e.ph2.st2.t2.output.e2e-artifact.retirement-record`: **out** WorkProductUse `wpu.e2e.ph2.st2.t2.output.e2e-artifact.retirement-record`
- `pp.e2e.ph2.st2.t2.out.wpu.e2e.ph2.st2.t2.output.e2e-artifact.cost-model`: **out** WorkProductUse `wpu.e2e.ph2.st2.t2.output.e2e-artifact.cost-model`

### e2e.ph2.st2.t3

<small>TaskUse of `task.e2e.ph2.st2.t3`</small>

**Process performers**

- `ppf.e2e.ph2.st2.t3.ru.e2e.ph2.st2.records-data-steward`: primary RoleUse `ru.e2e.ph2.st2.records-data-steward`
- `ppf.e2e.ph2.st2.t3.ru.e2e.ph2.st2.security-engineer`: supporting RoleUse `ru.e2e.ph2.st2.security-engineer`
- `ppf.e2e.ph2.st2.t3.ru.e2e.ph2.st2.service-owner`: supporting RoleUse `ru.e2e.ph2.st2.service-owner`
- `ppf.e2e.ph2.st2.t3.ru.e2e.ph2.st2.cloud-platform-engineer`: supporting RoleUse `ru.e2e.ph2.st2.cloud-platform-engineer`

**Process parameters**

- `pp.e2e.ph2.st2.t3.in.wpu.e2e.ph2.st2.t3.input.e2e-artifact.retirement-record`: **in** WorkProductUse `wpu.e2e.ph2.st2.t3.input.e2e-artifact.retirement-record`
- `pp.e2e.ph2.st2.t3.in.wpu.e2e.ph2.st2.t3.input.e2e-artifact.operations-record`: **in** WorkProductUse `wpu.e2e.ph2.st2.t3.input.e2e-artifact.operations-record`
- `pp.e2e.ph2.st2.t3.in.wpu.e2e.ph2.st2.t3.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.ph2.st2.t3.input.e2e-artifact.method-profile`
- `pp.e2e.ph2.st2.t3.out.wpu.e2e.ph2.st2.t3.output.e2e-artifact.retirement-record`: **out** WorkProductUse `wpu.e2e.ph2.st2.t3.output.e2e-artifact.retirement-record`

### e2e.ph2.st3.t1

<small>TaskUse of `task.e2e.ph2.st3.t1`</small>

**Process performers**

- `ppf.e2e.ph2.st3.t1.ru.e2e.ph2.st3.process-reviewer`: primary RoleUse `ru.e2e.ph2.st3.process-reviewer`
- `ppf.e2e.ph2.st3.t1.ru.e2e.ph2.st3.sponsor`: supporting RoleUse `ru.e2e.ph2.st3.sponsor`
- `ppf.e2e.ph2.st3.t1.ru.e2e.ph2.st3.product-owner`: supporting RoleUse `ru.e2e.ph2.st3.product-owner`
- `ppf.e2e.ph2.st3.t1.ru.e2e.ph2.st3.service-owner`: supporting RoleUse `ru.e2e.ph2.st3.service-owner`
- `ppf.e2e.ph2.st3.t1.ru.e2e.ph2.st3.finops-cost-analyst`: supporting RoleUse `ru.e2e.ph2.st3.finops-cost-analyst`
- `ppf.e2e.ph2.st3.t1.ru.e2e.ph2.st3.records-data-steward`: supporting RoleUse `ru.e2e.ph2.st3.records-data-steward`
- `ppf.e2e.ph2.st3.t1.ru.e2e.ph2.st3.method-engineer`: supporting RoleUse `ru.e2e.ph2.st3.method-engineer`

**Process parameters**

- `pp.e2e.ph2.st3.t1.in.wpu.e2e.ph2.st3.t1.input.e2e-artifact.retirement-record`: **in** WorkProductUse `wpu.e2e.ph2.st3.t1.input.e2e-artifact.retirement-record`
- `pp.e2e.ph2.st3.t1.in.wpu.e2e.ph2.st3.t1.input.e2e-artifact.operations-record`: **in** WorkProductUse `wpu.e2e.ph2.st3.t1.input.e2e-artifact.operations-record`
- `pp.e2e.ph2.st3.t1.in.wpu.e2e.ph2.st3.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.ph2.st3.t1.input.e2e-artifact.product-charter`
- `pp.e2e.ph2.st3.t1.in.wpu.e2e.ph2.st3.t1.input.e2e-artifact.increment-record`: **in** WorkProductUse `wpu.e2e.ph2.st3.t1.input.e2e-artifact.increment-record`
- `pp.e2e.ph2.st3.t1.in.wpu.e2e.ph2.st3.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.ph2.st3.t1.input.e2e-artifact.method-profile`
- `pp.e2e.ph2.st3.t1.in.wpu.e2e.ph2.st3.t1.input.e2e-artifact.release-record`: **in** WorkProductUse `wpu.e2e.ph2.st3.t1.input.e2e-artifact.release-record`
- `pp.e2e.ph2.st3.t1.in.wpu.e2e.ph2.st3.t1.input.e2e-artifact.cost-model`: **in** WorkProductUse `wpu.e2e.ph2.st3.t1.input.e2e-artifact.cost-model`
- `pp.e2e.ph2.st3.t1.in.wpu.e2e.ph2.st3.t1.input.e2e-artifact.improvement-record`: **in** WorkProductUse `wpu.e2e.ph2.st3.t1.input.e2e-artifact.improvement-record`
- `pp.e2e.ph2.st3.t1.out.wpu.e2e.ph2.st3.t1.output.e2e-artifact.retirement-record`: **out** WorkProductUse `wpu.e2e.ph2.st3.t1.output.e2e-artifact.retirement-record`
- `pp.e2e.ph2.st3.t1.out.wpu.e2e.ph2.st3.t1.output.e2e-artifact.method-profile`: **out** WorkProductUse `wpu.e2e.ph2.st3.t1.output.e2e-artifact.method-profile`
- `pp.e2e.ph2.st3.t1.out.wpu.e2e.ph2.st3.t1.output.e2e-artifact.cost-model`: **out** WorkProductUse `wpu.e2e.ph2.st3.t1.output.e2e-artifact.cost-model`
- `pp.e2e.ph2.st3.t1.out.wpu.e2e.ph2.st3.t1.output.e2e-artifact.improvement-record`: **out** WorkProductUse `wpu.e2e.ph2.st3.t1.output.e2e-artifact.improvement-record`

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
