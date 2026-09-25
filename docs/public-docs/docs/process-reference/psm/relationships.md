# AWS PSM modeling: flow and bindings

This page documents the **SPEM WorkSequence, ProcessPerformer, and ProcessParameter** elements used by the AWS PSM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

WorkSequence records explicit process flow. ProcessPerformer binds a role use to a task use. ProcessParameter binds a work-product use to a task use and states whether it enters or leaves the task. These relationships make dependencies inspectable and prevent document order from being treated as hidden process logic.

## Summary

| Relationship area  | Items | What it controls                                    |
| ------------------ | ----: | --------------------------------------------------- |
| Work sequences     |    34 | Ordering, iteration, feedback, and conditional flow |
| Task uses          |    28 | Placement of reusable tasks in activities           |
| Process performers |    28 | Primary and supporting role bindings                |
| Process parameters |   141 | Input and output work-product bindings              |

## Work sequences

### psm.ph1 → psm.ph2

<small>WorkSequence: `ws.psm.ph1.psm.ph2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph2 → psm.ph3

<small>WorkSequence: `ws.psm.ph2.psm.ph3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph3 → psm.ph4

<small>WorkSequence: `ws.psm.ph3.psm.ph4` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph4 → psm.ph5

<small>WorkSequence: `ws.psm.ph4.psm.ph5` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph5 → psm.ph6

<small>WorkSequence: `ws.psm.ph5.psm.ph6` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph1.st0 → psm.ph1.st1

<small>WorkSequence: `ws.psm.ph1.st0.psm.ph1.st1` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph1.st1 → psm.ph1.st2

<small>WorkSequence: `ws.psm.ph1.st1.psm.ph1.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph1.st2 → psm.ph1.st3

<small>WorkSequence: `ws.psm.ph1.st2.psm.ph1.st3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph1.st1.t1 → psm.ph1.st1.t2

<small>WorkSequence: `ws.psm.ph1.st1.t1.psm.ph1.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph1.st1.t2 → psm.ph1.st1.t3

<small>WorkSequence: `ws.psm.ph1.st1.t2.psm.ph1.st1.t3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph1.st2.t1 → psm.ph1.st2.t2

<small>WorkSequence: `ws.psm.ph1.st2.t1.psm.ph1.st2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph1.st3.t1 → psm.ph1.st3.t2

<small>WorkSequence: `ws.psm.ph1.st3.t1.psm.ph1.st3.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph2.st1 → psm.ph2.st2

<small>WorkSequence: `ws.psm.ph2.st1.psm.ph2.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph2.st1.t1 → psm.ph2.st1.t2

<small>WorkSequence: `ws.psm.ph2.st1.t1.psm.ph2.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph2.st2.t1 → psm.ph2.st2.t2

<small>WorkSequence: `ws.psm.ph2.st2.t1.psm.ph2.st2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph3.st1 → psm.ph3.st2

<small>WorkSequence: `ws.psm.ph3.st1.psm.ph3.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph3.st1.t1 → psm.ph3.st1.t2

<small>WorkSequence: `ws.psm.ph3.st1.t1.psm.ph3.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph3.st2.t1 → psm.ph3.st2.t2

<small>WorkSequence: `ws.psm.ph3.st2.t1.psm.ph3.st2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph4.st1 → psm.ph4.st2

<small>WorkSequence: `ws.psm.ph4.st1.psm.ph4.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph4.st1.t1 → psm.ph4.st1.t2

<small>WorkSequence: `ws.psm.ph4.st1.t1.psm.ph4.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph4.st2.t1 → psm.ph4.st2.t2

<small>WorkSequence: `ws.psm.ph4.st2.t1.psm.ph4.st2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph5.st1 → psm.ph5.st2

<small>WorkSequence: `ws.psm.ph5.st1.psm.ph5.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph5.st1.t1 → psm.ph5.st1.t2

<small>WorkSequence: `ws.psm.ph5.st1.t1.psm.ph5.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph5.st2.ss1 → psm.ph5.st2.ss2

<small>WorkSequence: `ws.psm.ph5.st2.ss1.psm.ph5.st2.ss2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph5.st2.ss2.t1 → psm.ph5.st2.ss2.t2

<small>WorkSequence: `ws.psm.ph5.st2.ss2.t1.psm.ph5.st2.ss2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph6.st1 → psm.ph6.st2

<small>WorkSequence: `ws.psm.ph6.st1.psm.ph6.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph6.st2 → psm.ph6.st3

<small>WorkSequence: `ws.psm.ph6.st2.psm.ph6.st3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### psm.ph6 → psm.ph1

<small>WorkSequence: `ws.engine-loop.psm.ph6.psm.ph1` · finishToStart</small>

After the phase-embedded review/adapt stage, revolve the PSM engine for the next deployable slice.

**Condition:** deployable-slices-remaining

### psm.ph2.st1 → psm.ph1.st3

<small>WorkSequence: `ws.rework.psm.loop.iam-baseline` · finishToStart</small>

Extend security baseline, then replay networking.

**Condition:** Networking requires new IAM roles, KMS keys, or secrets.

### psm.ph4.st2 → psm.ph3.st1

<small>WorkSequence: `ws.rework.psm.loop.storage-compute` · finishToStart</small>

Provision storage, then rewire compute event sources.

**Condition:** Lambda mappings reference unprovisioned tables or buckets.

### psm.ph4.st2 → psm.ph4.st1

<small>WorkSequence: `ws.rework.psm.loop.event-fabric` · finishToStart</small>

Extend event fabric, then update Lambda targets.

**Condition:** Functions need EventBridge rules not yet modeled.

### psm.ph5.st1 → psm.ph4.st2

<small>WorkSequence: `ws.rework.psm.loop.api-integration` · finishToStart</small>

Complete compute/identity, then rebuild API integrations.

**Condition:** API integrations reference incomplete Lambda or authorizers.

### psm.ph5.st2 → psm.ph4.st2

<small>WorkSequence: `ws.rework.psm.loop.workflow-obs` · finishToStart</small>

Reconcile compute targets, then regenerate ASL and observability.

**Condition:** Step Functions targets changed after compute phase.

### psm.ph6.st2 → psm.ph3.st1

<small>WorkSequence: `ws.rework.psm.loop.readiness-rework` · finishToStart</small>

Repair at source resource stage; regenerate views before M2T.

**Condition:** Integration views or PSM EVL reveal broken wiring.

## Task performer and parameter bindings

### psm.ph1.st0.t1

<small>TaskUse of `task.psm.ph1.st0.t1`</small>

**Process performers**

- `ppf.psm.ph1.st0.t1.ru.psm.ph1.st0.cloud-platform-engineer`: primary RoleUse `ru.psm.ph1.st0.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph1.st0.t1.out.wpu.psm.ph1.st0.t1.output.psm-artifact.increment-plan`: **out** WorkProductUse `wpu.psm.ph1.st0.t1.output.psm-artifact.increment-plan`

### psm.ph1.st1.t1

<small>TaskUse of `task.psm.ph1.st1.t1`</small>

**Process performers**

- `ppf.psm.ph1.st1.t1.ru.psm.ph1.st1.cloud-platform-engineer`: primary RoleUse `ru.psm.ph1.st1.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph1.st1.t1.in.wpu.psm.ph1.st1.t1.input.psm-artifact.increment-plan`: **in** WorkProductUse `wpu.psm.ph1.st1.t1.input.psm-artifact.increment-plan`
- `pp.psm.ph1.st1.t1.out.wpu.psm.ph1.st1.t1.output.psm-artifact.deployment-strategy`: **out** WorkProductUse `wpu.psm.ph1.st1.t1.output.psm-artifact.deployment-strategy`

### psm.ph1.st1.t2

<small>TaskUse of `task.psm.ph1.st1.t2`</small>

**Process performers**

- `ppf.psm.ph1.st1.t2.ru.psm.ph1.st1.cloud-platform-engineer`: primary RoleUse `ru.psm.ph1.st1.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph1.st1.t2.in.wpu.psm.ph1.st1.t2.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph1.st1.t2.input.psm-artifact.deployment-strategy`
- `pp.psm.ph1.st1.t2.in.wpu.psm.ph1.st1.t2.input.psm-artifact.increment-plan`: **in** WorkProductUse `wpu.psm.ph1.st1.t2.input.psm-artifact.increment-plan`
- `pp.psm.ph1.st1.t2.out.wpu.psm.ph1.st1.t2.output.psm-artifact.deployment-strategy`: **out** WorkProductUse `wpu.psm.ph1.st1.t2.output.psm-artifact.deployment-strategy`

### psm.ph1.st1.t3

<small>TaskUse of `task.psm.ph1.st1.t3`</small>

**Process performers**

- `ppf.psm.ph1.st1.t3.ru.psm.ph1.st1.method-engineer`: primary RoleUse `ru.psm.ph1.st1.method-engineer`

**Process parameters**

- `pp.psm.ph1.st1.t3.in.wpu.psm.ph1.st1.t3.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph1.st1.t3.input.psm-artifact.deployment-strategy`
- `pp.psm.ph1.st1.t3.in.wpu.psm.ph1.st1.t3.input.psm-artifact.increment-plan`: **in** WorkProductUse `wpu.psm.ph1.st1.t3.input.psm-artifact.increment-plan`
- `pp.psm.ph1.st1.t3.out.wpu.psm.ph1.st1.t3.output.psm-artifact.deployment-strategy`: **out** WorkProductUse `wpu.psm.ph1.st1.t3.output.psm-artifact.deployment-strategy`

### psm.ph1.st2.t1

<small>TaskUse of `task.psm.ph1.st2.t1`</small>

**Process performers**

- `ppf.psm.ph1.st2.t1.ru.psm.ph1.st2.cloud-platform-engineer`: primary RoleUse `ru.psm.ph1.st2.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph1.st2.t1.in.wpu.psm.ph1.st2.t1.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph1.st2.t1.input.psm-artifact.deployment-strategy`
- `pp.psm.ph1.st2.t1.in.wpu.psm.ph1.st2.t1.input.psm-artifact.increment-plan`: **in** WorkProductUse `wpu.psm.ph1.st2.t1.input.psm-artifact.increment-plan`
- `pp.psm.ph1.st2.t1.out.wpu.psm.ph1.st2.t1.output.psm-artifact.stack-scaffold`: **out** WorkProductUse `wpu.psm.ph1.st2.t1.output.psm-artifact.stack-scaffold`

### psm.ph1.st2.t2

<small>TaskUse of `task.psm.ph1.st2.t2`</small>

**Process performers**

- `ppf.psm.ph1.st2.t2.ru.psm.ph1.st2.cloud-platform-engineer`: primary RoleUse `ru.psm.ph1.st2.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph1.st2.t2.in.wpu.psm.ph1.st2.t2.input.psm-artifact.stack-scaffold`: **in** WorkProductUse `wpu.psm.ph1.st2.t2.input.psm-artifact.stack-scaffold`
- `pp.psm.ph1.st2.t2.in.wpu.psm.ph1.st2.t2.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph1.st2.t2.input.psm-artifact.deployment-strategy`
- `pp.psm.ph1.st2.t2.out.wpu.psm.ph1.st2.t2.output.psm-artifact.stack-scaffold`: **out** WorkProductUse `wpu.psm.ph1.st2.t2.output.psm-artifact.stack-scaffold`

### psm.ph1.st3.t1

<small>TaskUse of `task.psm.ph1.st3.t1`</small>

**Process performers**

- `ppf.psm.ph1.st3.t1.ru.psm.ph1.st3.cloud-platform-engineer`: primary RoleUse `ru.psm.ph1.st3.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph1.st3.t1.in.wpu.psm.ph1.st3.t1.input.psm-artifact.stack-scaffold`: **in** WorkProductUse `wpu.psm.ph1.st3.t1.input.psm-artifact.stack-scaffold`
- `pp.psm.ph1.st3.t1.in.wpu.psm.ph1.st3.t1.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph1.st3.t1.input.psm-artifact.deployment-strategy`
- `pp.psm.ph1.st3.t1.out.wpu.psm.ph1.st3.t1.output.psm-artifact.security-baseline`: **out** WorkProductUse `wpu.psm.ph1.st3.t1.output.psm-artifact.security-baseline`

### psm.ph1.st3.t2

<small>TaskUse of `task.psm.ph1.st3.t2`</small>

**Process performers**

- `ppf.psm.ph1.st3.t2.ru.psm.ph1.st3.cloud-platform-engineer`: primary RoleUse `ru.psm.ph1.st3.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph1.st3.t2.in.wpu.psm.ph1.st3.t2.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph1.st3.t2.input.psm-artifact.security-baseline`
- `pp.psm.ph1.st3.t2.in.wpu.psm.ph1.st3.t2.input.psm-artifact.stack-scaffold`: **in** WorkProductUse `wpu.psm.ph1.st3.t2.input.psm-artifact.stack-scaffold`
- `pp.psm.ph1.st3.t2.in.wpu.psm.ph1.st3.t2.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph1.st3.t2.input.psm-artifact.deployment-strategy`
- `pp.psm.ph1.st3.t2.out.wpu.psm.ph1.st3.t2.output.psm-artifact.security-baseline`: **out** WorkProductUse `wpu.psm.ph1.st3.t2.output.psm-artifact.security-baseline`

### psm.ph2.st1.t1

<small>TaskUse of `task.psm.ph2.st1.t1`</small>

**Process performers**

- `ppf.psm.ph2.st1.t1.ru.psm.ph2.st1.cloud-platform-engineer`: primary RoleUse `ru.psm.ph2.st1.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph2.st1.t1.in.wpu.psm.ph2.st1.t1.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph2.st1.t1.input.psm-artifact.security-baseline`
- `pp.psm.ph2.st1.t1.in.wpu.psm.ph2.st1.t1.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph2.st1.t1.input.psm-artifact.deployment-strategy`
- `pp.psm.ph2.st1.t1.in.wpu.psm.ph2.st1.t1.input.psm-artifact.stack-scaffold`: **in** WorkProductUse `wpu.psm.ph2.st1.t1.input.psm-artifact.stack-scaffold`
- `pp.psm.ph2.st1.t1.in.wpu.psm.ph2.st1.t1.input.psm-artifact.increment-plan`: **in** WorkProductUse `wpu.psm.ph2.st1.t1.input.psm-artifact.increment-plan`
- `pp.psm.ph2.st1.t1.out.wpu.psm.ph2.st1.t1.output.psm-artifact.network-identity`: **out** WorkProductUse `wpu.psm.ph2.st1.t1.output.psm-artifact.network-identity`

### psm.ph2.st1.t2

<small>TaskUse of `task.psm.ph2.st1.t2`</small>

**Process performers**

- `ppf.psm.ph2.st1.t2.ru.psm.ph2.st1.cloud-platform-engineer`: primary RoleUse `ru.psm.ph2.st1.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph2.st1.t2.in.wpu.psm.ph2.st1.t2.input.psm-artifact.network-identity`: **in** WorkProductUse `wpu.psm.ph2.st1.t2.input.psm-artifact.network-identity`
- `pp.psm.ph2.st1.t2.in.wpu.psm.ph2.st1.t2.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph2.st1.t2.input.psm-artifact.security-baseline`
- `pp.psm.ph2.st1.t2.in.wpu.psm.ph2.st1.t2.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph2.st1.t2.input.psm-artifact.deployment-strategy`
- `pp.psm.ph2.st1.t2.out.wpu.psm.ph2.st1.t2.output.psm-artifact.network-identity`: **out** WorkProductUse `wpu.psm.ph2.st1.t2.output.psm-artifact.network-identity`

### psm.ph2.st2.t1

<small>TaskUse of `task.psm.ph2.st2.t1`</small>

**Process performers**

- `ppf.psm.ph2.st2.t1.ru.psm.ph2.st2.cloud-platform-engineer`: primary RoleUse `ru.psm.ph2.st2.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph2.st2.t1.in.wpu.psm.ph2.st2.t1.input.psm-artifact.network-identity`: **in** WorkProductUse `wpu.psm.ph2.st2.t1.input.psm-artifact.network-identity`
- `pp.psm.ph2.st2.t1.in.wpu.psm.ph2.st2.t1.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph2.st2.t1.input.psm-artifact.security-baseline`
- `pp.psm.ph2.st2.t1.in.wpu.psm.ph2.st2.t1.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph2.st2.t1.input.psm-artifact.deployment-strategy`
- `pp.psm.ph2.st2.t1.out.wpu.psm.ph2.st2.t1.output.psm-artifact.network-identity`: **out** WorkProductUse `wpu.psm.ph2.st2.t1.output.psm-artifact.network-identity`

### psm.ph2.st2.t2

<small>TaskUse of `task.psm.ph2.st2.t2`</small>

**Process performers**

- `ppf.psm.ph2.st2.t2.ru.psm.ph2.st2.cloud-platform-engineer`: primary RoleUse `ru.psm.ph2.st2.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph2.st2.t2.in.wpu.psm.ph2.st2.t2.input.psm-artifact.network-identity`: **in** WorkProductUse `wpu.psm.ph2.st2.t2.input.psm-artifact.network-identity`
- `pp.psm.ph2.st2.t2.in.wpu.psm.ph2.st2.t2.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph2.st2.t2.input.psm-artifact.security-baseline`
- `pp.psm.ph2.st2.t2.in.wpu.psm.ph2.st2.t2.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph2.st2.t2.input.psm-artifact.deployment-strategy`
- `pp.psm.ph2.st2.t2.out.wpu.psm.ph2.st2.t2.output.psm-artifact.network-identity`: **out** WorkProductUse `wpu.psm.ph2.st2.t2.output.psm-artifact.network-identity`

### psm.ph3.st1.t1

<small>TaskUse of `task.psm.ph3.st1.t1`</small>

**Process performers**

- `ppf.psm.ph3.st1.t1.ru.psm.ph3.st1.cloud-platform-engineer`: primary RoleUse `ru.psm.ph3.st1.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph3.st1.t1.in.wpu.psm.ph3.st1.t1.input.psm-artifact.network-identity`: **in** WorkProductUse `wpu.psm.ph3.st1.t1.input.psm-artifact.network-identity`
- `pp.psm.ph3.st1.t1.in.wpu.psm.ph3.st1.t1.input.psm-artifact.stack-scaffold`: **in** WorkProductUse `wpu.psm.ph3.st1.t1.input.psm-artifact.stack-scaffold`
- `pp.psm.ph3.st1.t1.in.wpu.psm.ph3.st1.t1.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph3.st1.t1.input.psm-artifact.security-baseline`
- `pp.psm.ph3.st1.t1.in.wpu.psm.ph3.st1.t1.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph3.st1.t1.input.psm-artifact.deployment-strategy`
- `pp.psm.ph3.st1.t1.out.wpu.psm.ph3.st1.t1.output.psm-artifact.storage-layer`: **out** WorkProductUse `wpu.psm.ph3.st1.t1.output.psm-artifact.storage-layer`

### psm.ph3.st1.t2

<small>TaskUse of `task.psm.ph3.st1.t2`</small>

**Process performers**

- `ppf.psm.ph3.st1.t2.ru.psm.ph3.st1.cloud-platform-engineer`: primary RoleUse `ru.psm.ph3.st1.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph3.st1.t2.in.wpu.psm.ph3.st1.t2.input.psm-artifact.storage-layer`: **in** WorkProductUse `wpu.psm.ph3.st1.t2.input.psm-artifact.storage-layer`
- `pp.psm.ph3.st1.t2.in.wpu.psm.ph3.st1.t2.input.psm-artifact.network-identity`: **in** WorkProductUse `wpu.psm.ph3.st1.t2.input.psm-artifact.network-identity`
- `pp.psm.ph3.st1.t2.in.wpu.psm.ph3.st1.t2.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph3.st1.t2.input.psm-artifact.security-baseline`
- `pp.psm.ph3.st1.t2.in.wpu.psm.ph3.st1.t2.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph3.st1.t2.input.psm-artifact.deployment-strategy`
- `pp.psm.ph3.st1.t2.in.wpu.psm.ph3.st1.t2.input.psm-artifact.stack-scaffold`: **in** WorkProductUse `wpu.psm.ph3.st1.t2.input.psm-artifact.stack-scaffold`
- `pp.psm.ph3.st1.t2.out.wpu.psm.ph3.st1.t2.output.psm-artifact.storage-layer`: **out** WorkProductUse `wpu.psm.ph3.st1.t2.output.psm-artifact.storage-layer`

### psm.ph3.st2.t1

<small>TaskUse of `task.psm.ph3.st2.t1`</small>

**Process performers**

- `ppf.psm.ph3.st2.t1.ru.psm.ph3.st2.cloud-platform-engineer`: primary RoleUse `ru.psm.ph3.st2.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph3.st2.t1.in.wpu.psm.ph3.st2.t1.input.psm-artifact.storage-layer`: **in** WorkProductUse `wpu.psm.ph3.st2.t1.input.psm-artifact.storage-layer`
- `pp.psm.ph3.st2.t1.in.wpu.psm.ph3.st2.t1.input.psm-artifact.network-identity`: **in** WorkProductUse `wpu.psm.ph3.st2.t1.input.psm-artifact.network-identity`
- `pp.psm.ph3.st2.t1.in.wpu.psm.ph3.st2.t1.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph3.st2.t1.input.psm-artifact.security-baseline`
- `pp.psm.ph3.st2.t1.in.wpu.psm.ph3.st2.t1.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph3.st2.t1.input.psm-artifact.deployment-strategy`
- `pp.psm.ph3.st2.t1.out.wpu.psm.ph3.st2.t1.output.psm-artifact.messaging-layer`: **out** WorkProductUse `wpu.psm.ph3.st2.t1.output.psm-artifact.messaging-layer`

### psm.ph3.st2.t2

<small>TaskUse of `task.psm.ph3.st2.t2`</small>

**Process performers**

- `ppf.psm.ph3.st2.t2.ru.psm.ph3.st2.cloud-platform-engineer`: primary RoleUse `ru.psm.ph3.st2.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph3.st2.t2.in.wpu.psm.ph3.st2.t2.input.psm-artifact.messaging-layer`: **in** WorkProductUse `wpu.psm.ph3.st2.t2.input.psm-artifact.messaging-layer`
- `pp.psm.ph3.st2.t2.in.wpu.psm.ph3.st2.t2.input.psm-artifact.storage-layer`: **in** WorkProductUse `wpu.psm.ph3.st2.t2.input.psm-artifact.storage-layer`
- `pp.psm.ph3.st2.t2.in.wpu.psm.ph3.st2.t2.input.psm-artifact.network-identity`: **in** WorkProductUse `wpu.psm.ph3.st2.t2.input.psm-artifact.network-identity`
- `pp.psm.ph3.st2.t2.in.wpu.psm.ph3.st2.t2.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph3.st2.t2.input.psm-artifact.security-baseline`
- `pp.psm.ph3.st2.t2.in.wpu.psm.ph3.st2.t2.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph3.st2.t2.input.psm-artifact.deployment-strategy`
- `pp.psm.ph3.st2.t2.out.wpu.psm.ph3.st2.t2.output.psm-artifact.messaging-layer`: **out** WorkProductUse `wpu.psm.ph3.st2.t2.output.psm-artifact.messaging-layer`

### psm.ph4.st1.t1

<small>TaskUse of `task.psm.ph4.st1.t1`</small>

**Process performers**

- `ppf.psm.ph4.st1.t1.ru.psm.ph4.st1.cloud-platform-engineer`: primary RoleUse `ru.psm.ph4.st1.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph4.st1.t1.in.wpu.psm.ph4.st1.t1.input.psm-artifact.messaging-layer`: **in** WorkProductUse `wpu.psm.ph4.st1.t1.input.psm-artifact.messaging-layer`
- `pp.psm.ph4.st1.t1.in.wpu.psm.ph4.st1.t1.input.psm-artifact.network-identity`: **in** WorkProductUse `wpu.psm.ph4.st1.t1.input.psm-artifact.network-identity`
- `pp.psm.ph4.st1.t1.in.wpu.psm.ph4.st1.t1.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph4.st1.t1.input.psm-artifact.security-baseline`
- `pp.psm.ph4.st1.t1.in.wpu.psm.ph4.st1.t1.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph4.st1.t1.input.psm-artifact.deployment-strategy`
- `pp.psm.ph4.st1.t1.out.wpu.psm.ph4.st1.t1.output.psm-artifact.event-fabric`: **out** WorkProductUse `wpu.psm.ph4.st1.t1.output.psm-artifact.event-fabric`

### psm.ph4.st1.t2

<small>TaskUse of `task.psm.ph4.st1.t2`</small>

**Process performers**

- `ppf.psm.ph4.st1.t2.ru.psm.ph4.st1.cloud-platform-engineer`: primary RoleUse `ru.psm.ph4.st1.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph4.st1.t2.in.wpu.psm.ph4.st1.t2.input.psm-artifact.event-fabric`: **in** WorkProductUse `wpu.psm.ph4.st1.t2.input.psm-artifact.event-fabric`
- `pp.psm.ph4.st1.t2.in.wpu.psm.ph4.st1.t2.input.psm-artifact.messaging-layer`: **in** WorkProductUse `wpu.psm.ph4.st1.t2.input.psm-artifact.messaging-layer`
- `pp.psm.ph4.st1.t2.in.wpu.psm.ph4.st1.t2.input.psm-artifact.network-identity`: **in** WorkProductUse `wpu.psm.ph4.st1.t2.input.psm-artifact.network-identity`
- `pp.psm.ph4.st1.t2.in.wpu.psm.ph4.st1.t2.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph4.st1.t2.input.psm-artifact.security-baseline`
- `pp.psm.ph4.st1.t2.in.wpu.psm.ph4.st1.t2.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph4.st1.t2.input.psm-artifact.deployment-strategy`
- `pp.psm.ph4.st1.t2.out.wpu.psm.ph4.st1.t2.output.psm-artifact.event-fabric`: **out** WorkProductUse `wpu.psm.ph4.st1.t2.output.psm-artifact.event-fabric`

### psm.ph4.st2.t1

<small>TaskUse of `task.psm.ph4.st2.t1`</small>

**Process performers**

- `ppf.psm.ph4.st2.t1.ru.psm.ph4.st2.cloud-platform-engineer`: primary RoleUse `ru.psm.ph4.st2.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph4.st2.t1.in.wpu.psm.ph4.st2.t1.input.psm-artifact.event-fabric`: **in** WorkProductUse `wpu.psm.ph4.st2.t1.input.psm-artifact.event-fabric`
- `pp.psm.ph4.st2.t1.in.wpu.psm.ph4.st2.t1.input.psm-artifact.stack-scaffold`: **in** WorkProductUse `wpu.psm.ph4.st2.t1.input.psm-artifact.stack-scaffold`
- `pp.psm.ph4.st2.t1.in.wpu.psm.ph4.st2.t1.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph4.st2.t1.input.psm-artifact.security-baseline`
- `pp.psm.ph4.st2.t1.in.wpu.psm.ph4.st2.t1.input.psm-artifact.network-identity`: **in** WorkProductUse `wpu.psm.ph4.st2.t1.input.psm-artifact.network-identity`
- `pp.psm.ph4.st2.t1.in.wpu.psm.ph4.st2.t1.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph4.st2.t1.input.psm-artifact.deployment-strategy`
- `pp.psm.ph4.st2.t1.out.wpu.psm.ph4.st2.t1.output.psm-artifact.compute-layer`: **out** WorkProductUse `wpu.psm.ph4.st2.t1.output.psm-artifact.compute-layer`

### psm.ph4.st2.t2

<small>TaskUse of `task.psm.ph4.st2.t2`</small>

**Process performers**

- `ppf.psm.ph4.st2.t2.ru.psm.ph4.st2.cloud-platform-engineer`: primary RoleUse `ru.psm.ph4.st2.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph4.st2.t2.in.wpu.psm.ph4.st2.t2.input.psm-artifact.compute-layer`: **in** WorkProductUse `wpu.psm.ph4.st2.t2.input.psm-artifact.compute-layer`
- `pp.psm.ph4.st2.t2.in.wpu.psm.ph4.st2.t2.input.psm-artifact.event-fabric`: **in** WorkProductUse `wpu.psm.ph4.st2.t2.input.psm-artifact.event-fabric`
- `pp.psm.ph4.st2.t2.in.wpu.psm.ph4.st2.t2.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph4.st2.t2.input.psm-artifact.security-baseline`
- `pp.psm.ph4.st2.t2.in.wpu.psm.ph4.st2.t2.input.psm-artifact.network-identity`: **in** WorkProductUse `wpu.psm.ph4.st2.t2.input.psm-artifact.network-identity`
- `pp.psm.ph4.st2.t2.out.wpu.psm.ph4.st2.t2.output.psm-artifact.compute-layer`: **out** WorkProductUse `wpu.psm.ph4.st2.t2.output.psm-artifact.compute-layer`

### psm.ph5.st1.t1

<small>TaskUse of `task.psm.ph5.st1.t1`</small>

**Process performers**

- `ppf.psm.ph5.st1.t1.ru.psm.ph5.st1.cloud-platform-engineer`: primary RoleUse `ru.psm.ph5.st1.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph5.st1.t1.in.wpu.psm.ph5.st1.t1.input.psm-artifact.compute-layer`: **in** WorkProductUse `wpu.psm.ph5.st1.t1.input.psm-artifact.compute-layer`
- `pp.psm.ph5.st1.t1.in.wpu.psm.ph5.st1.t1.input.psm-artifact.network-identity`: **in** WorkProductUse `wpu.psm.ph5.st1.t1.input.psm-artifact.network-identity`
- `pp.psm.ph5.st1.t1.in.wpu.psm.ph5.st1.t1.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph5.st1.t1.input.psm-artifact.security-baseline`
- `pp.psm.ph5.st1.t1.in.wpu.psm.ph5.st1.t1.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph5.st1.t1.input.psm-artifact.deployment-strategy`
- `pp.psm.ph5.st1.t1.out.wpu.psm.ph5.st1.t1.output.psm-artifact.api-layer`: **out** WorkProductUse `wpu.psm.ph5.st1.t1.output.psm-artifact.api-layer`

### psm.ph5.st1.t2

<small>TaskUse of `task.psm.ph5.st1.t2`</small>

**Process performers**

- `ppf.psm.ph5.st1.t2.ru.psm.ph5.st1.cloud-platform-engineer`: primary RoleUse `ru.psm.ph5.st1.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph5.st1.t2.in.wpu.psm.ph5.st1.t2.input.psm-artifact.api-layer`: **in** WorkProductUse `wpu.psm.ph5.st1.t2.input.psm-artifact.api-layer`
- `pp.psm.ph5.st1.t2.in.wpu.psm.ph5.st1.t2.input.psm-artifact.compute-layer`: **in** WorkProductUse `wpu.psm.ph5.st1.t2.input.psm-artifact.compute-layer`
- `pp.psm.ph5.st1.t2.in.wpu.psm.ph5.st1.t2.input.psm-artifact.event-fabric`: **in** WorkProductUse `wpu.psm.ph5.st1.t2.input.psm-artifact.event-fabric`
- `pp.psm.ph5.st1.t2.in.wpu.psm.ph5.st1.t2.input.psm-artifact.network-identity`: **in** WorkProductUse `wpu.psm.ph5.st1.t2.input.psm-artifact.network-identity`
- `pp.psm.ph5.st1.t2.in.wpu.psm.ph5.st1.t2.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph5.st1.t2.input.psm-artifact.security-baseline`
- `pp.psm.ph5.st1.t2.out.wpu.psm.ph5.st1.t2.output.psm-artifact.api-layer`: **out** WorkProductUse `wpu.psm.ph5.st1.t2.output.psm-artifact.api-layer`

### psm.ph5.st2.ss1.t1

<small>TaskUse of `task.psm.ph5.st2.ss1.t1`</small>

**Process performers**

- `ppf.psm.ph5.st2.ss1.t1.ru.psm.ph5.st2.ss1.cloud-platform-engineer`: primary RoleUse `ru.psm.ph5.st2.ss1.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph5.st2.ss1.t1.in.wpu.psm.ph5.st2.ss1.t1.input.psm-artifact.api-layer`: **in** WorkProductUse `wpu.psm.ph5.st2.ss1.t1.input.psm-artifact.api-layer`
- `pp.psm.ph5.st2.ss1.t1.in.wpu.psm.ph5.st2.ss1.t1.input.psm-artifact.compute-layer`: **in** WorkProductUse `wpu.psm.ph5.st2.ss1.t1.input.psm-artifact.compute-layer`
- `pp.psm.ph5.st2.ss1.t1.in.wpu.psm.ph5.st2.ss1.t1.input.psm-artifact.event-fabric`: **in** WorkProductUse `wpu.psm.ph5.st2.ss1.t1.input.psm-artifact.event-fabric`
- `pp.psm.ph5.st2.ss1.t1.in.wpu.psm.ph5.st2.ss1.t1.input.psm-artifact.network-identity`: **in** WorkProductUse `wpu.psm.ph5.st2.ss1.t1.input.psm-artifact.network-identity`
- `pp.psm.ph5.st2.ss1.t1.in.wpu.psm.ph5.st2.ss1.t1.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph5.st2.ss1.t1.input.psm-artifact.security-baseline`
- `pp.psm.ph5.st2.ss1.t1.out.wpu.psm.ph5.st2.ss1.t1.output.psm-artifact.workflow-observability`: **out** WorkProductUse `wpu.psm.ph5.st2.ss1.t1.output.psm-artifact.workflow-observability`

### psm.ph5.st2.ss2.t1

<small>TaskUse of `task.psm.ph5.st2.ss2.t1`</small>

**Process performers**

- `ppf.psm.ph5.st2.ss2.t1.ru.psm.ph5.st2.ss2.cloud-platform-engineer`: primary RoleUse `ru.psm.ph5.st2.ss2.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph5.st2.ss2.t1.in.wpu.psm.ph5.st2.ss2.t1.input.psm-artifact.workflow-observability`: **in** WorkProductUse `wpu.psm.ph5.st2.ss2.t1.input.psm-artifact.workflow-observability`
- `pp.psm.ph5.st2.ss2.t1.in.wpu.psm.ph5.st2.ss2.t1.input.psm-artifact.compute-layer`: **in** WorkProductUse `wpu.psm.ph5.st2.ss2.t1.input.psm-artifact.compute-layer`
- `pp.psm.ph5.st2.ss2.t1.in.wpu.psm.ph5.st2.ss2.t1.input.psm-artifact.api-layer`: **in** WorkProductUse `wpu.psm.ph5.st2.ss2.t1.input.psm-artifact.api-layer`
- `pp.psm.ph5.st2.ss2.t1.in.wpu.psm.ph5.st2.ss2.t1.input.psm-artifact.event-fabric`: **in** WorkProductUse `wpu.psm.ph5.st2.ss2.t1.input.psm-artifact.event-fabric`
- `pp.psm.ph5.st2.ss2.t1.in.wpu.psm.ph5.st2.ss2.t1.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph5.st2.ss2.t1.input.psm-artifact.security-baseline`
- `pp.psm.ph5.st2.ss2.t1.out.wpu.psm.ph5.st2.ss2.t1.output.psm-artifact.workflow-observability`: **out** WorkProductUse `wpu.psm.ph5.st2.ss2.t1.output.psm-artifact.workflow-observability`

### psm.ph5.st2.ss2.t2

<small>TaskUse of `task.psm.ph5.st2.ss2.t2`</small>

**Process performers**

- `ppf.psm.ph5.st2.ss2.t2.ru.psm.ph5.st2.ss2.cloud-platform-engineer`: primary RoleUse `ru.psm.ph5.st2.ss2.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph5.st2.ss2.t2.in.wpu.psm.ph5.st2.ss2.t2.input.psm-artifact.workflow-observability`: **in** WorkProductUse `wpu.psm.ph5.st2.ss2.t2.input.psm-artifact.workflow-observability`
- `pp.psm.ph5.st2.ss2.t2.in.wpu.psm.ph5.st2.ss2.t2.input.psm-artifact.compute-layer`: **in** WorkProductUse `wpu.psm.ph5.st2.ss2.t2.input.psm-artifact.compute-layer`
- `pp.psm.ph5.st2.ss2.t2.in.wpu.psm.ph5.st2.ss2.t2.input.psm-artifact.api-layer`: **in** WorkProductUse `wpu.psm.ph5.st2.ss2.t2.input.psm-artifact.api-layer`
- `pp.psm.ph5.st2.ss2.t2.in.wpu.psm.ph5.st2.ss2.t2.input.psm-artifact.event-fabric`: **in** WorkProductUse `wpu.psm.ph5.st2.ss2.t2.input.psm-artifact.event-fabric`
- `pp.psm.ph5.st2.ss2.t2.in.wpu.psm.ph5.st2.ss2.t2.input.psm-artifact.network-identity`: **in** WorkProductUse `wpu.psm.ph5.st2.ss2.t2.input.psm-artifact.network-identity`
- `pp.psm.ph5.st2.ss2.t2.out.wpu.psm.ph5.st2.ss2.t2.output.psm-artifact.workflow-observability`: **out** WorkProductUse `wpu.psm.ph5.st2.ss2.t2.output.psm-artifact.workflow-observability`

### psm.ph6.st1.t1

<small>TaskUse of `task.psm.ph6.st1.t1`</small>

**Process performers**

- `ppf.psm.ph6.st1.t1.ru.psm.ph6.st1.cloud-platform-engineer`: primary RoleUse `ru.psm.ph6.st1.cloud-platform-engineer`

**Process parameters**

- `pp.psm.ph6.st1.t1.in.wpu.psm.ph6.st1.t1.input.psm-artifact.workflow-observability`: **in** WorkProductUse `wpu.psm.ph6.st1.t1.input.psm-artifact.workflow-observability`
- `pp.psm.ph6.st1.t1.in.wpu.psm.ph6.st1.t1.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph6.st1.t1.input.psm-artifact.deployment-strategy`
- `pp.psm.ph6.st1.t1.in.wpu.psm.ph6.st1.t1.input.psm-artifact.network-identity`: **in** WorkProductUse `wpu.psm.ph6.st1.t1.input.psm-artifact.network-identity`
- `pp.psm.ph6.st1.t1.in.wpu.psm.ph6.st1.t1.input.psm-artifact.storage-layer`: **in** WorkProductUse `wpu.psm.ph6.st1.t1.input.psm-artifact.storage-layer`
- `pp.psm.ph6.st1.t1.in.wpu.psm.ph6.st1.t1.input.psm-artifact.messaging-layer`: **in** WorkProductUse `wpu.psm.ph6.st1.t1.input.psm-artifact.messaging-layer`
- `pp.psm.ph6.st1.t1.in.wpu.psm.ph6.st1.t1.input.psm-artifact.event-fabric`: **in** WorkProductUse `wpu.psm.ph6.st1.t1.input.psm-artifact.event-fabric`
- `pp.psm.ph6.st1.t1.in.wpu.psm.ph6.st1.t1.input.psm-artifact.compute-layer`: **in** WorkProductUse `wpu.psm.ph6.st1.t1.input.psm-artifact.compute-layer`
- `pp.psm.ph6.st1.t1.in.wpu.psm.ph6.st1.t1.input.psm-artifact.api-layer`: **in** WorkProductUse `wpu.psm.ph6.st1.t1.input.psm-artifact.api-layer`
- `pp.psm.ph6.st1.t1.out.wpu.psm.ph6.st1.t1.output.psm-artifact.integration-views`: **out** WorkProductUse `wpu.psm.ph6.st1.t1.output.psm-artifact.integration-views`

### psm.ph6.st2.t1

<small>TaskUse of `task.psm.ph6.st2.t1`</small>

**Process performers**

- `ppf.psm.ph6.st2.t1.ru.psm.ph6.st2.process-reviewer`: primary RoleUse `ru.psm.ph6.st2.process-reviewer`

**Process parameters**

- `pp.psm.ph6.st2.t1.in.wpu.psm.ph6.st2.t1.input.psm-artifact.integration-views`: **in** WorkProductUse `wpu.psm.ph6.st2.t1.input.psm-artifact.integration-views`
- `pp.psm.ph6.st2.t1.in.wpu.psm.ph6.st2.t1.input.psm-artifact.increment-plan`: **in** WorkProductUse `wpu.psm.ph6.st2.t1.input.psm-artifact.increment-plan`
- `pp.psm.ph6.st2.t1.in.wpu.psm.ph6.st2.t1.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph6.st2.t1.input.psm-artifact.deployment-strategy`
- `pp.psm.ph6.st2.t1.in.wpu.psm.ph6.st2.t1.input.psm-artifact.stack-scaffold`: **in** WorkProductUse `wpu.psm.ph6.st2.t1.input.psm-artifact.stack-scaffold`
- `pp.psm.ph6.st2.t1.in.wpu.psm.ph6.st2.t1.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph6.st2.t1.input.psm-artifact.security-baseline`
- `pp.psm.ph6.st2.t1.in.wpu.psm.ph6.st2.t1.input.psm-artifact.network-identity`: **in** WorkProductUse `wpu.psm.ph6.st2.t1.input.psm-artifact.network-identity`
- `pp.psm.ph6.st2.t1.in.wpu.psm.ph6.st2.t1.input.psm-artifact.storage-layer`: **in** WorkProductUse `wpu.psm.ph6.st2.t1.input.psm-artifact.storage-layer`
- `pp.psm.ph6.st2.t1.in.wpu.psm.ph6.st2.t1.input.psm-artifact.messaging-layer`: **in** WorkProductUse `wpu.psm.ph6.st2.t1.input.psm-artifact.messaging-layer`
- `pp.psm.ph6.st2.t1.in.wpu.psm.ph6.st2.t1.input.psm-artifact.event-fabric`: **in** WorkProductUse `wpu.psm.ph6.st2.t1.input.psm-artifact.event-fabric`
- `pp.psm.ph6.st2.t1.in.wpu.psm.ph6.st2.t1.input.psm-artifact.compute-layer`: **in** WorkProductUse `wpu.psm.ph6.st2.t1.input.psm-artifact.compute-layer`
- `pp.psm.ph6.st2.t1.in.wpu.psm.ph6.st2.t1.input.psm-artifact.api-layer`: **in** WorkProductUse `wpu.psm.ph6.st2.t1.input.psm-artifact.api-layer`
- `pp.psm.ph6.st2.t1.in.wpu.psm.ph6.st2.t1.input.psm-artifact.workflow-observability`: **in** WorkProductUse `wpu.psm.ph6.st2.t1.input.psm-artifact.workflow-observability`
- `pp.psm.ph6.st2.t1.out.wpu.psm.ph6.st2.t1.output.psm-artifact.deployment-readiness`: **out** WorkProductUse `wpu.psm.ph6.st2.t1.output.psm-artifact.deployment-readiness`

### psm.ph6.st3.t1

<small>TaskUse of `task.psm.ph6.st3.t1`</small>

**Process performers**

- `ppf.psm.ph6.st3.t1.ru.psm.ph6.st3.process-reviewer`: primary RoleUse `ru.psm.ph6.st3.process-reviewer`

**Process parameters**

- `pp.psm.ph6.st3.t1.in.wpu.psm.ph6.st3.t1.input.psm-artifact.deployment-readiness`: **in** WorkProductUse `wpu.psm.ph6.st3.t1.input.psm-artifact.deployment-readiness`
- `pp.psm.ph6.st3.t1.in.wpu.psm.ph6.st3.t1.input.psm-artifact.increment-plan`: **in** WorkProductUse `wpu.psm.ph6.st3.t1.input.psm-artifact.increment-plan`
- `pp.psm.ph6.st3.t1.in.wpu.psm.ph6.st3.t1.input.psm-artifact.deployment-strategy`: **in** WorkProductUse `wpu.psm.ph6.st3.t1.input.psm-artifact.deployment-strategy`
- `pp.psm.ph6.st3.t1.in.wpu.psm.ph6.st3.t1.input.psm-artifact.integration-views`: **in** WorkProductUse `wpu.psm.ph6.st3.t1.input.psm-artifact.integration-views`
- `pp.psm.ph6.st3.t1.in.wpu.psm.ph6.st3.t1.input.psm-artifact.security-baseline`: **in** WorkProductUse `wpu.psm.ph6.st3.t1.input.psm-artifact.security-baseline`
- `pp.psm.ph6.st3.t1.in.wpu.psm.ph6.st3.t1.input.psm-artifact.workflow-observability`: **in** WorkProductUse `wpu.psm.ph6.st3.t1.input.psm-artifact.workflow-observability`
- `pp.psm.ph6.st3.t1.out.wpu.psm.ph6.st3.t1.output.psm-artifact.increment-review`: **out** WorkProductUse `wpu.psm.ph6.st3.t1.output.psm-artifact.increment-review`

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
