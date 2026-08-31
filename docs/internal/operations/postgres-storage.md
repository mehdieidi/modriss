# PostgreSQL Storage

Varka persists application-owned runtime data in PostgreSQL. The backend uses Spring JDBC for
data access and Flyway for schema migration.

## What Is Stored

- Users and sessions: `users`, `auth_sessions`
- Projects and access control: `projects`, `project_members`, `project_active_models`
- CIM/PIM/PSM model instances: `models.model_json` as `jsonb`
- CIM/PIM/PSM source XMI sidecars: `models.source_xmi` as `bytea`
- Temporary imported XMI: `staged_imports`, `staged_import_payloads`
- Generated artifact bundles: `artifacts`, `artifact_files`
- MDE job state and diagnostics: `mde_jobs`, `mde_job_diagnostics`
- Assistant threads, durable turns/events, checkpoints, provenance, provider-call audits, chat
  memory, and rate limits (see
  `docs/public-docs/docs/reference/data-storage.md`)

Static product/configuration assets, such as metamodels, EVL/ETL/EGX scripts, and UI metadata JSON
resources, are still versioned with the application source code.

## Local Database

Start PostgreSQL:

```powershell
docker compose up -d postgres
```

Run the backend against that database:

```powershell
mvn -pl apps/backend -am spring-boot:run
```

Default connection settings:

```text
VARKA_DB_URL=jdbc:postgresql://localhost:5432/varka
VARKA_DB_USER=varka
VARKA_DB_PASSWORD=varka
```

Override them in the shell when needed:

```powershell
$env:VARKA_DB_URL = "jdbc:postgresql://localhost:5432/varka"
$env:VARKA_DB_USER = "varka"
$env:VARKA_DB_PASSWORD = "varka"
mvn -pl apps/backend -am spring-boot:run
```

## Full Stack

Build and run PostgreSQL plus the backend container:

```powershell
docker compose up --build
```

Health endpoints:

```text
http://127.0.0.1:8080/api/health
http://127.0.0.1:8080/actuator/health
```

If Windows proxy settings route `localhost` through a local proxy, use `127.0.0.1` or bypass the
proxy in your HTTP client.

## Migrations

Platform schema migrations live in:

```text
packages/java/platform-storage-postgres/src/main/resources/db/migration
```

Assistant schema migrations live in:

```text
packages/java/platform-assistant/src/main/resources/db/assistant-migration
```

Flyway runs automatically during Spring Boot startup from `classpath:db/migration` and
`classpath:db/assistant-migration` on the backend
classpath. The first platform schema is:

```text
V1__create_platform_schema.sql
```

MDE job metadata extensions are in `V5__mde_job_async_metadata.sql` under
`platform-storage-postgres`; project-member custom roles are in
`V9__project_member_custom_roles.sql`. Model transformation baselines and pending conflict
sessions are stored by `V32__model_synchronization_baselines_and_sessions.sql`. Assistant durable-turn tables are introduced by
`V14__assistant_baseline.sql` and currently refined through
`V29__idempotent_assistant_provider_call_audit.sql`. Assistant migrations V24-V29 add
continuation/provenance lineage, context caching, source blueprints, provider prompt audit, durable
workflow/private-work-item state, and idempotent provider-call keys.

Flyway versions are global across both migration locations because the backend loads both locations
into the same Flyway instance. Do not choose a version by looking at only one folder. For future
schema edits, do not modify an already-applied migration in a shared or production database. Add a
new migration with the helper script instead:

```powershell
python scripts/flyway-next-migration.py "add example table" --location platform
python scripts/flyway-next-migration.py "assistant example table" --location assistant
```

The script scans both platform and assistant migrations and creates the next global version. To
check newly added migration files:

```powershell
python scripts/check-flyway-migration-versions.py --changed-only
```

Good migration rules:

- Use forward-only migrations.
- Never guess migration numbers manually.
- Make constraints explicit with `NOT NULL`, `CHECK`, primary keys, foreign keys, and indexes.
- Add backfill steps before adding new `NOT NULL` constraints to existing populated tables.
- Keep application code compatible with the migration order.
- Test migrations against an empty database and an existing database.

During early local development, if you intentionally change `V1` before it is shared, reset the
local database volume:

```powershell
docker compose down -v
docker compose up -d postgres
```

## Inspecting The Database

Open `psql` in the running container:

```powershell
docker exec -it varka-postgres-1 psql -U varka -d varka
```

Useful checks:

```sql
select version, description, success from flyway_schema_history order by installed_rank;
select count(*) from users;
select count(*) from projects;
select id, project_id, level, revision from models order by updated_at desc;
```

## Tests And Verification

Compile and run the full Maven test suite:

```powershell
mvn test
```

Build the backend:

```powershell
mvn -pl apps/backend -am package -DskipTests
```

Basic live smoke test:

```powershell
curl.exe --noproxy "*" http://127.0.0.1:8080/api/health
```

For storage changes, verify at least:

- user registration and login
- project create, list, get, update, delete
- model create/update/export for JSON and XMI
- staged import cleanup
- artifact create/update/download through transformation or service-level tests
- project deletion cascades models, artifacts, jobs, members, and staged imports
