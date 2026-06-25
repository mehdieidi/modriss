# Modless Modeling Methodologies

Canonical, machine-readable modeling process definitions for CIM, PIM, AWS PSM, and the end-to-end pipeline.

## Layout

| Path                   | Purpose                                                              |
| ---------------------- | -------------------------------------------------------------------- |
| `spem/`                | SPEM 2.0 method content and UML activity diagrams                    |
| `process-definitions/` | JSON process DSL consumed by the platform API and guided modeling UI |
| `coverage-matrix/`     | Generated concept → task mappings (CI-validated)                     |
| `tools/`               | Build, coverage, validation, and UI metadata augmentation scripts    |

## Maintenance

When metamodels change:

1. Update phase mappings in `tools/lib/phase-mappings.mjs` if new concepts need explicit assignment.
2. Regenerate artifacts:
   ```bash
   node mde/methodology/tools/build-process-definitions.mjs
   node mde/methodology/tools/generate-coverage-matrix.mjs
   node mde/methodology/tools/augment-ui-metadata.mjs
   ```
3. Run `node mde/methodology/tools/validate-coverage.mjs` (also invoked by `scripts/verify.py`).

## API

- `GET /api/modeling/process/{cim|pim|psm|end-to-end}` — process definition
- `GET /api/modeling/process/{cim|pim|psm}/coverage` — coverage matrix
