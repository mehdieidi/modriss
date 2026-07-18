# AI Modeling Assistant Pattern Assessment

**Reference:** Anthropic, [Building effective agents](https://www.anthropic.com/engineering/building-effective-agents), 19 December 2024.  
**Assessment date:** 18 July 2026.  
**Scope:** the current Varka assistant runtime: `AgentTurnLoop`, `AgentModelTools`, `AgenticTurnService`, and `DurableAssistantTurnWorker`.

## Conclusion

Varka is a **bounded, single autonomous agent** built from Anthropic's _augmented LLM_ building block. Its core is the article's agent loop: an LLM selects an action, receives ground truth from a tool result, and repeats until it returns a terminal action or the backend stops the turn.

It is not an ad-hoc chat wrapper. It is a custom, domain-specific implementation of a recognizable pattern:

`LLM -> strict AgentAction -> backend-validated tool -> working-model observation -> LLM`

The outer durable-turn lifecycle is a predefined **workflow**; its inner `AgentTurnLoop` is an **agent** because the model dynamically chooses its allowed action. This follows the article's distinction between code-orchestrated workflows and model-directed tool use.

The companion diagram is [29-anthropic-effective-agents-pattern-assessment.md](../../diagrams/29-anthropic-effective-agents-pattern-assessment.md).

## Pattern mapping

| Anthropic pattern        | Varka status                                | Evidence and assessment                                                                                                                                                                                                                                           |
| ------------------------ | ------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Augmented LLM            | **Used**                                    | `AgentTurnLoop` provides a compact model inventory, Ecore-derived contracts on demand, lexical retrieval snippets, recent conversation context, source units, and a defined action interface. This grounds the model in authoritative domain facts.               |
| Autonomous agent loop    | **Used, bounded**                           | The loop calls the provider, strictly parses one `AgentAction`, executes it, feeds inspection/contract/error results back, and repeats up to configured step and provider-call limits. Tool results and structural validation are the environmental ground truth. |
| Prompt chaining          | **Used only as deterministic control flow** | A mutation often follows inventory -> `describe_types` or `inspect_model` -> terminal batch. This is a small conditional chain inside the agent loop, not an independent multi-prompt pipeline.                                                                   |
| Routing                  | **Not used**                                | There is one responder role and one loop. The prompt decides whether to inspect, describe, mutate, answer, or ask. Add routing only if distinct task classes need distinct prompts, models, or policies.                                                          |
| Parallelization / voting | **Not used for LLM work**                   | ADR-004 excludes parallel planner, repair, source, and summarizer workers. `SourceDocumentWorkers` only splits and labels text locally; it makes no provider calls. Durable worker threads are execution capacity, not parallel reasoning.                        |
| Orchestrator-workers     | **Not used**                                | No model creates subtasks or delegates to other model workers. The single agent produces one terminal `commit_model_batch`. This avoids shared-workspace merge, conflict, provenance, and cost complexity.                                                        |
| Evaluator-optimizer      | **Partially, without a second LLM**         | Backend validation rejects invalid actions and repairable tool errors return to the same agent for correction. Final structural validation gates persistence. This is deterministic validator-and-repair feedback, not a generator/evaluator model pair.          |

## Implemented workflow

1. The API persists a user request as a durable turn with expected model revision and deadline, then returns `202 Accepted`.
2. `DurableAssistantTurnWorker` claims it from PostgreSQL and splits any attachment into labelled source units.
3. `AgenticTurnService` loads the current model into an in-memory `ModelWorkspace` and supplies bounded recent history.
4. `AgentTurnLoop` provides the model with system instructions, current-model inventory, request, optional source evidence, and optional retrieval snippets.
5. The model returns exactly one JSON action: `inspect_model`, `describe_types`, `commit_model_batch`, `answer_user`, or `ask_user`.
6. Inspection and schema results become the next model input. A model batch is checked against Ecore types, attributes, references, containment, enums, preconditions, and deletion confirmation.
7. Structural validation gates an atomic, revision-checked commit. Checkpoints, inverse patches, provenance, provider-call audits, and replayable events are persisted.
8. The worker reports success, partial, needs input/confirmation, conflict, cancellation, timeout, or failure; the UI can follow SSE events, continue, confirm, or undo.

## Agent-computer interface and safety

Anthropic emphasizes that tool design is as important as prompt design. Varka exposes a deliberately narrow, strict JSON action protocol rather than provider-managed automatic tool calling.

| Provider-visible action    | Backend capability                                                   | Reliability property                                                             |
| -------------------------- | -------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| `inspect_model`            | Read full workspace or a focused element                             | Fact finding without persistence access.                                         |
| `describe_types`           | Retrieve exact Ecore type contracts and required containment closure | Replaces schema guessing with canonical contracts.                               |
| `commit_model_batch`       | Create, update, connect, or delete in a working copy                 | Stable references, preconditions, structural validation, and confirmed deletion. |
| `answer_user` / `ask_user` | End informational turns or request a needed decision                 | Avoids unnecessary mutation and supplies a human checkpoint.                     |

Java-level helpers such as `searchModel`, `createElements`, and `validateModel` support these actions but are not independently provider-visible in the current loop.

- **Ground truth:** current `ModelWorkspace`, deterministic Ecore contracts, tool failures, and structural validation—not self-reported reasoning.
- **Memory and retrieval:** durable threads/messages, JDBC chat memory, a bounded recent-message window, and lexical metamodel retrieval.
- **Source accountability:** untrusted attachment text is split into source units and stored with source-grounded or inferred element provenance; incomplete coverage yields `PARTIAL`.
- **Operational guardrails:** secret redaction, injection labelling, rate limit, retry/circuit breaker, time/step/provider-call budgets, cancellation, idempotency, durable audit events, and SSE replay.
- **Mutation guardrails:** no provider database access; mutations remain private until structural validation and revision-checked atomic persistence. Deletion uses a server-controlled confirmation path; undo uses the stored inverse patch.
- **Transparency:** the user sees factual planning, tool, validation, checkpoint, and lifecycle events—not private chain-of-thought.

## Is it ad hoc?

The implementation contains custom mechanics, but they are intentional domain adaptations rather than ad-hoc behavior:

- The `AgentAction` envelope and direct provider calls implement a transparent, low-abstraction agent/tool loop, consistent with Anthropic's caution that frameworks can obscure prompts and responses.
- `ModelWorkspace`, Ecore contracts, and revision-locked persistence are modeling-specific agent-computer-interface protections that generic frameworks cannot supply.
- Queueing, checkpoints, provenance, cancellation, confirmation, and SSE replay are deterministic operational workflow infrastructure around the single agent.

The accurate label is: **a custom, production-oriented, bounded single-agent augmented-LLM pattern embedded in a durable deterministic workflow.**

## Next steps justified by the reference

1. Add task-level evaluation before adding agents: structural validity, user acceptance/undo rate, repair rate, source coverage, conflicts, latency, and cost by task type.
2. Continue treating the action envelope as an agent-computer interface: test tool descriptions and validation errors with representative prompts; consider native structured output only if it improves reliability without weakening backend authority.
3. Add routing or specialist workers only for measured, genuinely distinct task modes, with explicit plans for workspace merge, provenance, evaluation, and cost.
4. Retain the existing human controls for consequential work: confirmation, cancellation, checkpointing, undo, and visible stopping conditions.

## Code basis

- [Anthropic: Building effective agents](https://www.anthropic.com/engineering/building-effective-agents)
- `docs/internal/adr/ADR-003-explicit-tool-loop.md` and `docs/internal/adr/ADR-004-single-agent.md`
- `packages/java/platform-assistant/src/main/java/.../agent/AgentTurnLoop.java`
- `packages/java/platform-assistant/src/main/java/.../tools/AgentModelTools.java`
- `packages/java/platform-assistant/src/main/java/.../application/AgenticTurnService.java`
- `apps/backend/src/main/java/.../assistant/DurableAssistantTurnWorker.java`
