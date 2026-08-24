# Varka Backend

Spring Boot API for [`apps/frontend`](../frontend/README.md): auth, projects, models, MDE jobs,
artifacts, layout, and the AI assistant (REST + authenticated SSE).

## Run

```bash
# from repository root — PostgreSQL on localhost:5432
mvn -pl apps/backend -am spring-boot:run
```

- API: <http://127.0.0.1:8080>
- Health: <http://127.0.0.1:8080/api/health>
- Swagger UI: <http://127.0.0.1:8080/swagger-ui.html>

## Configuration

Defaults in `src/main/resources/application.yml`; overrides via environment or `.env`.
Copy [`.env.example`](../../.env.example) at the repo root.

| Concern      | Variables                                                                                                           |
| ------------ | ------------------------------------------------------------------------------------------------------------------- |
| Database     | `VARKA_DB_URL`, `VARKA_DB_USER`, `VARKA_DB_PASSWORD`                                                                |
| AI assistant | `VARKA_AI_ENABLED`, `VARKA_AI_PROVIDER`, provider keys — see [assistant setup](../../docs/internal/ai/assistant.md) |

Start only Postgres: `docker compose up -d postgres`

## Persistence

| Migrations                                  | Module                      |
| ------------------------------------------- | --------------------------- |
| Platform schema (V1), MDE job metadata (V5) | `platform-storage-postgres` |
| Assistant schema (V2–V4, V6–V8)             | `platform-assistant`        |

Details: [postgres-storage](../../docs/internal/operations/postgres-storage.md) ·
[data model](../../docs/public-docs/docs/reference/data-storage.md)

## API surface

Controllers live in `src/main/java/.../backend/api/`. Checked-in contract:
[`docs/api/openapi/openapi.yaml`](../../docs/api/openapi/openapi.yaml)

Impact analysis routes are implemented under `/api/impact/**`. Planned admin workspace routes under
`/api/admin/**` return `501`.

## Docs

- [REST API](../../docs/api/rest-api.md)
- [Repository layout](../../docs/public-docs/docs/reference/repository-layout.md)

# Model synchronization API

`CIM -> PIM` and `PIM -> AWS PSM` jobs expose a synchronization object in the job's
`validationResult` field. Its status is `APPLIED`, `CONFLICTS`, or `BOOTSTRAP_REQUIRED`, with merge
counts and transport-safe conflict details. When status is `CONFLICTS`, use:

- `GET /api/synchronizations/{projectId}/{sessionId}` to reload the pending session;
- `POST /api/synchronizations/{projectId}/{sessionId}/resolutions` with `conflictId` and
  `KEEP_USER` or `TAKE_GENERATED`;
- `POST /api/synchronizations/{projectId}/{sessionId}/finalize` after resolving every conflict;
- `DELETE /api/synchronizations/{projectId}/{sessionId}` to cancel without changing Working/Base.

Generated baselines and pending sessions are infrastructure records, not DSML elements. A baseline
is the untouched raw ETL output, never the merged user-refined Working model.
