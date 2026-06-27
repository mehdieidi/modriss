# Docker backend logs and local observability

When you start the stack with `docker compose up`, the backend logs are written to the container
output stream as structured JSON. In the default stack (`deploy/compose.yaml`, or root
`compose.yaml`), the backend runs with the Spring
`prod` profile, so there is no file-based backend log inside the container.

The stack also includes [Dozzle](https://dozzle.dev/), a lightweight browser UI for viewing and
searching Docker logs.

## Start the stack

```bash
docker compose up --build
```

Open Dozzle at [http://localhost:9999](http://localhost:9999), then select the backend container.
Dozzle reads local container logs through the mounted Docker socket. This setup is intended for
local development; do not expose the Dozzle port on an untrusted network.

## View the live logs

```bash
docker compose logs -f backend
```

Useful variants:

```bash
docker compose logs backend
docker compose logs --tail=200 backend
docker compose logs -f --timestamps backend
```

## Search and filter logs

Request completion events contain searchable text and structured fields for `method`, `path`,
`status`, `durationMs`, `remoteAddr`, and `userAgent`. Every log event emitted while handling the
request also includes its `requestId`.

Useful Dozzle searches include:

```text
ERROR
status=501
/api/admin/workspace/entries
req-123
```

Because the container output is JSON, exact structured values also appear in forms such as
`"status":501` and `"requestId":"req-123"`.

## Request IDs

The backend preserves a non-blank incoming `X-Request-Id` header. If the header is missing or
blank, it generates a UUID. The ID is:

- returned in the `X-Request-Id` response header
- available in SLF4J MDC as `requestId` throughout request handling
- included in request completion and exception logs
- removed from MDC after the request completes

Send a known ID when reproducing an issue to make all logs for that request easy to find:

```bash
curl -i -H "X-Request-Id: local-debug-123" http://localhost:8080/api/admin/workspace/entries
```

## Check whether the backend container is running

```bash
docker compose ps
```

## Open a shell in the container

```bash
docker compose exec backend sh
```

From there, you can inspect the running process and environment, but in the default compose setup
you should still use `docker compose logs` for the actual application log output.

## Logging modes

The backend only writes to a file when a profile or environment config sets `logging.file.name` or
`logging.file.path`. In this repository:

- `application.yml` uses human-readable console logging, including MDC and structured key/value
  pairs
- `application-prod.yml` uses Spring Boot's built-in Logstash JSON console format for Docker
- `application-dev.yml` writes human-readable logs to `logs/backend.log`

Structured JSON keeps stack traces and turns correlation and request metadata into fields rather
than requiring fragile text parsing. That makes the same logs ready for later ingestion into
Loki/Grafana, Seq, or OpenSearch without adding a larger observability stack now.
