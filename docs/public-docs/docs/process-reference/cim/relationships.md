# CIM modeling: flow and bindings

This page documents the **SPEM WorkSequence, ProcessPerformer, and ProcessParameter** elements used by the CIM modeling process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

WorkSequence records explicit process flow. ProcessPerformer binds a role use to a task use. ProcessParameter binds a work-product use to a task use and states whether it enters or leaves the task. These relationships make dependencies inspectable and prevent document order from being treated as hidden process logic.

## Summary

| Relationship area  | Items | What it controls                                    |
| ------------------ | ----: | --------------------------------------------------- |
| Work sequences     |    33 | Ordering, iteration, feedback, and conditional flow |
| Task uses          |    26 | Placement of reusable tasks in activities           |
| Process performers |    26 | Primary and supporting role bindings                |
| Process parameters |   103 | Input and output work-product bindings              |

## Work sequences

### cim.ph1 → cim.ph2

<small>WorkSequence: `ws.cim.ph1.cim.ph2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph2 → cim.ph3

<small>WorkSequence: `ws.cim.ph2.cim.ph3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph3 → cim.ph4

<small>WorkSequence: `ws.cim.ph3.cim.ph4` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph4 → cim.ph5

<small>WorkSequence: `ws.cim.ph4.cim.ph5` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph1.st1 → cim.ph1.st2

<small>WorkSequence: `ws.cim.ph1.st1.cim.ph1.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph1.st2 → cim.ph1.st3

<small>WorkSequence: `ws.cim.ph1.st2.cim.ph1.st3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph1.st1.t1 → cim.ph1.st1.t2

<small>WorkSequence: `ws.cim.ph1.st1.t1.cim.ph1.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph1.st3.t1 → cim.ph1.st3.t2

<small>WorkSequence: `ws.cim.ph1.st3.t1.cim.ph1.st3.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph2.st1 → cim.ph2.st2

<small>WorkSequence: `ws.cim.ph2.st1.cim.ph2.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph2.st2 → cim.ph2.st3

<small>WorkSequence: `ws.cim.ph2.st2.cim.ph2.st3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph2.st1.t1 → cim.ph2.st1.t2

<small>WorkSequence: `ws.cim.ph2.st1.t1.cim.ph2.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph2.st2.t1 → cim.ph2.st2.t2

<small>WorkSequence: `ws.cim.ph2.st2.t1.cim.ph2.st2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph3.st1 → cim.ph3.st2

<small>WorkSequence: `ws.cim.ph3.st1.cim.ph3.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph3.st2 → cim.ph3.st3

<small>WorkSequence: `ws.cim.ph3.st2.cim.ph3.st3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph3.st1.ss1 → cim.ph3.st1.ss2

<small>WorkSequence: `ws.cim.ph3.st1.ss1.cim.ph3.st1.ss2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph3.st2.ss1 → cim.ph3.st2.ss2

<small>WorkSequence: `ws.cim.ph3.st2.ss1.cim.ph3.st2.ss2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph3.st3.ss1 → cim.ph3.st3.ss2

<small>WorkSequence: `ws.cim.ph3.st3.ss1.cim.ph3.st3.ss2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph3.st3.ss2 → cim.ph3.st3.ss3

<small>WorkSequence: `ws.cim.ph3.st3.ss2.cim.ph3.st3.ss3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph4.st1 → cim.ph4.st2

<small>WorkSequence: `ws.cim.ph4.st1.cim.ph4.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph4.st2 → cim.ph4.st3

<small>WorkSequence: `ws.cim.ph4.st2.cim.ph4.st3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph4.st2.ss1 → cim.ph4.st2.ss2

<small>WorkSequence: `ws.cim.ph4.st2.ss1.cim.ph4.st2.ss2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph5.st1 → cim.ph5.st2

<small>WorkSequence: `ws.cim.ph5.st1.cim.ph5.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph5.st2 → cim.ph5.st3

<small>WorkSequence: `ws.cim.ph5.st2.cim.ph5.st3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph5.st3 → cim.ph5.st4

<small>WorkSequence: `ws.cim.ph5.st3.cim.ph5.st4` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph5.st1.t1 → cim.ph5.st1.t2

<small>WorkSequence: `ws.cim.ph5.st1.t1.cim.ph5.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### cim.ph5 → cim.ph1

<small>WorkSequence: `ws.engine-loop.cim.ph5.cim.ph1` · finishToStart</small>

After the phase-embedded review/adapt stage, revolve the CIM engine for the next capability slice.

**Condition:** increment-backlog-remaining

### cim.ph3.st2.ss1 → cim.ph2.st3

<small>WorkSequence: `ws.rework.cim.loop.language-refine` · finishToStart</small>

Update glossary, then replay information taxonomy and domain structure.

**Condition:** Entity naming conflicts during structural modeling.

### cim.ph3.st3 → cim.ph3.st1

<small>WorkSequence: `ws.rework.cim.loop.twin-peaks-explore` · finishToStart</small>

Extend taxonomy and structure, then resume behavior surface.

**Condition:** Behavior modeling reveals missing information items or entities.

### cim.ph4.st1 → cim.ph3.st3.ss1

<small>WorkSequence: `ws.rework.cim.loop.aggregate-rework` · finishToStart</small>

Adjust commands and events, then re-synthesize aggregates.

**Condition:** Aggregate grouping exposes command/event ownership conflicts.

### cim.ph4.st2.ss1 → cim.ph3.st3

<small>WorkSequence: `ws.rework.cim.loop.process-gap` · finishToStart</small>

Add behavior elements, then rebuild process flows.

**Condition:** Process steps reference behavior not on the CQRS surface.

### cim.ph4.st3 → cim.ph4.st1

<small>WorkSequence: `ws.rework.cim.loop.context-resynth` · finishToStart</small>

Revisit aggregates, then re-run context synthesis.

**Condition:** Context membership is incoherent across aggregates.

### cim.ph5.st1 → cim.ph3.st2

<small>WorkSequence: `ws.rework.cim.loop.requirements-backfill` · finishToStart</small>

Extend domain model, then re-link requirements.

**Condition:** Requirements cannot be traced to domain or behavior elements.

### cim.ph5.st3 → cim.ph5.st1

<small>WorkSequence: `ws.rework.cim.loop.readiness-rework` · finishToStart</small>

Resolve at source stage; replay forward to readiness gate.

**Condition:** EVL or readiness findings identify blocking gaps.

## Task performer and parameter bindings

### cim.ph1.st1.t1

<small>TaskUse of `task.cim.ph1.st1.t1`</small>

**Process performers**

- `ppf.cim.ph1.st1.t1.ru.cim.ph1.st1.business-modeler`: primary RoleUse `ru.cim.ph1.st1.business-modeler`

**Process parameters**

- `pp.cim.ph1.st1.t1.out.wpu.cim.ph1.st1.t1.output.cim-artifact.model-root`: **out** WorkProductUse `wpu.cim.ph1.st1.t1.output.cim-artifact.model-root`

### cim.ph1.st1.t2

<small>TaskUse of `task.cim.ph1.st1.t2`</small>

**Process performers**

- `ppf.cim.ph1.st1.t2.ru.cim.ph1.st1.method-engineer`: primary RoleUse `ru.cim.ph1.st1.method-engineer`

**Process parameters**

- `pp.cim.ph1.st1.t2.in.wpu.cim.ph1.st1.t2.input.cim-artifact.model-root`: **in** WorkProductUse `wpu.cim.ph1.st1.t2.input.cim-artifact.model-root`
- `pp.cim.ph1.st1.t2.out.wpu.cim.ph1.st1.t2.output.cim-artifact.model-root`: **out** WorkProductUse `wpu.cim.ph1.st1.t2.output.cim-artifact.model-root`

### cim.ph1.st2.t1

<small>TaskUse of `task.cim.ph1.st2.t1`</small>

**Process performers**

- `ppf.cim.ph1.st2.t1.ru.cim.ph1.st2.business-modeler`: primary RoleUse `ru.cim.ph1.st2.business-modeler`

**Process parameters**

- `pp.cim.ph1.st2.t1.in.wpu.cim.ph1.st2.t1.input.cim-artifact.model-root`: **in** WorkProductUse `wpu.cim.ph1.st2.t1.input.cim-artifact.model-root`
- `pp.cim.ph1.st2.t1.out.wpu.cim.ph1.st2.t1.output.cim-artifact.increment-plan`: **out** WorkProductUse `wpu.cim.ph1.st2.t1.output.cim-artifact.increment-plan`

### cim.ph1.st3.t1

<small>TaskUse of `task.cim.ph1.st3.t1`</small>

**Process performers**

- `ppf.cim.ph1.st3.t1.ru.cim.ph1.st3.business-modeler`: primary RoleUse `ru.cim.ph1.st3.business-modeler`

**Process parameters**

- `pp.cim.ph1.st3.t1.in.wpu.cim.ph1.st3.t1.input.cim-artifact.increment-plan`: **in** WorkProductUse `wpu.cim.ph1.st3.t1.input.cim-artifact.increment-plan`
- `pp.cim.ph1.st3.t1.out.wpu.cim.ph1.st3.t1.output.cim-artifact.strategic-intent`: **out** WorkProductUse `wpu.cim.ph1.st3.t1.output.cim-artifact.strategic-intent`

### cim.ph1.st3.t2

<small>TaskUse of `task.cim.ph1.st3.t2`</small>

**Process performers**

- `ppf.cim.ph1.st3.t2.ru.cim.ph1.st3.business-modeler`: primary RoleUse `ru.cim.ph1.st3.business-modeler`

**Process parameters**

- `pp.cim.ph1.st3.t2.in.wpu.cim.ph1.st3.t2.input.cim-artifact.strategic-intent`: **in** WorkProductUse `wpu.cim.ph1.st3.t2.input.cim-artifact.strategic-intent`
- `pp.cim.ph1.st3.t2.in.wpu.cim.ph1.st3.t2.input.cim-artifact.increment-plan`: **in** WorkProductUse `wpu.cim.ph1.st3.t2.input.cim-artifact.increment-plan`
- `pp.cim.ph1.st3.t2.out.wpu.cim.ph1.st3.t2.output.cim-artifact.strategic-intent`: **out** WorkProductUse `wpu.cim.ph1.st3.t2.output.cim-artifact.strategic-intent`

### cim.ph2.st1.t1

<small>TaskUse of `task.cim.ph2.st1.t1`</small>

**Process performers**

- `ppf.cim.ph2.st1.t1.ru.cim.ph2.st1.business-modeler`: primary RoleUse `ru.cim.ph2.st1.business-modeler`

**Process parameters**

- `pp.cim.ph2.st1.t1.in.wpu.cim.ph2.st1.t1.input.cim-artifact.strategic-intent`: **in** WorkProductUse `wpu.cim.ph2.st1.t1.input.cim-artifact.strategic-intent`
- `pp.cim.ph2.st1.t1.in.wpu.cim.ph2.st1.t1.input.cim-artifact.increment-plan`: **in** WorkProductUse `wpu.cim.ph2.st1.t1.input.cim-artifact.increment-plan`
- `pp.cim.ph2.st1.t1.out.wpu.cim.ph2.st1.t1.output.cim-artifact.participation-model`: **out** WorkProductUse `wpu.cim.ph2.st1.t1.output.cim-artifact.participation-model`

### cim.ph2.st1.t2

<small>TaskUse of `task.cim.ph2.st1.t2`</small>

**Process performers**

- `ppf.cim.ph2.st1.t2.ru.cim.ph2.st1.business-modeler`: primary RoleUse `ru.cim.ph2.st1.business-modeler`

**Process parameters**

- `pp.cim.ph2.st1.t2.in.wpu.cim.ph2.st1.t2.input.cim-artifact.participation-model`: **in** WorkProductUse `wpu.cim.ph2.st1.t2.input.cim-artifact.participation-model`
- `pp.cim.ph2.st1.t2.in.wpu.cim.ph2.st1.t2.input.cim-artifact.strategic-intent`: **in** WorkProductUse `wpu.cim.ph2.st1.t2.input.cim-artifact.strategic-intent`
- `pp.cim.ph2.st1.t2.out.wpu.cim.ph2.st1.t2.output.cim-artifact.participation-model`: **out** WorkProductUse `wpu.cim.ph2.st1.t2.output.cim-artifact.participation-model`

### cim.ph2.st2.t1

<small>TaskUse of `task.cim.ph2.st2.t1`</small>

**Process performers**

- `ppf.cim.ph2.st2.t1.ru.cim.ph2.st2.business-modeler`: primary RoleUse `ru.cim.ph2.st2.business-modeler`

**Process parameters**

- `pp.cim.ph2.st2.t1.in.wpu.cim.ph2.st2.t1.input.cim-artifact.participation-model`: **in** WorkProductUse `wpu.cim.ph2.st2.t1.input.cim-artifact.participation-model`
- `pp.cim.ph2.st2.t1.in.wpu.cim.ph2.st2.t1.input.cim-artifact.strategic-intent`: **in** WorkProductUse `wpu.cim.ph2.st2.t1.input.cim-artifact.strategic-intent`
- `pp.cim.ph2.st2.t1.out.wpu.cim.ph2.st2.t1.output.cim-artifact.capability-map`: **out** WorkProductUse `wpu.cim.ph2.st2.t1.output.cim-artifact.capability-map`

### cim.ph2.st2.t2

<small>TaskUse of `task.cim.ph2.st2.t2`</small>

**Process performers**

- `ppf.cim.ph2.st2.t2.ru.cim.ph2.st2.business-modeler`: primary RoleUse `ru.cim.ph2.st2.business-modeler`

**Process parameters**

- `pp.cim.ph2.st2.t2.in.wpu.cim.ph2.st2.t2.input.cim-artifact.capability-map`: **in** WorkProductUse `wpu.cim.ph2.st2.t2.input.cim-artifact.capability-map`
- `pp.cim.ph2.st2.t2.in.wpu.cim.ph2.st2.t2.input.cim-artifact.strategic-intent`: **in** WorkProductUse `wpu.cim.ph2.st2.t2.input.cim-artifact.strategic-intent`
- `pp.cim.ph2.st2.t2.out.wpu.cim.ph2.st2.t2.output.cim-artifact.capability-map`: **out** WorkProductUse `wpu.cim.ph2.st2.t2.output.cim-artifact.capability-map`

### cim.ph2.st3.t1

<small>TaskUse of `task.cim.ph2.st3.t1`</small>

**Process performers**

- `ppf.cim.ph2.st3.t1.ru.cim.ph2.st3.business-modeler`: primary RoleUse `ru.cim.ph2.st3.business-modeler`

**Process parameters**

- `pp.cim.ph2.st3.t1.in.wpu.cim.ph2.st3.t1.input.cim-artifact.capability-map`: **in** WorkProductUse `wpu.cim.ph2.st3.t1.input.cim-artifact.capability-map`
- `pp.cim.ph2.st3.t1.in.wpu.cim.ph2.st3.t1.input.cim-artifact.strategic-intent`: **in** WorkProductUse `wpu.cim.ph2.st3.t1.input.cim-artifact.strategic-intent`
- `pp.cim.ph2.st3.t1.out.wpu.cim.ph2.st3.t1.output.cim-artifact.glossary`: **out** WorkProductUse `wpu.cim.ph2.st3.t1.output.cim-artifact.glossary`

### cim.ph3.st1.ss1.t1

<small>TaskUse of `task.cim.ph3.st1.ss1.t1`</small>

**Process performers**

- `ppf.cim.ph3.st1.ss1.t1.ru.cim.ph3.st1.ss1.business-modeler`: primary RoleUse `ru.cim.ph3.st1.ss1.business-modeler`

**Process parameters**

- `pp.cim.ph3.st1.ss1.t1.in.wpu.cim.ph3.st1.ss1.t1.input.cim-artifact.glossary`: **in** WorkProductUse `wpu.cim.ph3.st1.ss1.t1.input.cim-artifact.glossary`
- `pp.cim.ph3.st1.ss1.t1.in.wpu.cim.ph3.st1.ss1.t1.input.cim-artifact.strategic-intent`: **in** WorkProductUse `wpu.cim.ph3.st1.ss1.t1.input.cim-artifact.strategic-intent`
- `pp.cim.ph3.st1.ss1.t1.out.wpu.cim.ph3.st1.ss1.t1.output.cim-artifact.information-taxonomy`: **out** WorkProductUse `wpu.cim.ph3.st1.ss1.t1.output.cim-artifact.information-taxonomy`

### cim.ph3.st1.ss2.t1

<small>TaskUse of `task.cim.ph3.st1.ss2.t1`</small>

**Process performers**

- `ppf.cim.ph3.st1.ss2.t1.ru.cim.ph3.st1.ss2.business-modeler`: primary RoleUse `ru.cim.ph3.st1.ss2.business-modeler`

**Process parameters**

- `pp.cim.ph3.st1.ss2.t1.in.wpu.cim.ph3.st1.ss2.t1.input.cim-artifact.information-taxonomy`: **in** WorkProductUse `wpu.cim.ph3.st1.ss2.t1.input.cim-artifact.information-taxonomy`
- `pp.cim.ph3.st1.ss2.t1.in.wpu.cim.ph3.st1.ss2.t1.input.cim-artifact.glossary`: **in** WorkProductUse `wpu.cim.ph3.st1.ss2.t1.input.cim-artifact.glossary`
- `pp.cim.ph3.st1.ss2.t1.out.wpu.cim.ph3.st1.ss2.t1.output.cim-artifact.information-taxonomy`: **out** WorkProductUse `wpu.cim.ph3.st1.ss2.t1.output.cim-artifact.information-taxonomy`

### cim.ph3.st2.ss1.t1

<small>TaskUse of `task.cim.ph3.st2.ss1.t1`</small>

**Process performers**

- `ppf.cim.ph3.st2.ss1.t1.ru.cim.ph3.st2.ss1.business-modeler`: primary RoleUse `ru.cim.ph3.st2.ss1.business-modeler`

**Process parameters**

- `pp.cim.ph3.st2.ss1.t1.in.wpu.cim.ph3.st2.ss1.t1.input.cim-artifact.information-taxonomy`: **in** WorkProductUse `wpu.cim.ph3.st2.ss1.t1.input.cim-artifact.information-taxonomy`
- `pp.cim.ph3.st2.ss1.t1.in.wpu.cim.ph3.st2.ss1.t1.input.cim-artifact.glossary`: **in** WorkProductUse `wpu.cim.ph3.st2.ss1.t1.input.cim-artifact.glossary`
- `pp.cim.ph3.st2.ss1.t1.in.wpu.cim.ph3.st2.ss1.t1.input.cim-artifact.capability-map`: **in** WorkProductUse `wpu.cim.ph3.st2.ss1.t1.input.cim-artifact.capability-map`
- `pp.cim.ph3.st2.ss1.t1.out.wpu.cim.ph3.st2.ss1.t1.output.cim-artifact.domain-structure`: **out** WorkProductUse `wpu.cim.ph3.st2.ss1.t1.output.cim-artifact.domain-structure`

### cim.ph3.st2.ss2.t1

<small>TaskUse of `task.cim.ph3.st2.ss2.t1`</small>

**Process performers**

- `ppf.cim.ph3.st2.ss2.t1.ru.cim.ph3.st2.ss2.business-modeler`: primary RoleUse `ru.cim.ph3.st2.ss2.business-modeler`

**Process parameters**

- `pp.cim.ph3.st2.ss2.t1.in.wpu.cim.ph3.st2.ss2.t1.input.cim-artifact.domain-structure`: **in** WorkProductUse `wpu.cim.ph3.st2.ss2.t1.input.cim-artifact.domain-structure`
- `pp.cim.ph3.st2.ss2.t1.in.wpu.cim.ph3.st2.ss2.t1.input.cim-artifact.information-taxonomy`: **in** WorkProductUse `wpu.cim.ph3.st2.ss2.t1.input.cim-artifact.information-taxonomy`
- `pp.cim.ph3.st2.ss2.t1.out.wpu.cim.ph3.st2.ss2.t1.output.cim-artifact.domain-structure`: **out** WorkProductUse `wpu.cim.ph3.st2.ss2.t1.output.cim-artifact.domain-structure`

### cim.ph3.st3.ss1.t1

<small>TaskUse of `task.cim.ph3.st3.ss1.t1`</small>

**Process performers**

- `ppf.cim.ph3.st3.ss1.t1.ru.cim.ph3.st3.ss1.business-modeler`: primary RoleUse `ru.cim.ph3.st3.ss1.business-modeler`

**Process parameters**

- `pp.cim.ph3.st3.ss1.t1.in.wpu.cim.ph3.st3.ss1.t1.input.cim-artifact.domain-structure`: **in** WorkProductUse `wpu.cim.ph3.st3.ss1.t1.input.cim-artifact.domain-structure`
- `pp.cim.ph3.st3.ss1.t1.in.wpu.cim.ph3.st3.ss1.t1.input.cim-artifact.participation-model`: **in** WorkProductUse `wpu.cim.ph3.st3.ss1.t1.input.cim-artifact.participation-model`
- `pp.cim.ph3.st3.ss1.t1.in.wpu.cim.ph3.st3.ss1.t1.input.cim-artifact.capability-map`: **in** WorkProductUse `wpu.cim.ph3.st3.ss1.t1.input.cim-artifact.capability-map`
- `pp.cim.ph3.st3.ss1.t1.out.wpu.cim.ph3.st3.ss1.t1.output.cim-artifact.behavior-surface`: **out** WorkProductUse `wpu.cim.ph3.st3.ss1.t1.output.cim-artifact.behavior-surface`

### cim.ph3.st3.ss2.t1

<small>TaskUse of `task.cim.ph3.st3.ss2.t1`</small>

**Process performers**

- `ppf.cim.ph3.st3.ss2.t1.ru.cim.ph3.st3.ss2.business-modeler`: primary RoleUse `ru.cim.ph3.st3.ss2.business-modeler`

**Process parameters**

- `pp.cim.ph3.st3.ss2.t1.in.wpu.cim.ph3.st3.ss2.t1.input.cim-artifact.behavior-surface`: **in** WorkProductUse `wpu.cim.ph3.st3.ss2.t1.input.cim-artifact.behavior-surface`
- `pp.cim.ph3.st3.ss2.t1.in.wpu.cim.ph3.st3.ss2.t1.input.cim-artifact.information-taxonomy`: **in** WorkProductUse `wpu.cim.ph3.st3.ss2.t1.input.cim-artifact.information-taxonomy`
- `pp.cim.ph3.st3.ss2.t1.in.wpu.cim.ph3.st3.ss2.t1.input.cim-artifact.participation-model`: **in** WorkProductUse `wpu.cim.ph3.st3.ss2.t1.input.cim-artifact.participation-model`
- `pp.cim.ph3.st3.ss2.t1.out.wpu.cim.ph3.st3.ss2.t1.output.cim-artifact.behavior-surface`: **out** WorkProductUse `wpu.cim.ph3.st3.ss2.t1.output.cim-artifact.behavior-surface`

### cim.ph3.st3.ss3.t1

<small>TaskUse of `task.cim.ph3.st3.ss3.t1`</small>

**Process performers**

- `ppf.cim.ph3.st3.ss3.t1.ru.cim.ph3.st3.ss3.business-modeler`: primary RoleUse `ru.cim.ph3.st3.ss3.business-modeler`

**Process parameters**

- `pp.cim.ph3.st3.ss3.t1.in.wpu.cim.ph3.st3.ss3.t1.input.cim-artifact.behavior-surface`: **in** WorkProductUse `wpu.cim.ph3.st3.ss3.t1.input.cim-artifact.behavior-surface`
- `pp.cim.ph3.st3.ss3.t1.in.wpu.cim.ph3.st3.ss3.t1.input.cim-artifact.domain-structure`: **in** WorkProductUse `wpu.cim.ph3.st3.ss3.t1.input.cim-artifact.domain-structure`
- `pp.cim.ph3.st3.ss3.t1.in.wpu.cim.ph3.st3.ss3.t1.input.cim-artifact.participation-model`: **in** WorkProductUse `wpu.cim.ph3.st3.ss3.t1.input.cim-artifact.participation-model`
- `pp.cim.ph3.st3.ss3.t1.out.wpu.cim.ph3.st3.ss3.t1.output.cim-artifact.behavior-surface`: **out** WorkProductUse `wpu.cim.ph3.st3.ss3.t1.output.cim-artifact.behavior-surface`

### cim.ph4.st1.t1

<small>TaskUse of `task.cim.ph4.st1.t1`</small>

**Process performers**

- `ppf.cim.ph4.st1.t1.ru.cim.ph4.st1.business-modeler`: primary RoleUse `ru.cim.ph4.st1.business-modeler`

**Process parameters**

- `pp.cim.ph4.st1.t1.in.wpu.cim.ph4.st1.t1.input.cim-artifact.behavior-surface`: **in** WorkProductUse `wpu.cim.ph4.st1.t1.input.cim-artifact.behavior-surface`
- `pp.cim.ph4.st1.t1.in.wpu.cim.ph4.st1.t1.input.cim-artifact.domain-structure`: **in** WorkProductUse `wpu.cim.ph4.st1.t1.input.cim-artifact.domain-structure`
- `pp.cim.ph4.st1.t1.in.wpu.cim.ph4.st1.t1.input.cim-artifact.capability-map`: **in** WorkProductUse `wpu.cim.ph4.st1.t1.input.cim-artifact.capability-map`
- `pp.cim.ph4.st1.t1.out.wpu.cim.ph4.st1.t1.output.cim-artifact.aggregate-model`: **out** WorkProductUse `wpu.cim.ph4.st1.t1.output.cim-artifact.aggregate-model`

### cim.ph4.st2.ss1.t1

<small>TaskUse of `task.cim.ph4.st2.ss1.t1`</small>

**Process performers**

- `ppf.cim.ph4.st2.ss1.t1.ru.cim.ph4.st2.ss1.business-modeler`: primary RoleUse `ru.cim.ph4.st2.ss1.business-modeler`

**Process parameters**

- `pp.cim.ph4.st2.ss1.t1.in.wpu.cim.ph4.st2.ss1.t1.input.cim-artifact.behavior-surface`: **in** WorkProductUse `wpu.cim.ph4.st2.ss1.t1.input.cim-artifact.behavior-surface`
- `pp.cim.ph4.st2.ss1.t1.in.wpu.cim.ph4.st2.ss1.t1.input.cim-artifact.domain-structure`: **in** WorkProductUse `wpu.cim.ph4.st2.ss1.t1.input.cim-artifact.domain-structure`
- `pp.cim.ph4.st2.ss1.t1.in.wpu.cim.ph4.st2.ss1.t1.input.cim-artifact.strategic-intent`: **in** WorkProductUse `wpu.cim.ph4.st2.ss1.t1.input.cim-artifact.strategic-intent`
- `pp.cim.ph4.st2.ss1.t1.in.wpu.cim.ph4.st2.ss1.t1.input.cim-artifact.aggregate-model`: **in** WorkProductUse `wpu.cim.ph4.st2.ss1.t1.input.cim-artifact.aggregate-model`
- `pp.cim.ph4.st2.ss1.t1.out.wpu.cim.ph4.st2.ss1.t1.output.cim-artifact.process-model`: **out** WorkProductUse `wpu.cim.ph4.st2.ss1.t1.output.cim-artifact.process-model`

### cim.ph4.st2.ss2.t1

<small>TaskUse of `task.cim.ph4.st2.ss2.t1`</small>

**Process performers**

- `ppf.cim.ph4.st2.ss2.t1.ru.cim.ph4.st2.ss2.business-modeler`: primary RoleUse `ru.cim.ph4.st2.ss2.business-modeler`

**Process parameters**

- `pp.cim.ph4.st2.ss2.t1.in.wpu.cim.ph4.st2.ss2.t1.input.cim-artifact.process-model`: **in** WorkProductUse `wpu.cim.ph4.st2.ss2.t1.input.cim-artifact.process-model`
- `pp.cim.ph4.st2.ss2.t1.in.wpu.cim.ph4.st2.ss2.t1.input.cim-artifact.behavior-surface`: **in** WorkProductUse `wpu.cim.ph4.st2.ss2.t1.input.cim-artifact.behavior-surface`
- `pp.cim.ph4.st2.ss2.t1.in.wpu.cim.ph4.st2.ss2.t1.input.cim-artifact.domain-structure`: **in** WorkProductUse `wpu.cim.ph4.st2.ss2.t1.input.cim-artifact.domain-structure`
- `pp.cim.ph4.st2.ss2.t1.out.wpu.cim.ph4.st2.ss2.t1.output.cim-artifact.decision-model`: **out** WorkProductUse `wpu.cim.ph4.st2.ss2.t1.output.cim-artifact.decision-model`

### cim.ph4.st3.t1

<small>TaskUse of `task.cim.ph4.st3.t1`</small>

**Process performers**

- `ppf.cim.ph4.st3.t1.ru.cim.ph4.st3.business-modeler`: primary RoleUse `ru.cim.ph4.st3.business-modeler`

**Process parameters**

- `pp.cim.ph4.st3.t1.in.wpu.cim.ph4.st3.t1.input.cim-artifact.capability-map`: **in** WorkProductUse `wpu.cim.ph4.st3.t1.input.cim-artifact.capability-map`
- `pp.cim.ph4.st3.t1.in.wpu.cim.ph4.st3.t1.input.cim-artifact.domain-structure`: **in** WorkProductUse `wpu.cim.ph4.st3.t1.input.cim-artifact.domain-structure`
- `pp.cim.ph4.st3.t1.in.wpu.cim.ph4.st3.t1.input.cim-artifact.behavior-surface`: **in** WorkProductUse `wpu.cim.ph4.st3.t1.input.cim-artifact.behavior-surface`
- `pp.cim.ph4.st3.t1.in.wpu.cim.ph4.st3.t1.input.cim-artifact.process-model`: **in** WorkProductUse `wpu.cim.ph4.st3.t1.input.cim-artifact.process-model`
- `pp.cim.ph4.st3.t1.in.wpu.cim.ph4.st3.t1.input.cim-artifact.decision-model`: **in** WorkProductUse `wpu.cim.ph4.st3.t1.input.cim-artifact.decision-model`
- `pp.cim.ph4.st3.t1.out.wpu.cim.ph4.st3.t1.output.cim-artifact.context-map`: **out** WorkProductUse `wpu.cim.ph4.st3.t1.output.cim-artifact.context-map`

### cim.ph5.st1.t1

<small>TaskUse of `task.cim.ph5.st1.t1`</small>

**Process performers**

- `ppf.cim.ph5.st1.t1.ru.cim.ph5.st1.requirements-engineer`: primary RoleUse `ru.cim.ph5.st1.requirements-engineer`

**Process parameters**

- `pp.cim.ph5.st1.t1.in.wpu.cim.ph5.st1.t1.input.cim-artifact.strategic-intent`: **in** WorkProductUse `wpu.cim.ph5.st1.t1.input.cim-artifact.strategic-intent`
- `pp.cim.ph5.st1.t1.in.wpu.cim.ph5.st1.t1.input.cim-artifact.domain-structure`: **in** WorkProductUse `wpu.cim.ph5.st1.t1.input.cim-artifact.domain-structure`
- `pp.cim.ph5.st1.t1.in.wpu.cim.ph5.st1.t1.input.cim-artifact.behavior-surface`: **in** WorkProductUse `wpu.cim.ph5.st1.t1.input.cim-artifact.behavior-surface`
- `pp.cim.ph5.st1.t1.in.wpu.cim.ph5.st1.t1.input.cim-artifact.context-map`: **in** WorkProductUse `wpu.cim.ph5.st1.t1.input.cim-artifact.context-map`
- `pp.cim.ph5.st1.t1.in.wpu.cim.ph5.st1.t1.input.cim-artifact.decision-model`: **in** WorkProductUse `wpu.cim.ph5.st1.t1.input.cim-artifact.decision-model`
- `pp.cim.ph5.st1.t1.out.wpu.cim.ph5.st1.t1.output.cim-artifact.requirements-package`: **out** WorkProductUse `wpu.cim.ph5.st1.t1.output.cim-artifact.requirements-package`

### cim.ph5.st1.t2

<small>TaskUse of `task.cim.ph5.st1.t2`</small>

**Process performers**

- `ppf.cim.ph5.st1.t2.ru.cim.ph5.st1.requirements-engineer`: primary RoleUse `ru.cim.ph5.st1.requirements-engineer`

**Process parameters**

- `pp.cim.ph5.st1.t2.in.wpu.cim.ph5.st1.t2.input.cim-artifact.requirements-package`: **in** WorkProductUse `wpu.cim.ph5.st1.t2.input.cim-artifact.requirements-package`
- `pp.cim.ph5.st1.t2.in.wpu.cim.ph5.st1.t2.input.cim-artifact.strategic-intent`: **in** WorkProductUse `wpu.cim.ph5.st1.t2.input.cim-artifact.strategic-intent`
- `pp.cim.ph5.st1.t2.in.wpu.cim.ph5.st1.t2.input.cim-artifact.participation-model`: **in** WorkProductUse `wpu.cim.ph5.st1.t2.input.cim-artifact.participation-model`
- `pp.cim.ph5.st1.t2.in.wpu.cim.ph5.st1.t2.input.cim-artifact.information-taxonomy`: **in** WorkProductUse `wpu.cim.ph5.st1.t2.input.cim-artifact.information-taxonomy`
- `pp.cim.ph5.st1.t2.in.wpu.cim.ph5.st1.t2.input.cim-artifact.domain-structure`: **in** WorkProductUse `wpu.cim.ph5.st1.t2.input.cim-artifact.domain-structure`
- `pp.cim.ph5.st1.t2.out.wpu.cim.ph5.st1.t2.output.cim-artifact.governance-package`: **out** WorkProductUse `wpu.cim.ph5.st1.t2.output.cim-artifact.governance-package`

### cim.ph5.st2.t1

<small>TaskUse of `task.cim.ph5.st2.t1`</small>

**Process performers**

- `ppf.cim.ph5.st2.t1.ru.cim.ph5.st2.business-modeler`: primary RoleUse `ru.cim.ph5.st2.business-modeler`

**Process parameters**

- `pp.cim.ph5.st2.t1.in.wpu.cim.ph5.st2.t1.input.cim-artifact.governance-package`: **in** WorkProductUse `wpu.cim.ph5.st2.t1.input.cim-artifact.governance-package`
- `pp.cim.ph5.st2.t1.in.wpu.cim.ph5.st2.t1.input.cim-artifact.requirements-package`: **in** WorkProductUse `wpu.cim.ph5.st2.t1.input.cim-artifact.requirements-package`
- `pp.cim.ph5.st2.t1.in.wpu.cim.ph5.st2.t1.input.cim-artifact.context-map`: **in** WorkProductUse `wpu.cim.ph5.st2.t1.input.cim-artifact.context-map`
- `pp.cim.ph5.st2.t1.in.wpu.cim.ph5.st2.t1.input.cim-artifact.domain-structure`: **in** WorkProductUse `wpu.cim.ph5.st2.t1.input.cim-artifact.domain-structure`
- `pp.cim.ph5.st2.t1.in.wpu.cim.ph5.st2.t1.input.cim-artifact.decision-model`: **in** WorkProductUse `wpu.cim.ph5.st2.t1.input.cim-artifact.decision-model`
- `pp.cim.ph5.st2.t1.out.wpu.cim.ph5.st2.t1.output.cim-artifact.transformation-contract`: **out** WorkProductUse `wpu.cim.ph5.st2.t1.output.cim-artifact.transformation-contract`

### cim.ph5.st3.t1

<small>TaskUse of `task.cim.ph5.st3.t1`</small>

**Process performers**

- `ppf.cim.ph5.st3.t1.ru.cim.ph5.st3.process-reviewer`: primary RoleUse `ru.cim.ph5.st3.process-reviewer`

**Process parameters**

- `pp.cim.ph5.st3.t1.in.wpu.cim.ph5.st3.t1.input.cim-artifact.transformation-contract`: **in** WorkProductUse `wpu.cim.ph5.st3.t1.input.cim-artifact.transformation-contract`
- `pp.cim.ph5.st3.t1.in.wpu.cim.ph5.st3.t1.input.cim-artifact.strategic-intent`: **in** WorkProductUse `wpu.cim.ph5.st3.t1.input.cim-artifact.strategic-intent`
- `pp.cim.ph5.st3.t1.in.wpu.cim.ph5.st3.t1.input.cim-artifact.requirements-package`: **in** WorkProductUse `wpu.cim.ph5.st3.t1.input.cim-artifact.requirements-package`
- `pp.cim.ph5.st3.t1.in.wpu.cim.ph5.st3.t1.input.cim-artifact.domain-structure`: **in** WorkProductUse `wpu.cim.ph5.st3.t1.input.cim-artifact.domain-structure`
- `pp.cim.ph5.st3.t1.in.wpu.cim.ph5.st3.t1.input.cim-artifact.behavior-surface`: **in** WorkProductUse `wpu.cim.ph5.st3.t1.input.cim-artifact.behavior-surface`
- `pp.cim.ph5.st3.t1.in.wpu.cim.ph5.st3.t1.input.cim-artifact.context-map`: **in** WorkProductUse `wpu.cim.ph5.st3.t1.input.cim-artifact.context-map`
- `pp.cim.ph5.st3.t1.in.wpu.cim.ph5.st3.t1.input.cim-artifact.governance-package`: **in** WorkProductUse `wpu.cim.ph5.st3.t1.input.cim-artifact.governance-package`
- `pp.cim.ph5.st3.t1.in.wpu.cim.ph5.st3.t1.input.cim-artifact.process-model`: **in** WorkProductUse `wpu.cim.ph5.st3.t1.input.cim-artifact.process-model`
- `pp.cim.ph5.st3.t1.in.wpu.cim.ph5.st3.t1.input.cim-artifact.decision-model`: **in** WorkProductUse `wpu.cim.ph5.st3.t1.input.cim-artifact.decision-model`
- `pp.cim.ph5.st3.t1.out.wpu.cim.ph5.st3.t1.output.cim-artifact.trace-readiness`: **out** WorkProductUse `wpu.cim.ph5.st3.t1.output.cim-artifact.trace-readiness`

### cim.ph5.st4.t1

<small>TaskUse of `task.cim.ph5.st4.t1`</small>

**Process performers**

- `ppf.cim.ph5.st4.t1.ru.cim.ph5.st4.process-reviewer`: primary RoleUse `ru.cim.ph5.st4.process-reviewer`

**Process parameters**

- `pp.cim.ph5.st4.t1.in.wpu.cim.ph5.st4.t1.input.cim-artifact.trace-readiness`: **in** WorkProductUse `wpu.cim.ph5.st4.t1.input.cim-artifact.trace-readiness`
- `pp.cim.ph5.st4.t1.in.wpu.cim.ph5.st4.t1.input.cim-artifact.strategic-intent`: **in** WorkProductUse `wpu.cim.ph5.st4.t1.input.cim-artifact.strategic-intent`
- `pp.cim.ph5.st4.t1.in.wpu.cim.ph5.st4.t1.input.cim-artifact.increment-plan`: **in** WorkProductUse `wpu.cim.ph5.st4.t1.input.cim-artifact.increment-plan`
- `pp.cim.ph5.st4.t1.in.wpu.cim.ph5.st4.t1.input.cim-artifact.requirements-package`: **in** WorkProductUse `wpu.cim.ph5.st4.t1.input.cim-artifact.requirements-package`
- `pp.cim.ph5.st4.t1.in.wpu.cim.ph5.st4.t1.input.cim-artifact.governance-package`: **in** WorkProductUse `wpu.cim.ph5.st4.t1.input.cim-artifact.governance-package`
- `pp.cim.ph5.st4.t1.out.wpu.cim.ph5.st4.t1.output.cim-artifact.increment-review`: **out** WorkProductUse `wpu.cim.ph5.st4.t1.output.cim-artifact.increment-review`

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
