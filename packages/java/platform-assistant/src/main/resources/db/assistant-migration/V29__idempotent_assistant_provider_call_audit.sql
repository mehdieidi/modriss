-- Add migration SQL here.
ALTER TABLE assistant_provider_calls ADD COLUMN call_key text;

CREATE UNIQUE INDEX assistant_provider_calls_turn_key_idx
    ON assistant_provider_calls(turn_id, call_key)
    WHERE call_key IS NOT NULL;
