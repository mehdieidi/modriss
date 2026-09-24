# MODRISS Process and Engineered Method

The normative English full-lifecycle development process is defined in
[`software-development-process.md`](software-development-process.md). It covers initiation,
situational tailoring, team coordination, iterative-incremental CIM/PIM/PSM delivery, release,
transition, operations, change propagation, and retirement. The machine-readable
definitions below specify the modeling processes and their reusable method content
for use within that lifecycle.

The method package also contains an exhaustive fictional
[ColdChain Sentinel enactment](method/13-hypothetical-enactment-and-process-validation.md)
with generated task, role, work-product, and G0–G8 coverage ledgers. It is an
analytical consistency test, not empirical validation.

Canonical, machine-readable **iterative-incremental** process definitions for CIM, PIM,
AWS PSM, generated-artifact deployment readiness, and the end-to-end pipeline. Each process
is a MODRISS JSON representation mapped to SPEM 2.0: reusable method content contains
RoleDefinitions, TaskDefinitions, WorkProductDefinitions, and Guidance; process Activities
contain RoleUses, WorkProductUses, TaskUses, explicit WorkSequences, and TaskUse input/output
bindings plus explicit SPEM-mapped ProcessParameters and ProcessPerformers. MODRISS-specific
process-engine, progress, governance, and change-management extensions are labeled as such.

## Layout

| Path                 | Meaning                                                                                                                |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `definitions/`       | Generated executable JSON definitions for the integrated, CIM, PIM, PSM, and artifact-readiness processes              |
| `method/`            | Engineered-method research, reusable content library, SPEM package, process views, templates, evaluation, and diagrams |
| `method/spem/`       | Consolidated formal SPEM representation and indexes                                                                    |
| `method/spem/views/` | All PlantUML lifecycle and child-process views; there is no second SPEM location                                       |
| `coverage-matrix/`   | Generated Ecore concept → task mappings                                                                                |
| `tools/lib/`         | Authoritative process specifications, orchestration, governance, engines, and guidance                                 |
| `tools/`             | Definition builders, coverage verification, guide generation, and frontend metadata generation                         |

## Maintenance

When metamodels change:

1. Update the process specifications in `tools/lib/spem-*.mjs` and concept assignment in `concept-assignment.mjs`.
   Every task must have an explicit `inputArtifactIds` declaration (including
   `[]` for no required inputs); task order is never used to infer inputs.
2. Regenerate artifacts:

   ```bash
   node mde/process/tools/build-process-definitions.mjs
   node mde/process/tools/generate-coverage-matrix.mjs
   node mde/process/tools/augment-ui-metadata.mjs
   node mde/process/tools/generate-methodology-guides.mjs
   node mde/process/tools/generate-methodology-narratives.mjs
   ```

3. Run `node mde/process/tools/validate-coverage.mjs` (also invoked by `scripts/verify.py`).

To rebuild and verify the formal method package, run:

```bash
node mde/process/method/tools/build-method-package.mjs
node mde/process/method/tools/verify-method-package.mjs
node mde/process/method/tools/verify-lifecycle-sync.mjs
```

## API

- `GET /api/modeling/process/{cim|pim|psm|artifact|end-to-end}`: process definition
- `GET /api/modeling/process/{cim|pim|psm}/coverage`: coverage matrix

The process endpoint returns the compiled method-content/process-use contract;
`metamodelBindings` are coverage bindings, not SPEM work products. The builder
generates CIM, PIM, PSM, end-to-end, and artifact-readiness definitions from
the compact sources, including `spem-artifact.mjs`; `artifact.json` is not a
hand-maintained exception.
