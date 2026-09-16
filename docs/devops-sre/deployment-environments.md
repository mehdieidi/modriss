# MODRISS deployment environments

MODRISS uses one shared Compose service definition with explicit environment overlays:

- `deploy/compose.base.yaml` contains services, networks, volumes, health checks, dependencies, and restart policies. It publishes no host ports.
- `deploy/compose.dev.yaml` adds local ports and mounts `infra/caddy/Caddyfile.dev`.
- `deploy/compose.prod.yaml` publishes only Caddy on `80/tcp`, `443/tcp`, and `443/udp`, and mounts `infra/caddy/Caddyfile.prod`.

The root `compose.yaml` includes the base and development overlay, so the default command is local development. Production always names both production files explicitly. Caddy `/data` and `/config` are named volumes and retain ACME certificates across container recreation.

## First-time local setup

```bash
cp .env.example .env
# edit secrets or provider settings if needed
./scripts/dev.sh
```

Local URLs:

| Surface    | URL                                |
| ---------- | ---------------------------------- |
| Landing    | `http://localhost:8088`            |
| Editor     | `http://editor.localhost:8088`     |
| Admin      | `http://admin.localhost:8088`      |
| API        | `http://api.localhost:8088`        |
| Grafana    | `http://grafana.localhost:8088`    |
| Prometheus | `http://prometheus.localhost:8088` |
| Logs       | `http://logs.localhost:8088`       |

The local overlay also preserves the direct development ports in `.env.example`, including PostgreSQL, the application services, Loki, the AWS emulator, and Dozzle. `./scripts/dev-down.sh` stops the stack without removing volumes; use `./scripts/dev-down.sh --volumes` only when intentionally discarding local state.

Equivalent root commands are `docker compose config` and `docker compose up -d --build --remove-orphans`.

## First-time production setup

On the Debian server:

```bash
git clone https://github.com/mehdieidi/modriss.git ~/modriss
cd ~/modriss
cp .env.example .env
nano .env
./scripts/deploy-prod.sh
```

Set these URL values in `.env`:

```env
MODRISS_PUBLIC_URL=https://modriss.site
MODRISS_EDITOR_URL=https://editor.modriss.site
MODRISS_ADMIN_URL=https://admin.modriss.site
MODRISS_FRONTEND_BACKEND_BASE_URL=
MODRISS_ALLOWED_ORIGINS=https://modriss.site,https://editor.modriss.site,https://admin.modriss.site,https://api.modriss.site
MODRISS_PROD_SPRING_PROFILE=prod
```

The production overlay also supplies the production Spring profile and uses the production URL defaults for the landing build and admin runtime configuration. The deployment script rejects a rendered browser configuration that contains `localhost` or `127.0.0.1`. Keep all passwords, bootstrap tokens, and provider API keys only in `.env`; replace the development placeholder values before exposing the server.

DNS records for `modriss.site`, `www.modriss.site`, `editor.modriss.site`, `admin.modriss.site`, and `api.modriss.site` must point to the server IP. Open inbound TCP ports `80` and `443` and UDP port `443`. Caddy obtains and renews Let's Encrypt certificates automatically. `www.modriss.site` permanently redirects to `https://modriss.site`.

Only the Caddy container is published in the rendered production Compose configuration. PostgreSQL, backend, frontend, admin, landing, Prometheus, Loki, Grafana, Dozzle, LocalStack, and Floci communicate over Docker networks and have no host port mappings. Caddy's `:2019` admin/metrics listener is available only on that internal network.

## Normal operation

```bash
# Developer
./scripts/dev.sh

# Production server
./scripts/deploy-prod.sh
```

The production script checks out the latest fast-forwardable commit, validates the production model, builds and recreates services with `--remove-orphans`, prints service status, and retries these HTTPS checks:

- `https://modriss.site`
- `https://editor.modriss.site`
- `https://admin.modriss.site`
- `https://api.modriss.site/actuator/health/readiness`

The API root is not used as a health check because `/` may legitimately return `404`.

## Migration from the old setup

The old `deploy/compose.yaml` and `infra/caddy/Caddyfile` have been replaced by the base/overlay files and separate dev/prod Caddyfiles. Existing named volumes retain their data. Stop any old stack before the first production start; do not run `down -v`, because that removes persistent data and Caddy certificates.
