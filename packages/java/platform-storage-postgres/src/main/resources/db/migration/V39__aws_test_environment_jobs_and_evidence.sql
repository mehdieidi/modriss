-- Execution records and immutable deployment inputs are retained for case-study evidence.
CREATE TABLE test_environment_jobs (
    id text PRIMARY KEY,
    project_id text NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    kind text NOT NULL,
    status text NOT NULL,
    deployment_id text,
    document jsonb NOT NULL,
    snapshot jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX test_environment_jobs_project_created_idx
    ON test_environment_jobs(project_id, created_at DESC);
CREATE UNIQUE INDEX test_environment_one_active_job_idx
    ON test_environment_jobs(project_id) WHERE status IN ('QUEUED', 'RUNNING');
