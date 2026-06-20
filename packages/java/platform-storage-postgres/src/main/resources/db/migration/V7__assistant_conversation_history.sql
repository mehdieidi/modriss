CREATE INDEX IF NOT EXISTS assistant_threads_scope_updated_idx
    ON assistant_threads (user_id, project_id, level, updated_at DESC);
