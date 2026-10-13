-- Squashed schema for the durable, non-streaming assistant-turn runtime.
-- This version is unique within the shared application Flyway locations. It is idempotent so
-- existing installations can apply the consolidated assistant schema after the old versions
-- have been removed; intentionally squashed historical versions are ignored by Flyway config.

DROP TABLE IF EXISTS assistant_pending_interactions;
DROP TABLE IF EXISTS assistant_model_contexts;
DROP TABLE IF EXISTS assistant_retrieval_documents;
DROP TABLE IF EXISTS assistant_source_evidence;
DROP TABLE IF EXISTS assistant_metamodel_contracts;
DROP TABLE IF EXISTS assistant_turn_diagnostics;
DROP TABLE IF EXISTS assistant_turn_executions;

CREATE TABLE IF NOT EXISTS assistant_threads
(
    id              text PRIMARY KEY,
    user_id         text        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    project_id      text        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    level           text        NOT NULL CHECK (level IN ('CIM', 'PIM', 'PSM')),
    active_model_id text,
    active_revision bigint,
    title           text        NOT NULL,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS assistant_threads_project_level_updated_idx
    ON assistant_threads (project_id, level, updated_at DESC);
CREATE INDEX IF NOT EXISTS assistant_threads_user_updated_idx
    ON assistant_threads (user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS assistant_threads_scope_updated_idx
    ON assistant_threads (user_id, project_id, level, updated_at DESC);

-- Assistant history/proposals are intentionally discarded by this clean migration. Projects and
-- models are not touched. Back up the previous assistant tables before applying in production.
CREATE TABLE IF NOT EXISTS assistant_turns
(
    id                     text PRIMARY KEY,
    thread_id              text        NOT NULL REFERENCES assistant_threads (id) ON DELETE CASCADE,
    user_id                text        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    project_id             text        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    level                  text        NOT NULL CHECK (level IN ('CIM', 'PIM', 'PSM')),
    model_id               text,
    expected_revision      bigint,
    idempotency_key        text        NOT NULL,
    message                text        NOT NULL,
    source_text            text,
    selected_element_ids   jsonb       NOT NULL DEFAULT '[]'::jsonb,
    state                  text        NOT NULL CHECK (state IN ('QUEUED','RUNNING','SUCCEEDED','PARTIAL','NEEDS_INPUT','NEEDS_CONFIRMATION','CONFLICTED','CANCELLED','TIMED_OUT','FAILED')),
    accepted_at            timestamptz NOT NULL,
    deadline_at            timestamptz NOT NULL,
    started_at             timestamptz,
    completed_at           timestamptz,
    worker_id              text,
    lease_until            timestamptz,
    cancellation_requested boolean     NOT NULL DEFAULT false,
    revision               bigint,
    checkpoint_count       integer     NOT NULL DEFAULT 0,
    saved_element_count    integer     NOT NULL DEFAULT 0,
    coverage_percent       integer,
    remaining_work         text,
    final_message          text,
    provider_calls         integer     NOT NULL DEFAULT 0,
    prompt_tokens          bigint      NOT NULL DEFAULT 0,
    completion_tokens      bigint      NOT NULL DEFAULT 0,
    UNIQUE (thread_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS assistant_turns_claim_idx ON assistant_turns (state, deadline_at, lease_until, accepted_at);
CREATE UNIQUE INDEX IF NOT EXISTS assistant_turns_one_active_model_idx ON assistant_turns (thread_id, model_id) WHERE state IN ('QUEUED','RUNNING');

CREATE TABLE IF NOT EXISTS assistant_turn_events
(
    id          bigserial PRIMARY KEY,
    turn_id     text        NOT NULL REFERENCES assistant_turns (id) ON DELETE CASCADE,
    sequence    bigint      NOT NULL DEFAULT 0,
    type        text        NOT NULL,
    occurred_at timestamptz NOT NULL,
    payload     jsonb       NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS assistant_turn_events_replay_idx ON assistant_turn_events (turn_id, id);

CREATE TABLE IF NOT EXISTS assistant_checkpoints
(
    id bigserial PRIMARY KEY, turn_id text NOT NULL REFERENCES assistant_turns(id) ON DELETE CASCADE,
    model_id text NOT NULL, revision bigint NOT NULL, inverse_patch jsonb, created_at timestamptz NOT NULL
);
CREATE TABLE IF NOT EXISTS assistant_source_units
(
    id text PRIMARY KEY, turn_id text NOT NULL REFERENCES assistant_turns(id) ON DELETE CASCADE,
    ordinal integer NOT NULL, start_offset integer NOT NULL, end_offset integer NOT NULL, content text NOT NULL,
    status text NOT NULL, reason text
);
CREATE TABLE IF NOT EXISTS assistant_element_provenance
(
    id bigserial PRIMARY KEY, turn_id text NOT NULL REFERENCES assistant_turns(id) ON DELETE CASCADE,
    element_id text NOT NULL, source_unit_id text REFERENCES assistant_source_units(id) ON DELETE SET NULL,
    kind text NOT NULL, assumption text
);
CREATE TABLE IF NOT EXISTS assistant_provider_calls
(
    id bigserial PRIMARY KEY, turn_id text NOT NULL REFERENCES assistant_turns(id) ON DELETE CASCADE,
    provider text, model text, started_at timestamptz NOT NULL, latency_ms bigint, prompt_tokens bigint,
    completion_tokens bigint, finish_reason text, error text
);

CREATE TABLE IF NOT EXISTS assistant_messages
(
    id         text PRIMARY KEY,
    thread_id  text        NOT NULL REFERENCES assistant_threads (id) ON DELETE CASCADE,
    role       text        NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    content    text        NOT NULL,
    metadata   jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS assistant_messages_thread_created_idx
    ON assistant_messages (thread_id, created_at);

CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY
(
    conversation_id text      NOT NULL,
    content         text      NOT NULL,
    type            varchar(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp"     timestamp NOT NULL
);
CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
    ON SPRING_AI_CHAT_MEMORY (conversation_id, "timestamp");

CREATE TABLE IF NOT EXISTS assistant_thread_summaries
(
    thread_id  text PRIMARY KEY REFERENCES assistant_threads (id) ON DELETE CASCADE,
    summary    text        NOT NULL,
    message_id text        REFERENCES assistant_messages (id) ON DELETE SET NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE IF NOT EXISTS assistant_proposals
(
    id                 text PRIMARY KEY,
    thread_id          text        NOT NULL REFERENCES assistant_threads (id) ON DELETE CASCADE,
    project_id         text        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    model_id           text,
    model_revision     bigint      NOT NULL,
    risk_level         text        NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    semantic_patch     jsonb       NOT NULL,
    inverse_patch      jsonb,
    validation_summary jsonb       NOT NULL,
    citations          jsonb       NOT NULL DEFAULT '[]'::jsonb,
    status             text        NOT NULL CHECK (status IN ('APPLIED', 'FAILED', 'UNDONE')),
    created_at         timestamptz NOT NULL,
    decided_at         timestamptz
);
CREATE INDEX IF NOT EXISTS assistant_proposals_thread_created_idx
    ON assistant_proposals (thread_id, created_at DESC);

CREATE TABLE IF NOT EXISTS assistant_action_audits
(
    id          text PRIMARY KEY,
    proposal_id text        REFERENCES assistant_proposals (id) ON DELETE SET NULL,
    turn_id     text        REFERENCES assistant_turns (id) ON DELETE CASCADE,
    project_id  text        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    actor_id    text        NOT NULL REFERENCES users (id),
    action      text        NOT NULL,
    details     jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at  timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS assistant_action_audits_project_created_idx
    ON assistant_action_audits (project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS assistant_action_audits_turn_created_idx
    ON assistant_action_audits (turn_id, created_at DESC);

CREATE TABLE IF NOT EXISTS assistant_rate_limits
(
    key          text PRIMARY KEY,
    window_start timestamptz NOT NULL,
    count        integer     NOT NULL CHECK (count >= 0)
);
