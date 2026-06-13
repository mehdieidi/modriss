# Docker backend logs

When you start the stack with `docker compose up`, the backend logs are written to the
container output stream. In the default `compose.yaml`, the backend runs with the `prod`
profile, so there is no file-based backend log inside the container.

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

## About log files

The backend only writes to a file when a profile or environment config sets `logging.file.name` or
`logging.file.path`. In this repository:

- `application.yml` logs to the console
- `application-prod.yml` keeps console logging for Docker
- `application-dev.yml` writes to `logs/backend.log`

So if you want a physical log file, run the backend with the dev profile or add a file logging
setting for the Docker profile.
