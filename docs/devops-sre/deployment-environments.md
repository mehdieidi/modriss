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
| Docs       | `http://docs.localhost:8088`       |
| Editor     | `http://editor.localhost:8088`     |
| Admin      | `http://admin.localhost:8088`      |
| API        | `http://api.localhost:8088`        |
| Grafana    | `http://grafana.localhost:8088`    |
| Prometheus | `http://prometheus.localhost:8088` |
| Logs       | `http://logs.localhost:8088`       |

The local overlay also preserves the direct development ports in `.env.example`, including PostgreSQL, the application services, Loki, the AWS emulator, and Dozzle. `./scripts/dev-down.sh` stops the stack without removing volumes; use `./scripts/dev-down.sh --volumes` only when intentionally discarding local state.

All development ports bind to `127.0.0.1` by default, including the LocalStack port range.
For deliberate LAN/mobile access, set `MODRISS_DEV_BIND_ADDRESS=0.0.0.0` in `.env` and recreate
the services with `sh scripts/dev.sh`. Use the host's LAN address and direct frontend ports,
or configure device DNS for Caddy's development hostnames. Set browser backend/docs URLs
and `MODRISS_ALLOWED_ORIGINS` to reachable LAN addresses as appropriate; `localhost` on a
phone refers to the phone. This setting does not change production port bindings.

Dozzle runs only in development. Production retains sockets for Floci/LocalStack to launch
Lambda containers, Promtail to discover Docker containers for log collection, and cAdvisor's
read-only `/var/run` mount for container monitoring. These are trusted infrastructure services
with host access; read-only socket mounts do not restrict Docker API operations.

Production exposes `/api/*` and the exact `/actuator/health/readiness` probe through Caddy.
Other Actuator routes and Swagger/API documentation routes return 404. Prometheus scrapes
`backend:8080/actuator/prometheus` internally. Production disables health details/components
and Swagger, including overrides for settings retained in an older `.env`. Local development
keeps Swagger and Actuator routes; health details can be enabled explicitly there.

The production editor is an immutable nginx image. Its entrypoint generates `backend-config.js`
from the two frontend URL settings at container startup; empty backend URL preserves same-origin
API access. Development retains the editable bind-mounted Python server. Source changes rebuild
the production image, so deployment only forces recreation of Caddy for its mounted configuration.

## Build inputs and cache maintenance

The root backend build context allows only Maven reactor inputs, Java sources, configuration,
and MDE resources. Environment files, including `.env.example`, are excluded from every build
context; Compose still injects runtime settings separately. Backend POMs are copied before
sources, and external dependency resolution uses the existing Maven cache mount. Admin and
landing keep lockfile-first installation and use separate npm download-cache mounts. Docs copy
only their MkDocs configuration and source documentation.

Dozzle defaults to the locally verified `v10.8.0` image; `DOZZLE_IMAGE` can override it.
LocalStack defaults to `3.8.1`, matching the repository's generated integration-test baseline;
`LOCALSTACK_IMAGE` can override it. Before changing a server with retained emulator state, pin
its existing version explicitly and check that version's persistence compatibility. This patch
does not delete emulator volumes or migrate state. Existing `.env` image overrides must be
updated deliberately; changing `.env.example` does not change them. npm versions match the
committed lockfile resolutions, and Hadolint is pinned
in `config/linting.json`. Update pins deliberately after checking compatibility.

Inspect the selected builder's cache with `sh scripts/build-cache.sh --inspect` or
`docker buildx du`. To reclaim old cache on an existing server, run:

```bash
docker buildx prune --filter 'until=168h'
# Equivalent opt-in helper (also asks for Docker's confirmation):
sh scripts/build-cache.sh --prune 168h
```

This removes unused build-cache records not accessed in seven days from the selected builder.
It preserves running containers, images, and persistent volumes. Future builds may download
dependencies or rebuild removed layers. No startup or deployment command prunes automatically.
Old cache remains until explicitly cleaned; smaller inputs and fewer unrelated invalidations
should reduce future growth, but cache is not size-bounded and no fixed GB saving is promised.

Run `RUN_DOCKER_TESTS=1 python -m unittest discover -s scripts/tests -v` to check rendered
development/production configurations and test environment-file exclusions with BuildKit
using synthetic secret canaries. The deployment configuration workflow runs these guards.

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
MODRISS_DOCS_URL=https://docs.modriss.site
MODRISS_FRONTEND_BACKEND_BASE_URL=
MODRISS_ALLOWED_ORIGINS=https://modriss.site,https://editor.modriss.site,https://admin.modriss.site,https://api.modriss.site
MODRISS_PROD_SPRING_PROFILE=prod
```

The production overlay also supplies the production Spring profile and uses the production URL defaults for the landing build and admin runtime configuration. The deployment script rejects a rendered browser configuration that contains `localhost` or `127.0.0.1`. Keep all passwords, bootstrap tokens, and provider API keys only in `.env`; replace the development placeholder values before exposing the server.

DNS records for `modriss.site`, `www.modriss.site`, `editor.modriss.site`, `admin.modriss.site`, `docs.modriss.site`, and `api.modriss.site` must point to the server IP. Open inbound TCP ports `80` and `443` and UDP port `443`. Caddy obtains and renews Let's Encrypt certificates automatically. `www.modriss.site` permanently redirects to `https://modriss.site`.

Only the Caddy container is published in the rendered production Compose configuration. PostgreSQL, backend, frontend, admin, landing, docs, Prometheus, Loki, Grafana, LocalStack, and Floci communicate over Docker networks and have no host port mappings. Caddy's `:2019` admin/metrics listener is available only on that internal network.

## Normal operation

```bash
# Developer
./scripts/dev.sh

# Production server
./scripts/deploy-prod.sh
```

The production script checks out the latest fast-forwardable commit, validates the production model, builds and recreates services with `--remove-orphans`, prints service status, and retries these HTTPS checks:

- `https://modriss.site`
- `https://docs.modriss.site`
- `https://editor.modriss.site`
- `https://admin.modriss.site`
- `https://api.modriss.site/actuator/health/readiness`

The API root is not used as a health check because `/` may legitimately return `404`.

## Migration from the old setup

The old `deploy/compose.yaml` and `infra/caddy/Caddyfile` have been replaced by the base/overlay files and separate dev/prod Caddyfiles. Existing named volumes retain their data. Stop any old stack before the first production start; do not run `down -v`, because that removes persistent data and Caddy certificates.
