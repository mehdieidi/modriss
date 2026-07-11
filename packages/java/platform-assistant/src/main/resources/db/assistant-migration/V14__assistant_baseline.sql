-- Squashed schema for the single streaming agent runtime.
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
    project_id  text        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    actor_id    text        NOT NULL REFERENCES users (id),
    action      text        NOT NULL,
    details     jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at  timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS assistant_action_audits_project_created_idx
    ON assistant_action_audits (project_id, created_at DESC);

CREATE TABLE IF NOT EXISTS assistant_rate_limits
(
    key          text PRIMARY KEY,
    window_start timestamptz NOT NULL,
    count        integer     NOT NULL CHECK (count >= 0)
);
