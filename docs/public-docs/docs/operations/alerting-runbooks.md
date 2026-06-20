# Alerting Runbooks

Operational alert rules and response procedures for Modless production deployments.

## Prerequisites

- Prometheus scraping `http://<backend>:8080/actuator/prometheus`
- Grafana dashboard: [`infra/grafana/modless-overview.json`](../../../../infra/grafana/modless-overview.json)
- Log aggregation with `requestId` (and `traceId` when tracing is enabled)
- On-call rotation and escalation path defined by your organization

## Alert catalog

### `ModlessBackendDown`

| Field         | Value                                          |
| ------------- | ---------------------------------------------- |
| **Condition** | `up{job="modless-backend"} == 0` for 2 minutes |
| **Severity**  | Critical                                       |
| **Runbook**   | [Backend unavailable](#backend-unavailable)    |

### `ModlessReadinessFailing`

| Field         | Value                                                        |
| ------------- | ------------------------------------------------------------ |
| **Condition** | Probe to `/actuator/health/readiness` not `UP` for 3 minutes |
| **Severity**  | Critical                                                     |
| **Runbook**   | [Readiness failing](#readiness-failing)                      |

### `ModlessHighErrorRate`

| Field         | Value                                    |
| ------------- | ---------------------------------------- |
| **Condition** | 5xx rate > 5% of requests over 5 minutes |
| **Severity**  | Warning → Critical if > 15%              |
| **Runbook**   | [Elevated 5xx rate](#elevated-5xx-rate)  |

### `ModlessDatabasePoolExhausted`

| Field         | Value                                                                        |
| ------------- | ---------------------------------------------------------------------------- |
| **Condition** | `hikaricp_connections_active / hikaricp_connections_max > 0.9` for 5 minutes |
| **Severity**  | Warning                                                                      |
| **Runbook**   | [Database pool pressure](#database-pool-pressure)                            |

### `ModlessMdeJobFailures`

| Field         | Value                                              |
| ------------- | -------------------------------------------------- |
| **Condition** | `increase(modless_mde_jobs_failed_total[15m]) > 5` |
| **Severity**  | Warning                                            |
| **Runbook**   | [MDE job failures](#mde-job-failures)              |

### `ModlessAssistantCircuitOpen`

| Field         | Value                                                    |
| ------------- | -------------------------------------------------------- |
| **Condition** | `increase(modless_assistant_circuit_open_total[5m]) > 0` |
| **Severity**  | Warning                                                  |
| **Runbook**   | [Assistant circuit open](#assistant-circuit-open)        |

### `ModlessPostgresDiskLow`

| Field         | Value                                       |
| ------------- | ------------------------------------------- |
| **Condition** | Database volume < 15% free                  |
| **Severity**  | Warning → Critical if < 5%                  |
| **Runbook**   | [Database disk space](#database-disk-space) |

---

## Runbook procedures

### Backend unavailable

1. Check container/pod status and recent deploy events.
2. Inspect backend logs for startup failures (Flyway, datasource, port binding).
3. Verify PostgreSQL is reachable from the backend network.
4. Roll back to last known-good image if a deploy coincides with the incident.
5. Post-incident: capture `requestId` samples and open a tracking issue if regression.

### Readiness failing

1. Hit `/actuator/health/readiness` and `/actuator/health` directly.
2. If `ai` health contributor is `DOWN`, confirm provider proxy reachability or disable AI temporarily.
3. Check database connectivity and migration version (`flyway_schema_history`).
4. Restart backend after fixing root cause; readiness should return within one probe interval.

### Elevated 5xx rate

1. Split errors by route using access logs (`path`, `status`, `durationMs`).
2. Check recent MDE job failures and transformation diagnostics.
3. Look for database deadlocks or pool timeouts in logs.
4. Scale backend replicas if CPU saturation is the cause; otherwise fix the failing dependency.

### Database pool pressure

1. Review slow queries and long-running MDE jobs holding connections.
2. Temporarily reduce `MODLESS_MDE_MAX_CONCURRENT_JOBS` if jobs dominate pool usage.
3. Increase `MODLESS_DB_MAX_POOL_SIZE` only after confirming PostgreSQL `max_connections` headroom.
4. Investigate connection leaks if active connections never return to idle.

### MDE job failures

1. Query failed jobs via API or `mde_jobs` table; read `mde_job_diagnostics`.
2. Reproduce with sample models under `mde/samples/` in a staging environment.
3. Check Epsilon runner timeouts (`MODLESS_MDE_EXECUTION_TIMEOUT`, `MODLESS_MDE_JOB_TIMEOUT`).
4. File a bug if validation/transformation rules regressed; communicate workaround to users.

### Assistant circuit open

1. Confirm provider API status and credential validity.
2. Check proxy settings (`MODLESS_AI_PROXY_*`) and outbound network policy.
3. Review `assistant.circuit.rejected` metrics and recent provider errors in logs.
4. Wait for `MODLESS_AI_CIRCUIT_OPEN_DURATION` to elapse or restart after fixing provider issues.
5. Set `MODLESS_AI_ENABLED=false` if the assistant must remain disabled during provider outage.

### Database disk space

1. Check volume usage on the PostgreSQL host.
2. Run `VACUUM (ANALYZE)` on large tables if bloat is suspected.
3. Purge expired staged imports and obsolete job artifacts per retention policy.
4. Execute backup then expand volume; verify restore procedure quarterly.

## Example Prometheus rules

Save as `infra/prometheus/modless-alerts.yml` and load into Prometheus:

```yaml
groups:
  - name: modless
    rules:
      - alert: ModlessBackendDown
        expr: up{job="modless-backend"} == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: Modless backend is unreachable

      - alert: ModlessHighErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
          /
          sum(rate(http_server_requests_seconds_count[5m])) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: Modless 5xx error rate above 5%

      - alert: ModlessAssistantCircuitOpen
        expr: increase(modless_assistant_circuit_open_total[5m]) > 0
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: Assistant circuit breaker rejected provider calls
```

## Related documentation

- [Deployment guide](deployment.md)
- [Security controls](security.md)
- [Testing guide](testing.md)
- [Backup scripts](../../../../deploy/scripts/backup-postgres.sh)
