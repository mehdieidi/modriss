# MODRISS Backend

Spring Boot API for [`apps/frontend`](../frontend/README.md): auth, projects, models, MDE jobs,
artifacts, layout, and the AI assistant (REST + authenticated SSE).

## Run

```bash
# from repository root: PostgreSQL on localhost:5432
mvn -pl apps/backend -am spring-boot:run
```

- API: <http://127.0.0.1:8080>
- Health: <http://127.0.0.1:8080/api/health>
- Swagger UI for source runs with the private OpenAPI file present: <http://127.0.0.1:8080/swagger-ui.html>

## Configuration

Defaults in `src/main/resources/application.yml`; overrides via environment or `.env`.
Copy [`.env.example`](../../.env.example) at the repo root.

| Concern      | Variables                                                                                                              |
| ------------ | ---------------------------------------------------------------------------------------------------------------------- |
| Database     | `MODRISS_DB_URL`, `MODRISS_DB_USER`, `MODRISS_DB_PASSWORD`                                                             |
| AI assistant | `MODRISS_AI_ENABLED`, `MODRISS_AI_PROVIDER`, and provider keys                                                          |

Start only Postgres: `docker compose up -d postgres`

## Persistence

| Migrations                                  | Module                        |
| ------------------------------------------- | ----------------------------- |
| Platform schema (V1), MDE job metadata (V5) | `platform-storage-postgres`   |
| Assistant schema (V2–V4, V6–V8)             | `platform-modeling-assistant` |

## API surface

Controllers live in `src/main/java/.../backend/api/`. The local OpenAPI contract lives in the
private archive at `.idea/docs/api/openapi/openapi.yaml`.

Impact analysis routes are implemented under `/api/impact/**`. Planned admin workspace routes under
`/api/admin/**` return `501`.


# Model synchronization API

`CIM -> PIM` and `PIM -> PSM` jobs expose a synchronization object in the job's
`validationResult` field. Its status is `APPLIED`, `CONFLICTS`, or `BOOTSTRAP_REQUIRED`, with merge
counts and transport-safe conflict details. When status is `CONFLICTS`, use:

- `GET /api/synchronizations/{projectId}/{sessionId}` to reload the pending session;
- `POST /api/synchronizations/{projectId}/{sessionId}/resolutions` with `conflictId` and
  `KEEP_USER` or `TAKE_GENERATED`;
- `POST /api/synchronizations/{projectId}/{sessionId}/finalize` after resolving every conflict;
- `DELETE /api/synchronizations/{projectId}/{sessionId}` to cancel without changing Working/Base.

Generated baselines and pending sessions are infrastructure records, not DSML elements. A baseline
is the untouched raw ETL output, never the merged user-refined Working model.
