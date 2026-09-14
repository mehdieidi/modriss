# Troubleshooting

## Backend Cannot Connect to PostgreSQL

- Confirm `docker compose ps` reports PostgreSQL healthy.
- Verify `MODRISS_DB_URL`, user, password, and mapped port.
- From inside Compose, use host `postgres`, not `localhost`.
- Inspect Flyway errors before retrying; do not casually edit applied migrations.

## Frontend Cannot Reach Backend

- Confirm `apps/frontend/backend-config.js` points to the running backend.
- Check `/api/health` and `/api/modeling/config`.
- Verify allowed origins in backend configuration.
- Use `127.0.0.1` if a host proxy intercepts `localhost`.

## Model Update Returns `409`

The model revision changed after the client loaded it. Reload the latest model, reconcile changes,
and retry using the current `expectedRevision`.

## Import or Generation Returns `413`

The request exceeded configured model, file-count, per-file, or artifact-size limits. Review the
MDE limit variables before raising limits; unexpectedly large output can indicate a model or
generator issue.

## Validation or Transformation Fails

- Inspect the structured report phase and diagnostics.
- Validate the source model independently with `mde-evl-cli`.
- Confirm combined Ecore files match the Emfatic sources.
- Confirm entry modules, imported EOL/ETL/EVL files, model aliases, and repository root.
- Use the CLI `--verbose` and `--log-file` options.

## Assistant Is Unavailable

- Confirm `MODRISS_AI_ENABLED=true`.
- Confirm the Arvan base URL/key, `Gemma-4-31B-IT`, `MODRISS_AI_MODE=unified`, JSON schema
  protocol, native tools disabled, and request timeout.
- Check dedicated AI proxy configuration.
- Set `MODRISS_AI_ENABLED=false` while diagnosing provider or durable turn issues.
- Make sure message requests include an `idempotencyKey`.
- Restart or rebuild the backend after Ecore/metamodel changes so packaged MDE resources and
  Ecore-derived assistant contracts are fresh. EVL-only changes do not alter the assistant gate.

For failed structured turns, inspect persisted provider calls before changing prompts. Distinguish
timeout, empty output, `finish_reason=length`, malformed JSON, compiler rejection, structural
rejection, and stale revision. A provider response is not a successful scenario unless the expected
checkpoint, preservation, structure, and source coverage were observed.

## Diagram Canvas Does Not Render

- Confirm AntV G6 assets load from `apps/frontend/vendor/antv/`.
- Check `/api/modeling/config` returns `diagramEditor.renderer` as `antv-g6`.
- Inspect the browser console for renderer mount errors.

## Assistant Events Do Not Arrive

- Use authenticated SSE: `GET /api/chatbot/turns/{turnId}/events`.
- Send `X-Auth-Token` and optionally `Last-Event-ID` or `eventCursor` to replay missed events.
- Confirm the browser origin is allowed.
- Poll `GET /api/chatbot/turns/{turnId}` if the SSE connection is interrupted.

## AWS emulator deployment fails

- Start from clean Floci state (`scripts/aws-emulator.* start` recreates its default in-memory container) for repeatable release evidence.
- Inspect CloudFormation stack events.
- Validate every SAM template and ASL definition before deployment.
- Treat unsupported or fallback emulator resources as simulator limitations and verify them in a
  real non-production AWS account.
