CREATE TABLE model_synchronization_records (
  project_id VARCHAR(255) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  record_kind VARCHAR(32) NOT NULL,
  record_id VARCHAR(512) NOT NULL,
  payload JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (project_id, record_kind, record_id)
);

CREATE INDEX idx_model_synchronization_records_kind
  ON model_synchronization_records(project_id, record_kind, updated_at DESC);
