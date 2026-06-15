# Backend and Storage

## Backend Layers

| Layer                | Main responsibilities                                             |
| -------------------- | ----------------------------------------------------------------- |
| Controllers          | HTTP validation, authentication extraction, response shapes       |
| Application services | Auth, projects, models, transformations, artifacts, layouts, jobs |
| Modeling services    | Metamodel resolution, UI config, layout, JSON/XMI conversion      |
| MDE runners          | EVL validation, ETL transformation, EGX/EGL generation            |
| Assistant services   | Retrieval, context, orchestration, patch compilation, proposals   |
| Persistence adapter  | PostgreSQL reads and writes behind `PlatformStore`                |

## Core Persistence

Flyway migrations create tables for:

- Users and auth sessions
- Projects, members, and active models
- Models and staged XMI imports
- Artifacts and artifact files
- MDE jobs and diagnostics

Models use `jsonb` for browser-facing model data and may retain source XMI as `bytea`. Generated
artifact files are stored by relative path and text content.

## Assistant Persistence

The assistant adds:

- Threads, durable messages, and rolling summaries
- Spring AI recent chat memory
- Proposals and action audits
- Retrieval documents with pgvector embeddings
- Compact model-context snapshots
- Persisted rate-limit windows

## Concurrency and Integrity

- Model revisions provide optimistic concurrency.
- Foreign keys and cascade rules enforce project ownership boundaries.
- Member roles are constrained in SQL and application services.
- File paths and artifact limits are validated before persistence.
- MDE execution and generation limits constrain resource use.

## Migrations

Migrations live under:

```text
packages/java/platform-storage-postgres/src/main/resources/db/migration
```

Use forward-only migrations. Do not edit a migration that has already been shared or applied to a
long-lived database.
