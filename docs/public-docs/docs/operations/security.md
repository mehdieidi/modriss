# Security

Varka combines user-authored models, generated files, external AI calls, and cloud deployment
artifacts. Security controls therefore span the platform runtime and the projects it generates.

## Platform Controls

- Protected REST commands require an `X-Auth-Token` session token.
- Project access is checked against owner, editor, and viewer membership.
- Model revisions prevent silent stale overwrites.
- PostgreSQL foreign keys and checks enforce key ownership and enum boundaries.
- Multipart uploads, generated files, file counts, and artifact size have configurable limits.
- Artifact paths reject absolute paths and directory traversal.
- MDE work has execution, queue, concurrency, and captured-output limits.
- Request logging includes a correlation ID.

## Assistant Controls

- AI is disabled unless explicitly enabled.
- The assistant runs as one unified durable CIM/PIM workflow with a strict adaptive strategy, an
  obligation-gated conceptual path, an inspect/contract path, and an enforced read-only answer
  path. There are no delegated model workers and no client-selectable strategy.
- The assistant uses backend-owned metamodel contracts, selected model state, optional attachments,
  and durable recent conversation context rather than raw EVL files or unchecked database access.
- Conceptual objects remain private until mandatory-obligation evidence and live Ecore structure
  pass. All mutation paths are structurally validated, committed against the expected model
  revision, and audited through durable turns.
- EVL and stored semantic validation are never assistant apply/repair/commit gates.
- Destructive batches require explicit confirmation; committed checkpoints may include inverse
  patches for undo.
- Provider calls have timeouts, retries, rate limits, a circuit breaker, and an optional dedicated
  proxy.
- Provider credentials belong in environment variables or a secret manager, never committed files.

## Deployment Hardening

The development Compose stack uses convenient local defaults. Before public deployment:

- Terminate TLS and restrict all exposed ports.
- Replace default database credentials and rotate secrets.
- Restrict CORS origins to deployed frontend origins.
- Restrict Actuator and database access.
- Configure backups, restoration tests, log retention, monitoring, and alerting.
- Define AI provider cost, privacy, retention, and acceptable-use policy.
- Review upload, artifact-editing, and download controls for the deployment threat model.
- Patch base images and dependencies through a managed release process.

## Generated Project Security

Generated AWS projects include IAM rationale, security review, manual-action reports, tests, and
protected regions. They still require human review.

Before deployment:

1. Replace secret and owner placeholders.
2. Review IAM permissions for least privilege.
3. Implement protected business logic.
4. Run generated security, contract, and integration tests.
5. Inspect infrastructure templates, API contracts, event schemas, and workflow definitions.
6. Deploy first to a dedicated non-production account.

LocalStack proves much of the packaging and integration behavior, but it is not a substitute for a
real AWS security review.
