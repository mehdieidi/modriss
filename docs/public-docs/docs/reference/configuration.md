# Configuration

Backend configuration is defined in `apps/backend/src/main/resources/application.yml` and overridden
through environment variables. Copy [`.env.example`](../../../../.env.example) to `.env` for local
development defaults (never commit `.env`).

## Database

| Variable                   | Default                                    |
| -------------------------- | ------------------------------------------ |
| `MODLESS_DB_URL`           | `jdbc:postgresql://localhost:5432/modless` |
| `MODLESS_DB_USER`          | `modless`                                  |
| `MODLESS_DB_PASSWORD`      | `modless`                                  |
| `MODLESS_DB_MAX_POOL_SIZE` | `10`                                       |
| `MODLESS_DB_MIN_IDLE`      | `2`                                        |

## MDE Limits and Execution

| Variable                                   | Default    | Purpose                    |
| ------------------------------------------ | ---------- | -------------------------- |
| `MODLESS_MDE_EXECUTION_TIMEOUT`            | `5m`       | Runner timeout             |
| `MODLESS_MDE_JOB_TIMEOUT`                  | `10m`      | Job timeout                |
| `MODLESS_MDE_MAX_CONCURRENT_JOBS`          | `2`        | Concurrent job limit       |
| `MODLESS_MDE_QUEUE_CAPACITY`               | `32`       | Job queue capacity         |
| `MODLESS_MDE_MAX_MODEL_UPLOAD_BYTES`       | `20971520` | Model upload limit         |
| `MODLESS_MDE_MAX_GENERATED_FILES`          | `2000`     | Generated file-count limit |
| `MODLESS_MDE_MAX_GENERATED_FILE_BYTES`     | `5242880`  | Per-file limit             |
| `MODLESS_MDE_MAX_GENERATED_ARTIFACT_BYTES` | `52428800` | Artifact limit             |
| `MODLESS_MDE_STAGED_IMPORT_TTL`            | `2h`       | Temporary import lifetime  |

## AI

| Variable                         | Default                          |
| -------------------------------- | -------------------------------- |
| `MODLESS_AI_ENABLED`             | `false` in backend configuration |
| `MODLESS_AI_MODE`                | `GUARDED_APPLY`                  |
| `MODLESS_AI_PROVIDER`            | `openai`                         |
| `MODLESS_AI_REQUEST_TIMEOUT`     | `5m`                             |
| `MODLESS_AI_MAX_TOOL_CALLS`      | `96`                             |
| `MODLESS_AI_TOKEN_BUDGET`        | `6000`                           |
| `MODLESS_AI_RATE_LIMIT_REQUESTS` | `30`                             |
| `MODLESS_AI_RATE_LIMIT_WINDOW`   | `1m`                             |

Provider variables include `OPENAI_COMPATIBLE_BASE_URL`, `OPENAI_COMPATIBLE_API_KEY`,
`OPENAI_API_KEY`, `GEMINI_API_KEY`, `GOOGLE_API_KEY`, and role-specific planner, responder, and
summarizer model names.

Embedding variables select `ONNX` or `HASH`, resources, cache behavior, GPU device, and hash
fallback. Dedicated AI proxy variables configure HTTP or SOCKS proxy behavior.

## Compose Ports

| Variable                  | Default |
| ------------------------- | ------- |
| `POSTGRES_PORT`           | `5432`  |
| `BACKEND_PORT`            | `8080`  |
| `FRONTEND_PORT`           | `8082`  |
| `LANDING_PORT`            | `8083`  |
| `LOCALSTACK_GATEWAY_PORT` | `4566`  |

Dozzle is exposed on host port `9999` in the default Compose stack (not configurable through
`.env.example`).

Do not publish default database credentials or provider keys in production. Use secret management,
TLS termination, restricted network access, and environment-specific allowed origins.
