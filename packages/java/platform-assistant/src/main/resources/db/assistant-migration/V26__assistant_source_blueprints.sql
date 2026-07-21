CREATE TABLE assistant_source_blueprints
(
    turn_id      text PRIMARY KEY REFERENCES assistant_turns (id) ON DELETE CASCADE,
    blueprint    jsonb       NOT NULL,
    next_slice   integer     NOT NULL DEFAULT 0 CHECK (next_slice >= 0),
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL
);
