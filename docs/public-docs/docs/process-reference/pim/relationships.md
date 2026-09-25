# PIM modeling: flow and bindings

This page documents the **SPEM WorkSequence, ProcessPerformer, and ProcessParameter** elements used by the PIM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

WorkSequence records explicit process flow. ProcessPerformer binds a role use to a task use. ProcessParameter binds a work-product use to a task use and states whether it enters or leaves the task. These relationships make dependencies inspectable and prevent document order from being treated as hidden process logic.

## Summary

| Relationship area  | Items | What it controls                                    |
| ------------------ | ----: | --------------------------------------------------- |
| Work sequences     |    39 | Ordering, iteration, feedback, and conditional flow |
| Task uses          |    32 | Placement of reusable tasks in activities           |
| Process performers |    32 | Primary and supporting role bindings                |
| Process parameters |   158 | Input and output work-product bindings              |

## Work sequences

### pim.ph1 → pim.ph2

<small>WorkSequence: `ws.pim.ph1.pim.ph2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph2 → pim.ph3

<small>WorkSequence: `ws.pim.ph2.pim.ph3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph3 → pim.ph4

<small>WorkSequence: `ws.pim.ph3.pim.ph4` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph4 → pim.ph5

<small>WorkSequence: `ws.pim.ph4.pim.ph5` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph5 → pim.ph6

<small>WorkSequence: `ws.pim.ph5.pim.ph6` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph1.st0 → pim.ph1.st1

<small>WorkSequence: `ws.pim.ph1.st0.pim.ph1.st1` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph1.st1 → pim.ph1.st2

<small>WorkSequence: `ws.pim.ph1.st1.pim.ph1.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph1.st1.t1 → pim.ph1.st1.t2

<small>WorkSequence: `ws.pim.ph1.st1.t1.pim.ph1.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph1.st1.t2 → pim.ph1.st1.t3

<small>WorkSequence: `ws.pim.ph1.st1.t2.pim.ph1.st1.t3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph1.st2.t1 → pim.ph1.st2.t2

<small>WorkSequence: `ws.pim.ph1.st2.t1.pim.ph1.st2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph2.st1 → pim.ph2.st2

<small>WorkSequence: `ws.pim.ph2.st1.pim.ph2.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph2.st1.t1 → pim.ph2.st1.t2

<small>WorkSequence: `ws.pim.ph2.st1.t1.pim.ph2.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph2.st2.t1 → pim.ph2.st2.t2

<small>WorkSequence: `ws.pim.ph2.st2.t1.pim.ph2.st2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph2.st2.t2 → pim.ph2.st2.t3

<small>WorkSequence: `ws.pim.ph2.st2.t2.pim.ph2.st2.t3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph3.st1 → pim.ph3.st2

<small>WorkSequence: `ws.pim.ph3.st1.pim.ph3.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph3.st1.t1 → pim.ph3.st1.t2

<small>WorkSequence: `ws.pim.ph3.st1.t1.pim.ph3.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph3.st2.t1 → pim.ph3.st2.t2

<small>WorkSequence: `ws.pim.ph3.st2.t1.pim.ph3.st2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph4.st1 → pim.ph4.st2

<small>WorkSequence: `ws.pim.ph4.st1.pim.ph4.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph4.st1.t1 → pim.ph4.st1.t2

<small>WorkSequence: `ws.pim.ph4.st1.t1.pim.ph4.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph4.st2.t1 → pim.ph4.st2.t2

<small>WorkSequence: `ws.pim.ph4.st2.t1.pim.ph4.st2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph5.st1 → pim.ph5.st2

<small>WorkSequence: `ws.pim.ph5.st1.pim.ph5.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph5.st2 → pim.ph5.st3

<small>WorkSequence: `ws.pim.ph5.st2.pim.ph5.st3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph5.st1.t1 → pim.ph5.st1.t2

<small>WorkSequence: `ws.pim.ph5.st1.t1.pim.ph5.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph5.st2.ss1 → pim.ph5.st2.ss2

<small>WorkSequence: `ws.pim.ph5.st2.ss1.pim.ph5.st2.ss2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph5.st2.ss2 → pim.ph5.st2.ss3

<small>WorkSequence: `ws.pim.ph5.st2.ss2.pim.ph5.st2.ss3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph5.st2.ss1.t1 → pim.ph5.st2.ss1.t2

<small>WorkSequence: `ws.pim.ph5.st2.ss1.t1.pim.ph5.st2.ss1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph5.st2.ss2.t1 → pim.ph5.st2.ss2.t2

<small>WorkSequence: `ws.pim.ph5.st2.ss2.t1.pim.ph5.st2.ss2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph5.st2.ss3.t1 → pim.ph5.st2.ss3.t2

<small>WorkSequence: `ws.pim.ph5.st2.ss3.t1.pim.ph5.st2.ss3.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph5.st3.t1 → pim.ph5.st3.t2

<small>WorkSequence: `ws.pim.ph5.st3.t1.pim.ph5.st3.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph6.st1 → pim.ph6.st2

<small>WorkSequence: `ws.pim.ph6.st1.pim.ph6.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph6.st2 → pim.ph6.st3

<small>WorkSequence: `ws.pim.ph6.st2.pim.ph6.st3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### pim.ph6 → pim.ph1

<small>WorkSequence: `ws.engine-loop.pim.ph6.pim.ph1` · finishToStart</small>

After the phase-embedded review/adapt stage, revolve the PIM engine for the next service slice.

**Condition:** service-slices-remaining

### pim.ph3.st1 → pim.ph2.st1

<small>WorkSequence: `ws.rework.pim.loop.contract-gap` · finishToStart</small>

Complete contracts, then update function bindings.

**Condition:** Function contracts reference undefined schemas or event types.

### pim.ph3.st1 → pim.ph2.st2

<small>WorkSequence: `ws.rework.pim.loop.data-access` · finishToStart</small>

Extend data architecture, then replay compute tasks.

**Condition:** Functions need stores or access patterns not yet modeled.

### pim.ph3.st2 → pim.ph2.st1

<small>WorkSequence: `ws.rework.pim.loop.api-contract` · finishToStart</small>

Complete contracts, then remap API routes.

**Condition:** API routes need schemas or error mappings not defined.

### pim.ph4.st1 → pim.ph3.st1

<small>WorkSequence: `ws.rework.pim.loop.integration-topology` · finishToStart</small>

Add compute targets, then rewire integration topology.

**Condition:** Channels reference producers/consumers not defined as functions.

### pim.ph5.st1 → pim.ph3.st2

<small>WorkSequence: `ws.rework.pim.loop.security-gap` · finishToStart</small>

Identify unprotected resources; add security bindings.

**Condition:** Public endpoints lack principals or auth policies.

### pim.ph5.st2 → pim.ph4.st1

<small>WorkSequence: `ws.rework.pim.loop.policy-ripple` · finishToStart</small>

Apply policy targets on integration/compute elements.

**Condition:** Policies require integration or compute changes.

### pim.ph6.st2 → pim.ph5.st1

<small>WorkSequence: `ws.rework.pim.loop.readiness-rework` · finishToStart</small>

Jump to earliest affected assurance stage; replay to readiness.

**Condition:** PIM EVL or platform mapping reveals blocking gaps.

## Task performer and parameter bindings

### pim.ph1.st0.t1

<small>TaskUse of `task.pim.ph1.st0.t1`</small>

**Process performers**

- `ppf.pim.ph1.st0.t1.ru.pim.ph1.st0.solution-architect`: primary RoleUse `ru.pim.ph1.st0.solution-architect`

**Process parameters**

- `pp.pim.ph1.st0.t1.out.wpu.pim.ph1.st0.t1.output.pim-artifact.increment-plan`: **out** WorkProductUse `wpu.pim.ph1.st0.t1.output.pim-artifact.increment-plan`

### pim.ph1.st1.t1

<small>TaskUse of `task.pim.ph1.st1.t1`</small>

**Process performers**

- `ppf.pim.ph1.st1.t1.ru.pim.ph1.st1.solution-architect`: primary RoleUse `ru.pim.ph1.st1.solution-architect`

**Process parameters**

- `pp.pim.ph1.st1.t1.in.wpu.pim.ph1.st1.t1.input.pim-artifact.increment-plan`: **in** WorkProductUse `wpu.pim.ph1.st1.t1.input.pim-artifact.increment-plan`
- `pp.pim.ph1.st1.t1.out.wpu.pim.ph1.st1.t1.output.pim-artifact.architecture-posture`: **out** WorkProductUse `wpu.pim.ph1.st1.t1.output.pim-artifact.architecture-posture`

### pim.ph1.st1.t2

<small>TaskUse of `task.pim.ph1.st1.t2`</small>

**Process performers**

- `ppf.pim.ph1.st1.t2.ru.pim.ph1.st1.solution-architect`: primary RoleUse `ru.pim.ph1.st1.solution-architect`

**Process parameters**

- `pp.pim.ph1.st1.t2.in.wpu.pim.ph1.st1.t2.input.pim-artifact.architecture-posture`: **in** WorkProductUse `wpu.pim.ph1.st1.t2.input.pim-artifact.architecture-posture`
- `pp.pim.ph1.st1.t2.in.wpu.pim.ph1.st1.t2.input.pim-artifact.increment-plan`: **in** WorkProductUse `wpu.pim.ph1.st1.t2.input.pim-artifact.increment-plan`
- `pp.pim.ph1.st1.t2.out.wpu.pim.ph1.st1.t2.output.pim-artifact.architecture-posture`: **out** WorkProductUse `wpu.pim.ph1.st1.t2.output.pim-artifact.architecture-posture`

### pim.ph1.st1.t3

<small>TaskUse of `task.pim.ph1.st1.t3`</small>

**Process performers**

- `ppf.pim.ph1.st1.t3.ru.pim.ph1.st1.method-engineer`: primary RoleUse `ru.pim.ph1.st1.method-engineer`

**Process parameters**

- `pp.pim.ph1.st1.t3.in.wpu.pim.ph1.st1.t3.input.pim-artifact.architecture-posture`: **in** WorkProductUse `wpu.pim.ph1.st1.t3.input.pim-artifact.architecture-posture`
- `pp.pim.ph1.st1.t3.in.wpu.pim.ph1.st1.t3.input.pim-artifact.increment-plan`: **in** WorkProductUse `wpu.pim.ph1.st1.t3.input.pim-artifact.increment-plan`
- `pp.pim.ph1.st1.t3.out.wpu.pim.ph1.st1.t3.output.pim-artifact.architecture-posture`: **out** WorkProductUse `wpu.pim.ph1.st1.t3.output.pim-artifact.architecture-posture`

### pim.ph1.st2.t1

<small>TaskUse of `task.pim.ph1.st2.t1`</small>

**Process performers**

- `ppf.pim.ph1.st2.t1.ru.pim.ph1.st2.solution-architect`: primary RoleUse `ru.pim.ph1.st2.solution-architect`

**Process parameters**

- `pp.pim.ph1.st2.t1.in.wpu.pim.ph1.st2.t1.input.pim-artifact.architecture-posture`: **in** WorkProductUse `wpu.pim.ph1.st2.t1.input.pim-artifact.architecture-posture`
- `pp.pim.ph1.st2.t1.in.wpu.pim.ph1.st2.t1.input.pim-artifact.increment-plan`: **in** WorkProductUse `wpu.pim.ph1.st2.t1.input.pim-artifact.increment-plan`
- `pp.pim.ph1.st2.t1.out.wpu.pim.ph1.st2.t1.output.pim-artifact.service-map`: **out** WorkProductUse `wpu.pim.ph1.st2.t1.output.pim-artifact.service-map`

### pim.ph1.st2.t2

<small>TaskUse of `task.pim.ph1.st2.t2`</small>

**Process performers**

- `ppf.pim.ph1.st2.t2.ru.pim.ph1.st2.solution-architect`: primary RoleUse `ru.pim.ph1.st2.solution-architect`

**Process parameters**

- `pp.pim.ph1.st2.t2.in.wpu.pim.ph1.st2.t2.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph1.st2.t2.input.pim-artifact.service-map`
- `pp.pim.ph1.st2.t2.in.wpu.pim.ph1.st2.t2.input.pim-artifact.architecture-posture`: **in** WorkProductUse `wpu.pim.ph1.st2.t2.input.pim-artifact.architecture-posture`
- `pp.pim.ph1.st2.t2.out.wpu.pim.ph1.st2.t2.output.pim-artifact.service-map`: **out** WorkProductUse `wpu.pim.ph1.st2.t2.output.pim-artifact.service-map`

### pim.ph2.st1.t1

<small>TaskUse of `task.pim.ph2.st1.t1`</small>

**Process performers**

- `ppf.pim.ph2.st1.t1.ru.pim.ph2.st1.solution-architect`: primary RoleUse `ru.pim.ph2.st1.solution-architect`

**Process parameters**

- `pp.pim.ph2.st1.t1.in.wpu.pim.ph2.st1.t1.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph2.st1.t1.input.pim-artifact.service-map`
- `pp.pim.ph2.st1.t1.in.wpu.pim.ph2.st1.t1.input.pim-artifact.architecture-posture`: **in** WorkProductUse `wpu.pim.ph2.st1.t1.input.pim-artifact.architecture-posture`
- `pp.pim.ph2.st1.t1.in.wpu.pim.ph2.st1.t1.input.pim-artifact.increment-plan`: **in** WorkProductUse `wpu.pim.ph2.st1.t1.input.pim-artifact.increment-plan`
- `pp.pim.ph2.st1.t1.out.wpu.pim.ph2.st1.t1.output.pim-artifact.contract-catalog`: **out** WorkProductUse `wpu.pim.ph2.st1.t1.output.pim-artifact.contract-catalog`

### pim.ph2.st1.t2

<small>TaskUse of `task.pim.ph2.st1.t2`</small>

**Process performers**

- `ppf.pim.ph2.st1.t2.ru.pim.ph2.st1.solution-architect`: primary RoleUse `ru.pim.ph2.st1.solution-architect`

**Process parameters**

- `pp.pim.ph2.st1.t2.in.wpu.pim.ph2.st1.t2.input.pim-artifact.contract-catalog`: **in** WorkProductUse `wpu.pim.ph2.st1.t2.input.pim-artifact.contract-catalog`
- `pp.pim.ph2.st1.t2.in.wpu.pim.ph2.st1.t2.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph2.st1.t2.input.pim-artifact.service-map`
- `pp.pim.ph2.st1.t2.out.wpu.pim.ph2.st1.t2.output.pim-artifact.contract-catalog`: **out** WorkProductUse `wpu.pim.ph2.st1.t2.output.pim-artifact.contract-catalog`

### pim.ph2.st2.t1

<small>TaskUse of `task.pim.ph2.st2.t1`</small>

**Process performers**

- `ppf.pim.ph2.st2.t1.ru.pim.ph2.st2.solution-architect`: primary RoleUse `ru.pim.ph2.st2.solution-architect`

**Process parameters**

- `pp.pim.ph2.st2.t1.in.wpu.pim.ph2.st2.t1.input.pim-artifact.contract-catalog`: **in** WorkProductUse `wpu.pim.ph2.st2.t1.input.pim-artifact.contract-catalog`
- `pp.pim.ph2.st2.t1.in.wpu.pim.ph2.st2.t1.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph2.st2.t1.input.pim-artifact.service-map`
- `pp.pim.ph2.st2.t1.in.wpu.pim.ph2.st2.t1.input.pim-artifact.architecture-posture`: **in** WorkProductUse `wpu.pim.ph2.st2.t1.input.pim-artifact.architecture-posture`
- `pp.pim.ph2.st2.t1.out.wpu.pim.ph2.st2.t1.output.pim-artifact.data-architecture`: **out** WorkProductUse `wpu.pim.ph2.st2.t1.output.pim-artifact.data-architecture`

### pim.ph2.st2.t2

<small>TaskUse of `task.pim.ph2.st2.t2`</small>

**Process performers**

- `ppf.pim.ph2.st2.t2.ru.pim.ph2.st2.solution-architect`: primary RoleUse `ru.pim.ph2.st2.solution-architect`

**Process parameters**

- `pp.pim.ph2.st2.t2.in.wpu.pim.ph2.st2.t2.input.pim-artifact.data-architecture`: **in** WorkProductUse `wpu.pim.ph2.st2.t2.input.pim-artifact.data-architecture`
- `pp.pim.ph2.st2.t2.in.wpu.pim.ph2.st2.t2.input.pim-artifact.contract-catalog`: **in** WorkProductUse `wpu.pim.ph2.st2.t2.input.pim-artifact.contract-catalog`
- `pp.pim.ph2.st2.t2.in.wpu.pim.ph2.st2.t2.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph2.st2.t2.input.pim-artifact.service-map`
- `pp.pim.ph2.st2.t2.out.wpu.pim.ph2.st2.t2.output.pim-artifact.data-architecture`: **out** WorkProductUse `wpu.pim.ph2.st2.t2.output.pim-artifact.data-architecture`

### pim.ph2.st2.t3

<small>TaskUse of `task.pim.ph2.st2.t3`</small>

**Process performers**

- `ppf.pim.ph2.st2.t3.ru.pim.ph2.st2.solution-architect`: primary RoleUse `ru.pim.ph2.st2.solution-architect`

**Process parameters**

- `pp.pim.ph2.st2.t3.in.wpu.pim.ph2.st2.t3.input.pim-artifact.data-architecture`: **in** WorkProductUse `wpu.pim.ph2.st2.t3.input.pim-artifact.data-architecture`
- `pp.pim.ph2.st2.t3.in.wpu.pim.ph2.st2.t3.input.pim-artifact.contract-catalog`: **in** WorkProductUse `wpu.pim.ph2.st2.t3.input.pim-artifact.contract-catalog`
- `pp.pim.ph2.st2.t3.in.wpu.pim.ph2.st2.t3.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph2.st2.t3.input.pim-artifact.service-map`
- `pp.pim.ph2.st2.t3.out.wpu.pim.ph2.st2.t3.output.pim-artifact.data-architecture`: **out** WorkProductUse `wpu.pim.ph2.st2.t3.output.pim-artifact.data-architecture`

### pim.ph3.st1.t1

<small>TaskUse of `task.pim.ph3.st1.t1`</small>

**Process performers**

- `ppf.pim.ph3.st1.t1.ru.pim.ph3.st1.solution-architect`: primary RoleUse `ru.pim.ph3.st1.solution-architect`

**Process parameters**

- `pp.pim.ph3.st1.t1.in.wpu.pim.ph3.st1.t1.input.pim-artifact.data-architecture`: **in** WorkProductUse `wpu.pim.ph3.st1.t1.input.pim-artifact.data-architecture`
- `pp.pim.ph3.st1.t1.in.wpu.pim.ph3.st1.t1.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph3.st1.t1.input.pim-artifact.service-map`
- `pp.pim.ph3.st1.t1.in.wpu.pim.ph3.st1.t1.input.pim-artifact.contract-catalog`: **in** WorkProductUse `wpu.pim.ph3.st1.t1.input.pim-artifact.contract-catalog`
- `pp.pim.ph3.st1.t1.out.wpu.pim.ph3.st1.t1.output.pim-artifact.compute-catalog`: **out** WorkProductUse `wpu.pim.ph3.st1.t1.output.pim-artifact.compute-catalog`

### pim.ph3.st1.t2

<small>TaskUse of `task.pim.ph3.st1.t2`</small>

**Process performers**

- `ppf.pim.ph3.st1.t2.ru.pim.ph3.st1.solution-architect`: primary RoleUse `ru.pim.ph3.st1.solution-architect`

**Process parameters**

- `pp.pim.ph3.st1.t2.in.wpu.pim.ph3.st1.t2.input.pim-artifact.compute-catalog`: **in** WorkProductUse `wpu.pim.ph3.st1.t2.input.pim-artifact.compute-catalog`
- `pp.pim.ph3.st1.t2.in.wpu.pim.ph3.st1.t2.input.pim-artifact.contract-catalog`: **in** WorkProductUse `wpu.pim.ph3.st1.t2.input.pim-artifact.contract-catalog`
- `pp.pim.ph3.st1.t2.out.wpu.pim.ph3.st1.t2.output.pim-artifact.compute-catalog`: **out** WorkProductUse `wpu.pim.ph3.st1.t2.output.pim-artifact.compute-catalog`

### pim.ph3.st2.t1

<small>TaskUse of `task.pim.ph3.st2.t1`</small>

**Process performers**

- `ppf.pim.ph3.st2.t1.ru.pim.ph3.st2.solution-architect`: primary RoleUse `ru.pim.ph3.st2.solution-architect`

**Process parameters**

- `pp.pim.ph3.st2.t1.in.wpu.pim.ph3.st2.t1.input.pim-artifact.compute-catalog`: **in** WorkProductUse `wpu.pim.ph3.st2.t1.input.pim-artifact.compute-catalog`
- `pp.pim.ph3.st2.t1.in.wpu.pim.ph3.st2.t1.input.pim-artifact.contract-catalog`: **in** WorkProductUse `wpu.pim.ph3.st2.t1.input.pim-artifact.contract-catalog`
- `pp.pim.ph3.st2.t1.in.wpu.pim.ph3.st2.t1.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph3.st2.t1.input.pim-artifact.service-map`
- `pp.pim.ph3.st2.t1.out.wpu.pim.ph3.st2.t1.output.pim-artifact.api-catalog`: **out** WorkProductUse `wpu.pim.ph3.st2.t1.output.pim-artifact.api-catalog`

### pim.ph3.st2.t2

<small>TaskUse of `task.pim.ph3.st2.t2`</small>

**Process performers**

- `ppf.pim.ph3.st2.t2.ru.pim.ph3.st2.solution-architect`: primary RoleUse `ru.pim.ph3.st2.solution-architect`

**Process parameters**

- `pp.pim.ph3.st2.t2.in.wpu.pim.ph3.st2.t2.input.pim-artifact.api-catalog`: **in** WorkProductUse `wpu.pim.ph3.st2.t2.input.pim-artifact.api-catalog`
- `pp.pim.ph3.st2.t2.in.wpu.pim.ph3.st2.t2.input.pim-artifact.contract-catalog`: **in** WorkProductUse `wpu.pim.ph3.st2.t2.input.pim-artifact.contract-catalog`
- `pp.pim.ph3.st2.t2.in.wpu.pim.ph3.st2.t2.input.pim-artifact.compute-catalog`: **in** WorkProductUse `wpu.pim.ph3.st2.t2.input.pim-artifact.compute-catalog`
- `pp.pim.ph3.st2.t2.out.wpu.pim.ph3.st2.t2.output.pim-artifact.api-catalog`: **out** WorkProductUse `wpu.pim.ph3.st2.t2.output.pim-artifact.api-catalog`

### pim.ph4.st1.t1

<small>TaskUse of `task.pim.ph4.st1.t1`</small>

**Process performers**

- `ppf.pim.ph4.st1.t1.ru.pim.ph4.st1.solution-architect`: primary RoleUse `ru.pim.ph4.st1.solution-architect`

**Process parameters**

- `pp.pim.ph4.st1.t1.in.wpu.pim.ph4.st1.t1.input.pim-artifact.api-catalog`: **in** WorkProductUse `wpu.pim.ph4.st1.t1.input.pim-artifact.api-catalog`
- `pp.pim.ph4.st1.t1.in.wpu.pim.ph4.st1.t1.input.pim-artifact.contract-catalog`: **in** WorkProductUse `wpu.pim.ph4.st1.t1.input.pim-artifact.contract-catalog`
- `pp.pim.ph4.st1.t1.in.wpu.pim.ph4.st1.t1.input.pim-artifact.compute-catalog`: **in** WorkProductUse `wpu.pim.ph4.st1.t1.input.pim-artifact.compute-catalog`
- `pp.pim.ph4.st1.t1.in.wpu.pim.ph4.st1.t1.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph4.st1.t1.input.pim-artifact.service-map`
- `pp.pim.ph4.st1.t1.out.wpu.pim.ph4.st1.t1.output.pim-artifact.integration-topology`: **out** WorkProductUse `wpu.pim.ph4.st1.t1.output.pim-artifact.integration-topology`

### pim.ph4.st1.t2

<small>TaskUse of `task.pim.ph4.st1.t2`</small>

**Process performers**

- `ppf.pim.ph4.st1.t2.ru.pim.ph4.st1.solution-architect`: primary RoleUse `ru.pim.ph4.st1.solution-architect`

**Process parameters**

- `pp.pim.ph4.st1.t2.in.wpu.pim.ph4.st1.t2.input.pim-artifact.integration-topology`: **in** WorkProductUse `wpu.pim.ph4.st1.t2.input.pim-artifact.integration-topology`
- `pp.pim.ph4.st1.t2.in.wpu.pim.ph4.st1.t2.input.pim-artifact.api-catalog`: **in** WorkProductUse `wpu.pim.ph4.st1.t2.input.pim-artifact.api-catalog`
- `pp.pim.ph4.st1.t2.in.wpu.pim.ph4.st1.t2.input.pim-artifact.contract-catalog`: **in** WorkProductUse `wpu.pim.ph4.st1.t2.input.pim-artifact.contract-catalog`
- `pp.pim.ph4.st1.t2.in.wpu.pim.ph4.st1.t2.input.pim-artifact.compute-catalog`: **in** WorkProductUse `wpu.pim.ph4.st1.t2.input.pim-artifact.compute-catalog`
- `pp.pim.ph4.st1.t2.out.wpu.pim.ph4.st1.t2.output.pim-artifact.integration-topology`: **out** WorkProductUse `wpu.pim.ph4.st1.t2.output.pim-artifact.integration-topology`

### pim.ph4.st2.t1

<small>TaskUse of `task.pim.ph4.st2.t1`</small>

**Process performers**

- `ppf.pim.ph4.st2.t1.ru.pim.ph4.st2.solution-architect`: primary RoleUse `ru.pim.ph4.st2.solution-architect`

**Process parameters**

- `pp.pim.ph4.st2.t1.in.wpu.pim.ph4.st2.t1.input.pim-artifact.integration-topology`: **in** WorkProductUse `wpu.pim.ph4.st2.t1.input.pim-artifact.integration-topology`
- `pp.pim.ph4.st2.t1.in.wpu.pim.ph4.st2.t1.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph4.st2.t1.input.pim-artifact.service-map`
- `pp.pim.ph4.st2.t1.in.wpu.pim.ph4.st2.t1.input.pim-artifact.api-catalog`: **in** WorkProductUse `wpu.pim.ph4.st2.t1.input.pim-artifact.api-catalog`
- `pp.pim.ph4.st2.t1.in.wpu.pim.ph4.st2.t1.input.pim-artifact.contract-catalog`: **in** WorkProductUse `wpu.pim.ph4.st2.t1.input.pim-artifact.contract-catalog`
- `pp.pim.ph4.st2.t1.out.wpu.pim.ph4.st2.t1.output.pim-artifact.workflow-model`: **out** WorkProductUse `wpu.pim.ph4.st2.t1.output.pim-artifact.workflow-model`

### pim.ph4.st2.t2

<small>TaskUse of `task.pim.ph4.st2.t2`</small>

**Process performers**

- `ppf.pim.ph4.st2.t2.ru.pim.ph4.st2.solution-architect`: primary RoleUse `ru.pim.ph4.st2.solution-architect`

**Process parameters**

- `pp.pim.ph4.st2.t2.in.wpu.pim.ph4.st2.t2.input.pim-artifact.workflow-model`: **in** WorkProductUse `wpu.pim.ph4.st2.t2.input.pim-artifact.workflow-model`
- `pp.pim.ph4.st2.t2.in.wpu.pim.ph4.st2.t2.input.pim-artifact.integration-topology`: **in** WorkProductUse `wpu.pim.ph4.st2.t2.input.pim-artifact.integration-topology`
- `pp.pim.ph4.st2.t2.in.wpu.pim.ph4.st2.t2.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph4.st2.t2.input.pim-artifact.service-map`
- `pp.pim.ph4.st2.t2.in.wpu.pim.ph4.st2.t2.input.pim-artifact.api-catalog`: **in** WorkProductUse `wpu.pim.ph4.st2.t2.input.pim-artifact.api-catalog`
- `pp.pim.ph4.st2.t2.in.wpu.pim.ph4.st2.t2.input.pim-artifact.contract-catalog`: **in** WorkProductUse `wpu.pim.ph4.st2.t2.input.pim-artifact.contract-catalog`
- `pp.pim.ph4.st2.t2.out.wpu.pim.ph4.st2.t2.output.pim-artifact.workflow-model`: **out** WorkProductUse `wpu.pim.ph4.st2.t2.output.pim-artifact.workflow-model`

### pim.ph5.st1.t1

<small>TaskUse of `task.pim.ph5.st1.t1`</small>

**Process performers**

- `ppf.pim.ph5.st1.t1.ru.pim.ph5.st1.solution-architect`: primary RoleUse `ru.pim.ph5.st1.solution-architect`

**Process parameters**

- `pp.pim.ph5.st1.t1.in.wpu.pim.ph5.st1.t1.input.pim-artifact.architecture-posture`: **in** WorkProductUse `wpu.pim.ph5.st1.t1.input.pim-artifact.architecture-posture`
- `pp.pim.ph5.st1.t1.in.wpu.pim.ph5.st1.t1.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph5.st1.t1.input.pim-artifact.service-map`
- `pp.pim.ph5.st1.t1.in.wpu.pim.ph5.st1.t1.input.pim-artifact.contract-catalog`: **in** WorkProductUse `wpu.pim.ph5.st1.t1.input.pim-artifact.contract-catalog`
- `pp.pim.ph5.st1.t1.out.wpu.pim.ph5.st1.t1.output.pim-artifact.security-model`: **out** WorkProductUse `wpu.pim.ph5.st1.t1.output.pim-artifact.security-model`

### pim.ph5.st1.t2

<small>TaskUse of `task.pim.ph5.st1.t2`</small>

**Process performers**

- `ppf.pim.ph5.st1.t2.ru.pim.ph5.st1.solution-architect`: primary RoleUse `ru.pim.ph5.st1.solution-architect`

**Process parameters**

- `pp.pim.ph5.st1.t2.in.wpu.pim.ph5.st1.t2.input.pim-artifact.security-model`: **in** WorkProductUse `wpu.pim.ph5.st1.t2.input.pim-artifact.security-model`
- `pp.pim.ph5.st1.t2.in.wpu.pim.ph5.st1.t2.input.pim-artifact.architecture-posture`: **in** WorkProductUse `wpu.pim.ph5.st1.t2.input.pim-artifact.architecture-posture`
- `pp.pim.ph5.st1.t2.in.wpu.pim.ph5.st1.t2.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph5.st1.t2.input.pim-artifact.service-map`
- `pp.pim.ph5.st1.t2.in.wpu.pim.ph5.st1.t2.input.pim-artifact.api-catalog`: **in** WorkProductUse `wpu.pim.ph5.st1.t2.input.pim-artifact.api-catalog`
- `pp.pim.ph5.st1.t2.in.wpu.pim.ph5.st1.t2.input.pim-artifact.compute-catalog`: **in** WorkProductUse `wpu.pim.ph5.st1.t2.input.pim-artifact.compute-catalog`
- `pp.pim.ph5.st1.t2.in.wpu.pim.ph5.st1.t2.input.pim-artifact.contract-catalog`: **in** WorkProductUse `wpu.pim.ph5.st1.t2.input.pim-artifact.contract-catalog`
- `pp.pim.ph5.st1.t2.out.wpu.pim.ph5.st1.t2.output.pim-artifact.security-model`: **out** WorkProductUse `wpu.pim.ph5.st1.t2.output.pim-artifact.security-model`

### pim.ph5.st2.ss1.t1

<small>TaskUse of `task.pim.ph5.st2.ss1.t1`</small>

**Process performers**

- `ppf.pim.ph5.st2.ss1.t1.ru.pim.ph5.st2.ss1.solution-architect`: primary RoleUse `ru.pim.ph5.st2.ss1.solution-architect`

**Process parameters**

- `pp.pim.ph5.st2.ss1.t1.in.wpu.pim.ph5.st2.ss1.t1.input.pim-artifact.security-model`: **in** WorkProductUse `wpu.pim.ph5.st2.ss1.t1.input.pim-artifact.security-model`
- `pp.pim.ph5.st2.ss1.t1.in.wpu.pim.ph5.st2.ss1.t1.input.pim-artifact.compute-catalog`: **in** WorkProductUse `wpu.pim.ph5.st2.ss1.t1.input.pim-artifact.compute-catalog`
- `pp.pim.ph5.st2.ss1.t1.in.wpu.pim.ph5.st2.ss1.t1.input.pim-artifact.integration-topology`: **in** WorkProductUse `wpu.pim.ph5.st2.ss1.t1.input.pim-artifact.integration-topology`
- `pp.pim.ph5.st2.ss1.t1.in.wpu.pim.ph5.st2.ss1.t1.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph5.st2.ss1.t1.input.pim-artifact.service-map`
- `pp.pim.ph5.st2.ss1.t1.out.wpu.pim.ph5.st2.ss1.t1.output.pim-artifact.policy-catalog`: **out** WorkProductUse `wpu.pim.ph5.st2.ss1.t1.output.pim-artifact.policy-catalog`

### pim.ph5.st2.ss1.t2

<small>TaskUse of `task.pim.ph5.st2.ss1.t2`</small>

**Process performers**

- `ppf.pim.ph5.st2.ss1.t2.ru.pim.ph5.st2.ss1.solution-architect`: primary RoleUse `ru.pim.ph5.st2.ss1.solution-architect`

**Process parameters**

- `pp.pim.ph5.st2.ss1.t2.in.wpu.pim.ph5.st2.ss1.t2.input.pim-artifact.policy-catalog`: **in** WorkProductUse `wpu.pim.ph5.st2.ss1.t2.input.pim-artifact.policy-catalog`
- `pp.pim.ph5.st2.ss1.t2.in.wpu.pim.ph5.st2.ss1.t2.input.pim-artifact.data-architecture`: **in** WorkProductUse `wpu.pim.ph5.st2.ss1.t2.input.pim-artifact.data-architecture`
- `pp.pim.ph5.st2.ss1.t2.in.wpu.pim.ph5.st2.ss1.t2.input.pim-artifact.api-catalog`: **in** WorkProductUse `wpu.pim.ph5.st2.ss1.t2.input.pim-artifact.api-catalog`
- `pp.pim.ph5.st2.ss1.t2.in.wpu.pim.ph5.st2.ss1.t2.input.pim-artifact.compute-catalog`: **in** WorkProductUse `wpu.pim.ph5.st2.ss1.t2.input.pim-artifact.compute-catalog`
- `pp.pim.ph5.st2.ss1.t2.in.wpu.pim.ph5.st2.ss1.t2.input.pim-artifact.integration-topology`: **in** WorkProductUse `wpu.pim.ph5.st2.ss1.t2.input.pim-artifact.integration-topology`
- `pp.pim.ph5.st2.ss1.t2.out.wpu.pim.ph5.st2.ss1.t2.output.pim-artifact.policy-catalog`: **out** WorkProductUse `wpu.pim.ph5.st2.ss1.t2.output.pim-artifact.policy-catalog`

### pim.ph5.st2.ss2.t1

<small>TaskUse of `task.pim.ph5.st2.ss2.t1`</small>

**Process performers**

- `ppf.pim.ph5.st2.ss2.t1.ru.pim.ph5.st2.ss2.solution-architect`: primary RoleUse `ru.pim.ph5.st2.ss2.solution-architect`

**Process parameters**

- `pp.pim.ph5.st2.ss2.t1.in.wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.policy-catalog`: **in** WorkProductUse `wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.policy-catalog`
- `pp.pim.ph5.st2.ss2.t1.in.wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.compute-catalog`: **in** WorkProductUse `wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.compute-catalog`
- `pp.pim.ph5.st2.ss2.t1.in.wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.api-catalog`: **in** WorkProductUse `wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.api-catalog`
- `pp.pim.ph5.st2.ss2.t1.in.wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.integration-topology`: **in** WorkProductUse `wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.integration-topology`
- `pp.pim.ph5.st2.ss2.t1.in.wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.workflow-model`: **in** WorkProductUse `wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.workflow-model`
- `pp.pim.ph5.st2.ss2.t1.in.wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph5.st2.ss2.t1.input.pim-artifact.service-map`
- `pp.pim.ph5.st2.ss2.t1.out.wpu.pim.ph5.st2.ss2.t1.output.pim-artifact.policy-catalog`: **out** WorkProductUse `wpu.pim.ph5.st2.ss2.t1.output.pim-artifact.policy-catalog`

### pim.ph5.st2.ss2.t2

<small>TaskUse of `task.pim.ph5.st2.ss2.t2`</small>

**Process performers**

- `ppf.pim.ph5.st2.ss2.t2.ru.pim.ph5.st2.ss2.solution-architect`: primary RoleUse `ru.pim.ph5.st2.ss2.solution-architect`

**Process parameters**

- `pp.pim.ph5.st2.ss2.t2.in.wpu.pim.ph5.st2.ss2.t2.input.pim-artifact.policy-catalog`: **in** WorkProductUse `wpu.pim.ph5.st2.ss2.t2.input.pim-artifact.policy-catalog`
- `pp.pim.ph5.st2.ss2.t2.in.wpu.pim.ph5.st2.ss2.t2.input.pim-artifact.api-catalog`: **in** WorkProductUse `wpu.pim.ph5.st2.ss2.t2.input.pim-artifact.api-catalog`
- `pp.pim.ph5.st2.ss2.t2.in.wpu.pim.ph5.st2.ss2.t2.input.pim-artifact.workflow-model`: **in** WorkProductUse `wpu.pim.ph5.st2.ss2.t2.input.pim-artifact.workflow-model`
- `pp.pim.ph5.st2.ss2.t2.in.wpu.pim.ph5.st2.ss2.t2.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph5.st2.ss2.t2.input.pim-artifact.service-map`
- `pp.pim.ph5.st2.ss2.t2.out.wpu.pim.ph5.st2.ss2.t2.output.pim-artifact.policy-catalog`: **out** WorkProductUse `wpu.pim.ph5.st2.ss2.t2.output.pim-artifact.policy-catalog`

### pim.ph5.st2.ss3.t1

<small>TaskUse of `task.pim.ph5.st2.ss3.t1`</small>

**Process performers**

- `ppf.pim.ph5.st2.ss3.t1.ru.pim.ph5.st2.ss3.solution-architect`: primary RoleUse `ru.pim.ph5.st2.ss3.solution-architect`

**Process parameters**

- `pp.pim.ph5.st2.ss3.t1.in.wpu.pim.ph5.st2.ss3.t1.input.pim-artifact.policy-catalog`: **in** WorkProductUse `wpu.pim.ph5.st2.ss3.t1.input.pim-artifact.policy-catalog`
- `pp.pim.ph5.st2.ss3.t1.in.wpu.pim.ph5.st2.ss3.t1.input.pim-artifact.security-model`: **in** WorkProductUse `wpu.pim.ph5.st2.ss3.t1.input.pim-artifact.security-model`
- `pp.pim.ph5.st2.ss3.t1.in.wpu.pim.ph5.st2.ss3.t1.input.pim-artifact.data-architecture`: **in** WorkProductUse `wpu.pim.ph5.st2.ss3.t1.input.pim-artifact.data-architecture`
- `pp.pim.ph5.st2.ss3.t1.out.wpu.pim.ph5.st2.ss3.t1.output.pim-artifact.policy-catalog`: **out** WorkProductUse `wpu.pim.ph5.st2.ss3.t1.output.pim-artifact.policy-catalog`

### pim.ph5.st2.ss3.t2

<small>TaskUse of `task.pim.ph5.st2.ss3.t2`</small>

**Process performers**

- `ppf.pim.ph5.st2.ss3.t2.ru.pim.ph5.st2.ss3.solution-architect`: primary RoleUse `ru.pim.ph5.st2.ss3.solution-architect`

**Process parameters**

- `pp.pim.ph5.st2.ss3.t2.in.wpu.pim.ph5.st2.ss3.t2.input.pim-artifact.policy-catalog`: **in** WorkProductUse `wpu.pim.ph5.st2.ss3.t2.input.pim-artifact.policy-catalog`
- `pp.pim.ph5.st2.ss3.t2.in.wpu.pim.ph5.st2.ss3.t2.input.pim-artifact.workflow-model`: **in** WorkProductUse `wpu.pim.ph5.st2.ss3.t2.input.pim-artifact.workflow-model`
- `pp.pim.ph5.st2.ss3.t2.in.wpu.pim.ph5.st2.ss3.t2.input.pim-artifact.api-catalog`: **in** WorkProductUse `wpu.pim.ph5.st2.ss3.t2.input.pim-artifact.api-catalog`
- `pp.pim.ph5.st2.ss3.t2.in.wpu.pim.ph5.st2.ss3.t2.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph5.st2.ss3.t2.input.pim-artifact.service-map`
- `pp.pim.ph5.st2.ss3.t2.out.wpu.pim.ph5.st2.ss3.t2.output.pim-artifact.policy-catalog`: **out** WorkProductUse `wpu.pim.ph5.st2.ss3.t2.output.pim-artifact.policy-catalog`

### pim.ph5.st3.t1

<small>TaskUse of `task.pim.ph5.st3.t1`</small>

**Process performers**

- `ppf.pim.ph5.st3.t1.ru.pim.ph5.st3.solution-architect`: primary RoleUse `ru.pim.ph5.st3.solution-architect`

**Process parameters**

- `pp.pim.ph5.st3.t1.in.wpu.pim.ph5.st3.t1.input.pim-artifact.policy-catalog`: **in** WorkProductUse `wpu.pim.ph5.st3.t1.input.pim-artifact.policy-catalog`
- `pp.pim.ph5.st3.t1.in.wpu.pim.ph5.st3.t1.input.pim-artifact.api-catalog`: **in** WorkProductUse `wpu.pim.ph5.st3.t1.input.pim-artifact.api-catalog`
- `pp.pim.ph5.st3.t1.in.wpu.pim.ph5.st3.t1.input.pim-artifact.integration-topology`: **in** WorkProductUse `wpu.pim.ph5.st3.t1.input.pim-artifact.integration-topology`
- `pp.pim.ph5.st3.t1.in.wpu.pim.ph5.st3.t1.input.pim-artifact.contract-catalog`: **in** WorkProductUse `wpu.pim.ph5.st3.t1.input.pim-artifact.contract-catalog`
- `pp.pim.ph5.st3.t1.in.wpu.pim.ph5.st3.t1.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph5.st3.t1.input.pim-artifact.service-map`
- `pp.pim.ph5.st3.t1.in.wpu.pim.ph5.st3.t1.input.pim-artifact.architecture-posture`: **in** WorkProductUse `wpu.pim.ph5.st3.t1.input.pim-artifact.architecture-posture`
- `pp.pim.ph5.st3.t1.out.wpu.pim.ph5.st3.t1.output.pim-artifact.config-package`: **out** WorkProductUse `wpu.pim.ph5.st3.t1.output.pim-artifact.config-package`

### pim.ph5.st3.t2

<small>TaskUse of `task.pim.ph5.st3.t2`</small>

**Process performers**

- `ppf.pim.ph5.st3.t2.ru.pim.ph5.st3.solution-architect`: primary RoleUse `ru.pim.ph5.st3.solution-architect`

**Process parameters**

- `pp.pim.ph5.st3.t2.in.wpu.pim.ph5.st3.t2.input.pim-artifact.config-package`: **in** WorkProductUse `wpu.pim.ph5.st3.t2.input.pim-artifact.config-package`
- `pp.pim.ph5.st3.t2.in.wpu.pim.ph5.st3.t2.input.pim-artifact.architecture-posture`: **in** WorkProductUse `wpu.pim.ph5.st3.t2.input.pim-artifact.architecture-posture`
- `pp.pim.ph5.st3.t2.in.wpu.pim.ph5.st3.t2.input.pim-artifact.security-model`: **in** WorkProductUse `wpu.pim.ph5.st3.t2.input.pim-artifact.security-model`
- `pp.pim.ph5.st3.t2.in.wpu.pim.ph5.st3.t2.input.pim-artifact.compute-catalog`: **in** WorkProductUse `wpu.pim.ph5.st3.t2.input.pim-artifact.compute-catalog`
- `pp.pim.ph5.st3.t2.in.wpu.pim.ph5.st3.t2.input.pim-artifact.api-catalog`: **in** WorkProductUse `wpu.pim.ph5.st3.t2.input.pim-artifact.api-catalog`
- `pp.pim.ph5.st3.t2.in.wpu.pim.ph5.st3.t2.input.pim-artifact.integration-topology`: **in** WorkProductUse `wpu.pim.ph5.st3.t2.input.pim-artifact.integration-topology`
- `pp.pim.ph5.st3.t2.out.wpu.pim.ph5.st3.t2.output.pim-artifact.config-package`: **out** WorkProductUse `wpu.pim.ph5.st3.t2.output.pim-artifact.config-package`

### pim.ph6.st1.t1

<small>TaskUse of `task.pim.ph6.st1.t1`</small>

**Process performers**

- `ppf.pim.ph6.st1.t1.ru.pim.ph6.st1.process-reviewer`: primary RoleUse `ru.pim.ph6.st1.process-reviewer`

**Process parameters**

- `pp.pim.ph6.st1.t1.in.wpu.pim.ph6.st1.t1.input.pim-artifact.config-package`: **in** WorkProductUse `wpu.pim.ph6.st1.t1.input.pim-artifact.config-package`
- `pp.pim.ph6.st1.t1.in.wpu.pim.ph6.st1.t1.input.pim-artifact.architecture-posture`: **in** WorkProductUse `wpu.pim.ph6.st1.t1.input.pim-artifact.architecture-posture`
- `pp.pim.ph6.st1.t1.in.wpu.pim.ph6.st1.t1.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph6.st1.t1.input.pim-artifact.service-map`
- `pp.pim.ph6.st1.t1.in.wpu.pim.ph6.st1.t1.input.pim-artifact.data-architecture`: **in** WorkProductUse `wpu.pim.ph6.st1.t1.input.pim-artifact.data-architecture`
- `pp.pim.ph6.st1.t1.in.wpu.pim.ph6.st1.t1.input.pim-artifact.compute-catalog`: **in** WorkProductUse `wpu.pim.ph6.st1.t1.input.pim-artifact.compute-catalog`
- `pp.pim.ph6.st1.t1.in.wpu.pim.ph6.st1.t1.input.pim-artifact.api-catalog`: **in** WorkProductUse `wpu.pim.ph6.st1.t1.input.pim-artifact.api-catalog`
- `pp.pim.ph6.st1.t1.in.wpu.pim.ph6.st1.t1.input.pim-artifact.integration-topology`: **in** WorkProductUse `wpu.pim.ph6.st1.t1.input.pim-artifact.integration-topology`
- `pp.pim.ph6.st1.t1.in.wpu.pim.ph6.st1.t1.input.pim-artifact.security-model`: **in** WorkProductUse `wpu.pim.ph6.st1.t1.input.pim-artifact.security-model`
- `pp.pim.ph6.st1.t1.in.wpu.pim.ph6.st1.t1.input.pim-artifact.policy-catalog`: **in** WorkProductUse `wpu.pim.ph6.st1.t1.input.pim-artifact.policy-catalog`
- `pp.pim.ph6.st1.t1.out.wpu.pim.ph6.st1.t1.output.pim-artifact.platform-readiness`: **out** WorkProductUse `wpu.pim.ph6.st1.t1.output.pim-artifact.platform-readiness`

### pim.ph6.st2.t1

<small>TaskUse of `task.pim.ph6.st2.t1`</small>

**Process performers**

- `ppf.pim.ph6.st2.t1.ru.pim.ph6.st2.process-reviewer`: primary RoleUse `ru.pim.ph6.st2.process-reviewer`

**Process parameters**

- `pp.pim.ph6.st2.t1.in.wpu.pim.ph6.st2.t1.input.pim-artifact.platform-readiness`: **in** WorkProductUse `wpu.pim.ph6.st2.t1.input.pim-artifact.platform-readiness`
- `pp.pim.ph6.st2.t1.in.wpu.pim.ph6.st2.t1.input.pim-artifact.increment-plan`: **in** WorkProductUse `wpu.pim.ph6.st2.t1.input.pim-artifact.increment-plan`
- `pp.pim.ph6.st2.t1.in.wpu.pim.ph6.st2.t1.input.pim-artifact.architecture-posture`: **in** WorkProductUse `wpu.pim.ph6.st2.t1.input.pim-artifact.architecture-posture`
- `pp.pim.ph6.st2.t1.in.wpu.pim.ph6.st2.t1.input.pim-artifact.service-map`: **in** WorkProductUse `wpu.pim.ph6.st2.t1.input.pim-artifact.service-map`
- `pp.pim.ph6.st2.t1.in.wpu.pim.ph6.st2.t1.input.pim-artifact.contract-catalog`: **in** WorkProductUse `wpu.pim.ph6.st2.t1.input.pim-artifact.contract-catalog`
- `pp.pim.ph6.st2.t1.in.wpu.pim.ph6.st2.t1.input.pim-artifact.data-architecture`: **in** WorkProductUse `wpu.pim.ph6.st2.t1.input.pim-artifact.data-architecture`
- `pp.pim.ph6.st2.t1.in.wpu.pim.ph6.st2.t1.input.pim-artifact.compute-catalog`: **in** WorkProductUse `wpu.pim.ph6.st2.t1.input.pim-artifact.compute-catalog`
- `pp.pim.ph6.st2.t1.in.wpu.pim.ph6.st2.t1.input.pim-artifact.api-catalog`: **in** WorkProductUse `wpu.pim.ph6.st2.t1.input.pim-artifact.api-catalog`
- `pp.pim.ph6.st2.t1.in.wpu.pim.ph6.st2.t1.input.pim-artifact.integration-topology`: **in** WorkProductUse `wpu.pim.ph6.st2.t1.input.pim-artifact.integration-topology`
- `pp.pim.ph6.st2.t1.in.wpu.pim.ph6.st2.t1.input.pim-artifact.workflow-model`: **in** WorkProductUse `wpu.pim.ph6.st2.t1.input.pim-artifact.workflow-model`
- `pp.pim.ph6.st2.t1.in.wpu.pim.ph6.st2.t1.input.pim-artifact.security-model`: **in** WorkProductUse `wpu.pim.ph6.st2.t1.input.pim-artifact.security-model`
- `pp.pim.ph6.st2.t1.in.wpu.pim.ph6.st2.t1.input.pim-artifact.policy-catalog`: **in** WorkProductUse `wpu.pim.ph6.st2.t1.input.pim-artifact.policy-catalog`
- `pp.pim.ph6.st2.t1.in.wpu.pim.ph6.st2.t1.input.pim-artifact.config-package`: **in** WorkProductUse `wpu.pim.ph6.st2.t1.input.pim-artifact.config-package`
- `pp.pim.ph6.st2.t1.out.wpu.pim.ph6.st2.t1.output.pim-artifact.platform-readiness`: **out** WorkProductUse `wpu.pim.ph6.st2.t1.output.pim-artifact.platform-readiness`

### pim.ph6.st3.t1

<small>TaskUse of `task.pim.ph6.st3.t1`</small>

**Process performers**

- `ppf.pim.ph6.st3.t1.ru.pim.ph6.st3.process-reviewer`: primary RoleUse `ru.pim.ph6.st3.process-reviewer`

**Process parameters**

- `pp.pim.ph6.st3.t1.in.wpu.pim.ph6.st3.t1.input.pim-artifact.platform-readiness`: **in** WorkProductUse `wpu.pim.ph6.st3.t1.input.pim-artifact.platform-readiness`
- `pp.pim.ph6.st3.t1.in.wpu.pim.ph6.st3.t1.input.pim-artifact.increment-plan`: **in** WorkProductUse `wpu.pim.ph6.st3.t1.input.pim-artifact.increment-plan`
- `pp.pim.ph6.st3.t1.in.wpu.pim.ph6.st3.t1.input.pim-artifact.architecture-posture`: **in** WorkProductUse `wpu.pim.ph6.st3.t1.input.pim-artifact.architecture-posture`
- `pp.pim.ph6.st3.t1.in.wpu.pim.ph6.st3.t1.input.pim-artifact.security-model`: **in** WorkProductUse `wpu.pim.ph6.st3.t1.input.pim-artifact.security-model`
- `pp.pim.ph6.st3.t1.in.wpu.pim.ph6.st3.t1.input.pim-artifact.policy-catalog`: **in** WorkProductUse `wpu.pim.ph6.st3.t1.input.pim-artifact.policy-catalog`
- `pp.pim.ph6.st3.t1.in.wpu.pim.ph6.st3.t1.input.pim-artifact.config-package`: **in** WorkProductUse `wpu.pim.ph6.st3.t1.input.pim-artifact.config-package`
- `pp.pim.ph6.st3.t1.out.wpu.pim.ph6.st3.t1.output.pim-artifact.increment-review`: **out** WorkProductUse `wpu.pim.ph6.st3.t1.output.pim-artifact.increment-review`

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
