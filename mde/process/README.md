# MODRISS Modeling Methodologies

The normative English full-lifecycle method is defined in
[`software-development-process.md`](software-development-process.md). It covers initiation,
situational tailoring, team coordination, iterative-incremental CIM/PIM/PSM delivery, release,
transition, operations, change propagation, and retirement. The Persian lifecycle translation is
available in [`software-development-process-fa.md`](software-development-process-fa.md). The
machine-readable definitions below are the executable method content enacted inside that lifecycle.

Canonical, machine-readable **iterative-incremental** process definitions for CIM, PIM,
AWS PSM, generated-artifact deployment readiness, and the end-to-end pipeline. Each process
is a MODRISS JSON representation mapped to SPEM 2.0: reusable method content contains
RoleDefinitions, TaskDefinitions, WorkProductDefinitions, and Guidance; process Activities
contain RoleUses, WorkProductUses, TaskUses, explicit WorkSequences, and TaskUse input/output
bindings plus explicit SPEM-mapped ProcessParameters and ProcessPerformers. MODRISS-specific
process-engine, progress, governance, and change-management extensions are labeled as such.

## Layout

| Path                   | Purpose                                                                                                                 |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `spem/`                | SPEM 2.0 method content and UML activity diagrams                                                                       |
| `process-definitions/` | JSON DSL: method content, Activities, TaskUses, WorkSequences, progress/governance contracts, and `processEngine`       |
| `coverage-matrix/`     | Generated concept → task mappings (CI-validated)                                                                        |
| `tools/lib/`           | SPEM specs, full-lifecycle orchestration, process engine, iteration loops, governance, artifact process, and guidelines |
| `tools/`               | Build, coverage, validation, and UI metadata augmentation scripts                                                       |

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

## API

- `GET /api/modeling/process/{cim|pim|psm|artifact|end-to-end}`: process definition
- `GET /api/modeling/process/{cim|pim|psm}/coverage`: coverage matrix

The process endpoint returns the compiled method-content/process-use contract;
`metamodelBindings` are coverage bindings, not SPEM work products. The builder
generates CIM, PIM, PSM, end-to-end, and artifact-readiness definitions from
the compact sources, including `spem-artifact.mjs`; `artifact.json` is not a
hand-maintained exception.
