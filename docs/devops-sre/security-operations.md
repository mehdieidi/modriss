# Security Operations

Security operations cover how access, secrets, audit trails, and sensitive operational surfaces are
managed after deployment.

## Admin Access

- Grant admin roles only to named user accounts.
- Prefer `VIEWER` for inspection-only access.
- Use `OPERATOR` for people who need to cancel work or revoke sessions.
- Use `ADMIN` for people allowed to manage roles and disable accounts.
- Remove bootstrap admin configuration after durable admin roles are granted.

## Secrets

Do not commit secrets. `.env` is for local development and is ignored by Git.

Production secrets should come from:

- orchestrator secrets,
- a cloud secret manager,
- injected environment variables from CI/CD,
- or an equivalent controlled store.

Sensitive values include:

- database password,
- AI provider keys,
- Grafana admin password,
- LocalStack token if used,
- any future OAuth or SMTP credentials.

## Network Exposure

Never expose these directly to the public internet:

- PostgreSQL,
- Prometheus,
- Loki,
- Promtail,
- Actuator endpoints,
- Docker socket,
- LocalStack,
- Grafana without strong authentication and TLS.

The admin app should be protected by HTTPS and preferably by an additional network or identity
boundary.

## Audit

Admin mutating actions are recorded in `admin_audit_events`.

Audit fields include:

- actor,
- action,
- target type,
- target ID,
- reason,
- details,
- request ID,
- timestamp.

Operationally, audit events should answer:

1. Who changed access?
2. Who disabled or enabled a user?
3. Who revoked sessions?
4. Who canceled jobs or assistant turns?
5. Which request ID connects the action to logs?

## Account Response

If an account is suspected to be compromised:

1. Disable the account in the admin Users view.
2. Revoke active sessions.
3. Review recent admin audit events and project activity.
4. Rotate any exposed secrets.
5. Re-enable only after credential reset and review.

## Production Checklist

- `GRAFANA_ADMIN_PASSWORD` is not the default.
- `VARKA_ADMIN_BOOTSTRAP_EMAILS` is empty or tightly controlled after bootstrap.
- `VARKA_ALLOWED_ORIGINS` contains only trusted browser origins.
- PostgreSQL is private.
- Admin app is private or identity-protected.
- Backups are encrypted.
- Logs do not contain provider keys, passwords, or raw session tokens.
- Incident contacts and escalation paths are documented.
