# ADR-004: No delegated multi-agent runtime

Status: amended by the unified adaptive assistant, 2026-08-12

Varka uses one provider model without parallel planner, repair, source, summarizer, voting, or
specialist-agent workers. The current runtime does route between two internal workflows:

- an obligation-ledger, type-selection, blueprint, private-slice, independent-review prompt chain
  with deterministic Ecore compilation; and
- one bounded inspect/contract action agent.

This amendment preserves the original cost and shared-context decision while recognizing that the
runtime is no longer accurately described as only one action path. The adaptive strategy call and
independent obligation-review pass are bounded workflow stages, not delegated business agents.
Backend worker concurrency is execution capacity, not parallel LLM reasoning.
