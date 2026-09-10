# platform-storage-postgres

PostgreSQL adapter for `PlatformStore`. Implements `PostgresPlatformStore` in
`platform.storage.postgres` with JDBC mapping for platform domain types.

Flyway migrations in this module cover **platform** schema only:

- `V1__create_platform_schema.sql`. Users, projects, models, artifacts, jobs, …
- `V5__mde_job_async_metadata.sql`, job validation results and timing metadata

Assistant tables are migrated from `platform-assistant` (V2–V4, V6–V8). The backend applies both
locations at startup.

Feature modules depend on `platform-storage-api`; this adapter is wired in `apps/backend`.
