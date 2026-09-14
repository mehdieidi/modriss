# Data and Storage

MODRISS stores runtime state in PostgreSQL through Spring JDBC. Flyway owns schema creation and
migration. Static metamodels, constraints, transformations, generators, and UI metadata remain
versioned repository assets rather than database records.

## Core Data

| Area            | Tables                                                 | Notes                                                |
| --------------- | ------------------------------------------------------ | ---------------------------------------------------- |
| Identity        | `users`, `auth_sessions`                               | Password hashes, salts, session expiry               |
| Projects        | `projects`, `project_members`, `project_active_models` | Ownership, roles, active level models                |
| Models          | `models`                                               | JSON model, source XMI, metamodel metadata, revision |
| Imports         | `staged_imports`, `staged_import_payloads`             | Temporary XMI import payloads with expiry            |
| Artifacts       | `artifacts`, `artifact_files`                          | Generated project metadata and file content          |
| Jobs            | `mde_jobs`, `mde_job_diagnostics`                      | Operation status, progress, result IDs, diagnostics  |
| Synchronization | `model_synchronization_records`                        | JSONB raw generated baselines and pending sessions   |

Project deletion cascades through project-owned models, artifacts, jobs, membership, staged imports,
and assistant state. User and project references use foreign keys, while level and role values use
SQL checks.

## Model Record

A persisted model records:

- Project and modeling level
- Name and browser-facing `jsonb` model
- Metamodel version and hash
- Numeric revision
- Optional source XMI and XMI hash
- Migration state
- Creation and update timestamps

The metamodel hash detects that a stored model was created against a different runtime metamodel.
Detection does not itself migrate a model; breaking language changes require a compatibility or
migration policy.

## Transformation Synchronization Records

`model_synchronization_records` is created by `V32__model_synchronization_baselines_and_sessions.sql`.
It stores `BASELINE` records for raw generated JSON/XMI and `SESSION` records for pending conflicts,
including the Base, Working, and NewGenerated XMI participants, safe merged candidate, decisions,
and revision/fingerprint metadata. Baselines are keyed by project, direction, and source model;
sessions are keyed by project and session ID.

Resolutions update only a pending session. Working and the raw baseline advance only after target
semantic validation, in one transaction. Cancelling, failing, or rejecting a stale session leaves
both canonical records unchanged. Project deletion cascades synchronization records.

## Assistant Data

| Area                    | Tables                                                                                  |
| ----------------------- | --------------------------------------------------------------------------------------- |
| Conversation            | `assistant_threads`, `assistant_messages`, `assistant_thread_summaries`                 |
| Recent Spring AI memory | `SPRING_AI_CHAT_MEMORY`                                                                 |
| Durable turns           | `assistant_turns`, `assistant_turn_events`, `assistant_checkpoints`                     |
| Source provenance/audit | `assistant_source_units`, `assistant_element_provenance`, `assistant_action_audits`     |
| Source planning/context | `assistant_source_facts`, `assistant_source_blueprints`, `assistant_turn_context_cache` |
| Durable workflow        | `assistant_workflows`, `assistant_work_items`, `assistant_validation_attempts`          |
| Provider usage/audit    | `assistant_provider_calls`, including stored system/user prompts and token usage        |
| Rate limiting           | `assistant_rate_limits` (schema reserved; runtime limit is in-memory)                   |

Turn checkpoints are tied to model revisions, so an inverse is rejected when the model changed
after that checkpoint.

## Inspecting a Local Database

```powershell
docker exec -it modriss-postgres-1 psql -U modriss -d modriss
```

Useful checks:

```sql
select version, description, success
from flyway_schema_history
order by installed_rank;

select id, project_id, level, revision, updated_at
from models
order by updated_at desc;
```

Do not edit already-applied migrations in shared environments. Add a new forward-only migration and
test it against both empty and existing databases.
