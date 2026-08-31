# Backend and Storage

## Backend Layers

| Layer                | Main responsibilities                                                                                                    |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| Controllers          | HTTP validation, authentication extraction, response shapes                                                              |
| Application services | Auth, projects, models, transformations, artifacts, layouts, jobs                                                        |
| Modeling services    | Metamodel resolution, UI config, layout, JSON/XMI conversion                                                             |
| MDE runners          | EVL validation, ETL transformation, EGX/EGL generation                                                                   |
| Assistant services   | Adaptive strategy, conceptual compilation, checked agent tools, durable turns/checkpoints, provenance and provider audit |
| Persistence adapter  | PostgreSQL reads and writes behind `PlatformStore`                                                                       |

## Core Persistence

Flyway migrations create tables for:

- Users and auth sessions
- Projects, members, and active models
- Models and staged XMI imports
- Artifacts and artifact files
- MDE jobs and diagnostics
- Model-transformation baselines and pending synchronization sessions (`model_synchronization_records`)

Models use `jsonb` for browser-facing model data and may retain source XMI as `bytea`. Generated
artifact files are stored by relative path and text content.

Transformation baselines and pending conflict sessions are stored as typed JSONB payloads in
`model_synchronization_records`; the application maps them to `GeneratedBaseline` and
`SynchronizationSession` through `PlatformStore`. A successful synchronization transaction
updates Working, advances the raw baseline, and closes a resolved session together.

## Assistant Persistence

The assistant adds:

- Threads, durable turns, replayable events, durable messages, and rolling summaries
- Spring AI recent chat memory
- Checkpoints with inverse patches for undo
- Source units and per-element provenance
- Provider-call prompts/usage and action audits
- Source blueprints, context caches, workflows, private work items, source facts, and structural
  validation attempts
- A reserved rate-limit table; current runtime limiting is in memory

For conceptual generation, `assistant_workflows.plan` stores the obligation ledger, selected exact
types, stable-ID blueprint, current slice size, accounting, diagnostics, and accepted obligation
verdict. Generated slice objects remain in private `assistant_work_items.payload` records until a
complete structurally valid checkpoint commits. Provider calls store prompts, latency, token usage,
finish reason/error, and an optional idempotent `call_key`.

## Concurrency and Integrity

- Model revisions provide optimistic concurrency.
- Foreign keys and cascade rules enforce project ownership boundaries.
- Member roles are constrained in SQL and application services.
- File paths and artifact limits are validated before persistence.
- MDE execution and generation limits constrain resource use.

## Migrations

Platform migrations:

```text
packages/java/platform-storage-postgres/src/main/resources/db/migration
```

Assistant migrations:

```text
packages/java/platform-assistant/src/main/resources/db/assistant-migration
```

Flyway applies both locations at backend startup. Use forward-only migrations. Do not edit a
migration that has already been shared or applied to a long-lived database.
