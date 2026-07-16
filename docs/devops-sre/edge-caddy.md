# Caddy Edge Proxy

Caddy is the local edge proxy for the Varka stack. It gives one browser-facing entrypoint while
keeping the existing service ports available for direct debugging.

## Local Entry Points

Start the stack from the repository root:

```powershell
docker compose up --build
```

The Caddy listener defaults to `CADDY_HTTP_PORT=8088`.

| Surface     | URL                                | Upstream service                    |
| ----------- | ---------------------------------- | ----------------------------------- |
| Landing     | `http://localhost:8088`            | `landing:8083` with backend routes  |
| Editor      | `http://editor.localhost:8088`     | `frontend:8082` with backend routes |
| Backend API | `http://api.localhost:8088`        | `backend:8080`                      |
| Admin app   | `http://admin.localhost:8088`      | `admin:8084` with backend routes    |
| Grafana     | `http://grafana.localhost:8088`    | `grafana:3000`                      |
| Prometheus  | `http://prometheus.localhost:8088` | `prometheus:9090`                   |
| Logs        | `http://logs.localhost:8088`       | `dozzle:8080`                       |
| Loki        | `http://loki.localhost:8088`       | `loki:3100`                         |
| LocalStack  | `http://localstack.localhost:8088` | `localstack:4566`                   |

The `*.localhost` names normally resolve to loopback on modern systems. If an environment does not
support that behavior, use the direct service ports or add local hosts-file aliases.

## Backend Routing

The landing page, editor, and admin app all proxy backend paths through Caddy:

- `/api/*`
- `/actuator/*`
- `/v3/api-docs*`
- `/swagger-ui*`
- `/swagger-ui.html`

This keeps browser API calls same-origin for the Caddy-hosted frontend surfaces and reduces CORS
friction during local development.

## Configuration Files

- `infra/caddy/Caddyfile` owns host routing and shared security headers.
- `deploy/compose.yaml` defines the `caddy` container, port publishing, volumes, and dependencies.
- `.env` and `.env.example` define `CADDY_HTTP_PORT`.
- Caddy writes JSON access logs to the `varka-caddy-logs` Docker volume.
- Caddy exposes internal Prometheus metrics on `caddy:2019/metrics`.

To restart only the edge proxy after a config change:

```powershell
docker compose up --force-recreate -d caddy
```

Validate the Caddyfile before relying on it:

```powershell
$caddyfile = (Resolve-Path infra\caddy\Caddyfile).Path
docker run --rm -v "${caddyfile}:/etc/caddy/Caddyfile:ro" caddy:2.8-alpine caddy validate --config /etc/caddy/Caddyfile
```

## Production Guidance

The checked-in Caddyfile is intentionally local-first and has `auto_https off` because it listens on
a local high port. A production edge should use real hostnames, HTTPS, and environment-specific
routing.

Production expectations:

- Terminate TLS at Caddy or at a managed load balancer in front of Caddy.
- Keep PostgreSQL, Prometheus, Loki, LocalStack, and Actuator off the public internet.
- Put the admin app behind strong authentication, authorization, audit logging, and network access
  controls.
- Restrict operational tools such as Grafana, Prometheus, Dozzle, and Loki to private networks or
  SSO-protected routes.
- Keep Caddy's admin/metrics endpoint private; Prometheus should scrape it over the internal
  Docker network only.
- Review security headers per application. Some production apps need a stricter Content Security
  Policy than the shared local headers provide.
- Use orchestrator-managed secrets and avoid baking credentials into Caddy config files.
