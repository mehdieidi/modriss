# Incident Response

This runbook defines a practical incident process for Varka operators.

## Severity

| Severity | Meaning                                          | Examples                                                               |
| -------- | ------------------------------------------------ | ---------------------------------------------------------------------- |
| SEV1     | Broad outage or data integrity risk.             | Backend unavailable, database corrupted, authentication broken.        |
| SEV2     | Major feature unavailable or severe degradation. | Transformations stuck, assistant turns failing broadly, high 5xx rate. |
| SEV3     | Limited issue with workaround.                   | One project affected, slow admin view, isolated provider failure.      |
| SEV4     | Low-impact issue or maintenance task.            | Documentation bug, noisy non-actionable alert.                         |

## First 10 Minutes

1. Identify the user-visible symptom.
2. Check backend readiness: `/actuator/health/readiness`.
3. Check the admin Overview page.
4. Check Grafana for API errors, latency, jobs, database pool, and assistant failures.
5. Search logs by `requestId` if a user report includes one.
6. Decide severity and assign an incident owner.
7. Record a timeline with timestamps and actions taken.

## Useful Commands

```powershell
docker compose ps
docker compose logs backend --tail=200
docker compose logs postgres --tail=100
docker compose logs prometheus --tail=100
docker compose logs grafana --tail=100
```

Database inspection:

```powershell
docker exec -it varka-postgres-1 psql -U varka -d varka
```

Common SQL probes:

```sql
select status, count(*)
from mde_jobs
group by status
order by status;

select state, count(*)
from assistant_turns
group by state
order by state;

select provider, model, finish_reason, error, count(*)
from assistant_provider_calls
where started_at > now() - interval '1 hour'
group by provider, model, finish_reason, error
order by count(*) desc;

select version, description, success
from flyway_schema_history
order by installed_rank desc;
```

## Mitigation Patterns

| Symptom                        | Possible mitigation                                                                                                                                               |
| ------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Backend not ready              | Check database health, Flyway errors, environment variables, and backend logs.                                                                                    |
| Users cannot login             | Check auth errors, disabled user state, session expiry, and database connectivity.                                                                                |
| Jobs stuck                     | Use admin Jobs view, inspect `mde_jobs`, cancel queued/running jobs if needed.                                                                                    |
| Assistant turns stuck          | Use admin Assistant view, request cancellation, inspect workflow/work-item state, provider finish reason/error, request deadline, and circuit state.              |
| Assistant generation truncated | Preserve the failed turn/report, inspect completion usage and `finish_reason`, and reduce bounded work; do not retry the same oversized semantic request blindly. |
| High API latency               | Check database pool, slow queries, JVM memory, and active job load.                                                                                               |
| Log search missing             | Check backend file logging, Promtail status, and Loki readiness.                                                                                                  |

## Communication

For each incident, record:

- Start time and detection source.
- Severity.
- Impacted users/features.
- Current mitigation.
- Next update time.
- Resolution time.
- Follow-up actions.

## Post-Incident Review

After SEV1/SEV2 incidents:

1. Summarize what happened.
2. Identify why detection did or did not work.
3. Identify why mitigation was fast or slow.
4. Add or improve alerts and dashboards.
5. Add tests or safety checks where appropriate.
6. Track concrete follow-up work with owners.
