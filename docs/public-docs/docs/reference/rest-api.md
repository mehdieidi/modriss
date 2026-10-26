# REST API

Custom cloud platform APIs are scoped to `/api/projects/{projectId}/psm` and use the normal
`X-Auth-Token` project membership checks. See the [custom platform guide](../guides/custom-cloud-platforms.md).

| Method             | Relative path                      | Purpose                                                                                                                                                                                 |
| ------------------ | ---------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GET / POST         | `/packages`                        | List platform summaries / upload Ecore with optional ETL, EGX/EGL and EVL ZIPs and entry paths. Ecore alone enables the editor.                                                         |
| GET                | `/packages/{id}/definition`        | Read the Ecore schema and optional visual notation.                                                                                                                                     |
| PUT                | `/packages/{id}/notation`          | Save palette groups, element notation and views for the project provider.                                                                                                               |
| POST               | `/packages/{packageId}/model`      | Create the single provider model from semantic workbench JSON.                                                                                                                          |
| PUT                | `/packages/{packageId}/model/{id}` | Structurally validate and save semantic JSON and canvas state with `expectedRevision`.                                                                                                  |
| POST               | `/packages/{packageId}/import`     | Convert structurally valid XMI into semantic workbench JSON without persisting or running EVL.                                                                                          |
| GET                | `/packages/{id}/{asset}`           | Download `platform.ecore`, `transformation.zip`, `generation.zip` or `validation.zip`.                                                                                                  |
| GET / POST         | `/branches`                        | List provider models / generate with `packageId`, `sourcePimId`, `expectedRevision` and `name`. Regeneration updates the existing model and also requires its `expectedTargetRevision`. |
| GET                | `/branches/{id}/editor`            | Read the normal workbench record, including semantic JSON, revision and saved canvas state.                                                                                             |
| GET / PUT / DELETE | `/branches/{id}`                   | Read / structurally validate and save / delete a branch. PUT includes `name`, `xmi` and `expectedRevision`; DELETE requires `expectedRevision` as a query parameter.                    |
| GET                | `/branches/{id}/schema`            | Inspect the branch's Ecore classes and features.                                                                                                                                        |
| POST               | `/branches/{id}/validate`          | Explicit structural and optional EVL validation.                                                                                                                                        |
| GET / POST         | `/branches/{id}/artifacts`         | List branch artifact history / generate with `expectedRevision`.                                                                                                                        |
| GET                | `/branches/{id}/model.xmi`         | Download the branch model.                                                                                                                                                              |

Extension operations are synchronous and use isolated containers. Invalid packages or structurally
invalid output return 422. Stale revisions return 409; concurrent execution capacity returns 429.
The native `/api/transformations/*` asynchronous AWS endpoints remain available.

The backend API defaults to `http://127.0.0.1:8080`. Most application endpoints are under `/api`.

Interactive and machine-readable contracts:

- Swagger-style browser: `/swagger-ui.html`
- Runtime OpenAPI summary: `/v3/api-docs`
- Checked-in contract: `docs/api/openapi/openapi.yaml`

## Authentication

Login and guest access return a session token. Registration sends an email verification link and
does not create a session until the address is verified. Send a session token on protected endpoints:

```http
X-Auth-Token: <token>
```

Public endpoints include health, registration, email verification, login, guest-session creation,
modeling configuration, layout, generated API documentation, and Swagger UI.

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

Common statuses are `400`, `401`, `403`, `404`, `409`, `413`, `500`, `501`, and `503`.

## Endpoint Groups

### Authentication

| Method | Path                                    | Purpose                                                             |
| ------ | --------------------------------------- | ------------------------------------------------------------------- |
| `POST` | `/api/auth/register`                    | Register and email an address verification link                     |
| `POST` | `/api/auth/email-verification/request`  | Resend an address verification link                                 |
| `POST` | `/api/auth/email-verification/complete` | Verify an address from its emailed link                             |
| `POST` | `/api/auth/login`                       | Start a session after email verification                            |
| `POST` | `/api/auth/password-reset/request`      | Email a password reset link                                         |
| `POST` | `/api/auth/password-reset/complete`     | Set a password from a reset link                                    |
| `POST` | `/api/auth/guest`                       | Start an isolated guest session (five assistant prompts by default) |
| `GET`  | `/api/auth/me`                          | Get current user                                                    |
| `PUT`  | `/api/auth/me`                          | Update display name                                                 |
| `POST` | `/api/auth/logout`                      | End current session                                                 |

Guest accounts are normal authenticated accounts with an anonymous display name; their projects,
models, uploads, and assistant history remain isolated by the same ownership checks as registered
users. The server atomically counts accepted assistant prompts and returns `403` once the guest
allowance is exhausted. The limit defaults to five and can be configured with `MODRISS_GUEST_PROMPT_LIMIT`.
Verification links expire after 24 hours; password reset links expire after 30 minutes. Both can be
used once. SMTP settings must be configured before the editor can send account emails.

### Projects

`/api/projects` supports list, create, get, update, delete, ZIP download, member list, invitation,
member role updates, and member removal. The project creator keeps the reserved `OWNER` role. All
other members use custom role labels chosen by the project owner (for example `Architect` or
`Reviewer`).

### Models

Replace `{level}` with `cim`, `pim`, or `psm`.

| Method                          | Path                                    | Purpose                                |
| ------------------------------- | --------------------------------------- | -------------------------------------- |
| `GET`, `POST`                   | `/api/{level}`                          | List or create models                  |
| `GET`, `PUT`, `PATCH`, `DELETE` | `/api/{level}/{id}`                     | Read, replace, patch, or delete        |
| `GET`                           | `/api/{level}/{id}/editing-lock`        | Read the assistant editing lock        |
| `GET`                           | `/api/{level}/{id}/views/{viewId}`      | Read a materialized model view         |
| `POST`                          | `/api/{level}/validate`                 | Validate an unsaved model              |
| `POST`                          | `/api/{level}/validate/structural`      | Structurally validate an unsaved model |
| `POST`                          | `/api/{level}/{id}/validate`            | Validate a saved model                 |
| `POST`                          | `/api/{level}/{id}/validate/structural` | Structurally validate a saved model    |
| `POST`                          | `/api/{level}/{id}/validate/jobs`       | Submit saved-model validation          |
| `POST`                          | `/api/{level}/export`                   | Export an unsaved model                |
| `POST`                          | `/api/{level}/{id}/export`              | Export a saved model                   |
| `POST`                          | `/api/{level}/import`                   | Import multipart JSON or XMI           |

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

### Model Synchronization

CIM→PIM and PIM→PSM use a durable three-way merge between raw Base, downstream Working, and fresh
NewGenerated. Real conflicts create a pending session without changing canonical Working or Base.

| Method   | Path                                                              | Purpose                            |
| -------- | ----------------------------------------------------------------- | ---------------------------------- |
| `GET`    | `/api/synchronizations/{projectId}/{sessionId}`                   | Read conflicts and decisions       |
| `POST`   | `/api/synchronizations/{projectId}/{sessionId}/resolutions`       | Save one decision                  |
| `POST`   | `/api/synchronizations/{projectId}/{sessionId}/resolutions/batch` | Save multiple decisions atomically |
| `POST`   | `/api/synchronizations/{projectId}/{sessionId}/finalize`          | Recompare, validate, and commit    |
| `DELETE` | `/api/synchronizations/{projectId}/{sessionId}`                   | Cancel the pending session         |

Finalization rejects incomplete or stale sessions with `409`; an accepted commit advances Base to
raw generated XMI, never to merged Working.

### Artifacts

`/api/artifact` supports project-scoped listing, artifact retrieval, file reads, file updates, and
ZIP download. File paths must be relative and cannot escape the artifact root.

### Modeling and Layout

- `GET /api/modeling/config`
- `GET /api/modeling/process/{level}` for `cim`, `pim`, `psm`, or `end-to-end`
- `GET /api/modeling/process/{level}/coverage` for `cim`, `pim`, or `psm`
- `POST /api/layout`
- `POST /api/{level}/{modelId}/views/{viewId}/layout`
- `GET /api/health`

### Impact Analysis

- `GET /api/impact/{level}/{modelId}/element/{elementId}` returns the selected element,
  unique upstream transformation sources, downstream elements and individual generated files,
  and `traceEdges` with model-scoped endpoints and transformation rules. Ordinary model
  relationships do not establish impact; `connectedElements` remains empty for compatibility.
- `GET /api/impact/artifact/{artifactId}?filePath={path}` returns all recorded contributors to
  that file and their transitive transformation sources. Omit `filePath` to inspect the whole
  bundle. Model ancestors are metadata and do not substitute for missing element traces.

The editor groups results by model and artifact bundle, counts each element/file once, and
exposes each result's immediate trace links. Filter by element, path, type, or rule; use Locate
to inspect an element (including elements outside the active view) or Open file to inspect a
generated file and its sources. Empty results mean no recorded traces were found. Analysis
uses saved models and generation provenance, so save edits and rerun transformations or code
generation when those records need updating.

New generation reports record exact PSM `sourceElementIds` alongside `sourceStableIds`, and
artifact metadata carries `traceabilityVersion: 2` plus per-file `generationRules`. Inherited
stable keys can be shared by several resources. Ambiguous legacy file attributions are excluded
from impact results and explained in `notices`; regenerate the affected bundle to obtain exact
provenance. Artifact summaries supply file paths and metadata without downloading file contents.

### Assistant

Assistant REST routes create and clear sessions, list conversations, load thread history, submit
idempotent durable turns, upload text attachments, replay authenticated SSE events, continue
partial turns, confirm destructive batches, rebase non-overlapping revision drift, roll back a
specific checkpoint, record binary feedback, and undo saved checkpoints. See
[Realtime Assistant API](realtime-api.md).

Message requests include an `idempotencyKey`, user `message`, optional `modelId`, `revision` or
`expectedRevision`, current `activeView`, selected element IDs, and uploaded `attachmentIds`.
Uploaded `.md`, `.txt`, and `.json` attachments can drive source-backed modeling. Turn status
responses include checkpoint counts, saved element counts, source coverage, remaining work,
provider-call/token counters, repair attempts, provenance, continuation turns, and current
workflow/work-item fields.

The chatbot supports CIM and PIM; PSM session creation is rejected with HTTP 422. Requests do not
include an agent/conceptual mode, provider, or internal strategy. The backend automatically selects
a bounded conceptual generator, inspect/contract action loop, or enforced read-only answer path
while retaining the same durable turn controls.

`coveragePercent=100` means enforced source/mandatory-obligation accounting passed for the active
workflow; it is separate from EVL validity. Call the model-validation endpoint after a checkpoint
when EVL feedback is required.

Workflow fields in a turn response report resumable backend state; clients must not treat them as a
strategy-selection contract. A checkpoint confirms structural Ecore/EMF conformance and atomic
persistence, not EVL semantic validity.

### Planned Routes

Requests under `/api/admin/**` currently return HTTP `501`.
