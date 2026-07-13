# ADR-004: One modeling agent

The default path is one bounded agent with deterministic tools. Parallel planner, repair, source,
and summarizer workers are excluded because they increase provider calls and lose shared context.
