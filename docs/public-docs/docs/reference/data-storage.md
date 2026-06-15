# Data and Storage

Modless stores runtime state in PostgreSQL through Spring JDBC. Flyway owns schema creation and
migration. Static metamodels, constraints, transformations, generators, and UI metadata remain
versioned repository assets rather than database records.

## Core Data

| Area      | Tables                                                 | Notes                                                |
| --------- | ------------------------------------------------------ | ---------------------------------------------------- |
| Identity  | `users`, `auth_sessions`                               | Password hashes, salts, session expiry               |
| Projects  | `projects`, `project_members`, `project_active_models` | Ownership, roles, active level models                |
| Models    | `models`                                               | JSON model, source XMI, metamodel metadata, revision |
| Imports   | `staged_imports`, `staged_import_payloads`             | Temporary XMI import payloads with expiry            |
| Artifacts | `artifacts`, `artifact_files`                          | Generated project metadata and file content          |
| Jobs      | `mde_jobs`, `mde_job_diagnostics`                      | Operation status, progress, result IDs, diagnostics  |

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

## Assistant Data

| Area                    | Tables                                                                  |
| ----------------------- | ----------------------------------------------------------------------- |
| Conversation            | `assistant_threads`, `assistant_messages`, `assistant_thread_summaries` |
| Recent Spring AI memory | `SPRING_AI_CHAT_MEMORY`                                                 |
| Proposals and audit     | `assistant_proposals`, `assistant_action_audits`                        |
| Retrieval               | `assistant_retrieval_documents`                                         |
| Model context           | `assistant_model_contexts`                                              |
| Rate limiting           | `assistant_rate_limits`                                                 |

Retrieval documents can store 384-dimensional pgvector embeddings. Model contexts are keyed by
model ID and revision so a changed model receives a new compact context snapshot.

## Inspecting a Local Database

```powershell
docker exec -it modless-postgres-1 psql -U modless -d modless
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
