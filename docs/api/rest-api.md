# REST API

The Spring Boot backend exposes the frontend API under `/api`. A runtime OpenAPI summary is served
at `/v3/api-docs`, the browser UI is available at `/swagger-ui.html`, and the full checked-in
contract lives at `docs/api/openapi/openapi.yaml`.

## Base URL

Local development defaults to:

```text
http://127.0.0.1:8080
```

All protected endpoints require the session token returned by login or registration:

```http
X-Auth-Token: <token>
```

`/api/health`, `/api/auth/register`, `/api/auth/login`, `/api/modeling/config`, and `/api/layout`
do not require this header.

## Errors

Errors are returned as JSON:

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

Common status codes are `400` for invalid input, `401` for missing or expired auth, `403` for
project permission failures, `404` for missing records, `409` for conflicts such as stale revisions,
`413` for model/artifact size limits, `501` for planned endpoints, and `500` for unexpected errors.

## Auth

| Method | Path                 | Body                               | Response       |
| ------ | -------------------- | ---------------------------------- | -------------- |
| `POST` | `/api/auth/register` | `email`, `password`, `displayName` | `AuthResponse` |
| `POST` | `/api/auth/login`    | `email`, `password`                | `AuthResponse` |
| `GET`  | `/api/auth/me`       | none                               | `UserDto`      |
| `PUT`  | `/api/auth/me`       | `displayName`                      | `UserDto`      |
| `POST` | `/api/auth/logout`   | none                               | empty response |

Passwords must be at least 8 characters. `displayName` is required and has a maximum length of 80
characters.

## Projects

| Method   | Path                                  | Query/Body                              | Response          |
| -------- | ------------------------------------- | --------------------------------------- | ----------------- |
| `GET`    | `/api/projects`                       | none                                    | `ProjectRecord[]` |
| `POST`   | `/api/projects`                       | `name`, `description`                   | `ProjectRecord`   |
| `GET`    | `/api/projects/{id}`                  | none                                    | `ProjectRecord`   |
| `PUT`    | `/api/projects/{id}`                  | `name`, `description`, `activeModelIds` | `ProjectRecord`   |
| `DELETE` | `/api/projects/{id}`                  | none                                    | empty response    |
| `GET`    | `/api/projects/{id}/download`         | none                                    | ZIP download      |
| `GET`    | `/api/projects/{id}/members`          | none                                    | `ProjectMember[]` |
| `POST`   | `/api/projects/{id}/invite`           | `email`, `role`                         | `ProjectMember`   |
| `PUT`    | `/api/projects/{id}/members/{userId}` | `role`                                  | `ProjectMember`   |
| `DELETE` | `/api/projects/{id}/members/{userId}` | none                                    | empty response    |

`role` is a custom display label. Only project owners can manage membership or delete projects.
Project members can update project-owned models and artifacts.

## Models

Model-level routes use `{level}` with one of `cim`, `pim`, or `psm`.

| Method   | Path                              | Query/Body                               | Response           |
| -------- | --------------------------------- | ---------------------------------------- | ------------------ |
| `GET`    | `/api/{level}`                    | optional `projectId` query               | `ModelSummary[]`   |
| `POST`   | `/api/{level}`                    | `name`, `projectId`, `model`             | `ModelSummary`     |
| `GET`    | `/api/{level}/{id}`               | none                                     | `ModelRecord`      |
| `PUT`    | `/api/{level}/{id}`               | `name`, `model`, `expectedRevision`      | `ModelSummary`     |
| `PATCH`  | `/api/{level}/{id}`               | `name`, `operations`, `expectedRevision` | `ModelSummary`     |
| `DELETE` | `/api/{level}/{id}`               | none                                     | empty response     |
| `POST`   | `/api/{level}/validate`           | `model`                                  | `ValidationResult` |
| `POST`   | `/api/{level}/{id}/validate`      | none                                     | `ValidationResult` |
| `POST`   | `/api/{level}/{id}/validate/jobs` | optional `expectedRevision`              | `202 JobResponse`  |
| `POST`   | `/api/{level}/export`             | `name`, `model`, `format`                | file download      |
| `POST`   | `/api/{level}/{id}/export`        | `name`, `format`                         | file download      |
| `POST`   | `/api/{level}/import`             | multipart `projectId`, `format`, `file`  | `ImportResult`     |

Updates and patches require `expectedRevision`; the server returns `409` if the stored revision has
changed. Patch operations support JSON Pointer paths and the `add`, `replace`, and `remove` ops.
Root model replacement is not supported by patch. Import/export formats are `json` and `xmi`; the
default is `json`. The default upload limit is 20 MiB unless configured otherwise.

## Transformations

| Method | Path                                    | Body                                | Response          |
| ------ | --------------------------------------- | ----------------------------------- | ----------------- |
| `POST` | `/api/transformations/cim-to-pim`       | `sourceModelId`, `expectedRevision` | `202 JobResponse` |
| `POST` | `/api/transformations/pim-to-psm`       | `sourceModelId`, `expectedRevision` | `202 JobResponse` |
| `POST` | `/api/transformations/psm-to-artifact`  | `sourceModelId`, `expectedRevision` | `202 JobResponse` |
| `GET`  | `/api/transformations/jobs/{id}`        | none                                | `MdeJobRecord`    |
| `POST` | `/api/transformations/jobs/{id}/cancel` | none                                | `MdeJobRecord`    |

Transformation and stored-validation submissions are asynchronous. Successful submissions return
`202 Accepted`, a `Location: /api/transformations/jobs/{id}` header, and a compact job body. Clients
may send `Idempotency-Key`; replaying the same request returns the original job, while reusing the
key for a different request returns `409`. Job records expose status, diagnostics, result model or
artifact ids, validation results, and phase timings.

## Artifacts

| Method | Path                          | Query/Body                 | Response                  |
| ------ | ----------------------------- | -------------------------- | ------------------------- |
| `GET`  | `/api/artifact`               | required `projectId` query | `ArtifactRecord[]`        |
| `GET`  | `/api/artifact/{id}`          | none                       | `ArtifactRecord`          |
| `GET`  | `/api/artifact/{id}/file`     | required `path` query      | `text/plain` file content |
| `PUT`  | `/api/artifact/{id}/files`    | `path`, `content`          | `ArtifactRecord`          |
| `GET`  | `/api/artifact/{id}/download` | none                       | ZIP download              |

Artifact file paths must stay inside the artifact and cannot be absolute or directory traversal
paths.

## Modeling And Layout

| Method | Path                                                                                 | Body            | Response                         |
| ------ | ------------------------------------------------------------------------------------ | --------------- | -------------------------------- |
| `GET`  | `/api/modeling/config`                                                               | none            | modeling palette and UI metadata |
| `POST` | `/api/layout`                                                                        | `LayoutRequest` | `LayoutResponse`                 |
| `POST` | `/api/{level}/{modelId}/views/{viewId}/layout?force=false&strategy=SPACIOUS_LAYERED` | none            | Persisted lazy view layout       |
| `GET`  | `/api/health`                                                                        | none            | health object                    |

`LayoutRequest` contains `nodes`, `edges`, optional `fixedNodeIds`, optional profile/options, and
returns node positions, routed edge sections, bend points, and warnings.

## Assistant

Assistant commands use REST. Realtime progress uses SSE or the receive-only assistant WebSocket.
See [websocket-api.md](websocket-api.md) and
[public realtime reference](../public-docs/docs/reference/realtime-api.md).

| Method   | Path                                                            | Purpose                          |
| -------- | --------------------------------------------------------------- | -------------------------------- |
| `POST`   | `/api/chatbot/sessions`                                         | Create or resume a session       |
| `GET`    | `/api/chatbot/conversations`                                    | List recent conversations        |
| `POST`   | `/api/chatbot/sessions/{sessionId}/messages`                    | Submit a user message            |
| `POST`   | `/api/chatbot/sessions/{sessionId}/attachments`                 | Upload a text attachment         |
| `GET`    | `/api/chatbot/sessions/{sessionId}/thread`                      | Load thread history              |
| `GET`    | `/api/chatbot/sessions/{sessionId}/events`                      | Open SSE event stream            |
| `DELETE` | `/api/chatbot/sessions/{sessionId}`                             | Clear session memory             |
| `POST`   | `/api/chatbot/catalogs/reindex`                                 | Reindex metamodel/EVL catalogs   |
| `GET`    | `/api/chatbot/sessions/{sessionId}/proposals/{proposalId}`      | Get proposal details             |
| `POST`   | `/api/chatbot/sessions/{sessionId}/proposals/{proposalId}/undo` | Undo an applied proposal         |
| `POST`   | `/api/chatbot/sessions/{sessionId}/choices`                     | Answer structured clarifications |

WebSocket stream: `ws://<host>/ws/chatbot/sessions/{sessionId}` (receive-only).

## Impact Analysis

| Method | Path                                                | Purpose                                    |
| ------ | --------------------------------------------------- | ------------------------------------------ |
| `GET`  | `/api/impact/{level}/{modelId}/element/{elementId}` | Trace one CIM/PIM/PSM element up and down  |
| `GET`  | `/api/impact/artifact/{artifactId}`                 | Return generated artifact upstream lineage |

Element impact responses include `focalElement`, `upstream`, `downstream`, and
`connectedElements`. Artifact impact responses include artifact metadata and ordered ancestors.

## Planned Endpoints

Requests under `/api/admin/**` currently return `501` with the message
`This feature is planned for a future backend iteration.`

## Shared Shapes

Core response records:

- `UserDto`: `id`, `email`, `displayName`
- `AuthResponse`: `token`, `user`
- `ProjectRecord`: `id`, `name`, `description`, `ownerUserId`, `activeModelIds`, `members`,
  `createdAt`, `updatedAt`
- `ProjectMember`: `userId`, `email`, `displayName`, `role`, `addedAt`
- `ModelSummary`: `id`, `projectId`, `level`, `name`, `revision`, `metamodelVersion`,
  `migrationState`, `createdAt`, `updatedAt`
- `ModelRecord`: `id`, `projectId`, `level`, `name`, `modelJson`, `metamodelVersion`,
  `metamodelHash`, `revision`, `sourceXmiHash`, `migrationState`, `createdAt`, `updatedAt`
- `ValidationResult`: `valid`, `issues`
- `ValidationIssue`: `severity`, `constraint`, `issueClass`, `message`, `guidance`, `elementId`,
  `elementName`
- `ImportResult`: `name`, `modelJson`, `issues`
- `ArtifactRecord`: `id`, `projectId`, `name`, `modelJson`, `files`, `createdAt`, `updatedAt`
- `MdeJobRecord`: `id`, `projectId`, `userId`, `sourceModelId`, `sourceLevel`, `sourceRevision`,
  `sourceModelHash`, `operation`, `status`, `progressPercent`, `resultModelId`,
  `resultArtifactId`, `diagnostics`, `createdAt`, `startedAt`, `finishedAt`
