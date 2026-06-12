CREATE
EXTENSION IF NOT EXISTS vector;

CREATE TABLE assistant_threads
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
CREATE INDEX assistant_threads_project_level_updated_idx
    ON assistant_threads (project_id, level, updated_at DESC);
CREATE INDEX assistant_threads_user_updated_idx
    ON assistant_threads (user_id, updated_at DESC);

CREATE TABLE assistant_messages
(
    id         text PRIMARY KEY,
    thread_id  text        NOT NULL REFERENCES assistant_threads (id) ON DELETE CASCADE,
    role       text        NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    content    text        NOT NULL,
    metadata   jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL
);
CREATE INDEX assistant_messages_thread_created_idx
    ON assistant_messages (thread_id, created_at);

CREATE TABLE SPRING_AI_CHAT_MEMORY
(
    conversation_id text      NOT NULL,
    content         text      NOT NULL,
    type            varchar(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp"     timestamp NOT NULL
);
CREATE INDEX SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
    ON SPRING_AI_CHAT_MEMORY (conversation_id, "timestamp");

CREATE TABLE assistant_thread_summaries
(
    thread_id  text PRIMARY KEY REFERENCES assistant_threads (id) ON DELETE CASCADE,
    summary    text        NOT NULL,
    message_id text        REFERENCES assistant_messages (id) ON DELETE SET NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE assistant_proposals
(
    id                 text PRIMARY KEY,
    thread_id          text        NOT NULL REFERENCES assistant_threads (id) ON DELETE CASCADE,
    project_id         text        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    model_id           text        NOT NULL,
    model_revision     bigint      NOT NULL,
    risk_level         text        NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    approval_required  boolean     NOT NULL,
    semantic_patch     jsonb       NOT NULL,
    inverse_patch      jsonb,
    validation_summary jsonb       NOT NULL,
    citations          jsonb       NOT NULL DEFAULT '[]'::jsonb,
    status             text        NOT NULL CHECK (status IN
                                                   ('PROPOSED', 'APPROVED', 'REJECTED', 'APPLIED',
                                                    'FAILED', 'UNDONE')),
    created_at         timestamptz NOT NULL,
    decided_at         timestamptz
);
CREATE INDEX assistant_proposals_thread_created_idx
    ON assistant_proposals (thread_id, created_at DESC);

CREATE TABLE assistant_action_audits
(
    id          text PRIMARY KEY,
    proposal_id text        REFERENCES assistant_proposals (id) ON DELETE SET NULL,
    project_id  text        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    actor_id    text        NOT NULL REFERENCES users (id),
    action      text        NOT NULL,
    details     jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at  timestamptz NOT NULL
);
CREATE INDEX assistant_action_audits_project_created_idx
    ON assistant_action_audits (project_id, created_at DESC);

CREATE TABLE assistant_retrieval_documents
(
    id          text PRIMARY KEY,
    scope       text        NOT NULL,
    source      text        NOT NULL,
    source_hash text        NOT NULL,
    title       text        NOT NULL,
    content     text        NOT NULL,
    metadata    jsonb       NOT NULL DEFAULT '{}'::jsonb,
    embedding   vector(384),
    updated_at  timestamptz NOT NULL,
    UNIQUE (scope, source, source_hash, title)
);
CREATE INDEX assistant_retrieval_documents_source_hash_idx
    ON assistant_retrieval_documents (source, source_hash);
CREATE INDEX assistant_retrieval_documents_metadata_idx
    ON assistant_retrieval_documents USING gin (metadata);

CREATE TABLE assistant_model_contexts
(
    model_id           text        NOT NULL,
    project_id         text        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    level              text        NOT NULL CHECK (level IN ('CIM', 'PIM', 'PSM')),
    revision           bigint      NOT NULL,
    context_json       jsonb       NOT NULL,
    model_hash         text        NOT NULL,
    latest_issues_json jsonb       NOT NULL DEFAULT '[]'::jsonb,
    updated_at         timestamptz NOT NULL,
    PRIMARY KEY (model_id, revision)
);
CREATE INDEX assistant_model_contexts_project_level_revision_idx
    ON assistant_model_contexts (project_id, level, revision DESC);

CREATE TABLE assistant_rate_limits
(
    key          text PRIMARY KEY,
    window_start timestamptz NOT NULL,
    count        integer     NOT NULL CHECK (count >= 0)
);
