-- A provider response can race with a requested cancellation. Before the worker gave cancellation
-- precedence, a concurrent provider error could incorrectly leave the terminal row as FAILED.
WITH corrected AS (
  UPDATE assistant_turns AS turn
  SET state = 'CANCELLED',
      worker_id = NULL,
      lease_until = NULL,
      final_message = COALESCE((
        SELECT 'Stopped after checkpoint ' || checkpoint.ordinal || '. Revision '
          || checkpoint.revision || ' is saved.'
        FROM assistant_checkpoints AS checkpoint
        WHERE checkpoint.turn_id = turn.id
          AND checkpoint.status = 'COMMITTED'
        ORDER BY checkpoint.id DESC
        LIMIT 1
      ), 'Assistant turn was cancelled before a model checkpoint was saved.')
  WHERE turn.state = 'FAILED'
    AND turn.cancellation_requested = true
    AND turn.completed_at IS NOT NULL
  RETURNING turn.id, turn.final_message
)
INSERT INTO assistant_turn_events(turn_id, sequence, type, occurred_at, payload)
SELECT corrected.id,
       COALESCE((
         SELECT max(event.sequence) + 1
         FROM assistant_turn_events AS event
         WHERE event.turn_id = corrected.id
       ), 1),
       'turn.completed',
       now(),
       jsonb_build_object('state', 'CANCELLED', 'message', corrected.final_message)
FROM corrected;
