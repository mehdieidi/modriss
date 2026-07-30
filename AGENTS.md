# Agent Instructions

## Tooling

- Do not run linters, code formatters, or auto-format commands during normal work.
- Do not run commands such as `npm run lint`, `npm run format`, `prettier`, `ruff format`, `black`, `mvn spotless:apply`, or equivalent formatting tools unless the user explicitly asks.
- If verification is needed, prefer focused tests or build/type checks that do not rewrite files.
- If a formatter or linter would normally be useful, mention it in the final response instead of running it.

## Flyway Migrations

- Never guess a Flyway migration version manually.
- Before adding a migration, run `python scripts/flyway-next-migration.py "<description>" --location platform` or `--location assistant` and edit the created file.
- Flyway versions are global across both `db/migration` and `db/assistant-migration`; a platform migration may need a version higher than the latest assistant migration.
- Run `python scripts/check-flyway-migration-versions.py --changed-only` after adding or renaming migration files.

## AI Modeling Assistant Validation Boundary

- Chatbot/LLM assistant generated actions, patches, proposals, checkpoints, and model outputs must be gated only by structural Ecore/EMF
  conformance.
- Do not call `ModelService.validate(...)`, stored validation endpoints, `validateGeneratedXmi(...)`, `EpsilonEvlValidator`, EVL CLIs, or EVL
  profiles from chatbot assistant apply/repair/commit paths.
- Assistant code may call only `ModelService.validateStructural(...)` for generated model output validation.
- EVL semantic validation is allowed only for explicit user/model validation workflows outside the chatbot assistant.
- If assistant docs mention validation, state this boundary clearly.
