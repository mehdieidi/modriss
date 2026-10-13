CREATE TABLE assistant_turn_context_cache
(
    turn_id             text PRIMARY KEY REFERENCES assistant_turns (id) ON DELETE CASCADE,
    selected_source_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    contract_closures   jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_at          timestamptz NOT NULL
);
