# Configuration

Backend configuration is defined in `apps/backend/src/main/resources/application.yml` and overridden
through environment variables. Copy the repository root `.env.example` file to `.env` for local
development defaults (never commit `.env`).

See also the contributor reference [environment-variables.md](../../../internal/environment-variables.md)
for field-by-field explanations of every `.env.example` key.

## Database

| Variable                   | Default                                    |
| -------------------------- | ------------------------------------------ |
| `MODLESS_DB_URL`           | `jdbc:postgresql://localhost:5432/modless` |
| `MODLESS_DB_USER`          | `modless`                                  |
| `MODLESS_DB_PASSWORD`      | `modless`                                  |
| `MODLESS_DB_MAX_POOL_SIZE` | `10`                                       |
| `MODLESS_DB_MIN_IDLE`      | `2`                                        |

## MDE Limits and Execution

| Variable                                   | Default    | Purpose                      |
| ------------------------------------------ | ---------- | ---------------------------- |
| `MODLESS_MDE_EXECUTION_TIMEOUT`            | `5m`       | Runner timeout               |
| `MODLESS_MDE_JOB_TIMEOUT`                  | `10m`      | Job timeout                  |
| `MODLESS_MDE_MAX_CAPTURED_OUTPUT_BYTES`    | `1048576`  | Captured stdout/stderr limit |
| `MODLESS_MDE_MAX_CONCURRENT_JOBS`          | `2`        | Concurrent job limit         |
| `MODLESS_MDE_QUEUE_CAPACITY`               | `32`       | Job queue capacity           |
| `MODLESS_MDE_MAX_MODEL_UPLOAD_BYTES`       | `20971520` | Model upload limit           |
| `MODLESS_MDE_MAX_GENERATED_FILES`          | `2000`     | Generated file-count limit   |
| `MODLESS_MDE_MAX_GENERATED_FILE_BYTES`     | `5242880`  | Per-file limit               |
| `MODLESS_MDE_MAX_GENERATED_ARTIFACT_BYTES` | `52428800` | Artifact limit               |
| `MODLESS_MDE_STAGED_IMPORT_TTL`            | `2h`       | Temporary import lifetime    |
| `MODLESS_MDE_IMPORT_CLEANUP_INTERVAL`      | `PT15M`    | Import cleanup interval      |

## Upload Limits

| Variable                        | Default   | Purpose                             |
| ------------------------------- | --------- | ----------------------------------- |
| `MODLESS_UPLOAD_ROOT`           | `uploads` | Backend upload directory            |
| `MODLESS_UPLOAD_MAX_FILE_BYTES` | `1048576` | Maximum uploaded file size          |
| `MODLESS_UPLOAD_MAX_TEXT_CHARS` | `120000`  | Maximum text characters for uploads |

Docker Compose overrides `MODLESS_UPLOAD_ROOT` inside the backend container to
`/app/uploads` via `MODLESS_CONTAINER_UPLOAD_ROOT`.

## Diagram Editor

The packaged modeling config defaults to `antv-g6`. The Docker Compose stack overrides
`MODLESS_DIAGRAM_RENDERER` to `glsp-sprotty` and starts the GLSP sidecar on port `8081`.

| Variable                   | Default in `.env.example`     | Purpose                        |
| -------------------------- | ----------------------------- | ------------------------------ |
| `GLSP_PORT`                | `8081`                        | Host port for the GLSP sidecar |
| `MODLESS_GLSP_LOG_LEVEL`   | `info`                        | GLSP sidecar log level         |
| `MODLESS_DIAGRAM_RENDERER` | `glsp-sprotty`                | Frontend diagram renderer      |
| `MODLESS_GLSP_SERVER_URL`  | `ws://127.0.0.1:8081/modless` | Browser WebSocket URL for GLSP |

## AI

| Variable                                | Default                          |
| --------------------------------------- | -------------------------------- |
| `MODLESS_AI_ENABLED`                    | `false` in backend configuration |
| `MODLESS_AI_PROVIDER`                   | `openai`                         |
| `MODLESS_AI_REQUEST_TIMEOUT`            | `5m`                             |
| `MODLESS_AI_TURN_TIMEOUT`               | `5m`                             |
| `MODLESS_AI_MAX_REPAIR_ATTEMPTS`        | `1`                              |
| `MODLESS_AI_MAX_TOOL_CALLS`             | `24`                             |
| `MODLESS_AI_MAX_AGENT_STEPS`            | `8`                              |
| `MODLESS_AI_MAX_TOOL_CALLS_PER_STEP`    | `4`                              |
| `MODLESS_AI_TOKEN_BUDGET`               | `16000`                          |
| `MODLESS_AI_MAX_PROMPT_TOKENS`          | `24000`                          |
| `MODLESS_AI_MAX_SOURCE_CHUNK_TOKENS`    | `4000`                           |
| `MODLESS_AI_MAX_SOURCE_CHUNKS_PER_TURN` | `24`                             |
| `MODLESS_AI_REQUIRE_IDEMPOTENCY_KEY`    | `true`                           |
| `MODLESS_AI_MAX_CONTEXT_SNIPPETS`       | `24`                             |
| `MODLESS_AI_RESERVED_SCHEMA_SNIPPETS`   | `10`                             |
| `MODLESS_AI_MAX_SNIPPET_CHARS`          | `2400`                           |
| `MODLESS_AI_MAX_SYSTEM_CHARS`           | `14000`                          |
| `MODLESS_AI_RATE_LIMIT_REQUESTS`        | `30`                             |
| `MODLESS_AI_RATE_LIMIT_WINDOW`          | `1m`                             |
| `MODLESS_AI_CIRCUIT_FAILURE_THRESHOLD`  | `3`                              |
| `MODLESS_AI_CIRCUIT_OPEN_DURATION`      | `1m`                             |
| `MODLESS_AI_PROVIDER_RETRY_ATTEMPTS`    | `2`                              |
| `MODLESS_AI_RETRY_BACKOFF`              | `250ms`                          |
| `MODLESS_AI_RECENT_MESSAGE_WINDOW`      | `24`                             |
| `MODLESS_AI_FALLBACK_PROVIDER`          | empty (used on HTTP 429 only)    |

The assistant exposes one modeling mutation protocol, `ModelDelta`. The backend compiles the delta
into internal patch operations, structurally validates it, and applies valid turns atomically.

Provider variables include `OPENAI_COMPATIBLE_BASE_URL`, `OPENAI_COMPATIBLE_API_KEY`,
`GEMINI_API_KEY`, and role-specific planner, responder, and summarizer model names.

Embedding variables default to `ONNX` with hash fallback for constrained local environments. Related
variables cover resources, cache behavior, GPU device, and hash fallback.

Dedicated AI proxy variables configure HTTP or SOCKS proxy behavior for provider calls only.

`MODLESS_AI_FALLBACK_PROVIDER` is consulted only when the primary provider returns HTTP 429.

## Observability

| Variable                      | Default                           |
| ----------------------------- | --------------------------------- |
| `MODLESS_METRICS_ENABLED`     | `true`                            |
| `MODLESS_TRACING_ENABLED`     | `false`                           |
| `MODLESS_TRACING_SAMPLE_RATE` | `0.1`                             |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318/v1/traces` |
| `OTEL_SERVICE_NAME`           | `modless-backend`                 |

## Compose Ports and Origins

| Variable                  | Default |
| ------------------------- | ------- |
| `POSTGRES_PORT`           | `5432`  |
| `BACKEND_PORT`            | `8080`  |
| `FRONTEND_PORT`           | `8082`  |
| `LANDING_PORT`            | `8083`  |
| `GLSP_PORT`               | `8081`  |
| `LOCALSTACK_GATEWAY_PORT` | `4566`  |

Optional Compose-only overrides:

- `MODLESS_ALLOWED_ORIGINS` — comma-separated CORS and WebSocket origins. When unset, Compose derives
  origins from `BACKEND_PORT`, `FRONTEND_PORT`, and `LANDING_PORT`.
- `MODLESS_CONTAINER_UPLOAD_ROOT` — upload directory inside the backend container (default
  `/app/uploads`).
- `FREELLMAPI_NETWORK` — external Docker network name joined by the backend for optional host AI
  proxy access (default `freellmapi_default`).

Dozzle is exposed on host port `9999` in the default Compose stack (not configurable through
`.env.example`).

Do not publish default database credentials or provider keys in production. Use secret management,
TLS termination, restricted network access, and environment-specific allowed origins.
