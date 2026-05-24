# Modless Backend

Spring Boot backend for the plain HTML/CSS/JS frontend in `apps/frontend`.

## Run

From the repository root:

```powershell
mvn -pl apps/backend -am spring-boot:run
```

The API starts on `http://127.0.0.1:8080`. Swagger UI is available at:

```text
http://127.0.0.1:8080/swagger-ui.html
```

The frontend already points to `http://127.0.0.1:8080` through `apps/frontend/backend-config.js`.

## Storage

No database is used yet. Runtime data is stored under `storage/` by default:

- `storage/users`: registered users
- `storage/sessions`: token sessions
- `storage/projects/{projectId}/project.json`: project metadata and members
- `storage/projects/{projectId}/models/{cim|pim|psm}`: saved models
- `storage/projects/{projectId}/artifacts`: generated artifact records

Set `MODLESS_STORAGE_ROOT` to use another location.

## Modeling UI Metadata

The formal metamodel source of truth remains the Emfatic/EMF files under `mde/`.
The backend also ships frontend presentation metadata in:

- `packages/java/platform-core/src/main/resources/modeling/cim-ui-metadata.json`
- `packages/java/platform-core/src/main/resources/modeling/pim-ui-metadata.json`
- `packages/java/platform-core/src/main/resources/modeling/psm-ui-metadata.json`

These files provide frontend-only details such as labels, icons, colors, and
palette categories for each modeling element. They do not redefine the metamodel.

## Implemented

- Token-based auth: register, login, logout, current user, display-name update
- Project CRUD and file-backed project membership
- CIM/PIM/PSM CRUD
- JSON import/export
- Basic model validation API shape
- Modeling config API consumed by the frontend palette
- CIM to PIM, PIM to PSM, and PSM to artifact orchestration
- Artifact explorer APIs: list, load, read file, save file, download ZIP
- Layout API with deterministic fallback positioning
- OpenAPI docs at `/v3/api-docs` and Swagger-style UI at `/swagger-ui.html`
- Structured error responses and backend logging to `logs/backend.log`

## Still Missing

- Full EMF/XMI persistence bridge for the Epsilon ETL/EVL/EGX runners
- Production validation semantics backed by EVL execution for browser JSON models
- Real transformation mapping from frontend JSON models to formal MDE models
- Chatbot, GitHub integration, impact analysis, admin workspace editing, and WebSocket collaboration
- Database-backed repositories
