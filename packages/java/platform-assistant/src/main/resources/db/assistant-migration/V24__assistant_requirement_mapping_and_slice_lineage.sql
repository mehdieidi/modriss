ALTER TABLE assistant_turns
    ADD COLUMN IF NOT EXISTS parent_turn_id text REFERENCES assistant_turns (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS assistant_turns_parent_turn_idx ON assistant_turns (parent_turn_id, accepted_at);

ALTER TABLE assistant_element_provenance
    ADD COLUMN IF NOT EXISTS requirement_id text;
CREATE INDEX IF NOT EXISTS assistant_element_provenance_requirement_idx
    ON assistant_element_provenance (turn_id, requirement_id)
    WHERE requirement_id IS NOT NULL;
