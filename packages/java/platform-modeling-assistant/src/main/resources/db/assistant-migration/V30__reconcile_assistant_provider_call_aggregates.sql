-- Per-call rows are authoritative. Older cancellation paths could finish after the last provider
-- response was recorded but before the aggregate turn counters were updated.
UPDATE assistant_turns AS turn
SET provider_calls = ledger.provider_calls,
    prompt_tokens = ledger.prompt_tokens,
    completion_tokens = ledger.completion_tokens
FROM (
  SELECT turn_id,
         count(*) AS provider_calls,
         COALESCE(sum(prompt_tokens), 0) AS prompt_tokens,
         COALESCE(sum(completion_tokens), 0) AS completion_tokens
  FROM assistant_provider_calls
  GROUP BY turn_id
) AS ledger
WHERE turn.id = ledger.turn_id
  AND (turn.provider_calls, turn.prompt_tokens, turn.completion_tokens)
      IS DISTINCT FROM (ledger.provider_calls, ledger.prompt_tokens, ledger.completion_tokens);
