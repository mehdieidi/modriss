-- Existing installations may already have applied the squashed V14 baseline. Keep durable-turn
-- action linkage additive so cancellation/confirmation/checkpoint audit records remain available.
ALTER TABLE assistant_action_audits
    ADD COLUMN IF NOT EXISTS turn_id text REFERENCES assistant_turns (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS assistant_action_audits_turn_created_idx
    ON assistant_action_audits (turn_id, created_at DESC);
