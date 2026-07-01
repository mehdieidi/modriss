# Change Guide

Modless has several synchronized representations of the same language. Treat a formal language
change as a contract change across every affected producer, consumer, representation, and test.

## Source-of-Truth Map

| Change                    | Primary source                       |
| ------------------------- | ------------------------------------ |
| Abstract syntax           | Emfatic under `mde/metamodels/`      |
| Runtime metamodel         | Combined Ecore files                 |
| Semantic rules            | EVL under `mde/validation/`          |
| Transformation meaning    | ETL/EOL under `mde/transformations/` |
| Generated project meaning | EGX/EGL/EOL under `mde/generation/`  |
| Visual syntax             | Level UI metadata plus Ecore         |
| Browser behavior          | `apps/frontend/js/` and CSS          |
| Persistence schema        | Flyway migrations                    |
| Public API                | Controllers and OpenAPI              |

## Changing a Metamodel

1. Define whether the change is additive, restrictive, breaking, or visual-only.
2. Search for every producer and consumer of the affected type or feature.
3. Edit the authoritative Emfatic module.
4. Regenerate and inspect affected Ecore files.
5. Update UI metadata and viewpoints.
6. Update JSON/XMI conversion where needed.
7. Update EVL, transformations, generation, assistant behavior, and samples.
8. Define existing-model compatibility or migration behavior.
9. Run focused tests, full tests, and an end-to-end smoke test.
10. Update API docs, architecture diagrams, and this public package.

## Changing Validation

Choose the correct enforcement level:

- Ecore for structural invariants
- EVL constraints for mandatory semantic invariants
- EVL critiques for optional improvement guidance
- Transformation or generator preconditions for execution-specific requirements

Add both failing and passing coverage and update generated/default models so they remain valid.

## Changing Visual Syntax

Visual-only changes usually belong in level UI metadata. New rendering behavior may also require
canvas, G6, CSS, view, or layout changes. Verify all affected palettes, views, connectors,
inspectors, import behavior, save/reload, and auto-layout.

## Persistence Changes

Add a new forward-only Flyway migration only when the application persistence schema changes.
Platform schema migrations belong under `packages/java/platform-storage-postgres/.../db/migration`.
Assistant schema migrations belong under `packages/java/platform-assistant/.../db/assistant-migration`.
DSML changes usually do not require a database migration because model JSON and XMI are stored
generically.

## Pull Request Checklist

- Formal source and derived Ecore are synchronized.
- Validation, transformations, and generation were reviewed.
- UI metadata and specialized frontend behavior were reviewed.
- JSON/XMI and stored-model compatibility were tested.
- AI retrieval and hard-coded assistant behavior were reviewed (metamodel/methodology catalog,
  structural apply gate, auto-apply/undo semantics).
- API, migrations, samples, tests, docs, and diagrams were updated where affected.
