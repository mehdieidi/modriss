# Modless Modeling Methodologies

Canonical, machine-readable **iterative-incremental** modeling process definitions for CIM, PIM,
AWS PSM, and the end-to-end pipeline. Each process follows **SPEM 2.0**: sequential phases → stages
→ optional sub-stages → atomic tasks, plus roles, artifact kinds, guidelines, and a
**process engine** (the agile kernel that delivers increments).

## Layout

| Path                   | Purpose                                                                                                                             |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `spem/`                | SPEM 2.0 method content and UML activity diagrams                                                                                   |
| `process-definitions/` | JSON DSL: phases, stages, tasks, roles, artifacts, `processEngine`                                                                  |
| `coverage-matrix/`     | Generated concept → task mappings (CI-validated)                                                                                    |
| `tools/lib/`           | `spem-cim.mjs`, `spem-pim.mjs`, `spem-psm.mjs`, `process-engine.mjs`, `iteration-loops.mjs`, `artifact-kinds.mjs`, `guidelines.mjs` |
| `tools/`               | Build, coverage, validation, and UI metadata augmentation scripts                                                                   |

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

- `GET /api/modeling/process/{cim|pim|psm|end-to-end}` — process definition
- `GET /api/modeling/process/{cim|pim|psm}/coverage` — coverage matrix
