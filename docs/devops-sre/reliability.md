# Reliability Model

Reliability work should start with explicit expectations. Varka has several different user-visible
surfaces, so one generic uptime number is not enough.

## Critical User Journeys

1. User can register/login and open the modeling app.
2. User can create and update projects.
3. User can save, validate, transform, and generate models.
4. User can download generated artifacts.
5. Assistant turns are accepted, tracked, and either complete or fail visibly.
6. Operators can inspect state and take safe action through the admin app.

## Suggested SLIs

| SLI                      | Measurement                                                              |
| ------------------------ | ------------------------------------------------------------------------ |
| API availability         | Percentage of non-5xx responses for user-facing API routes.              |
| API latency              | p95 latency for HTTP requests.                                           |
| Job success              | Percentage of MDE jobs ending in successful states.                      |
| Job freshness            | Percentage of queued/running jobs completing within timeout.             |
| Assistant success        | Percentage of accepted assistant turns ending in useful terminal states. |
| Readiness                | Percentage of successful readiness probe checks.                         |
| Admin audit completeness | Percentage of admin mutating actions with audit events.                  |

## Suggested SLO Starting Points

These are starting points, not promises. Adjust them after real usage data exists.

| Journey                   | Starting SLO                                                    |
| ------------------------- | --------------------------------------------------------------- |
| Backend API availability  | 99.0% monthly for non-maintenance windows.                      |
| Backend API p95 latency   | Under 1s for ordinary CRUD/list routes.                         |
| MDE job completion        | 95% of accepted jobs finish before configured job timeout.      |
| Assistant turn visibility | 99% of accepted turns are visible in status or event endpoints. |
| Admin action audit        | 100% of admin mutating actions create audit events.             |

## Error Budgets

An error budget is the acceptable unreliability implied by an SLO. If a service burns the budget too
quickly, pause risky feature work and spend engineering time on reliability.

Examples:

- Repeated failed jobs should trigger investigation before adding more transformation complexity.
- Provider outage or high assistant failure rate should shift focus to circuit-breaker behavior,
  fallback messaging, and user-visible recovery.
- Rising p95 latency should trigger database query review and capacity checks.

## Capacity Signals

Watch these before scaling:

- HTTP request rate and p95 latency.
- Hikari active/idle/pending connections.
- JVM heap usage after garbage collection.
- PostgreSQL CPU, memory, storage, and connection count.
- MDE job queue depth and job duration.
- Assistant provider latency and token usage.

## Reliability Practices

- Add timeouts to all external calls.
- Bound queue sizes and file sizes.
- Prefer idempotency keys for retried job submissions.
- Keep long-running work observable through durable job/turn records.
- Keep admin actions reversible or at least auditable.
- Write runbooks for every alert before treating it as production-ready.
