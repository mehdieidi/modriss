CREATE TABLE psm_extension_records (
    project_id text NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    record_key text NOT NULL,
    payload jsonb NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, record_key)
);
