CREATE TABLE assistant_turn_executions
(
    turn_id                text PRIMARY KEY,
    idempotency_key        text UNIQUE,
    session_id             text        NOT NULL REFERENCES assistant_threads (id) ON DELETE CASCADE,
    model_id               text,
    expected_revision      bigint,
    status                 text        NOT NULL CHECK (status IN
        ('RUNNING', 'APPLIED', 'ANSWERED', 'WAITING_FOR_CHOICE', 'FAILED', 'CANCELED', 'STALE_REVISION')),
    phase                  text        NOT NULL,
    deadline_at            timestamptz NOT NULL,
    cancel_requested_at    timestamptz,
    terminal_response_json jsonb,
    error_json             jsonb,
    created_at             timestamptz NOT NULL,
    updated_at             timestamptz NOT NULL
);
CREATE INDEX assistant_turn_executions_session_created_idx
    ON assistant_turn_executions (session_id, created_at DESC);
CREATE INDEX assistant_turn_executions_status_deadline_idx
    ON assistant_turn_executions (status, deadline_at);

CREATE TABLE assistant_turn_diagnostics
(
    turn_id                    text PRIMARY KEY REFERENCES assistant_turn_executions (turn_id)
        ON DELETE CASCADE,
    session_id                 text        NOT NULL REFERENCES assistant_threads (id) ON DELETE CASCADE,
    provider_calls             integer     NOT NULL DEFAULT 0 CHECK (provider_calls >= 0),
    tool_calls                 integer     NOT NULL DEFAULT 0 CHECK (tool_calls >= 0),
    repair_attempts            integer     NOT NULL DEFAULT 0 CHECK (repair_attempts >= 0),
    phase_timings_json         jsonb       NOT NULL DEFAULT '{}'::jsonb,
    retrieval_diagnostics_json jsonb       NOT NULL DEFAULT '{}'::jsonb,
    validation_feedback_json   jsonb       NOT NULL DEFAULT '[]'::jsonb,
    created_at                 timestamptz NOT NULL
);

CREATE TABLE assistant_metamodel_contracts
(
    id             text PRIMARY KEY,
    level          text        NOT NULL CHECK (level IN ('CIM', 'PIM', 'PSM')),
    metamodel_hash text        NOT NULL,
    contract_kind  text        NOT NULL,
    eclass         text,
    feature        text,
    content_json   jsonb       NOT NULL,
    embedding      vector(384),
    updated_at     timestamptz NOT NULL,
    UNIQUE (level, metamodel_hash, contract_kind, eclass, feature)
);
CREATE INDEX assistant_metamodel_contracts_level_kind_idx
    ON assistant_metamodel_contracts (level, contract_kind, eclass);

CREATE TABLE assistant_source_evidence
(
    source_id     text PRIMARY KEY,
    session_id    text        NOT NULL REFERENCES assistant_threads (id) ON DELETE CASCADE,
    model_id      text,
    source_hash   text        NOT NULL,
    evidence_json jsonb       NOT NULL,
    coverage_json jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL
);
CREATE INDEX assistant_source_evidence_session_hash_idx
    ON assistant_source_evidence (session_id, source_hash);
