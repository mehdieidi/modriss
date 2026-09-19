# MODRISS Modeling Methodologies

The normative English full-lifecycle method is defined in
[`software-development-process.md`](software-development-process.md). It covers initiation,
situational tailoring, team coordination, iterative-incremental CIM/PIM/PSM delivery, release,
transition, operations, change propagation, and retirement. The Persian lifecycle translation is
available in [`software-development-process-fa.md`](software-development-process-fa.md). The
machine-readable definitions below are the executable method content enacted inside that lifecycle.

Canonical, machine-readable **iterative-incremental** process definitions for CIM, PIM,
AWS PSM, generated-artifact deployment readiness, and the end-to-end pipeline. Each process follows **SPEM 2.0**: sequential phases → stages
→ optional sub-stages → atomic tasks, plus roles, artifact kinds, guidelines, and a
**process engine** (the agile kernel that delivers increments).

## Layout

| Path                   | Purpose                                                                                                               |
| ---------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `spem/`                | SPEM 2.0 method content and UML activity diagrams                                                                     |
| `process-definitions/` | JSON DSL: phases, stages, tasks, roles, artifacts, progress/governance contracts, and `processEngine`                 |
| `coverage-matrix/`     | Generated concept → task mappings (CI-validated)                                                                      |
| `tools/lib/`           | SPEM specs, full-lifecycle orchestration, process engine, iteration loops, governance, artifact kinds, and guidelines |
| `tools/`               | Build, coverage, validation, and UI metadata augmentation scripts                                                     |

## Maintenance

When metamodels change:

1. Update SPEM specs in `tools/lib/spem-*.mjs` and concept assignment in `concept-assignment.mjs`.
2. Regenerate artifacts:

   ```bash
   node mde/methodology/tools/build-process-definitions.mjs
   node mde/methodology/tools/generate-coverage-matrix.mjs
   node mde/methodology/tools/augment-ui-metadata.mjs
   node mde/methodology/tools/generate-methodology-guides.mjs
   node mde/methodology/tools/generate-methodology-narratives.mjs
   ```

3. Run `node mde/methodology/tools/validate-coverage.mjs` (also invoked by `scripts/verify.py`).

## API

- `GET /api/modeling/process/{cim|pim|psm|artifact|end-to-end}`: process definition
- `GET /api/modeling/process/{cim|pim|psm}/coverage`: coverage matrix
