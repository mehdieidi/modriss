# Backup and Recovery

PostgreSQL is the primary persistent system of record for MODRISS runtime state.

## What Must Be Backed Up

| Data                 | Location                                                                    |
| -------------------- | --------------------------------------------------------------------------- |
| Users and sessions   | PostgreSQL tables `users`, `auth_sessions`.                                 |
| Projects and members | PostgreSQL tables `projects`, `project_members`, `project_active_models`.   |
| Models               | PostgreSQL table `models`.                                                  |
| Artifacts            | PostgreSQL tables `artifacts`, `artifact_files`.                            |
| Jobs                 | PostgreSQL tables `mde_jobs`, `mde_job_diagnostics`, `mde_job_idempotency`. |
| Assistant state      | PostgreSQL assistant tables.                                                |
| Admin audit          | PostgreSQL table `admin_audit_events`.                                      |
| Upload files         | `modriss-backend-uploads` volume or production upload storage.              |

## Local Backup

The repository includes helper scripts:

```powershell
bash deploy/scripts/backup-postgres.sh
```

The script writes a timestamped PostgreSQL custom-format dump.

## Local Restore

```powershell
.\deploy\scripts\restore-postgres.ps1 -BackupFile backups\modriss-YYYYMMDDTHHMMSSZ.dump
```

Restore into a non-production environment first when testing migrations or recovery.

## Recovery Objectives

Define these per environment:

| Objective | Meaning                                     |
| --------- | ------------------------------------------- |
| RPO       | Maximum acceptable data loss window.        |
| RTO       | Maximum acceptable time to restore service. |

Suggested starting point for early production:

- RPO: 24 hours.
- RTO: 4 hours.

Tighten these only after backup automation and restore drills are proven.

## Restore Drill

At least periodically:

1. Create a backup from a representative database.
2. Restore into a clean PostgreSQL instance.
3. Start the backend against the restored database.
4. Confirm Flyway status.
5. Login with a test account.
6. Open projects, models, jobs, assistant history, and admin audit views.
7. Document restore duration and failures.

## Retention

For production, choose retention based on data sensitivity and storage cost. A reasonable starting
policy:

- Daily backups for 14 days.
- Weekly backups for 8 weeks.
- Monthly backups for 12 months if compliance or operational needs require it.

Encrypt backups and restrict restore permissions.
