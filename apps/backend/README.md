# Modless Backend

Spring Boot API for [`apps/frontend`](../frontend/README.md): auth, projects, models, MDE jobs,
artifacts, layout, and the AI assistant (REST + SSE + WebSocket).

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

| Concern      | Variables                                                                                                               |
| ------------ | ----------------------------------------------------------------------------------------------------------------------- |
| Database     | `MODLESS_DB_URL`, `MODLESS_DB_USER`, `MODLESS_DB_PASSWORD`                                                              |
| AI assistant | `MODLESS_AI_ENABLED`, `MODLESS_AI_PROVIDER`, provider keys — see [assistant setup](../../docs/internal/ai/assistant.md) |

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

Planned routes under `/api/github/**`, `/api/impact/**`, and `/api/admin/**` return `501`.

## Docs

- [REST API](../../docs/api/rest-api.md)
- [WebSocket](../../docs/api/websocket-api.md)
- [Repository layout](../../docs/public-docs/docs/reference/repository-layout.md)
