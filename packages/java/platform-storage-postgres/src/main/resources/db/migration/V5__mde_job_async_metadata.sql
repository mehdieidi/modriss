ALTER TABLE mde_jobs
    ADD COLUMN validation_result jsonb,
    ADD COLUMN timings jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN idempotency_key text,
    ADD COLUMN fingerprint text;

CREATE TABLE mde_job_idempotency
(
    scope_hash  text PRIMARY KEY,
    job_id      text NOT NULL REFERENCES mde_jobs (id) ON DELETE CASCADE,
    project_id  text NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    fingerprint text NOT NULL
);
