# Observability Stack

MODRISS uses application metrics, structured request logs, admin audit events, and health probes to
make the running system inspectable.

## Components

| Component            | Role                                                                    |
| -------------------- | ----------------------------------------------------------------------- |
| Spring Boot Actuator | Health, readiness, and Prometheus metrics endpoints.                    |
| Micrometer           | Application and JVM metrics instrumentation.                            |
| Prometheus           | Scrapes backend, edge, database, host, container, and stack metrics.    |
| Grafana              | Dashboards for metrics and logs.                                        |
| Loki                 | Stores backend, edge, and container logs.                               |
| Promtail             | Reads backend, Caddy, and Docker container logs and ships them to Loki. |
| Dozzle               | Quick local container log inspection.                                   |
| Admin app            | Domain-level operational state and safe admin actions.                  |
| Caddy                | Edge access logs and edge proxy metrics.                                |
| postgres-exporter    | PostgreSQL operational metrics.                                         |
| node-exporter        | Host CPU, memory, disk, network, and filesystem metrics.                |
| cAdvisor             | Docker container CPU, memory, network, and filesystem metrics.          |

## Metrics

The backend exposes metrics at:

```text
GET /actuator/prometheus
```

Prometheus config lives at:

```text
infra/prometheus/prometheus.yml
```

Alert rules live at:

```text
infra/prometheus/modriss-alerts.yml
```

Useful metric groups:

- `http_server_requests_*` for API rate, latency, and errors.
- `jvm_*` for runtime memory and garbage collection.
- `hikaricp_*` for database connection pool behavior.
- `caddy_*` for edge proxy request and handler metrics.
- `pg_*` for PostgreSQL exporter metrics.
- `node_*` for host metrics.
- `container_*` for Docker container metrics from cAdvisor.
- `modriss_frontend_telemetry_events_total` for browser-side app telemetry.
- `modriss.mde.jobs.*` for transformation/generation job activity.
- `modriss.assistant.*` for assistant requests, provider calls, rate limits, and failures.

For assistant diagnosis, correlate metrics/logs with `assistantTurnId`, then inspect durable
workflow kind/phase, work items, provider calls and prompts, validation attempts, checkpoints,
provenance, and coverage. Classify failures as transport timeout, empty output, length truncation,
malformed schema, compiler/tool rejection, structural rejection, conflict, cancellation, or overall
deadline. The internal strategy is not a client-selected dimension.

## Logs

The backend writes logs to `/app/logs/backend.log` inside the backend container. Compose mounts this
through the `modriss-backend-logs` volume. Caddy writes JSON access logs to `/var/log/caddy/access.log`
inside the Caddy container. Promtail reads those volumes and Docker container JSON logs, then pushes
entries to Loki.

Useful Loki queries:

```logql
{service="modriss-backend"}
```

```logql
{service="caddy"}
```

```logql
{compose_project="modriss"}
```

Every backend response includes `X-Request-Id`. The same value is placed in log MDC as `requestId`.
Use this value to trace an error across:

1. Browser/network response headers.
2. Backend logs in Loki or Dozzle.
3. Admin audit events.
4. Operator notes in an incident timeline.

## Dashboards

Grafana provisions:

- Prometheus datasource: `http://prometheus:9090`
- Loki datasource: `http://loki:3100`
- MODRISS overview dashboard from `infra/grafana/modriss-overview.json`
- MODRISS production observability dashboard from `infra/grafana/modriss-production-observability.json`

Local Grafana defaults:

```text
URL: http://127.0.0.1:3000
User: admin
Password: admin
```

Override with:

```env
GRAFANA_ADMIN_USER=...
GRAFANA_ADMIN_PASSWORD=...
```

## Tracing

Tracing is configurable but disabled by default:

```env
MODRISS_TRACING_ENABLED=false
MODRISS_TRACING_SAMPLE_RATE=0.1
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces
```

Enable tracing only when an OpenTelemetry collector or compatible tracing backend is deployed.

## Browser Telemetry

The frontend, admin app, and landing page send minimal browser telemetry to:

```text
POST /api/telemetry/frontend
```

Collected event types:

- `js_error`
- `unhandled_rejection`
- `page_load`

The endpoint records a Micrometer counter and emits a backend log entry. It intentionally limits
message sizes and does not collect page content, form values, passwords, model data, or tokens.
For high-volume production use, add edge rate limits or move browser telemetry to a dedicated
service such as Sentry, GlitchTip, or an OpenTelemetry collector.

## Host and Container Metrics

`node-exporter`, `cAdvisor`, and Docker JSON log scraping are designed for Linux production hosts.
They may be limited or unavailable on Docker Desktop depending on the host operating system and
Docker Desktop file-sharing settings.

Production expectations:

- Keep exporter endpoints private to the Compose network or private infrastructure network.
- Do not expose Prometheus, Loki, cAdvisor, node-exporter, or postgres-exporter publicly.
- Watch disk growth for Prometheus and Loki volumes.
- Tune retention before production traffic grows.

## What To Watch First

1. Readiness failures.
2. API 5xx rate.
3. API p95 latency.
4. PostgreSQL connection pool saturation.
5. Failed MDE jobs.
6. Queued/running jobs stuck beyond expected duration.
7. Assistant provider failures and circuit breaker rejections.
8. Admin audit events for unusual user/session/role changes.
9. Frontend JavaScript errors and page-load spikes.
10. Caddy 5xx responses and upstream failures.
11. Host disk, CPU, memory, and container restart pressure.
