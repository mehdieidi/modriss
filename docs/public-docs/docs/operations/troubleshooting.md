# Troubleshooting

## Backend Cannot Connect to PostgreSQL

- Confirm `docker compose ps` reports PostgreSQL healthy.
- Verify `VARKA_DB_URL`, user, password, and mapped port.
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

- Confirm `VARKA_AI_ENABLED=true`.
- Check provider base URL, credentials, model names, and request timeout.
- Check dedicated AI proxy configuration.
- Set `VARKA_AI_ENABLED=false` while diagnosing provider or durable turn issues.
- Make sure message requests include an `idempotencyKey`.
- Restart or rebuild the backend after metamodel or EVL changes so packaged MDE resources and
  Ecore-derived assistant contracts are fresh.

## Diagram Canvas Does Not Render

- Confirm AntV G6 assets load from `apps/frontend/vendor/antv/`.
- Check `/api/modeling/config` returns `diagramEditor.renderer` as `antv-g6`.
- Inspect the browser console for renderer mount errors.

## Assistant Events Do Not Arrive

- Use authenticated SSE: `GET /api/chatbot/turns/{turnId}/events`.
- Send `X-Auth-Token` and optionally `Last-Event-ID` or `eventCursor` to replay missed events.
- Confirm the browser origin is allowed.
- Poll `GET /api/chatbot/turns/{turnId}` if the SSE connection is interrupted.

## LocalStack Deployment Fails

- Start from clean LocalStack state for repeatable release evidence.
- Inspect CloudFormation stack events.
- Validate every SAM template and ASL definition before deployment.
- Treat unsupported or fallback LocalStack resources as simulator limitations and verify them in a
  real non-production AWS account.
