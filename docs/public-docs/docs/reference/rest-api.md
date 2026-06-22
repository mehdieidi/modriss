# REST API

The backend API defaults to `http://127.0.0.1:8080`. Most application endpoints are under `/api`.

Interactive and machine-readable contracts:

- Swagger-style browser: `/swagger-ui.html`
- Generated OpenAPI JSON: `/v3/api-docs`
- Checked-in contract: `docs/api/openapi/openapi.yaml`

## Authentication

Registration and login return a session token. Send it on protected endpoints:

```http
X-Auth-Token: <token>
```

Public endpoints include health, registration, login, modeling configuration, layout, generated API
documentation, and Swagger UI.

## Error Shape

```json
{
  "message": "Request validation failed.",
  "status": 400,
  "timestamp": "2026-06-05T12:00:00Z",
  "issues": ["field: detail"],
  "errorId": "request-correlation-id"
}
```

`errorId` matches the request correlation ID when available; otherwise the server generates a UUID
for log lookup.

Common statuses are `400`, `401`, `403`, `404`, `409`, `413`, `500`, and `501`.

## Endpoint Groups

### Authentication

| Method | Path                 | Purpose                      |
| ------ | -------------------- | ---------------------------- |
| `POST` | `/api/auth/register` | Register and start a session |
| `POST` | `/api/auth/login`    | Start a session              |
| `GET`  | `/api/auth/me`       | Get current user             |
| `PUT`  | `/api/auth/me`       | Update display name          |
| `POST` | `/api/auth/logout`   | End current session          |

### Projects

`/api/projects` supports list, create, get, update, delete, ZIP download, member list, invitation,
and member revocation. Member roles are `OWNER`, `EDITOR`, and `VIEWER`.

### Models

Replace `{level}` with `cim`, `pim`, or `psm`.

| Method                          | Path                              | Purpose                         |
| ------------------------------- | --------------------------------- | ------------------------------- |
| `GET`, `POST`                   | `/api/{level}`                    | List or create models           |
| `GET`, `PUT`, `PATCH`, `DELETE` | `/api/{level}/{id}`               | Read, replace, patch, or delete |
| `POST`                          | `/api/{level}/validate`           | Validate an unsaved model       |
| `POST`                          | `/api/{level}/{id}/validate`      | Validate a saved model          |
| `POST`                          | `/api/{level}/{id}/validate/jobs` | Submit saved-model validation   |
| `POST`                          | `/api/{level}/export`             | Export an unsaved model         |
| `POST`                          | `/api/{level}/{id}/export`        | Export a saved model            |
| `POST`                          | `/api/{level}/import`             | Import multipart JSON or XMI    |

Updates and transformations should include `expectedRevision`. Patch operations use JSON Pointer
paths and support `add`, `replace`, and `remove`; root replacement is not supported by patch.

### Transformations and Jobs

| Method | Path                                    |
| ------ | --------------------------------------- |
| `POST` | `/api/transformations/cim-to-pim`       |
| `POST` | `/api/transformations/pim-to-psm`       |
| `POST` | `/api/transformations/psm-to-artifact`  |
| `GET`  | `/api/transformations/jobs/{id}`        |
| `POST` | `/api/transformations/jobs/{id}/cancel` |

Transformation routes and saved-model validation job routes return `202 Accepted` with a
`Location` header pointing to `/api/transformations/jobs/{id}`. Send `Idempotency-Key` on submission
to safely retry the same request. Job records expose status, diagnostics, result ids, validation
results, and phase timings.

### Artifacts

`/api/artifact` supports project-scoped listing, artifact retrieval, file reads, file updates, and
ZIP download. File paths must be relative and cannot escape the artifact root.

### Modeling and Layout

- `GET /api/modeling/config`
- `POST /api/layout`
- `POST /api/{level}/{modelId}/views/{viewId}/layout`
- `GET /api/health`

### Assistant

Assistant REST routes create and clear sessions, list conversations, load thread history, submit
messages and choices, open SSE streams, reindex catalogs, and get, approve, reject, or undo
proposals. See [Realtime Assistant API](realtime-api.md).

### Planned Routes

Requests under `/api/github/**`, `/api/impact/**`, and `/api/admin/**` currently return HTTP `501`.
