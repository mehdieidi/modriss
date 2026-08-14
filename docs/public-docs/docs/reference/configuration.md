# Configuration

Varka reads configuration from Spring Boot, Docker Compose, the frontend runtime bootstrap, and
the PostgreSQL/LocalStack helper scripts. The root `.env` and `.env.example` files contain the same
active keys. `.env.example` is safe to copy; keep real credentials only in the untracked `.env`.

For the complete field-by-field reference, including accepted values and behavior, see the
[internal environment-variable reference](../../../internal/environment-variables.md).

## How configuration is applied

- Spring placeholders in `apps/backend/src/main/resources/application.yml` configure database,
  MDE, uploads, AI, metrics, tracing, and OTLP.
- `@ConfigurationProperties` binds the `varka.*` backend and assistant settings. Invalid values
  can prevent the backend from starting; zero or negative limits may be normalized to safe defaults.
- Compose substitutes host ports and infrastructure values from `.env`, then passes `.env` through
  `env_file`. It overrides the backend database connection to use the `postgres` service and the
  upload directory to use `/app/uploads`.
- The frontend receives its backend URL from generated `backend-config.js`; changing `BACKEND_PORT`
  changes that URL in the Compose deployment.

## Database and runtime

| Variables                                            | Default                                | Effect                                                                                                            |
| ---------------------------------------------------- | -------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`  | `varka`, `varka`, `varka`              | Create the PostgreSQL database and credentials. Change all matching backend credentials together.                 |
| `POSTGRES_HOST`, `POSTGRES_PORT`                     | `localhost`, `5432`                    | Host/port used by database helper scripts and the host-side port published by Compose.                            |
| `VARKA_DB_URL`, `VARKA_DB_USER`, `VARKA_DB_PASSWORD` | local JDBC URL and `varka` credentials | Backend database connection when running outside Compose; Compose supplies container-specific values.             |
| `VARKA_DB_MAX_POOL_SIZE`, `VARKA_DB_MIN_IDLE`        | `10`, `2`                              | Connection-pool capacity. Higher values support more concurrent work but consume more database connections.       |
| `SPRING_PROFILES_ACTIVE`                             | `dev`                                  | Selects Spring profiles such as `dev`, `prod`, or `test`; profiles can change logging and observability behavior. |
| `VARKA_STORAGE_ROOT`, `VARKA_SESSION_TTL`            | `storage`, `30d`                       | Local backend data location and authenticated-session lifetime.                                                   |

## Ports, origins, and frontend

| Variable                      | Default                   | Effect                                                                                                                                                |
| ----------------------------- | ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `BACKEND_PORT`                | `8080`                    | Host port for the API; the container still listens on `8080`.                                                                                         |
| `FRONTEND_PORT`               | `8082`                    | Host port for the modeling frontend.                                                                                                                  |
| `LANDING_PORT`                | `8083`                    | Host port for the landing site.                                                                                                                       |
| `VARKA_DIAGRAM_RENDERER`      | `antv-g6`                 | Selects the diagram editor renderer. Unsupported values can make the modeling editor fail to initialize.                                              |
| `VARKA_ALLOWED_ORIGINS`       | derived by Compose        | Comma-separated CORS origins for browser REST/SSE requests. Setting it replaces the generated origin list; an incorrect list blocks browser requests. |
| `VARKA_CONTAINER_UPLOAD_ROOT` | `/app/uploads` in Compose | Backend-container upload path. Change it only if the corresponding storage mount/path exists.                                                         |
| `FREELLMAPI_NETWORK`          | `freellmapi_default`      | Optional external Docker network for host-based FreeLLM/API access. A wrong network name affects only that optional attachment.                       |

## MDE and uploads

`VARKA_MDE_EXECUTION_TIMEOUT` and `VARKA_MDE_JOB_TIMEOUT` control command and queued-job
deadlines. The remaining `VARKA_MDE_*` keys limit output size, concurrency, queue depth, model
uploads, generated file count/size, artifact size, and staged-import lifetime. Increasing limits
permits larger or slower jobs but increases memory, disk, and CPU risk; decreasing them causes
large or long-running jobs to be rejected or terminated.

`VARKA_UPLOAD_ROOT`, `VARKA_UPLOAD_MAX_FILE_BYTES`, and `VARKA_UPLOAD_MAX_TEXT_CHARS` control
non-MDE uploads. Smaller limits reduce resource usage; larger limits allow larger inputs and longer
assistant context, but increase storage and processing costs.

## AI assistant

Set `VARKA_AI_ENABLED=true` to enable provider calls. The deployed configuration uses
`VARKA_AI_PROVIDER=openai`, Arvan's `OPENAI_COMPATIBLE_BASE_URL` and
`OPENAI_COMPATIBLE_API_KEY`, and `VARKA_AI_MODEL=DeepSeek-V4-Flash`. The code retains other provider
adapters, but they are not the validated production configuration described here.

`VARKA_AI_MODE=unified` is the only normal mode. `agent-test` and `conceptual-test` are strict
acceptance-test overrides, and any other value fails startup. Clients never select this mode or an
internal strategy per request.

`VARKA_AI_METAMODEL_MODE=normal` exposes the complete CIM/PIM metamodel to the assistant and is the
default. Set it to `excerpt` to expose only the curated core business, process, data, service,
function, API, event, workflow, integration, security, and configuration concepts. This setting
changes only assistant discovery and patch contracts; generated models are still structurally
validated against the complete canonical Ecore metamodel. Restart the backend after changing it.

Timeouts, token budgets, context limits, agent steps, provider-call limits, repair attempts, source
passes, rate limits, retries, and recent-message windows all trade completeness and resilience
against latency, memory use, and provider cost. `VARKA_AI_MAX_PROVIDER_CALLS_SOURCE_TURN` and
`VARKA_AI_SOURCE_TURN_TIMEOUT` are the separately configurable budgets used for source-backed
attachment turns. The tested DeepSeek profile uses 20 calls for both normal and source turns, with
12- and 15-minute timeouts respectively. Increasing them allows more complex documents; lowering
them makes failures faster and cheaper but can starve review or correction.
`VARKA_AI_FALLBACK_PROVIDER` is used only after an HTTP 429 from the primary provider. The current
single-provider Arvan deployment must leave it empty; configuring another adapter does not make that
provider supported in production. Durable assistant message submission currently requires an
`idempotencyKey` in the request body so retries
cannot apply duplicate work.

Model selection uses one production model: `VARKA_AI_MODEL`. Tests and evaluations that need a
different model can use `VARKA_AI_TEST_MODEL`; when it is empty, they fall back to
`VARKA_AI_MODEL`. Production must set `DeepSeek-V4-Flash` explicitly rather than relying on an
adapter default.

For Arvan, use `VARKA_AI_OPENAI_PROTOCOL=json_schema`,
`VARKA_AI_NATIVE_TOOLS_PREFERRED=false`, and
`VARKA_AI_FORCED_TOOL_CHOICE_RELIABLE=false`. Structured requests use temperature zero and the
DeepSeek `thinking` disable object. `VARKA_AI_PREFER_LLM_SOURCE_EXTRACTION` and
`VARKA_AI_LLM_CONTRACT_RERANK_ENABLED` can improve source/retrieval quality at the cost of extra
provider calls. The proxy variables accept `DIRECT`, `HTTP`, or `SOCKS`; proxy settings affect AI
provider traffic only.

The conceptual blueprint maximum (16 objects/types), obligation-ledger maximum (12 obligations),
type-selection attempts (4), and compact truncation-retry protocol are implementation invariants,
not environment variables. Effective conceptual capacity is lower when required Ecore closure or
the provider-call reserve consumes budget.

Values in `application.yml` are safe code defaults, not the validated Arvan production profile.
The repository `.env.example` intentionally overrides several of them, including 12/15-minute turn
timeouts, 16,000 completion tokens, JSON protocol, disabled native tools, and zero provider retries.

## Observability

`VARKA_METRICS_ENABLED` controls Prometheus metrics. `VARKA_TRACING_ENABLED` enables tracing and
`VARKA_TRACING_SAMPLE_RATE` accepts `0.0` through `1.0`; higher sampling gives more diagnostic data
but adds overhead. `OTEL_EXPORTER_OTLP_ENDPOINT` selects the OTLP HTTP endpoint and
`OTEL_SERVICE_NAME` names the service in the telemetry backend.

## LocalStack

`LOCALSTACK_GATEWAY_PORT` publishes LocalStack's AWS-compatible endpoint. `AWS_DEFAULT_REGION`
sets the default region. `LOCALSTACK_DEBUG` accepts `0`, `1`, `true`, or `false`; persistence accepts
`0` or `1`; `LOCALSTACK_CFN_IGNORE_UNSUPPORTED_RESOURCE_TYPES=1` makes unsupported CloudFormation
resources non-fatal. The HTTP/HTTPS proxy and `LOCALSTACK_NO_PROXY` values control outbound network
routing from the LocalStack container. `LOCALSTACK_AUTH_TOKEN` is optional and secret.

Never publish database passwords, API keys, or LocalStack tokens. Use a secret manager and restrict
allowed origins and network exposure in production.
