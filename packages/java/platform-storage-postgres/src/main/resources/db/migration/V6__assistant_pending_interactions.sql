CREATE TABLE assistant_pending_interactions
(
    thread_id         text PRIMARY KEY REFERENCES assistant_threads (id) ON DELETE CASCADE,
    request_payload   jsonb       NOT NULL,
    questions         jsonb       NOT NULL,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL
);
