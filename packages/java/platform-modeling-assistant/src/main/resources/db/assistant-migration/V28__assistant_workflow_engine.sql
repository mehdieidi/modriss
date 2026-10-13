-- Durable workflow state is deliberately separate from the original turn row.  A request remains
-- one turn throughout retries/resume instead of creating child turns with control text.
CREATE TABLE assistant_workflows (
    turn_id text PRIMARY KEY REFERENCES assistant_turns(id) ON DELETE CASCADE,
    workflow_kind text NOT NULL,
    phase text NOT NULL,
    current_work_item_id text,
    plan jsonb NOT NULL DEFAULT '{}'::jsonb,
    updated_at timestamptz NOT NULL
);

CREATE TABLE assistant_work_items (
    id text PRIMARY KEY,
    turn_id text NOT NULL REFERENCES assistant_turns(id) ON DELETE CASCADE,
    ordinal integer NOT NULL,
    label text NOT NULL,
    status text NOT NULL,
    idempotency_key text NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE (turn_id, ordinal),
    UNIQUE (turn_id, idempotency_key)
);

CREATE TABLE assistant_source_facts (
    id text PRIMARY KEY,
    turn_id text NOT NULL REFERENCES assistant_turns(id) ON DELETE CASCADE,
    kind text NOT NULL,
    status text NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    assumption text
);

CREATE TABLE assistant_validation_attempts (
    id bigserial PRIMARY KEY,
    turn_id text NOT NULL REFERENCES assistant_turns(id) ON DELETE CASCADE,
    work_item_id text REFERENCES assistant_work_items(id) ON DELETE SET NULL,
    attempt integer NOT NULL,
    valid boolean NOT NULL,
    diagnostics jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_at timestamptz NOT NULL,
    UNIQUE (turn_id, work_item_id, attempt)
);

ALTER TABLE assistant_checkpoints ADD COLUMN IF NOT EXISTS idempotency_key text;
ALTER TABLE assistant_checkpoints ADD COLUMN IF NOT EXISTS ordinal integer;
ALTER TABLE assistant_checkpoints ADD COLUMN IF NOT EXISTS label text;
ALTER TABLE assistant_checkpoints ADD COLUMN IF NOT EXISTS base_revision bigint;
ALTER TABLE assistant_checkpoints ADD COLUMN IF NOT EXISTS candidate_hash text;
ALTER TABLE assistant_checkpoints ADD COLUMN IF NOT EXISTS status text NOT NULL DEFAULT 'COMMITTED';
ALTER TABLE assistant_checkpoints ADD COLUMN IF NOT EXISTS validation_summary jsonb NOT NULL DEFAULT '{}'::jsonb;
UPDATE assistant_checkpoints
SET ordinal = numbered.ordinal,
    idempotency_key = COALESCE(assistant_checkpoints.idempotency_key, 'legacy-' || assistant_checkpoints.id::text),
    label = COALESCE(label, 'Checkpoint ' || numbered.ordinal::text)
FROM (SELECT id, row_number() OVER (PARTITION BY turn_id ORDER BY id) AS ordinal FROM assistant_checkpoints) numbered
WHERE numbered.id = assistant_checkpoints.id;
ALTER TABLE assistant_checkpoints ALTER COLUMN ordinal SET NOT NULL;
ALTER TABLE assistant_checkpoints ALTER COLUMN idempotency_key SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS assistant_checkpoints_turn_key_idx
    ON assistant_checkpoints(turn_id, idempotency_key);
CREATE INDEX IF NOT EXISTS assistant_work_items_turn_idx ON assistant_work_items(turn_id, ordinal);
