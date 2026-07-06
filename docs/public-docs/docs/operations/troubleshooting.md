# Troubleshooting

## Backend Cannot Connect to PostgreSQL

- Confirm `docker compose ps` reports PostgreSQL healthy.
- Verify `MODLESS_DB_URL`, user, password, and mapped port.
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

- Confirm `MODLESS_AI_ENABLED=true`.
- Check provider base URL, credentials, model names, and request timeout.
- Check dedicated AI proxy configuration.
- Set `MODLESS_AI_ENABLED=false` while diagnosing provider or proposal issues.
- If ONNX cannot load, enable hash fallback or select `HASH`.
- Restart the backend after metamodel or EVL changes so retrieval documents are reindexed.

## Diagram Canvas Does Not Render

- Confirm AntV G6 assets load from `apps/frontend/vendor/antv/`.
- Check `/api/modeling/config` returns `diagramEditor.renderer` as `antv-g6`.
- Inspect the browser console for renderer mount errors.

## WebSocket Does Not Connect

- Use `/ws/chatbot/sessions/{sessionId}`.
- Confirm the browser origin is allowed.
- Remember that the WebSocket is receive-only; submit messages with REST.
- Fall back to the SSE event endpoint when appropriate.

## LocalStack Deployment Fails

- Start from clean LocalStack state for repeatable release evidence.
- Inspect CloudFormation stack events.
- Validate every SAM template and ASL definition before deployment.
- Treat unsupported or fallback LocalStack resources as simulator limitations and verify them in a
  real non-production AWS account.
