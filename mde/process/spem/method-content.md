# MODRISS Method Content and SPEM 2.0 Mapping

MODRISS is an executable JSON process representation mapped to SPEM 2.0. It is
not native SPEM XMI and does not claim SPEM implementation conformance merely
because a definition contains `spemVersion: "2.0"`. The generated definitions
state this explicitly with:

```json
{
  "representation": "MODRISS JSON DSL mapped to SPEM 2.0",
  "conformance": "mapped",
  "mappingScope": [
    "Core",
    "ManagedContent",
    "MethodContent",
    "ProcessStructure",
    "ProcessWithMethods"
  ]
}
```

The normative source is the [OMG SPEM 2.0 specification](https://www.omg.org/spec/SPEM/2.0/PDF/).
The repository uses the SPEM concepts needed for reusable method content and
process structure, while retaining MODRISS extensions for model-driven
coverage, transformations, governance, progress, and UI navigation.
`mappingScope` names the selected SPEM package concepts; it is an explicit
mapping scope, not a claim of one of OMG's official compliance points.

## Separation of method content and process use

The MODRISS methodology has two parts: the development process and the
modeling framework. The representation below concerns the process part. Its
`methodContent` and `process` objects are SPEM structures within that part;
they are not the two parts of the methodology. Reusable method content is used
by the development process.

Every generated process definition has two semantic parts:

```text
methodContent
├── roleDefinitions
├── taskDefinitions
├── workProductDefinitions
└── guidance

process
├── roleUses
├── workProductUses
├── phases/stages/taskUses
├── workSequences
└── MODRISS extensions (engine, governance, progress, change management)
```

The `methodContent` object is a `MethodContentPackage`; the root object is a
MODRISS `Process` projection that references that package through the explicit
use objects below.

The distinction is normative for this DSL:

| MODRISS JSON                                        | SPEM 2.0 meaning                                          |
| --------------------------------------------------- | --------------------------------------------------------- |
| `methodContent.roleDefinitions[]`                   | `RoleDefinition` reusable method content                  |
| `process.roleUses[]`                                | `RoleUse` scoped to a process Activity                    |
| `methodContent.taskDefinitions[]`                   | `TaskDefinition` reusable work description                |
| `phase/stage.taskUses[]`                            | `TaskUse` proxy for one TaskDefinition in one Activity    |
| `methodContent.workProductDefinitions[]`            | `WorkProductDefinition` reusable work product description |
| `process.workProductUses[]`                         | `WorkProductUse` in a concrete Activity/task context      |
| `methodContent.guidance[]`                          | SPEM `Guidance` method content                            |
| `phase` and `stage` objects with `type: "Activity"` | SPEM `Activity` with a MODRISS/SPEM Kind                  |
| `process.workSequences[]`                           | SPEM `WorkSequence` predecessor/successor dependency      |

The process occurrence ID remains stable for progress tracking, for example
`cim.ph1.st3.t1`, but its `taskDefinitionRef` points to reusable method
content, for example `task.cim.ph1.st3.t1`. A future process variant can reuse
the same TaskDefinition with another TaskUse and can select a different subset
of its steps.

## Work products versus metamodel bindings

MODRISS has two different modeling dimensions:

1. A SPEM work product is something consumed, produced, or modified by work,
   such as a strategic-intent package, domain model, readiness record, release
   record, or operations handover pack.
2. A CIM/PIM/PSM classifier is a concept in the language metamodel used to
   construct that work product, such as `BusinessGoal`, `Requirement`, or
   `AwsStage`.

The compiled representation keeps them separate:

```json
{
  "id": "task.cim.ph1.st3.t1",
  "type": "TaskDefinition",
  "outputWorkProductRefs": ["cim-artifact.strategic-intent"],
  "metamodelBindings": [
    { "metamodel": "cim", "classifier": "BusinessGoal", "kind": "EClass" },
    { "metamodel": "cim", "classifier": "KPI", "kind": "EClass" },
    { "metamodel": "cim", "classifier": "Priority", "kind": "EEnum" }
  ]
}
```

An EClass or EEnum is therefore never silently presented as a SPEM
WorkProductDefinition. Coverage matrices use `metamodelBindings`; process
guides and gates use actual work-product definitions and uses. Each
WorkProductDefinition is typed as `WorkProductDefinition` and carries a
`workProductKind` such as `Artifact` rather than treating `ArtifactDefinition`
as a separate primary metaclass.

## Task work-product parameters

TaskDefinitions expose both incoming and outgoing work products. The compiler
represents the SPEM parameter direction explicitly and creates corresponding
WorkProductUses for each TaskUse:

```json
{
  "inputWorkProductRefs": ["cim-artifact.domain-structure"],
  "outputWorkProductRefs": ["cim-artifact.requirements-package"],
  "workProductParameters": [
    {
      "workProductDefinitionRef": "cim-artifact.domain-structure",
      "direction": "in",
      "optional": false
    },
    {
      "workProductDefinitionRef": "cim-artifact.requirements-package",
      "direction": "out",
      "optional": false
    }
  ]
}
```

Every published process authoring source explicitly declares `inputArtifactIds`
for every task. An empty array is an intentional declaration that the task has
no required input WorkProductDefinition. The compiler rejects a missing
declaration and records `inputSource: "declared"` on the resulting
TaskDefinition; it never infers inputs from task order or from the preceding
task's outputs. This keeps TaskDefinition semantics stable when process activities are
reordered, grouped, split, or tailored.

## Explicit process parameters and performers

SPEM represents the process-time relationship between a TaskUse and its
WorkProductUses with owned `ProcessParameter` objects, and represents the
performing role through a `ProcessPerformer` relationship. The generated DSL
now emits those objects explicitly:

```json
{
  "id": "pim.ph5.st1.t1",
  "type": "TaskUse",
  "processParameters": [
    {
      "id": "pp.pim.ph5.st1.t1.in.wpu.pim.ph5.st1.t1.input.pim-artifact.service-map",
      "type": "ProcessParameter",
      "direction": "in",
      "workProductUseRef": "wpu.pim.ph5.st1.t1.input.pim-artifact.service-map"
    }
  ],
  "processPerformers": [
    {
      "id": "ppf.pim.ph5.st1.t1.ru.pim.ph5.st1.solution-architect",
      "type": "ProcessPerformer",
      "kind": "primary",
      "roleUseRef": "ru.pim.ph5.st1.solution-architect"
    }
  ]
}
```

The legacy `inputWorkProductUseRefs`, `outputWorkProductUseRefs`, and
`performerRoleUseRefs` arrays remain as indexed convenience projections for
the UI and existing clients; they are validated against the explicit
relationship objects. The TaskDefinition's `DefaultTaskDefinitionParameter`
objects describe reusable default direction and optionality, while each
TaskUse's `ProcessParameter` binds that process occurrence to concrete
WorkProductUses. The generated `type` is the SPEM name
`Default_TaskDefinitionParameter` for reusable task parameters. This is a JSON projection mapped to the SPEM 2.0 semantics
described by the [OMG SPEM 2.0 specification](https://www.omg.org/spec/SPEM/2.0/PDF/),
not a claim that the JSON is native SPEM XMI.

## Activities, phases, stages, and iterations

SPEM 2.0 provides `Activity` and a general Kind mechanism. `Phase` and
`Iteration` are standard examples of Activities qualified by a Kind; `Stage`
is a MODRISS extension, represented as `Activity(kind =
"MODRISS::Stage")`. The JSON keeps the legacy `phases` container and `ph*`
IDs for guided-modeling compatibility. In the CIM, PIM, PSM, and artifact
subprocesses, those top-level entries are explicitly typed
`MODRISS::Stage` and their children `MODRISS::SubStage`, because the entire
subprocess can recur for another increment. Only the three top-level entries
of the end-to-end lifecycle are typed `Phase`.

The MODRISS `processEngine` is not a SPEM metaclass. It is an operational
extension whose `iteration` mapping identifies an `Activity(kind =
"Iteration")`, whose cycle refers to process Activities, and whose loop and
rework paths are represented by WorkSequences with conditions and guidance.
This preserves the agile increment engine without misrepresenting it as a
standard SPEM class.

The end-to-end process distinguishes two repeatable Activities inside the
single active-product phase. `processEngine.iteration` is the vertical
model-driven increment. `processEngine.releaseCycle` is an `Iteration` that
references construction, qualification, promotion, handover, and review
Activities; it does not reference or repeat a Phase. The separate
`modriss.operations-maintenance` Process is ongoing and event-driven: it begins
at G7, sustains accepted live releases while later releases are developed, and
terminates at G8. Explicit WorkSequences route release rejection and
operational product changes to increment planning, and authorized retirement
from both active streams into Phase 2.

## Explicit sequencing

Array order is only a presentation order. The authoritative process
dependencies are `workSequences[]` with SPEM link kinds:

- `finishToStart`
- `finishToFinish`
- `startToStart`
- `startToFinish`

MODRISS emits `finishToStart` for ordinary phase, stage, and task order and
records iteration and rework conditions as extension attributes. This allows
the same definition to express sequential flow, rework, iteration loops, and
future parallel or conditional paths without treating JSON array position as
the process semantics.

For the full lifecycle, the decisive conditional sequences are:

- release qualification/promotion → increment planning when G6 rejects a
  candidate or promotion fails;
- Operations and Maintenance → increment planning when a product-changing
  service item is selected;
- Phase 1 → Phase 2 when retirement is authorized and no development/release
  work is in flight;
- Operations and Maintenance → Phase 2 to coordinate live-service retirement;
  and
- Phase 2 → Operations and Maintenance as a finish-to-finish constraint so the
  service process ends only at G8.

## Compliance boundary

This representation covers the method-content and process-structure concepts
needed by MODRISS and documents their mapping. It does not claim full
`SPEM Complete` compliance, UML-profile interchange, or native CMOF/XMI
serialization. OMG publishes the normative machine-readable CMOF and XMI
assets on the [SPEM 2.0 specification page](https://www.omg.org/spec/SPEM/2.0/About-SPEM).
An exporter to native SPEM XMI would be a separate conformance step, not an
implicit property of the JSON DSL.

## Research references

- Object Management Group, [Software & Systems Process Engineering Metamodel (SPEM), Version 2.0](https://www.omg.org/spec/SPEM/2.0/PDF/), 2008.
- ISO/IEC/IEEE, [12207:2026 — Software life cycle processes](https://www.iso.org/standard/90219.html). It provides a common life-cycle framework but does not prescribe one methodology or life-cycle model.
- ISO/IEC/IEEE, [15288:2023 — System life cycle processes](https://www.iso.org/standard/81702.html). It supports concurrent, iterative, and recursive application of life-cycle processes without prescribing a specific method.
- Brinkkemper, S., [“Method engineering: Engineering of information systems development methods and tools”](<https://doi.org/10.1016/S0950-5849(95)01059-9>), _Information and Software Technology_, 1996.
- Brinkkemper, S., Saeki, M., & Harmsen, F., [“Assembly techniques for method engineering”](https://doi.org/10.1016/S0306437999000162), _Information Systems_, 2001.
- [Agile Manifesto principles](https://agilemanifesto.org/principles), used here for empirical iteration and adaptation rather than as a replacement for assurance or lifecycle responsibilities.
