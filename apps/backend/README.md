# Modless Backend

Spring Boot backend for the plain HTML/CSS/JS frontend in `apps/frontend`.

## Run

From the repository root, run with PostgreSQL available on `localhost:5432`:

```powershell
mvn -pl apps/backend -am spring-boot:run
```

The API starts on `http://127.0.0.1:8080`. Swagger UI is available at:

```text
http://127.0.0.1:8080/swagger-ui.html
```

The frontend already points to `http://127.0.0.1:8080` through `apps/frontend/backend-config.js`.

## PostgreSQL

The backend now persists application state in PostgreSQL through Spring JDBC and Flyway.
The schema is versioned in:

-

`packages/java/platform-storage-postgres/src/main/resources/db/migration/V1__create_platform_schema.sql`

Detailed operations and migration guidance lives in:

- `docs/postgres-storage.md`

Tables:

- `users`, `auth_sessions`
- `projects`, `project_members`, `project_active_models`
- `models`
- `staged_imports`, `staged_import_payloads`
- `artifacts`, `artifact_files`
- `mde_jobs`, `mde_job_diagnostics`

Default connection settings come from `application.yml`:

- `MODLESS_DB_URL=jdbc:postgresql://localhost:5432/modless`
- `MODLESS_DB_USER=modless`
- `MODLESS_DB_PASSWORD=modless`

For local development, start the database with:

```powershell
docker compose up -d postgres
```

Or bring up the full stack:

```powershell
docker compose up --build
```

## Modeling UI Metadata

The formal metamodel source of truth remains the Emfatic/EMF files under `mde/`.
The backend also ships frontend presentation metadata in:

- `packages/java/platform-modeling/src/main/resources/modeling/cim-ui-metadata.json`
- `packages/java/platform-modeling/src/main/resources/modeling/pim-ui-metadata.json`
- `packages/java/platform-modeling/src/main/resources/modeling/psm-ui-metadata.json`

These files provide frontend-only details such as labels, icons, colors, and
palette categories for each modeling element. They do not redefine the metamodel.

## Implemented

- Token-based auth: register, login, logout, current user, display-name update
- Project CRUD and PostgreSQL-backed project membership
- CIM/PIM/PSM CRUD
- JSON import/export
- Basic model validation API shape
- Modeling config API consumed by the frontend palette
- CIM to PIM, PIM to PSM, and PSM to artifact orchestration
- Artifact explorer APIs: list, load, read file, save file, download ZIP
- Layout API with deterministic fallback positioning
- OpenAPI docs at `/v3/api-docs` and Swagger-style UI at `/swagger-ui.html`
- Structured error responses and request-correlated backend logging
- PostgreSQL persistence with Flyway migrations and Docker Compose runtime

## Still Missing

- Full EMF/XMI persistence bridge for the Epsilon ETL/EVL/EGX runners
- Production validation semantics backed by EVL execution for browser JSON models
- Real transformation mapping from frontend JSON models to formal MDE models
- Chatbot, GitHub integration, impact analysis, admin workspace editing, and WebSocket collaboration
- Full production hardening of the remaining MDE execution workflows
