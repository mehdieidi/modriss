# Admin Control Plane

The admin app is a standalone React application in `apps/admin`. It is served by its own container
and talks to protected backend routes under `/api/admin/**`.

## Access Model

Admin access is separate from ordinary application authentication.

- Users still authenticate through `/api/auth/login`.
- Admin authorization is granted through `admin_roles`.
- Supported roles are `ADMIN`, `OPERATOR`, and `VIEWER`.
- Disabled users are rejected by authenticated backend flows.
- Guest accounts appear in the Users view with their prompt allowance and usage. Administrators can
  disable guests and revoke their sessions through the same audited controls used for registered users.
  An `ADMIN` can also permanently delete a guest and the projects it owns; this is audited and
  intentionally unavailable for registered accounts.
- Bootstrap access is controlled by `MODRISS_ADMIN_BOOTSTRAP_ENABLED` and
  `MODRISS_ADMIN_BOOTSTRAP_EMAILS`, and `MODRISS_ADMIN_BOOTSTRAP_TOKEN`.

Bootstrap flow:

1. Set `MODRISS_ADMIN_BOOTSTRAP_ENABLED=true` in `.env`.
2. Set `MODRISS_ADMIN_BOOTSTRAP_EMAILS` to the first administrator email.
3. Set `MODRISS_ADMIN_BOOTSTRAP_TOKEN` to a long random setup token.
4. Start the stack.
5. Register or login as one of those emails in the user app.
6. Open `http://admin.localhost:8088`.
7. Enter the same email, password, and one-time setup token.
8. Grant additional admin roles from the Users view.
9. Set `MODRISS_ADMIN_BOOTSTRAP_ENABLED=false` and remove the bootstrap email and token once durable
   admin roles exist.

Bootstrap is intentionally one-time: it only grants the first `ADMIN` role while `admin_roles` is
empty. It requires a configured email and a private setup token. After any admin role exists,
bootstrap cannot grant another administrator. The bootstrap grant is written to
`admin_audit_events`.

## Role Intent

| Role       | Intended use                                                                              |
| ---------- | ----------------------------------------------------------------------------------------- |
| `ADMIN`    | Full control: grant/revoke admin roles, disable users, enable users, revoke sessions.     |
| `OPERATOR` | Operational control: revoke sessions, cancel jobs, request assistant turn cancellation.   |
| `VIEWER`   | Read-only inspection of users, projects, models, jobs, assistant turns, and audit events. |

## Admin Views

| View      | Purpose                                                                                                 |
| --------- | ------------------------------------------------------------------------------------------------------- |
| Overview  | Counts for users, sessions, projects, models, artifacts, active jobs, failed jobs, and assistant turns. |
| Users     | Account status, active sessions, project footprint, admin roles, disable/enable controls.               |
| Projects  | Project ownership, member counts, model counts, artifact counts, job counts.                            |
| Models    | Persisted model revisions by project and level.                                                         |
| Jobs      | MDE transformation/generation job state and cancellation for queued/running jobs.                       |
| Assistant | Durable assistant turns, provider usage, token counts, and cancellation requests.                       |
| Audit     | Admin actions with actor, target, reason, request ID, details, and timestamp.                           |

## Safety Rules

The admin panel intentionally does not expose raw database editing. Administrative actions should
preserve domain invariants:

- Revoke sessions instead of deleting session rows manually.
- Disable users before considering hard deletion.
- Cancel jobs through explicit status transitions.
- Request assistant turn cancellation instead of deleting turn history.
- Keep destructive actions auditable with a reason and request ID.

## Relevant Tables

| Table                | Purpose                             |
| -------------------- | ----------------------------------- |
| `admin_roles`        | Admin role grants.                  |
| `disabled_users`     | Disabled account state and reason.  |
| `admin_audit_events` | Durable administrative audit trail. |
| `auth_sessions`      | Active user sessions.               |
| `mde_jobs`           | Transformation and generation jobs. |
| `assistant_turns`    | Durable assistant turns.            |

## Production Notes

- Put the admin app behind HTTPS.
- Restrict admin access by network policy, VPN, identity-aware proxy, or equivalent control.
- Set a strong `GRAFANA_ADMIN_PASSWORD`; do not use the local default in production.
- Keep `MODRISS_ADMIN_BOOTSTRAP_ENABLED=false` and `MODRISS_ADMIN_BOOTSTRAP_TOKEN` empty after the
  first administrator is created.
- Do not expose PostgreSQL, Loki, Prometheus, or Actuator directly to the public internet.
- Review audit events regularly and export them to a longer-retention store if required.
