-- Event cursors are replay contracts. Reject any accidental duplicate sequence per durable turn.
CREATE UNIQUE INDEX IF NOT EXISTS assistant_turn_events_turn_sequence_idx
    ON assistant_turn_events (turn_id, sequence);
