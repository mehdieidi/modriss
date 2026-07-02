# Agent Instructions

## Tooling

- Do not run linters, code formatters, or auto-format commands during normal work.
- Do not run commands such as `npm run lint`, `npm run format`, `prettier`, `ruff format`, `black`, `mvn spotless:apply`, or equivalent formatting tools unless the user explicitly asks.
- If verification is needed, prefer focused tests or build/type checks that do not rewrite files.
- If a formatter or linter would normally be useful, mention it in the final response instead of running it.
