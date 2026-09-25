# Generated-artifact readiness: flow and bindings

This page documents the **SPEM WorkSequence, ProcessPerformer, and ProcessParameter** elements used by the Generated-artifact readiness process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

WorkSequence records explicit process flow. ProcessPerformer binds a role use to a task use. ProcessParameter binds a work-product use to a task use and states whether it enters or leaves the task. These relationships make dependencies inspectable and prevent document order from being treated as hidden process logic.

## Summary

| Relationship area  | Items | What it controls                                    |
| ------------------ | ----: | --------------------------------------------------- |
| Work sequences     |    16 | Ordering, iteration, feedback, and conditional flow |
| Task uses          |    16 | Placement of reusable tasks in activities           |
| Process performers |    16 | Primary and supporting role bindings                |
| Process parameters |    65 | Input and output work-product bindings              |

## Work sequences

### artifact.ph1 → artifact.ph2

<small>WorkSequence: `ws.artifact.ph1.artifact.ph2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### artifact.ph2 → artifact.ph3

<small>WorkSequence: `ws.artifact.ph2.artifact.ph3` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### artifact.ph3 → artifact.ph4

<small>WorkSequence: `ws.artifact.ph3.artifact.ph4` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### artifact.ph1.st1 → artifact.ph1.st2

<small>WorkSequence: `ws.artifact.ph1.st1.artifact.ph1.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### artifact.ph1.st1.t1 → artifact.ph1.st1.t2

<small>WorkSequence: `ws.artifact.ph1.st1.t1.artifact.ph1.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### artifact.ph1.st2.t1 → artifact.ph1.st2.t2

<small>WorkSequence: `ws.artifact.ph1.st2.t1.artifact.ph1.st2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### artifact.ph2.st1 → artifact.ph2.st2

<small>WorkSequence: `ws.artifact.ph2.st1.artifact.ph2.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### artifact.ph2.st1.t1 → artifact.ph2.st1.t2

<small>WorkSequence: `ws.artifact.ph2.st1.t1.artifact.ph2.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### artifact.ph2.st2.t1 → artifact.ph2.st2.t2

<small>WorkSequence: `ws.artifact.ph2.st2.t1.artifact.ph2.st2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### artifact.ph3.st1 → artifact.ph3.st2

<small>WorkSequence: `ws.artifact.ph3.st1.artifact.ph3.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### artifact.ph3.st1.t1 → artifact.ph3.st1.t2

<small>WorkSequence: `ws.artifact.ph3.st1.t1.artifact.ph3.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### artifact.ph3.st2.t1 → artifact.ph3.st2.t2

<small>WorkSequence: `ws.artifact.ph3.st2.t1.artifact.ph3.st2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### artifact.ph4.st1 → artifact.ph4.st2

<small>WorkSequence: `ws.artifact.ph4.st1.artifact.ph4.st2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### artifact.ph4.st1.t1 → artifact.ph4.st1.t2

<small>WorkSequence: `ws.artifact.ph4.st1.t1.artifact.ph4.st1.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### artifact.ph4.st2.t1 → artifact.ph4.st2.t2

<small>WorkSequence: `ws.artifact.ph4.st2.t1.artifact.ph4.st2.t2` · finishToStart</small>

Complete the predecessor condition before treating the successor as ready.

### artifact.ph4 → artifact.ph1

<small>WorkSequence: `ws.engine-loop.artifact.ph4.artifact.ph1` · finishToStart</small>

When verification, approval, or operational rehearsal exposes a defect, correct the source PSM or artifact baseline, regenerate where necessary, and repeat the affected readiness stages.

**Condition:** release-candidate-rework-required

## Task performer and parameter bindings

### artifact.ph1.st1.t1

<small>TaskUse of `task.artifact.ph1.st1.t1`</small>

**Process performers**

- `ppf.artifact.ph1.st1.t1.ru.artifact.ph1.st1.application-engineer`: primary RoleUse `ru.artifact.ph1.st1.application-engineer`

**Process parameters**

- `pp.artifact.ph1.st1.t1.out.wpu.artifact.ph1.st1.t1.output.artifact.generated-baseline`: **out** WorkProductUse `wpu.artifact.ph1.st1.t1.output.artifact.generated-baseline`

### artifact.ph1.st1.t2

<small>TaskUse of `task.artifact.ph1.st1.t2`</small>

**Process performers**

- `ppf.artifact.ph1.st1.t2.ru.artifact.ph1.st1.release-engineer`: primary RoleUse `ru.artifact.ph1.st1.release-engineer`

**Process parameters**

- `pp.artifact.ph1.st1.t2.in.wpu.artifact.ph1.st1.t2.input.artifact.generated-baseline`: **in** WorkProductUse `wpu.artifact.ph1.st1.t2.input.artifact.generated-baseline`
- `pp.artifact.ph1.st1.t2.out.wpu.artifact.ph1.st1.t2.output.artifact.generated-baseline`: **out** WorkProductUse `wpu.artifact.ph1.st1.t2.output.artifact.generated-baseline`

### artifact.ph1.st2.t1

<small>TaskUse of `task.artifact.ph1.st2.t1`</small>

**Process performers**

- `ppf.artifact.ph1.st2.t1.ru.artifact.ph1.st2.cloud-platform-engineer`: primary RoleUse `ru.artifact.ph1.st2.cloud-platform-engineer`

**Process parameters**

- `pp.artifact.ph1.st2.t1.in.wpu.artifact.ph1.st2.t1.input.artifact.generated-baseline`: **in** WorkProductUse `wpu.artifact.ph1.st2.t1.input.artifact.generated-baseline`
- `pp.artifact.ph1.st2.t1.out.wpu.artifact.ph1.st2.t1.output.artifact.generated-baseline`: **out** WorkProductUse `wpu.artifact.ph1.st2.t1.output.artifact.generated-baseline`

### artifact.ph1.st2.t2

<small>TaskUse of `task.artifact.ph1.st2.t2`</small>

**Process performers**

- `ppf.artifact.ph1.st2.t2.ru.artifact.ph1.st2.application-engineer`: primary RoleUse `ru.artifact.ph1.st2.application-engineer`

**Process parameters**

- `pp.artifact.ph1.st2.t2.in.wpu.artifact.ph1.st2.t2.input.artifact.generated-baseline`: **in** WorkProductUse `wpu.artifact.ph1.st2.t2.input.artifact.generated-baseline`
- `pp.artifact.ph1.st2.t2.out.wpu.artifact.ph1.st2.t2.output.artifact.generated-baseline`: **out** WorkProductUse `wpu.artifact.ph1.st2.t2.output.artifact.generated-baseline`

### artifact.ph2.st1.t1

<small>TaskUse of `task.artifact.ph2.st1.t1`</small>

**Process performers**

- `ppf.artifact.ph2.st1.t1.ru.artifact.ph2.st1.cloud-platform-engineer`: primary RoleUse `ru.artifact.ph2.st1.cloud-platform-engineer`

**Process parameters**

- `pp.artifact.ph2.st1.t1.in.wpu.artifact.ph2.st1.t1.input.artifact.generated-baseline`: **in** WorkProductUse `wpu.artifact.ph2.st1.t1.input.artifact.generated-baseline`
- `pp.artifact.ph2.st1.t1.out.wpu.artifact.ph2.st1.t1.output.artifact.environment-contract`: **out** WorkProductUse `wpu.artifact.ph2.st1.t1.output.artifact.environment-contract`

### artifact.ph2.st1.t2

<small>TaskUse of `task.artifact.ph2.st1.t2`</small>

**Process performers**

- `ppf.artifact.ph2.st1.t2.ru.artifact.ph2.st1.security-engineer`: primary RoleUse `ru.artifact.ph2.st1.security-engineer`

**Process parameters**

- `pp.artifact.ph2.st1.t2.in.wpu.artifact.ph2.st1.t2.input.artifact.generated-baseline`: **in** WorkProductUse `wpu.artifact.ph2.st1.t2.input.artifact.generated-baseline`
- `pp.artifact.ph2.st1.t2.in.wpu.artifact.ph2.st1.t2.input.artifact.environment-contract`: **in** WorkProductUse `wpu.artifact.ph2.st1.t2.input.artifact.environment-contract`
- `pp.artifact.ph2.st1.t2.out.wpu.artifact.ph2.st1.t2.output.artifact.environment-contract`: **out** WorkProductUse `wpu.artifact.ph2.st1.t2.output.artifact.environment-contract`
- `pp.artifact.ph2.st1.t2.out.wpu.artifact.ph2.st1.t2.output.artifact.security-record`: **out** WorkProductUse `wpu.artifact.ph2.st1.t2.output.artifact.security-record`

### artifact.ph2.st2.t1

<small>TaskUse of `task.artifact.ph2.st2.t1`</small>

**Process performers**

- `ppf.artifact.ph2.st2.t1.ru.artifact.ph2.st2.security-engineer`: primary RoleUse `ru.artifact.ph2.st2.security-engineer`

**Process parameters**

- `pp.artifact.ph2.st2.t1.in.wpu.artifact.ph2.st2.t1.input.artifact.environment-contract`: **in** WorkProductUse `wpu.artifact.ph2.st2.t1.input.artifact.environment-contract`
- `pp.artifact.ph2.st2.t1.in.wpu.artifact.ph2.st2.t1.input.artifact.security-record`: **in** WorkProductUse `wpu.artifact.ph2.st2.t1.input.artifact.security-record`
- `pp.artifact.ph2.st2.t1.in.wpu.artifact.ph2.st2.t1.input.artifact.generated-baseline`: **in** WorkProductUse `wpu.artifact.ph2.st2.t1.input.artifact.generated-baseline`
- `pp.artifact.ph2.st2.t1.out.wpu.artifact.ph2.st2.t1.output.artifact.security-record`: **out** WorkProductUse `wpu.artifact.ph2.st2.t1.output.artifact.security-record`

### artifact.ph2.st2.t2

<small>TaskUse of `task.artifact.ph2.st2.t2`</small>

**Process performers**

- `ppf.artifact.ph2.st2.t2.ru.artifact.ph2.st2.release-engineer`: primary RoleUse `ru.artifact.ph2.st2.release-engineer`

**Process parameters**

- `pp.artifact.ph2.st2.t2.in.wpu.artifact.ph2.st2.t2.input.artifact.security-record`: **in** WorkProductUse `wpu.artifact.ph2.st2.t2.input.artifact.security-record`
- `pp.artifact.ph2.st2.t2.in.wpu.artifact.ph2.st2.t2.input.artifact.environment-contract`: **in** WorkProductUse `wpu.artifact.ph2.st2.t2.input.artifact.environment-contract`
- `pp.artifact.ph2.st2.t2.in.wpu.artifact.ph2.st2.t2.input.artifact.generated-baseline`: **in** WorkProductUse `wpu.artifact.ph2.st2.t2.input.artifact.generated-baseline`
- `pp.artifact.ph2.st2.t2.out.wpu.artifact.ph2.st2.t2.output.artifact.environment-contract`: **out** WorkProductUse `wpu.artifact.ph2.st2.t2.output.artifact.environment-contract`

### artifact.ph3.st1.t1

<small>TaskUse of `task.artifact.ph3.st1.t1`</small>

**Process performers**

- `ppf.artifact.ph3.st1.t1.ru.artifact.ph3.st1.quality-engineer`: primary RoleUse `ru.artifact.ph3.st1.quality-engineer`

**Process parameters**

- `pp.artifact.ph3.st1.t1.in.wpu.artifact.ph3.st1.t1.input.artifact.environment-contract`: **in** WorkProductUse `wpu.artifact.ph3.st1.t1.input.artifact.environment-contract`
- `pp.artifact.ph3.st1.t1.in.wpu.artifact.ph3.st1.t1.input.artifact.security-record`: **in** WorkProductUse `wpu.artifact.ph3.st1.t1.input.artifact.security-record`
- `pp.artifact.ph3.st1.t1.in.wpu.artifact.ph3.st1.t1.input.artifact.generated-baseline`: **in** WorkProductUse `wpu.artifact.ph3.st1.t1.input.artifact.generated-baseline`
- `pp.artifact.ph3.st1.t1.out.wpu.artifact.ph3.st1.t1.output.artifact.verification-evidence`: **out** WorkProductUse `wpu.artifact.ph3.st1.t1.output.artifact.verification-evidence`

### artifact.ph3.st1.t2

<small>TaskUse of `task.artifact.ph3.st1.t2`</small>

**Process performers**

- `ppf.artifact.ph3.st1.t2.ru.artifact.ph3.st1.security-engineer`: primary RoleUse `ru.artifact.ph3.st1.security-engineer`

**Process parameters**

- `pp.artifact.ph3.st1.t2.in.wpu.artifact.ph3.st1.t2.input.artifact.verification-evidence`: **in** WorkProductUse `wpu.artifact.ph3.st1.t2.input.artifact.verification-evidence`
- `pp.artifact.ph3.st1.t2.in.wpu.artifact.ph3.st1.t2.input.artifact.security-record`: **in** WorkProductUse `wpu.artifact.ph3.st1.t2.input.artifact.security-record`
- `pp.artifact.ph3.st1.t2.in.wpu.artifact.ph3.st1.t2.input.artifact.environment-contract`: **in** WorkProductUse `wpu.artifact.ph3.st1.t2.input.artifact.environment-contract`
- `pp.artifact.ph3.st1.t2.in.wpu.artifact.ph3.st1.t2.input.artifact.generated-baseline`: **in** WorkProductUse `wpu.artifact.ph3.st1.t2.input.artifact.generated-baseline`
- `pp.artifact.ph3.st1.t2.out.wpu.artifact.ph3.st1.t2.output.artifact.security-record`: **out** WorkProductUse `wpu.artifact.ph3.st1.t2.output.artifact.security-record`
- `pp.artifact.ph3.st1.t2.out.wpu.artifact.ph3.st1.t2.output.artifact.verification-evidence`: **out** WorkProductUse `wpu.artifact.ph3.st1.t2.output.artifact.verification-evidence`

### artifact.ph3.st2.t1

<small>TaskUse of `task.artifact.ph3.st2.t1`</small>

**Process performers**

- `ppf.artifact.ph3.st2.t1.ru.artifact.ph3.st2.quality-engineer`: primary RoleUse `ru.artifact.ph3.st2.quality-engineer`

**Process parameters**

- `pp.artifact.ph3.st2.t1.in.wpu.artifact.ph3.st2.t1.input.artifact.security-record`: **in** WorkProductUse `wpu.artifact.ph3.st2.t1.input.artifact.security-record`
- `pp.artifact.ph3.st2.t1.in.wpu.artifact.ph3.st2.t1.input.artifact.verification-evidence`: **in** WorkProductUse `wpu.artifact.ph3.st2.t1.input.artifact.verification-evidence`
- `pp.artifact.ph3.st2.t1.in.wpu.artifact.ph3.st2.t1.input.artifact.environment-contract`: **in** WorkProductUse `wpu.artifact.ph3.st2.t1.input.artifact.environment-contract`
- `pp.artifact.ph3.st2.t1.in.wpu.artifact.ph3.st2.t1.input.artifact.generated-baseline`: **in** WorkProductUse `wpu.artifact.ph3.st2.t1.input.artifact.generated-baseline`
- `pp.artifact.ph3.st2.t1.out.wpu.artifact.ph3.st2.t1.output.artifact.verification-evidence`: **out** WorkProductUse `wpu.artifact.ph3.st2.t1.output.artifact.verification-evidence`

### artifact.ph3.st2.t2

<small>TaskUse of `task.artifact.ph3.st2.t2`</small>

**Process performers**

- `ppf.artifact.ph3.st2.t2.ru.artifact.ph3.st2.cloud-platform-engineer`: primary RoleUse `ru.artifact.ph3.st2.cloud-platform-engineer`

**Process parameters**

- `pp.artifact.ph3.st2.t2.in.wpu.artifact.ph3.st2.t2.input.artifact.verification-evidence`: **in** WorkProductUse `wpu.artifact.ph3.st2.t2.input.artifact.verification-evidence`
- `pp.artifact.ph3.st2.t2.in.wpu.artifact.ph3.st2.t2.input.artifact.environment-contract`: **in** WorkProductUse `wpu.artifact.ph3.st2.t2.input.artifact.environment-contract`
- `pp.artifact.ph3.st2.t2.in.wpu.artifact.ph3.st2.t2.input.artifact.security-record`: **in** WorkProductUse `wpu.artifact.ph3.st2.t2.input.artifact.security-record`
- `pp.artifact.ph3.st2.t2.out.wpu.artifact.ph3.st2.t2.output.artifact.verification-evidence`: **out** WorkProductUse `wpu.artifact.ph3.st2.t2.output.artifact.verification-evidence`
- `pp.artifact.ph3.st2.t2.out.wpu.artifact.ph3.st2.t2.output.artifact.operations-pack`: **out** WorkProductUse `wpu.artifact.ph3.st2.t2.output.artifact.operations-pack`

### artifact.ph4.st1.t1

<small>TaskUse of `task.artifact.ph4.st1.t1`</small>

**Process performers**

- `ppf.artifact.ph4.st1.t1.ru.artifact.ph4.st1.release-engineer`: primary RoleUse `ru.artifact.ph4.st1.release-engineer`

**Process parameters**

- `pp.artifact.ph4.st1.t1.in.wpu.artifact.ph4.st1.t1.input.artifact.verification-evidence`: **in** WorkProductUse `wpu.artifact.ph4.st1.t1.input.artifact.verification-evidence`
- `pp.artifact.ph4.st1.t1.in.wpu.artifact.ph4.st1.t1.input.artifact.operations-pack`: **in** WorkProductUse `wpu.artifact.ph4.st1.t1.input.artifact.operations-pack`
- `pp.artifact.ph4.st1.t1.in.wpu.artifact.ph4.st1.t1.input.artifact.security-record`: **in** WorkProductUse `wpu.artifact.ph4.st1.t1.input.artifact.security-record`
- `pp.artifact.ph4.st1.t1.in.wpu.artifact.ph4.st1.t1.input.artifact.environment-contract`: **in** WorkProductUse `wpu.artifact.ph4.st1.t1.input.artifact.environment-contract`
- `pp.artifact.ph4.st1.t1.out.wpu.artifact.ph4.st1.t1.output.artifact.release-plan`: **out** WorkProductUse `wpu.artifact.ph4.st1.t1.output.artifact.release-plan`

### artifact.ph4.st1.t2

<small>TaskUse of `task.artifact.ph4.st1.t2`</small>

**Process performers**

- `ppf.artifact.ph4.st1.t2.ru.artifact.ph4.st1.service-owner`: primary RoleUse `ru.artifact.ph4.st1.service-owner`

**Process parameters**

- `pp.artifact.ph4.st1.t2.in.wpu.artifact.ph4.st1.t2.input.artifact.release-plan`: **in** WorkProductUse `wpu.artifact.ph4.st1.t2.input.artifact.release-plan`
- `pp.artifact.ph4.st1.t2.in.wpu.artifact.ph4.st1.t2.input.artifact.verification-evidence`: **in** WorkProductUse `wpu.artifact.ph4.st1.t2.input.artifact.verification-evidence`
- `pp.artifact.ph4.st1.t2.in.wpu.artifact.ph4.st1.t2.input.artifact.security-record`: **in** WorkProductUse `wpu.artifact.ph4.st1.t2.input.artifact.security-record`
- `pp.artifact.ph4.st1.t2.in.wpu.artifact.ph4.st1.t2.input.artifact.operations-pack`: **in** WorkProductUse `wpu.artifact.ph4.st1.t2.input.artifact.operations-pack`
- `pp.artifact.ph4.st1.t2.out.wpu.artifact.ph4.st1.t2.output.artifact.release-plan`: **out** WorkProductUse `wpu.artifact.ph4.st1.t2.output.artifact.release-plan`
- `pp.artifact.ph4.st1.t2.out.wpu.artifact.ph4.st1.t2.output.artifact.security-record`: **out** WorkProductUse `wpu.artifact.ph4.st1.t2.output.artifact.security-record`

### artifact.ph4.st2.t1

<small>TaskUse of `task.artifact.ph4.st2.t1`</small>

**Process performers**

- `ppf.artifact.ph4.st2.t1.ru.artifact.ph4.st2.service-owner`: primary RoleUse `ru.artifact.ph4.st2.service-owner`

**Process parameters**

- `pp.artifact.ph4.st2.t1.in.wpu.artifact.ph4.st2.t1.input.artifact.release-plan`: **in** WorkProductUse `wpu.artifact.ph4.st2.t1.input.artifact.release-plan`
- `pp.artifact.ph4.st2.t1.in.wpu.artifact.ph4.st2.t1.input.artifact.security-record`: **in** WorkProductUse `wpu.artifact.ph4.st2.t1.input.artifact.security-record`
- `pp.artifact.ph4.st2.t1.in.wpu.artifact.ph4.st2.t1.input.artifact.verification-evidence`: **in** WorkProductUse `wpu.artifact.ph4.st2.t1.input.artifact.verification-evidence`
- `pp.artifact.ph4.st2.t1.in.wpu.artifact.ph4.st2.t1.input.artifact.environment-contract`: **in** WorkProductUse `wpu.artifact.ph4.st2.t1.input.artifact.environment-contract`
- `pp.artifact.ph4.st2.t1.in.wpu.artifact.ph4.st2.t1.input.artifact.generated-baseline`: **in** WorkProductUse `wpu.artifact.ph4.st2.t1.input.artifact.generated-baseline`
- `pp.artifact.ph4.st2.t1.out.wpu.artifact.ph4.st2.t1.output.artifact.operations-pack`: **out** WorkProductUse `wpu.artifact.ph4.st2.t1.output.artifact.operations-pack`

### artifact.ph4.st2.t2

<small>TaskUse of `task.artifact.ph4.st2.t2`</small>

**Process performers**

- `ppf.artifact.ph4.st2.t2.ru.artifact.ph4.st2.release-engineer`: primary RoleUse `ru.artifact.ph4.st2.release-engineer`

**Process parameters**

- `pp.artifact.ph4.st2.t2.in.wpu.artifact.ph4.st2.t2.input.artifact.operations-pack`: **in** WorkProductUse `wpu.artifact.ph4.st2.t2.input.artifact.operations-pack`
- `pp.artifact.ph4.st2.t2.in.wpu.artifact.ph4.st2.t2.input.artifact.release-plan`: **in** WorkProductUse `wpu.artifact.ph4.st2.t2.input.artifact.release-plan`
- `pp.artifact.ph4.st2.t2.in.wpu.artifact.ph4.st2.t2.input.artifact.verification-evidence`: **in** WorkProductUse `wpu.artifact.ph4.st2.t2.input.artifact.verification-evidence`
- `pp.artifact.ph4.st2.t2.in.wpu.artifact.ph4.st2.t2.input.artifact.security-record`: **in** WorkProductUse `wpu.artifact.ph4.st2.t2.input.artifact.security-record`
- `pp.artifact.ph4.st2.t2.in.wpu.artifact.ph4.st2.t2.input.artifact.environment-contract`: **in** WorkProductUse `wpu.artifact.ph4.st2.t2.input.artifact.environment-contract`
- `pp.artifact.ph4.st2.t2.out.wpu.artifact.ph4.st2.t2.output.artifact.verification-evidence`: **out** WorkProductUse `wpu.artifact.ph4.st2.t2.output.artifact.verification-evidence`
- `pp.artifact.ph4.st2.t2.out.wpu.artifact.ph4.st2.t2.output.artifact.operations-pack`: **out** WorkProductUse `wpu.artifact.ph4.st2.t2.output.artifact.operations-pack`

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
