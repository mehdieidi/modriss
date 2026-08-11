# ADR-004: No delegated multi-agent runtime

Status: amended by the unified adaptive assistant, 2026-08-12

Varka uses one provider model without parallel planner, repair, source, summarizer, voting, or
specialist-agent workers. The current runtime does route between two internal workflows:

- a paper-style conceptual prompt chain with deterministic Ecore compilation; and
- one bounded inspect/contract action agent.

This amendment preserves the original cost and shared-context decision while recognizing that the
runtime is no longer accurately described as only one agent path. The adaptive strategy call is
workflow routing, not delegation to independent agents. Backend worker concurrency is execution
capacity, not parallel LLM reasoning.
