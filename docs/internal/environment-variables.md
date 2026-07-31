# Environment Variables

This file explains every active key in the root `.env` and `.env.example` files. The two files are
kept at the same active key set; `.env` contains local values while `.env.example` contains safe
defaults. The list below is derived from the Spring configuration, Docker Compose file, frontend
runtime wiring, and database/LocalStack helper scripts.

An environment variable is a named setting that the app reads when it starts. The name is the part
before `=`, and the value is the part after it:

```dotenv
BACKEND_PORT=8080
```

Do not commit real secrets. `.env.example` is the safe template. `.env` is your local/private file.

## How To Read Values

- `true` / `false`: a switch. Use lowercase unless a tool says otherwise.
- Port numbers: numbers from `1` to `65535`. If a port is already used on your machine, pick another
  host port.
- Byte limits: plain numbers in bytes. For example, `1048576` is 1 MiB.
- Durations: Spring accepts values like `250ms`, `2s`, `5m`, `2h`, and ISO-8601 values like `PT15M`.
- Empty value: `KEY=` means "configured, but blank". This is useful for optional secrets and optional
  paths.
- URLs: keep the protocol, such as `http://`, `https://`, `ws://`, or `jdbc:postgresql://`.

## Compose And Ports

These mostly control Docker Compose and what ports are exposed on your host machine.

| Variable                  | Possible values                    | What it means                                                                                                                              |
| ------------------------- | ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `POSTGRES_PORT`           | Any free host port, usually `5432` | The port on your machine that forwards to PostgreSQL inside Docker. Change it if you already have Postgres running locally.                |
| `BACKEND_PORT`            | Any free host port, usually `8080` | The host port for the Spring Boot API. The backend still listens on `8080` inside the container.                                           |
| `FRONTEND_PORT`           | Any free host port, usually `8082` | The host port for the modeling frontend.                                                                                                   |
| `LANDING_PORT`            | Any free host port, usually `8083` | The host port for the landing page.                                                                                                        |
| `LOCALSTACK_GATEWAY_PORT` | Any free host port, usually `4566` | The host port for LocalStack's main AWS-compatible endpoint.                                                                               |
| `POSTGRES_HOST`           | Hostname, usually `localhost`      | Host used by the backup and restore scripts. Compose itself uses the `postgres` service name internally.                                   |
| `FREELLMAPI_NETWORK`      | Docker network name                | Optional external network joined by Compose for host-based FreeLLM/API access. A wrong name only affects that optional network attachment. |

## Diagram Editor

The modeling frontend uses the AntV G6 canvas renderer.

| Variable                 | Possible values | What it means                               |
| ------------------------ | --------------- | ------------------------------------------- |
| `VARKA_DIAGRAM_RENDERER` | `antv-g6`       | Frontend diagram renderer (AntV G6 canvas). |

## PostgreSQL

These define the database container and the backend connection to it.

| Variable                 | Possible values                                                | What it means                                                                                                                     |
| ------------------------ | -------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `POSTGRES_DB`            | Database name, usually `varka`                                 | The database created inside the Postgres container.                                                                               |
| `POSTGRES_USER`          | Database username                                              | The Postgres user created by the container.                                                                                       |
| `POSTGRES_PASSWORD`      | Any password                                                   | Password for `POSTGRES_USER`. Treat this as a secret outside local development.                                                   |
| `VARKA_DB_URL`           | JDBC URL, for example `jdbc:postgresql://localhost:5432/varka` | Backend database connection string. In Docker Compose, the backend overrides this to use the `postgres` service name.             |
| `VARKA_DB_USER`          | Database username                                              | Username used by the backend when it connects to Postgres.                                                                        |
| `VARKA_DB_PASSWORD`      | Database password                                              | Password used by the backend when it connects to Postgres.                                                                        |
| `VARKA_DB_MAX_POOL_SIZE` | Positive integer                                               | Maximum number of database connections the backend may keep in its pool. Raise only if you understand database connection limits. |
| `VARKA_DB_MIN_IDLE`      | `0` or positive integer, not higher than max pool size         | Minimum idle database connections the backend tries to keep ready.                                                                |

## Spring Profiles

| Variable                 | Possible values                                                 | What it means                                                                                                         |
| ------------------------ | --------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `SPRING_PROFILES_ACTIVE` | `dev`, `prod`, `test`, or a comma-separated Spring profile list | Selects which Spring configuration profile is active. Set in `.env`; Docker Compose passes it through via `env_file`. |

## Backend Runtime Defaults

These values are bound by the backend's `varka.*` configuration properties. They are useful when
running the backend directly; Compose still supplies its own container-specific database and
upload overrides.

| Variable             | Possible values                    | Effect                                                                                                                    |
| -------------------- | ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `VARKA_STORAGE_ROOT` | Filesystem path, usually `storage` | Root for backend-persisted local data. Changing it changes where local sessions and stored data are written.              |
| `VARKA_SESSION_TTL`  | Spring duration, usually `30d`     | Lifetime of authenticated sessions. Shorter values require more frequent sign-ins; longer values extend session lifetime. |

## MDE Execution Limits

MDE means model-driven engineering: validation, transformation, and artifact generation.

| Variable                                 | Possible values                   | What it means                                                                                                             |
| ---------------------------------------- | --------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `VARKA_MDE_EXECUTION_TIMEOUT`            | Duration like `5m`, `30s`, `PT5M` | Maximum runtime for an individual MDE command.                                                                            |
| `VARKA_MDE_JOB_TIMEOUT`                  | Duration like `10m`               | Maximum total runtime for a queued MDE job. Keep this at least as high as `VARKA_MDE_EXECUTION_TIMEOUT`.                  |
| `VARKA_MDE_MAX_CAPTURED_OUTPUT_BYTES`    | Positive byte count               | Maximum stdout/stderr output captured from MDE tools. Prevents huge logs from filling memory or responses.                |
| `VARKA_MDE_MAX_CONCURRENT_JOBS`          | Positive integer                  | How many MDE jobs may run at the same time. Higher values can make the system busier and faster, but also more CPU-heavy. |
| `VARKA_MDE_QUEUE_CAPACITY`               | Positive integer                  | How many jobs can wait in line before the backend starts rejecting new work.                                              |
| `VARKA_MDE_MAX_MODEL_UPLOAD_BYTES`       | Positive byte count               | Largest model file the backend accepts for upload/import.                                                                 |
| `VARKA_MDE_MAX_GENERATED_FILES`          | Positive integer                  | Maximum number of files a generation run may create.                                                                      |
| `VARKA_MDE_MAX_GENERATED_FILE_BYTES`     | Positive byte count               | Maximum size of any single generated file.                                                                                |
| `VARKA_MDE_MAX_GENERATED_ARTIFACT_BYTES` | Positive byte count               | Maximum total size of the generated artifact bundle, such as the downloadable ZIP.                                        |
| `VARKA_MDE_STAGED_IMPORT_TTL`            | Duration like `2h`                | How long a staged import is kept before it expires.                                                                       |
| `VARKA_MDE_IMPORT_CLEANUP_INTERVAL`      | Duration like `15m` or `PT15M`    | How often the backend cleans expired staged imports.                                                                      |

## Upload Limits

These apply to non-MDE upload storage handled by the backend.

| Variable                      | Possible values                                          | What it means                                                                                                                                                        |
| ----------------------------- | -------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `VARKA_UPLOAD_ROOT`           | Filesystem path, for example `uploads` or `/app/uploads` | Directory where uploaded files are stored. Docker Compose overrides this inside the backend container to `/app/uploads` via `VARKA_CONTAINER_UPLOAD_ROOT`.           |
| `VARKA_CONTAINER_UPLOAD_ROOT` | Absolute path, usually `/app/uploads`                    | Optional Compose override for the backend upload directory inside the container. Leave empty to use the Compose default.                                             |
| `VARKA_ALLOWED_ORIGINS`       | Comma-separated browser origins                          | Optional CORS origin list for browser REST/SSE requests. When empty in Docker Compose, origins are derived from `BACKEND_PORT`, `FRONTEND_PORT`, and `LANDING_PORT`. |
| `VARKA_UPLOAD_MAX_FILE_BYTES` | Positive byte count                                      | Maximum size of an uploaded file.                                                                                                                                    |
| `VARKA_UPLOAD_MAX_TEXT_CHARS` | Positive integer                                         | Maximum number of text characters accepted for text-based upload/input flows. This is characters, not bytes.                                                         |

## AI Assistant

These control whether the assistant is available, what provider it calls, and how much work it is
allowed to do.

| Variable                                     | Possible values                                              | What it means                                                                                                                                         |
| -------------------------------------------- | ------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `VARKA_AI_ENABLED`                           | `true`, `false`                                              | Master switch for outbound AI calls. `false` means the backend should not call an AI provider.                                                        |
| `VARKA_AI_PROVIDER`                          | `openai`, `openai-compatible`, `openai_compatible`, `gemini` | Which provider family to use. The OpenAI-compatible aliases normalize to the `openai` provider path.                                                  |
| `VARKA_AI_REQUEST_TIMEOUT`                   | Duration like `5m`, `10m`                                    | Maximum time to wait for one provider request before giving up.                                                                                       |
| `VARKA_AI_TURN_TIMEOUT`                      | Duration like `5m`                                           | Overall assistant turn ceiling. Timed-out turns must not apply later.                                                                                 |
| `VARKA_AI_MAX_MODEL_DELTA_ELEMENTS_PER_PASS` | Non-negative integer                                         | Maximum model elements changed in one source-analysis pass. Lower values reduce mutation size; higher values allow larger automatic changes.          |
| `VARKA_AI_MAX_CIM_MODELING_PASSES`           | Positive integer                                             | Maximum incremental CIM passes for a source-backed turn. Higher values can improve completeness but increase latency and provider usage.              |
| `VARKA_AI_PREFER_LLM_SOURCE_EXTRACTION`      | `true`, `false`                                              | When true, source attachments use LLM evidence extraction when available; false prefers deterministic/local extraction.                               |
| `VARKA_AI_MAX_REPAIR_ATTEMPTS`               | Positive integer                                             | Maximum structural repair attempts after validation fails.                                                                                            |
| `VARKA_AI_MAX_TOOL_CALLS`                    | Positive integer                                             | Maximum total tool calls for bounded agent turns.                                                                                                     |
| `VARKA_AI_MAX_AGENT_STEPS`                   | Positive integer                                             | Maximum agent loop steps for bounded agent turns.                                                                                                     |
| `VARKA_AI_MAX_TOOL_CALLS_PER_STEP`           | Positive integer                                             | Maximum tool calls in one agent loop step.                                                                                                            |
| `VARKA_AI_TOKEN_BUDGET`                      | Positive integer                                             | Approximate maximum tokens the assistant should spend in a turn. Larger budgets allow more context but cost more and may be slower.                   |
| `VARKA_AI_MAX_PROMPT_TOKENS`                 | Positive integer                                             | Maximum prompt token budget per provider call.                                                                                                        |
| `VARKA_AI_MAX_SOURCE_CHUNK_TOKENS`           | Positive integer                                             | Maximum source text budget per source-understanding chunk.                                                                                            |
| `VARKA_AI_MAX_SOURCE_CHUNKS_PER_TURN`        | Positive integer                                             | Maximum source chunks processed in one turn.                                                                                                          |
| `VARKA_AI_MAX_PROVIDER_CALLS_PER_TURN`       | Positive integer                                             | Maximum provider calls for a standard assistant turn. Higher values allow more retries/planning but increase latency and cost.                        |
| `VARKA_AI_MAX_PROVIDER_CALLS_SOURCE_TURN`    | Positive integer                                             | Maximum provider calls when source analysis runs. Higher values allow deeper extraction but increase latency and cost.                                |
| `VARKA_AI_SOURCE_TURN_TIMEOUT`               | Duration like `12m`                                          | Overall timeout for source-backed turns. It should be at least as large as the normal turn timeout when source analysis is enabled.                   |
| `VARKA_AI_REQUIRE_IDEMPOTENCY_KEY`           | `true`, `false`                                              | Compatibility setting. Durable message submission currently requires request-body `idempotencyKey` whenever durable turn storage is active.           |
| `VARKA_AI_MAX_CONTEXT_SNIPPETS`              | Positive integer                                             | Maximum retrieved context snippets sent to the model.                                                                                                 |
| `VARKA_AI_RESERVED_SCHEMA_SNIPPETS`          | Positive integer                                             | Minimum context slots reserved for schema/metamodel information.                                                                                      |
| `VARKA_AI_MAX_SNIPPET_CHARS`                 | Positive integer                                             | Maximum characters per retrieved context snippet.                                                                                                     |
| `VARKA_AI_MAX_SYSTEM_CHARS`                  | Positive integer                                             | Maximum characters in the generated system prompt.                                                                                                    |
| `VARKA_AI_RATE_LIMIT_REQUESTS`               | Positive integer                                             | Maximum assistant requests per user in one rate-limit window.                                                                                         |
| `VARKA_AI_RATE_LIMIT_WINDOW`                 | Duration like `1m`                                           | The window used with `VARKA_AI_RATE_LIMIT_REQUESTS`.                                                                                                  |
| `VARKA_AI_CIRCUIT_FAILURE_THRESHOLD`         | Positive integer                                             | Number of consecutive provider failures before the circuit breaker opens.                                                                             |
| `VARKA_AI_CIRCUIT_OPEN_DURATION`             | Duration like `1m`                                           | How long the app waits before trying the provider again after the circuit opens.                                                                      |
| `VARKA_AI_PROVIDER_RETRY_ATTEMPTS`           | `0` or positive integer                                      | How many retry attempts the app makes for provider calls.                                                                                             |
| `VARKA_AI_RETRY_BACKOFF`                     | Duration like `250ms`, `1s`                                  | Delay between provider retry attempts.                                                                                                                |
| `VARKA_AI_RECENT_MESSAGE_WINDOW`             | Positive integer                                             | Number of recent durable chat messages included as context.                                                                                           |
| `VARKA_AI_FALLBACK_PROVIDER`                 | Empty, `openai`, `gemini`                                    | Optional backup provider used after HTTP 429 from the primary provider. Empty means no fallback. Not used for 5xx, timeout, or circuit-open failures. |
| `VARKA_AI_VALIDATION_REPAIR_ATTEMPTS`        | Non-negative integer                                         | Legacy validation-repair setting. `VARKA_AI_MAX_REPAIR_ATTEMPTS` takes precedence when it is positive.                                                |
| `VARKA_AI_LLM_CONTRACT_RERANK_ENABLED`       | `true`, `false`                                              | Enables optional LLM reranking of retrieved contracts. It can improve relevance but adds provider calls and latency.                                  |
| `VARKA_AI_OPENAI_PROTOCOL`                   | `auto`, `tools`, legacy JSON mode                            | Selects the OpenAI-compatible response protocol. `tools` uses native Chat Completions tool calls and is preferred when supported.                     |

## AI Provider Credentials And Models

These are secrets or provider-specific names. Keep real keys in `.env`, not `.env.example`.

| Variable                     | Possible values                                                            | What it means                                                                                                                    |
| ---------------------------- | -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `OPENAI_COMPATIBLE_BASE_URL` | URL like `https://api.openai.com/v1` or another OpenAI-compatible base URL | Base URL for OpenAI-style providers. Use the provider's API root expected by the OpenAI SDK, usually including `/v1`.            |
| `OPENAI_COMPATIBLE_API_KEY`  | Provider API key or empty                                                  | API key for OpenAI-compatible providers.                                                                                         |
| `GEMINI_API_KEY`             | Gemini API key or empty                                                    | API key for Google Gemini.                                                                                                       |
| `VARKA_AI_MODEL`             | Empty, `auto`, or a provider model name                                    | Single model used by production assistant provider calls. Empty uses provider defaults.                                          |
| `VARKA_AI_TEST_MODEL`        | Empty, `auto`, or a provider model name                                    | Optional model used by tests/evaluations that intentionally run against a different model. Empty falls back to `VARKA_AI_MODEL`. |

## AI Proxy

These only affect outbound AI provider calls, not every network call in the app.

| Variable                         | Possible values           | What it means                                                                                                                       |
| -------------------------------- | ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `VARKA_AI_PROXY_ENABLED`         | `true`, `false`           | Whether AI provider traffic should use a proxy.                                                                                     |
| `VARKA_AI_PROXY_TYPE`            | `DIRECT`, `HTTP`, `SOCKS` | Proxy type. `DIRECT` means no proxy.                                                                                                |
| `VARKA_AI_PROXY_HOST`            | Hostname or IP address    | Proxy server host. Use `127.0.0.1` for host-local development, or `host.docker.internal` from inside Docker on supported platforms. |
| `VARKA_AI_PROXY_PORT`            | Port number               | Proxy server port. Common local defaults are `2081` for HTTP and `2082` for SOCKS.                                                  |
| `VARKA_AI_PROXY_CONNECT_TIMEOUT` | Duration like `2s`        | How long to wait when checking or connecting to the proxy.                                                                          |

## Observability

Observability means logs, metrics, and traces: the stuff you use to understand what the app is doing.

| Variable                      | Possible values               | What it means                                                                                 |
| ----------------------------- | ----------------------------- | --------------------------------------------------------------------------------------------- |
| `VARKA_METRICS_ENABLED`       | `true`, `false`               | Enables Prometheus metrics export through Spring Actuator.                                    |
| `VARKA_TRACING_ENABLED`       | `true`, `false`               | Enables distributed tracing. Turn on only when you have an OTLP collector or tracing backend. |
| `VARKA_TRACING_SAMPLE_RATE`   | Decimal from `0.0` to `1.0`   | Fraction of requests to trace. `0.1` means about 10 percent. `1.0` means all requests.        |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP HTTP traces endpoint URL | Where traces are sent, for example `http://localhost:4318/v1/traces`.                         |
| `OTEL_SERVICE_NAME`           | Service name string           | Name used to identify this backend in telemetry tools.                                        |

## LocalStack

LocalStack is the local AWS emulator used for generated-project deployment tests.

| Variable                                           | Possible values                                            | What it means                                                                                                                     |
| -------------------------------------------------- | ---------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `AWS_DEFAULT_REGION`                               | AWS region code like `us-east-1`, `eu-west-1`              | Default AWS region used by LocalStack and generated-project tests.                                                                |
| `LOCALSTACK_DEBUG`                                 | `0`, `1`, `true`, `false`                                  | LocalStack debug logging switch. `1` or `true` is noisier.                                                                        |
| `LOCALSTACK_AUTH_TOKEN`                            | Empty or LocalStack auth token                             | Token for LocalStack features that require authentication. Treat as a secret.                                                     |
| `LOCALSTACK_PERSISTENCE`                           | `0`, `1`                                                   | `0` starts clean state by default. `1` keeps LocalStack state across restarts.                                                    |
| `LOCALSTACK_CFN_IGNORE_UNSUPPORTED_RESOURCE_TYPES` | `0`, `1`                                                   | `1` tells LocalStack CloudFormation to ignore unsupported resource types instead of failing immediately.                          |
| `LOCALSTACK_HTTP_PROXY`                            | Empty or proxy URL like `http://host.docker.internal:2081` | HTTP proxy used by the LocalStack container. Empty means no explicit proxy from `.env.example`, but Compose has its own fallback. |
| `LOCALSTACK_HTTPS_PROXY`                           | Empty or proxy URL like `http://host.docker.internal:2081` | HTTPS proxy used by the LocalStack container.                                                                                     |
| `LOCALSTACK_NO_PROXY`                              | Comma-separated hosts                                      | Hosts that should bypass the LocalStack proxy, usually `localhost,127.0.0.1,localstack`.                                          |

## Practical Defaults

- For normal local Docker usage, copy `.env.example` to `.env`, then only change ports that conflict
  and provider keys you actually use.
- To disable AI completely, set `VARKA_AI_ENABLED=false`.
- If something gets slow or memory-heavy, look first at the MDE limits, AI token/context settings,
  and database pool size.
