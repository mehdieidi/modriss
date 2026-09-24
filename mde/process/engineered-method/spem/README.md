# SPEM 2.0 Representation

## Representation decision

SPEM 2.0 represents the process component and preserves its distinction between:

- reusable lifecycle-independent method content (`RoleDefinition`,
  `TaskDefinition`, `WorkProductDefinition`, `Guidance`); and
- process-specific uses (`Activity`, `TaskUse`, `RoleUse`, `WorkProductUse`,
  `ProcessParameter`, `ProcessPerformer`, `WorkSequence`, and `Milestone`).

The library packages the existing CIM, PIM, PSM, artifact-readiness, and
end-to-end definitions as method plug-in content and process packages. The
logical hierarchy is:

```text
MethodLibrary: MODRISS
  MethodPlugin: MODRISS Core
    MethodContentPackage: consolidated canonical method content
      roles, tasks, work products, guidance, process patterns
    ProcessPackage: full lifecycle and child process components
  MethodConfiguration: exploration
  MethodConfiguration: standard
  MethodConfiguration: multi-team
  MethodConfiguration: regulated/high-criticality
```

## Files

| File                                                   | Purpose                                                                                                                                                      |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `method-content-index.json`                            | Consolidated, lossless index of source RoleDefinitions, TaskDefinitions, WorkProductDefinitions, Guidance, and provenance                                    |
| `modriss-method-library.spem.xml`                      | SPEM-logical XMI/XML representation of the library, method plugin, content packages, configurations, Activities, uses, parameters, performers, and sequences |
| `../method-library/reusable-method-content-catalog.md` | Generated human-readable catalog of every reusable role, task, work product, and guidance element                                                            |
| `../12-method-library-and-fragment-report.md`          | Academic narrative explaining the library design, all method fragments, selection, assembly, governance, and validity boundary                               |
| `lifecycle.puml`                                       | Normative overview of the product lifecycle, nested release cycle, increment cycle, and retirement decision                                                  |
| `release-cycle.puml`                                   | Focused release-to-release control flow, including G6 rework, promotion rollback, G7, continued operation, and the next release                              |
| `operations-flow.puml`                                 | Phase 3 pull flow from operational intake through classification, WIP/SLE control, verification, and three explicit dispositions                             |
| `model-driven-increment.puml`                          | Phase 1 detail from increment framing through CIM, PIM, PSM, generation, verification, and G5                                                                |
| `change-routing.puml`                                  | Operational change classification and return to the earliest authoritative source                                                                            |
| `diagram-catalog.md`                                   | Diagram hierarchy, semantic authority, and synchronization rules                                                                                             |

The first two files and `../method-library/repository-inventory.csv` are built
by `../tools/build-method-package.mjs` from the executable process definitions,
the engineered core-content catalog, and the reusable fragment catalog. Shared
elements occur once in the canonical content package, while provenance records
all source processes in which they appear.

The lifecycle is intentionally cyclic rather than a one-shot sequence. After
G7, the accepted release remains in operation. If retirement is not authorized
and further work is selected, control returns from Phase 3 to Phase 1 with a
new release hypothesis; Phase 0 is not repeated. Phase 4 is reached only by an
explicit retirement decision.

Phase 3 also contains a separately repeatable `KanbanFlow` Activity. It keeps
unpredictable production demand out of fictional release schedules while
remaining connected to the lifecycle: an item may finish as operations-only
work, invoke the shortest safe model-driven/release path, or be committed to a
planned release. Its MODRISS extension metadata records pull control, explicit
WIP limits, service-level expectations, and the stage references serialized by
the package builder. Maintenance purpose, emergency status, and class of
service remain independent classifications; none is used as a proxy for the
others.

## Conformance statement

The XML uses the normative SPEM 2.0 namespace and metaclass names from the OMG
SPEM 2.0 Complete CMOF model. It models the Core, Managed Content, Method
Content, Process Structure, Process with Methods, and Method Plugin concepts
used by MODRISS. MODRISS extensions are isolated under the
`http://modriss.org/spec/method/1.0` namespace and preserve source identifiers,
gate/evidence metadata, and process-engine semantics not defined by SPEM.

This is a transparent **SPEM-logical exchange model**. It is not claimed to be
certified for native import into every proprietary SPEM/EPF/RMC tool. Such a
claim requires validation against the normative CMOF serialization and the
target tool's dialect. The source MODRISS JSON is likewise described as
“mapped to SPEM 2.0,” not native SPEM XMI.

The authoritative OMG resources are:

- <https://www.omg.org/spec/SPEM/2.0/>
- <https://www.omg.org/spec/SPEM/2.0/PDF/>
- <https://www.omg.org/spec/SPEM/20070801/SPEM2.merged.cmof>

## Mapping

| MODRISS source                           | SPEM concept                                                                                 |
| ---------------------------------------- | -------------------------------------------------------------------------------------------- |
| `methodContent.roleDefinitions[]`        | `RoleDefinition`                                                                             |
| `methodContent.taskDefinitions[]`        | `TaskDefinition` with steps and default parameters                                           |
| `methodContent.workProductDefinitions[]` | `WorkProductDefinition`                                                                      |
| `methodContent.guidance[]`               | `Guidance`                                                                                   |
| lifecycle phase/stage/substage           | nested `Activity`; Phase/Iteration are expressed with Process Kinds, Stage is a MODRISS kind |
| task occurrence                          | `TaskUse` referring to one `TaskDefinition`                                                  |
| activity-scoped role                     | `RoleUse` referring to one `RoleDefinition`                                                  |
| activity/task-scoped product             | `WorkProductUse` referring to one `WorkProductDefinition`                                    |
| input/output binding                     | `ProcessParameter` referring to a `WorkProductUse`                                           |
| task performer binding                   | `ProcessPerformer` linking `TaskUse` and `RoleUse`                                           |
| predecessor/successor                    | `WorkSequence` with an explicit link kind                                                    |
| gate/checkpoint                          | `Milestone` plus MODRISS decision/evidence guidance                                          |
| tailoring profile                        | `MethodConfiguration` plus MODRISS selection metadata                                        |

`metamodelBindings` are not SPEM work products. They remain MODRISS coverage
metadata linking a TaskDefinition to Ecore EClasses/EEnums. Similarly, the
runtime `processEngine`, progress events, governance rules, and change routing
are explicit MODRISS extensions rather than invented SPEM metaclasses.
