# Operations and Maintenance: flow and bindings

This page documents the **SPEM WorkSequence, ProcessPerformer, and ProcessParameter** elements used by the Operations and Maintenance process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.

WorkSequence records explicit process flow. ProcessPerformer binds a role use to a task use. ProcessParameter binds a work-product use to a task use and states whether it enters or leaves the task. These relationships make dependencies inspectable and prevent document order from being treated as hidden process logic.

## Summary

| Relationship area  | Items | What it controls                                    |
| ------------------ | ----: | --------------------------------------------------- |
| Work sequences     |     0 | Ordering, iteration, feedback, and conditional flow |
| Task uses          |     7 | Placement of reusable tasks in activities           |
| Process performers |    17 | Primary and supporting role bindings                |
| Process parameters |    66 | Input and output work-product bindings              |

## Work sequences

## Task performer and parameter bindings

### e2e.ops.a1.t1

<small>TaskUse of `task.e2e.ops.a1.t1`</small>

**Process performers**

- `ppf.e2e.ops.a1.t1.ru.e2e.ops.a1.service-owner`: primary RoleUse `ru.e2e.ops.a1.service-owner`
- `ppf.e2e.ops.a1.t1.ru.e2e.ops.a1.product-owner`: supporting RoleUse `ru.e2e.ops.a1.product-owner`
- `ppf.e2e.ops.a1.t1.ru.e2e.ops.a1.security-engineer`: supporting RoleUse `ru.e2e.ops.a1.security-engineer`
- `ppf.e2e.ops.a1.t1.ru.e2e.ops.a1.finops-cost-analyst`: supporting RoleUse `ru.e2e.ops.a1.finops-cost-analyst`

**Process parameters**

- `pp.e2e.ops.a1.t1.in.wpu.e2e.ops.a1.t1.input.e2e-artifact.release-record`: **in** WorkProductUse `wpu.e2e.ops.a1.t1.input.e2e-artifact.release-record`
- `pp.e2e.ops.a1.t1.in.wpu.e2e.ops.a1.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.ops.a1.t1.input.e2e-artifact.product-charter`
- `pp.e2e.ops.a1.t1.in.wpu.e2e.ops.a1.t1.input.e2e-artifact.operations-record`: **in** WorkProductUse `wpu.e2e.ops.a1.t1.input.e2e-artifact.operations-record`
- `pp.e2e.ops.a1.t1.in.wpu.e2e.ops.a1.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.ops.a1.t1.input.e2e-artifact.method-profile`
- `pp.e2e.ops.a1.t1.in.wpu.e2e.ops.a1.t1.input.e2e-artifact.service-flow-system`: **in** WorkProductUse `wpu.e2e.ops.a1.t1.input.e2e-artifact.service-flow-system`
- `pp.e2e.ops.a1.t1.in.wpu.e2e.ops.a1.t1.input.e2e-artifact.cost-model`: **in** WorkProductUse `wpu.e2e.ops.a1.t1.input.e2e-artifact.cost-model`
- `pp.e2e.ops.a1.t1.out.wpu.e2e.ops.a1.t1.output.e2e-artifact.operations-record`: **out** WorkProductUse `wpu.e2e.ops.a1.t1.output.e2e-artifact.operations-record`
- `pp.e2e.ops.a1.t1.out.wpu.e2e.ops.a1.t1.output.e2e-artifact.operational-work-item`: **out** WorkProductUse `wpu.e2e.ops.a1.t1.output.e2e-artifact.operational-work-item`
- `pp.e2e.ops.a1.t1.out.wpu.e2e.ops.a1.t1.output.e2e-artifact.cost-model`: **out** WorkProductUse `wpu.e2e.ops.a1.t1.output.e2e-artifact.cost-model`

### e2e.ops.a2.t1

<small>TaskUse of `task.e2e.ops.a2.t1`</small>

**Process performers**

- `ppf.e2e.ops.a2.t1.ru.e2e.ops.a2.service-owner`: primary RoleUse `ru.e2e.ops.a2.service-owner`

**Process parameters**

- `pp.e2e.ops.a2.t1.in.wpu.e2e.ops.a2.t1.input.e2e-artifact.operational-work-item`: **in** WorkProductUse `wpu.e2e.ops.a2.t1.input.e2e-artifact.operational-work-item`
- `pp.e2e.ops.a2.t1.in.wpu.e2e.ops.a2.t1.input.e2e-artifact.operations-record`: **in** WorkProductUse `wpu.e2e.ops.a2.t1.input.e2e-artifact.operations-record`
- `pp.e2e.ops.a2.t1.in.wpu.e2e.ops.a2.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.ops.a2.t1.input.e2e-artifact.method-profile`
- `pp.e2e.ops.a2.t1.in.wpu.e2e.ops.a2.t1.input.e2e-artifact.service-flow-system`: **in** WorkProductUse `wpu.e2e.ops.a2.t1.input.e2e-artifact.service-flow-system`
- `pp.e2e.ops.a2.t1.out.wpu.e2e.ops.a2.t1.output.e2e-artifact.operational-work-item`: **out** WorkProductUse `wpu.e2e.ops.a2.t1.output.e2e-artifact.operational-work-item`
- `pp.e2e.ops.a2.t1.out.wpu.e2e.ops.a2.t1.output.e2e-artifact.service-flow-system`: **out** WorkProductUse `wpu.e2e.ops.a2.t1.output.e2e-artifact.service-flow-system`

### e2e.ops.a2.t2

<small>TaskUse of `task.e2e.ops.a2.t2`</small>

**Process performers**

- `ppf.e2e.ops.a2.t2.ru.e2e.ops.a2.delivery-lead`: primary RoleUse `ru.e2e.ops.a2.delivery-lead`

**Process parameters**

- `pp.e2e.ops.a2.t2.in.wpu.e2e.ops.a2.t2.input.e2e-artifact.operational-work-item`: **in** WorkProductUse `wpu.e2e.ops.a2.t2.input.e2e-artifact.operational-work-item`
- `pp.e2e.ops.a2.t2.in.wpu.e2e.ops.a2.t2.input.e2e-artifact.operations-record`: **in** WorkProductUse `wpu.e2e.ops.a2.t2.input.e2e-artifact.operations-record`
- `pp.e2e.ops.a2.t2.in.wpu.e2e.ops.a2.t2.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.ops.a2.t2.input.e2e-artifact.method-profile`
- `pp.e2e.ops.a2.t2.in.wpu.e2e.ops.a2.t2.input.e2e-artifact.service-flow-system`: **in** WorkProductUse `wpu.e2e.ops.a2.t2.input.e2e-artifact.service-flow-system`
- `pp.e2e.ops.a2.t2.out.wpu.e2e.ops.a2.t2.output.e2e-artifact.operational-work-item`: **out** WorkProductUse `wpu.e2e.ops.a2.t2.output.e2e-artifact.operational-work-item`
- `pp.e2e.ops.a2.t2.out.wpu.e2e.ops.a2.t2.output.e2e-artifact.service-flow-system`: **out** WorkProductUse `wpu.e2e.ops.a2.t2.output.e2e-artifact.service-flow-system`
- `pp.e2e.ops.a2.t2.out.wpu.e2e.ops.a2.t2.output.e2e-artifact.operations-record`: **out** WorkProductUse `wpu.e2e.ops.a2.t2.output.e2e-artifact.operations-record`

### e2e.ops.a3.t1

<small>TaskUse of `task.e2e.ops.a3.t1`</small>

**Process performers**

- `ppf.e2e.ops.a3.t1.ru.e2e.ops.a3.service-owner`: primary RoleUse `ru.e2e.ops.a3.service-owner`

**Process parameters**

- `pp.e2e.ops.a3.t1.in.wpu.e2e.ops.a3.t1.input.e2e-artifact.operational-work-item`: **in** WorkProductUse `wpu.e2e.ops.a3.t1.input.e2e-artifact.operational-work-item`
- `pp.e2e.ops.a3.t1.in.wpu.e2e.ops.a3.t1.input.e2e-artifact.service-flow-system`: **in** WorkProductUse `wpu.e2e.ops.a3.t1.input.e2e-artifact.service-flow-system`
- `pp.e2e.ops.a3.t1.in.wpu.e2e.ops.a3.t1.input.e2e-artifact.operations-record`: **in** WorkProductUse `wpu.e2e.ops.a3.t1.input.e2e-artifact.operations-record`
- `pp.e2e.ops.a3.t1.in.wpu.e2e.ops.a3.t1.input.e2e-artifact.release-record`: **in** WorkProductUse `wpu.e2e.ops.a3.t1.input.e2e-artifact.release-record`
- `pp.e2e.ops.a3.t1.in.wpu.e2e.ops.a3.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.ops.a3.t1.input.e2e-artifact.method-profile`
- `pp.e2e.ops.a3.t1.out.wpu.e2e.ops.a3.t1.output.e2e-artifact.operations-record`: **out** WorkProductUse `wpu.e2e.ops.a3.t1.output.e2e-artifact.operations-record`
- `pp.e2e.ops.a3.t1.out.wpu.e2e.ops.a3.t1.output.e2e-artifact.operational-work-item`: **out** WorkProductUse `wpu.e2e.ops.a3.t1.output.e2e-artifact.operational-work-item`

### e2e.ops.a3.t2

<small>TaskUse of `task.e2e.ops.a3.t2`</small>

**Process performers**

- `ppf.e2e.ops.a3.t2.ru.e2e.ops.a3.quality-engineer`: primary RoleUse `ru.e2e.ops.a3.quality-engineer`
- `ppf.e2e.ops.a3.t2.ru.e2e.ops.a3.service-owner`: supporting RoleUse `ru.e2e.ops.a3.service-owner`
- `ppf.e2e.ops.a3.t2.ru.e2e.ops.a3.security-engineer`: supporting RoleUse `ru.e2e.ops.a3.security-engineer`
- `ppf.e2e.ops.a3.t2.ru.e2e.ops.a3.method-engineer`: supporting RoleUse `ru.e2e.ops.a3.method-engineer`

**Process parameters**

- `pp.e2e.ops.a3.t2.in.wpu.e2e.ops.a3.t2.input.e2e-artifact.operational-work-item`: **in** WorkProductUse `wpu.e2e.ops.a3.t2.input.e2e-artifact.operational-work-item`
- `pp.e2e.ops.a3.t2.in.wpu.e2e.ops.a3.t2.input.e2e-artifact.service-flow-system`: **in** WorkProductUse `wpu.e2e.ops.a3.t2.input.e2e-artifact.service-flow-system`
- `pp.e2e.ops.a3.t2.in.wpu.e2e.ops.a3.t2.input.e2e-artifact.operations-record`: **in** WorkProductUse `wpu.e2e.ops.a3.t2.input.e2e-artifact.operations-record`
- `pp.e2e.ops.a3.t2.in.wpu.e2e.ops.a3.t2.input.e2e-artifact.increment-record`: **in** WorkProductUse `wpu.e2e.ops.a3.t2.input.e2e-artifact.increment-record`
- `pp.e2e.ops.a3.t2.in.wpu.e2e.ops.a3.t2.input.e2e-artifact.release-record`: **in** WorkProductUse `wpu.e2e.ops.a3.t2.input.e2e-artifact.release-record`
- `pp.e2e.ops.a3.t2.in.wpu.e2e.ops.a3.t2.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.ops.a3.t2.input.e2e-artifact.method-profile`
- `pp.e2e.ops.a3.t2.in.wpu.e2e.ops.a3.t2.input.e2e-artifact.risk-register`: **in** WorkProductUse `wpu.e2e.ops.a3.t2.input.e2e-artifact.risk-register`
- `pp.e2e.ops.a3.t2.in.wpu.e2e.ops.a3.t2.input.e2e-artifact.improvement-record`: **in** WorkProductUse `wpu.e2e.ops.a3.t2.input.e2e-artifact.improvement-record`
- `pp.e2e.ops.a3.t2.out.wpu.e2e.ops.a3.t2.output.e2e-artifact.operations-record`: **out** WorkProductUse `wpu.e2e.ops.a3.t2.output.e2e-artifact.operations-record`
- `pp.e2e.ops.a3.t2.out.wpu.e2e.ops.a3.t2.output.e2e-artifact.method-profile`: **out** WorkProductUse `wpu.e2e.ops.a3.t2.output.e2e-artifact.method-profile`
- `pp.e2e.ops.a3.t2.out.wpu.e2e.ops.a3.t2.output.e2e-artifact.operational-work-item`: **out** WorkProductUse `wpu.e2e.ops.a3.t2.output.e2e-artifact.operational-work-item`
- `pp.e2e.ops.a3.t2.out.wpu.e2e.ops.a3.t2.output.e2e-artifact.risk-register`: **out** WorkProductUse `wpu.e2e.ops.a3.t2.output.e2e-artifact.risk-register`
- `pp.e2e.ops.a3.t2.out.wpu.e2e.ops.a3.t2.output.e2e-artifact.improvement-record`: **out** WorkProductUse `wpu.e2e.ops.a3.t2.output.e2e-artifact.improvement-record`

### e2e.ops.a4.t1

<small>TaskUse of `task.e2e.ops.a4.t1`</small>

**Process performers**

- `ppf.e2e.ops.a4.t1.ru.e2e.ops.a4.delivery-lead`: primary RoleUse `ru.e2e.ops.a4.delivery-lead`

**Process parameters**

- `pp.e2e.ops.a4.t1.in.wpu.e2e.ops.a4.t1.input.e2e-artifact.operational-work-item`: **in** WorkProductUse `wpu.e2e.ops.a4.t1.input.e2e-artifact.operational-work-item`
- `pp.e2e.ops.a4.t1.in.wpu.e2e.ops.a4.t1.input.e2e-artifact.service-flow-system`: **in** WorkProductUse `wpu.e2e.ops.a4.t1.input.e2e-artifact.service-flow-system`
- `pp.e2e.ops.a4.t1.in.wpu.e2e.ops.a4.t1.input.e2e-artifact.operations-record`: **in** WorkProductUse `wpu.e2e.ops.a4.t1.input.e2e-artifact.operations-record`
- `pp.e2e.ops.a4.t1.in.wpu.e2e.ops.a4.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.ops.a4.t1.input.e2e-artifact.method-profile`
- `pp.e2e.ops.a4.t1.in.wpu.e2e.ops.a4.t1.input.e2e-artifact.increment-record`: **in** WorkProductUse `wpu.e2e.ops.a4.t1.input.e2e-artifact.increment-record`
- `pp.e2e.ops.a4.t1.in.wpu.e2e.ops.a4.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.ops.a4.t1.input.e2e-artifact.product-charter`
- `pp.e2e.ops.a4.t1.in.wpu.e2e.ops.a4.t1.input.e2e-artifact.release-record`: **in** WorkProductUse `wpu.e2e.ops.a4.t1.input.e2e-artifact.release-record`
- `pp.e2e.ops.a4.t1.out.wpu.e2e.ops.a4.t1.output.e2e-artifact.increment-record`: **out** WorkProductUse `wpu.e2e.ops.a4.t1.output.e2e-artifact.increment-record`
- `pp.e2e.ops.a4.t1.out.wpu.e2e.ops.a4.t1.output.e2e-artifact.operations-record`: **out** WorkProductUse `wpu.e2e.ops.a4.t1.output.e2e-artifact.operations-record`
- `pp.e2e.ops.a4.t1.out.wpu.e2e.ops.a4.t1.output.e2e-artifact.operational-work-item`: **out** WorkProductUse `wpu.e2e.ops.a4.t1.output.e2e-artifact.operational-work-item`

### e2e.ops.a5.t1

<small>TaskUse of `task.e2e.ops.a5.t1`</small>

**Process performers**

- `ppf.e2e.ops.a5.t1.ru.e2e.ops.a5.process-reviewer`: primary RoleUse `ru.e2e.ops.a5.process-reviewer`
- `ppf.e2e.ops.a5.t1.ru.e2e.ops.a5.delivery-lead`: supporting RoleUse `ru.e2e.ops.a5.delivery-lead`
- `ppf.e2e.ops.a5.t1.ru.e2e.ops.a5.method-engineer`: supporting RoleUse `ru.e2e.ops.a5.method-engineer`
- `ppf.e2e.ops.a5.t1.ru.e2e.ops.a5.finops-cost-analyst`: supporting RoleUse `ru.e2e.ops.a5.finops-cost-analyst`
- `ppf.e2e.ops.a5.t1.ru.e2e.ops.a5.service-owner`: supporting RoleUse `ru.e2e.ops.a5.service-owner`

**Process parameters**

- `pp.e2e.ops.a5.t1.in.wpu.e2e.ops.a5.t1.input.e2e-artifact.operational-work-item`: **in** WorkProductUse `wpu.e2e.ops.a5.t1.input.e2e-artifact.operational-work-item`
- `pp.e2e.ops.a5.t1.in.wpu.e2e.ops.a5.t1.input.e2e-artifact.service-flow-system`: **in** WorkProductUse `wpu.e2e.ops.a5.t1.input.e2e-artifact.service-flow-system`
- `pp.e2e.ops.a5.t1.in.wpu.e2e.ops.a5.t1.input.e2e-artifact.increment-record`: **in** WorkProductUse `wpu.e2e.ops.a5.t1.input.e2e-artifact.increment-record`
- `pp.e2e.ops.a5.t1.in.wpu.e2e.ops.a5.t1.input.e2e-artifact.operations-record`: **in** WorkProductUse `wpu.e2e.ops.a5.t1.input.e2e-artifact.operations-record`
- `pp.e2e.ops.a5.t1.in.wpu.e2e.ops.a5.t1.input.e2e-artifact.product-charter`: **in** WorkProductUse `wpu.e2e.ops.a5.t1.input.e2e-artifact.product-charter`
- `pp.e2e.ops.a5.t1.in.wpu.e2e.ops.a5.t1.input.e2e-artifact.method-profile`: **in** WorkProductUse `wpu.e2e.ops.a5.t1.input.e2e-artifact.method-profile`
- `pp.e2e.ops.a5.t1.in.wpu.e2e.ops.a5.t1.input.e2e-artifact.release-record`: **in** WorkProductUse `wpu.e2e.ops.a5.t1.input.e2e-artifact.release-record`
- `pp.e2e.ops.a5.t1.in.wpu.e2e.ops.a5.t1.input.e2e-artifact.cost-model`: **in** WorkProductUse `wpu.e2e.ops.a5.t1.input.e2e-artifact.cost-model`
- `pp.e2e.ops.a5.t1.in.wpu.e2e.ops.a5.t1.input.e2e-artifact.improvement-record`: **in** WorkProductUse `wpu.e2e.ops.a5.t1.input.e2e-artifact.improvement-record`
- `pp.e2e.ops.a5.t1.out.wpu.e2e.ops.a5.t1.output.e2e-artifact.operations-record`: **out** WorkProductUse `wpu.e2e.ops.a5.t1.output.e2e-artifact.operations-record`
- `pp.e2e.ops.a5.t1.out.wpu.e2e.ops.a5.t1.output.e2e-artifact.method-profile`: **out** WorkProductUse `wpu.e2e.ops.a5.t1.output.e2e-artifact.method-profile`
- `pp.e2e.ops.a5.t1.out.wpu.e2e.ops.a5.t1.output.e2e-artifact.service-flow-system`: **out** WorkProductUse `wpu.e2e.ops.a5.t1.output.e2e-artifact.service-flow-system`
- `pp.e2e.ops.a5.t1.out.wpu.e2e.ops.a5.t1.output.e2e-artifact.cost-model`: **out** WorkProductUse `wpu.e2e.ops.a5.t1.output.e2e-artifact.cost-model`
- `pp.e2e.ops.a5.t1.out.wpu.e2e.ops.a5.t1.output.e2e-artifact.improvement-record`: **out** WorkProductUse `wpu.e2e.ops.a5.t1.output.e2e-artifact.improvement-record`

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
