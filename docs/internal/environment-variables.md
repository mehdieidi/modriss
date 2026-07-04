# Environment Variables

This file explains every active key in the root `.env` and `.env.example` files.

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

| Variable                  | Possible values                    | What it means                                                                                                                      |
| ------------------------- | ---------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `POSTGRES_PORT`           | Any free host port, usually `5432` | The port on your machine that forwards to PostgreSQL inside Docker. Change it if you already have Postgres running locally.        |
| `BACKEND_PORT`            | Any free host port, usually `8080` | The host port for the Spring Boot API. The backend still listens on `8080` inside the container.                                   |
| `FRONTEND_PORT`           | Any free host port, usually `8082` | The host port for the modeling frontend.                                                                                           |
| `LANDING_PORT`            | Any free host port, usually `8083` | The host port for the landing page.                                                                                                |
| `LOCALSTACK_GATEWAY_PORT` | Any free host port, usually `4566` | The host port for LocalStack's main AWS-compatible endpoint.                                                                       |
| `GLSP_PORT`               | Any free host port, usually `8081` | The host port for the GLSP diagram server WebSocket endpoint. This is the port your browser connects to when using `glsp-sprotty`. |

## Diagram Editor

These choose which diagram renderer the frontend should use.

| Variable                   | Possible values                                                          | What it means                                                                                                                   |
| -------------------------- | ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------- |
| `MODLESS_GLSP_LOG_LEVEL`   | `debug`, `info`, `warn`, `error`                                         | Logging detail for the GLSP server. Use `debug` when diagnosing diagram-server behavior; use `info` or higher for quieter logs. |
| `MODLESS_DIAGRAM_RENDERER` | `antv-g6`, `glsp-sprotty`                                                | `antv-g6` uses the local canvas renderer. `glsp-sprotty` uses the Eclipse GLSP/Sprotty renderer and needs the GLSP server.      |
| `MODLESS_GLSP_SERVER_URL`  | A browser-reachable WebSocket URL, usually `ws://127.0.0.1:8081/modless` | The URL the browser uses to reach the GLSP server. If you change `GLSP_PORT`, change this URL to match.                         |

## PostgreSQL

These define the database container and the backend connection to it.

| Variable                   | Possible values                                                  | What it means                                                                                                                     |
| -------------------------- | ---------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `POSTGRES_DB`              | Database name, usually `modless`                                 | The database created inside the Postgres container.                                                                               |
| `POSTGRES_USER`            | Database username                                                | The Postgres user created by the container.                                                                                       |
| `POSTGRES_PASSWORD`        | Any password                                                     | Password for `POSTGRES_USER`. Treat this as a secret outside local development.                                                   |
| `MODLESS_DB_URL`           | JDBC URL, for example `jdbc:postgresql://localhost:5432/modless` | Backend database connection string. In Docker Compose, the backend overrides this to use the `postgres` service name.             |
| `MODLESS_DB_USER`          | Database username                                                | Username used by the backend when it connects to Postgres.                                                                        |
| `MODLESS_DB_PASSWORD`      | Database password                                                | Password used by the backend when it connects to Postgres.                                                                        |
| `MODLESS_DB_MAX_POOL_SIZE` | Positive integer                                                 | Maximum number of database connections the backend may keep in its pool. Raise only if you understand database connection limits. |
| `MODLESS_DB_MIN_IDLE`      | `0` or positive integer, not higher than max pool size           | Minimum idle database connections the backend tries to keep ready.                                                                |

## Integration Test Database

These are fallback values for integration tests. Testcontainers can override them automatically.

| Variable                         | Possible values   | What it means                                                   |
| -------------------------------- | ----------------- | --------------------------------------------------------------- |
| `MODLESS_POSTGRES_TEST_URL`      | JDBC URL          | Database URL used by tests when not replaced by Testcontainers. |
| `MODLESS_POSTGRES_TEST_USER`     | Database username | Test database username.                                         |
| `MODLESS_POSTGRES_TEST_PASSWORD` | Database password | Test database password.                                         |

## Spring Profiles

| Variable                 | Possible values                                                 | What it means                                                                                                         |
| ------------------------ | --------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `SPRING_PROFILES_ACTIVE` | `dev`, `prod`, `test`, or a comma-separated Spring profile list | Selects which Spring configuration profile is active. Set in `.env`; Docker Compose passes it through via `env_file`. |

## MDE Execution Limits

MDE means model-driven engineering: validation, transformation, and artifact generation.

| Variable                                   | Possible values                   | What it means                                                                                                             |
| ------------------------------------------ | --------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `MODLESS_MDE_EXECUTION_TIMEOUT`            | Duration like `5m`, `30s`, `PT5M` | Maximum runtime for an individual MDE command.                                                                            |
| `MODLESS_MDE_JOB_TIMEOUT`                  | Duration like `10m`               | Maximum total runtime for a queued MDE job. Keep this at least as high as `MODLESS_MDE_EXECUTION_TIMEOUT`.                |
| `MODLESS_MDE_MAX_CAPTURED_OUTPUT_BYTES`    | Positive byte count               | Maximum stdout/stderr output captured from MDE tools. Prevents huge logs from filling memory or responses.                |
| `MODLESS_MDE_MAX_CONCURRENT_JOBS`          | Positive integer                  | How many MDE jobs may run at the same time. Higher values can make the system busier and faster, but also more CPU-heavy. |
| `MODLESS_MDE_QUEUE_CAPACITY`               | Positive integer                  | How many jobs can wait in line before the backend starts rejecting new work.                                              |
| `MODLESS_MDE_MAX_MODEL_UPLOAD_BYTES`       | Positive byte count               | Largest model file the backend accepts for upload/import.                                                                 |
| `MODLESS_MDE_MAX_GENERATED_FILES`          | Positive integer                  | Maximum number of files a generation run may create.                                                                      |
| `MODLESS_MDE_MAX_GENERATED_FILE_BYTES`     | Positive byte count               | Maximum size of any single generated file.                                                                                |
| `MODLESS_MDE_MAX_GENERATED_ARTIFACT_BYTES` | Positive byte count               | Maximum total size of the generated artifact bundle, such as the downloadable ZIP.                                        |
| `MODLESS_MDE_STAGED_IMPORT_TTL`            | Duration like `2h`                | How long a staged import is kept before it expires.                                                                       |
| `MODLESS_MDE_IMPORT_CLEANUP_INTERVAL`      | Duration like `15m` or `PT15M`    | How often the backend cleans expired staged imports.                                                                      |

## Upload Limits

These apply to non-MDE upload storage handled by the backend.

| Variable                        | Possible values                                          | What it means                                                                                                                                                |
| ------------------------------- | -------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `MODLESS_UPLOAD_ROOT`           | Filesystem path, for example `uploads` or `/app/uploads` | Directory where uploaded files are stored. Docker Compose overrides this inside the backend container to `/app/uploads` via `MODLESS_CONTAINER_UPLOAD_ROOT`. |
| `MODLESS_CONTAINER_UPLOAD_ROOT` | Absolute path, usually `/app/uploads`                    | Optional Compose override for the backend upload directory inside the container. Leave empty to use the Compose default.                                     |
| `MODLESS_ALLOWED_ORIGINS`       | Comma-separated browser origins                          | Optional CORS and WebSocket origin list. When empty in Docker Compose, origins are derived from `BACKEND_PORT`, `FRONTEND_PORT`, and `LANDING_PORT`.         |
| `MODLESS_UPLOAD_MAX_FILE_BYTES` | Positive byte count                                      | Maximum size of an uploaded file.                                                                                                                            |
| `MODLESS_UPLOAD_MAX_TEXT_CHARS` | Positive integer                                         | Maximum number of text characters accepted for text-based upload/input flows. This is characters, not bytes.                                                 |

## AI Assistant

These control whether the assistant is available, what provider it calls, and how much work it is
allowed to do.

| Variable                                | Possible values                                              | What it means                                                                                                                                         |
| --------------------------------------- | ------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `MODLESS_AI_ENABLED`                    | `true`, `false`                                              | Master switch for outbound AI calls. `false` means the backend should not call an AI provider.                                                        |
| `MODLESS_AI_PROVIDER`                   | `openai`, `openai-compatible`, `openai_compatible`, `gemini` | Which provider family to use. OpenAI-compatible providers use the OpenAI-style API shape.                                                             |
| `MODLESS_AI_REQUEST_TIMEOUT`            | Duration like `5m`, `10m`                                    | Maximum time to wait for one provider request before giving up.                                                                                       |
| `MODLESS_AI_TURN_TIMEOUT`               | Duration like `5m`                                           | Overall assistant turn ceiling. Timed-out turns must not apply later.                                                                                 |
| `MODLESS_AI_MAX_REPAIR_ATTEMPTS`        | Positive integer                                             | Maximum structural repair attempts after validation fails.                                                                                            |
| `MODLESS_AI_MAX_TOOL_CALLS`             | Positive integer                                             | Maximum total tool calls for bounded agent turns.                                                                                                     |
| `MODLESS_AI_MAX_AGENT_STEPS`            | Positive integer                                             | Maximum agent loop steps for bounded agent turns.                                                                                                     |
| `MODLESS_AI_MAX_TOOL_CALLS_PER_STEP`    | Positive integer                                             | Maximum tool calls in one agent loop step.                                                                                                            |
| `MODLESS_AI_TOKEN_BUDGET`               | Positive integer                                             | Approximate maximum tokens the assistant should spend in a turn. Larger budgets allow more context but cost more and may be slower.                   |
| `MODLESS_AI_MAX_PROMPT_TOKENS`          | Positive integer                                             | Maximum prompt token budget per provider call.                                                                                                        |
| `MODLESS_AI_MAX_SOURCE_CHUNK_TOKENS`    | Positive integer                                             | Maximum source text budget per source-understanding chunk.                                                                                            |
| `MODLESS_AI_MAX_SOURCE_CHUNKS_PER_TURN` | Positive integer                                             | Maximum source chunks processed in one turn.                                                                                                          |
| `MODLESS_AI_REQUIRE_IDEMPOTENCY_KEY`    | `true`, `false`                                              | Whether clients must provide idempotency keys for turn execution.                                                                                     |
| `MODLESS_AI_MAX_CONTEXT_SNIPPETS`       | Positive integer                                             | Maximum retrieved context snippets sent to the model.                                                                                                 |
| `MODLESS_AI_RESERVED_SCHEMA_SNIPPETS`   | Positive integer                                             | Minimum context slots reserved for schema/metamodel information.                                                                                      |
| `MODLESS_AI_MAX_SNIPPET_CHARS`          | Positive integer                                             | Maximum characters per retrieved context snippet.                                                                                                     |
| `MODLESS_AI_MAX_SYSTEM_CHARS`           | Positive integer                                             | Maximum characters in the generated system prompt.                                                                                                    |
| `MODLESS_AI_RATE_LIMIT_REQUESTS`        | Positive integer                                             | Maximum assistant requests per user in one rate-limit window.                                                                                         |
| `MODLESS_AI_RATE_LIMIT_WINDOW`          | Duration like `1m`                                           | The window used with `MODLESS_AI_RATE_LIMIT_REQUESTS`.                                                                                                |
| `MODLESS_AI_CIRCUIT_FAILURE_THRESHOLD`  | Positive integer                                             | Number of consecutive provider failures before the circuit breaker opens.                                                                             |
| `MODLESS_AI_CIRCUIT_OPEN_DURATION`      | Duration like `1m`                                           | How long the app waits before trying the provider again after the circuit opens.                                                                      |
| `MODLESS_AI_PROVIDER_RETRY_ATTEMPTS`    | `0` or positive integer                                      | How many retry attempts the app makes for provider calls.                                                                                             |
| `MODLESS_AI_RETRY_BACKOFF`              | Duration like `250ms`, `1s`                                  | Delay between provider retry attempts.                                                                                                                |
| `MODLESS_AI_RECENT_MESSAGE_WINDOW`      | Positive integer                                             | Number of recent durable chat messages included as context.                                                                                           |
| `MODLESS_AI_FALLBACK_PROVIDER`          | Empty, `openai`, `gemini`                                    | Optional backup provider used after HTTP 429 from the primary provider. Empty means no fallback. Not used for 5xx, timeout, or circuit-open failures. |

## AI Provider Credentials And Models

These are secrets or provider-specific names. Keep real keys in `.env`, not `.env.example`.

| Variable                      | Possible values                                                         | What it means                                                                                                                                                  |
| ----------------------------- | ----------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `OPENAI_COMPATIBLE_BASE_URL`  | URL like `https://api.openai.com` or another OpenAI-compatible base URL | Base URL for OpenAI-style providers. The backend normalizes a trailing `/v1`, so both `https://api.openai.com` and `https://api.openai.com/v1` are acceptable. |
| `OPENAI_COMPATIBLE_API_KEY`   | Provider API key or empty                                               | API key for OpenAI-compatible providers.                                                                                                                       |
| `GEMINI_API_KEY`              | Gemini API key or empty                                                 | API key for Google Gemini.                                                                                                                                     |
| `MODLESS_AI_PLANNER_MODEL`    | Empty, `auto`, or a provider model name                                 | Model used for planning changes. Empty uses provider defaults; `auto` is only useful if your gateway understands it as a model alias.                          |
| `MODLESS_AI_RESPONDER_MODEL`  | Empty, `auto`, or a provider model name                                 | Model used for user-facing assistant responses.                                                                                                                |
| `MODLESS_AI_SUMMARIZER_MODEL` | Empty, `auto`, or a provider model name                                 | Model used for conversation/context summaries.                                                                                                                 |

## AI Embeddings

Embeddings turn text into vectors for retrieval. Hash embeddings are simple and local. ONNX embeddings
can be better, but require the ONNX setup/profile to be available.

| Variable                                   | Possible values                                                      | What it means                                                                                                                                     |
| ------------------------------------------ | -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `MODLESS_AI_EMBEDDINGS_PROVIDER`           | `HASH`, `ONNX`                                                       | `HASH` uses deterministic local hash vectors. `ONNX` uses a local ONNX embedding model.                                                           |
| `MODLESS_AI_EMBEDDINGS_MODEL_RESOURCE`     | Empty or resource/path URI like `file:/absolute/path/model.onnx`     | Optional ONNX model location. Empty means use library/application defaults.                                                                       |
| `MODLESS_AI_EMBEDDINGS_TOKENIZER_RESOURCE` | Empty or resource/path URI like `file:/absolute/path/tokenizer.json` | Optional tokenizer location for ONNX embeddings.                                                                                                  |
| `MODLESS_AI_EMBEDDINGS_MODEL_OUTPUT_NAME`  | Empty or ONNX tensor output name                                     | Optional output tensor name if the ONNX model needs a specific output selected.                                                                   |
| `MODLESS_AI_EMBEDDINGS_CACHE_DIRECTORY`    | Empty or filesystem path                                             | Optional directory where embedding model files may be cached.                                                                                     |
| `MODLESS_AI_EMBEDDINGS_DISABLE_CACHING`    | `true`, `false`                                                      | Whether to disable embedding model caching. Usually `false`.                                                                                      |
| `MODLESS_AI_EMBEDDINGS_GPU_DEVICE_ID`      | `-1`, `0`, `1`, ...                                                  | GPU device ID for ONNX. `-1` means CPU/default.                                                                                                   |
| `MODLESS_AI_EMBEDDINGS_FALLBACK_TO_HASH`   | `true`, `false`                                                      | If `ONNX` fails to initialize, `true` lets the backend fall back to hash embeddings. In production, `false` makes broken embedding setup obvious. |

## AI Proxy

These only affect outbound AI provider calls, not every network call in the app.

| Variable                           | Possible values           | What it means                                                                                                                       |
| ---------------------------------- | ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| `MODLESS_AI_PROXY_ENABLED`         | `true`, `false`           | Whether AI provider traffic should use a proxy.                                                                                     |
| `MODLESS_AI_PROXY_TYPE`            | `DIRECT`, `HTTP`, `SOCKS` | Proxy type. `DIRECT` means no proxy.                                                                                                |
| `MODLESS_AI_PROXY_HOST`            | Hostname or IP address    | Proxy server host. Use `127.0.0.1` for host-local development, or `host.docker.internal` from inside Docker on supported platforms. |
| `MODLESS_AI_PROXY_PORT`            | Port number               | Proxy server port. Common local defaults are `2081` for HTTP and `2082` for SOCKS.                                                  |
| `MODLESS_AI_PROXY_CONNECT_TIMEOUT` | Duration like `2s`        | How long to wait when checking or connecting to the proxy.                                                                          |

## Observability

Observability means logs, metrics, and traces: the stuff you use to understand what the app is doing.

| Variable                      | Possible values               | What it means                                                                                 |
| ----------------------------- | ----------------------------- | --------------------------------------------------------------------------------------------- |
| `MODLESS_METRICS_ENABLED`     | `true`, `false`               | Enables Prometheus metrics export through Spring Actuator.                                    |
| `MODLESS_TRACING_ENABLED`     | `true`, `false`               | Enables distributed tracing. Turn on only when you have an OTLP collector or tracing backend. |
| `MODLESS_TRACING_SAMPLE_RATE` | Decimal from `0.0` to `1.0`   | Fraction of requests to trace. `0.1` means about 10 percent. `1.0` means all requests.        |
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
- To disable AI completely, set `MODLESS_AI_ENABLED=false`.
- To use the local canvas renderer, set `MODLESS_DIAGRAM_RENDERER=antv-g6`.
- To use GLSP/Sprotty, set `MODLESS_DIAGRAM_RENDERER=glsp-sprotty` and make sure
  `MODLESS_GLSP_SERVER_URL` points at the published `GLSP_PORT`.
- If something gets slow or memory-heavy, look first at the MDE limits, AI token/context settings,
  and database pool size.
