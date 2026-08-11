# AI modeling assistant pattern assessment

Updated: 2026-08-12

Varka is now a **deterministic durable workflow with LLM routing between two bounded internal
workflows**. It is not accurately described as only a single tool-calling agent.

## Pattern mapping

| Pattern               | Current status                        | Implementation                                                                                                                                                                                                        |
| --------------------- | ------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Augmented LLM         | Used                                  | Current model inventory, Ecore-derived contracts, source units, recent memory, and tool/compiler diagnostics ground provider decisions.                                                                               |
| Routing               | Used, bounded                         | `AgentTurnLoop.runAdaptive` requests one strict strategy enum. Structural safety facts constrain the legal strategy set; users do not select it.                                                                      |
| Prompt chaining       | Used                                  | Conceptual generation chains semantic EClass selection, complete instance generation, deterministic compilation, and bounded complete-response repair. Agent mutation chains plan/inspect/contracts/commit as needed. |
| Autonomous agent loop | Used for inspect/edit                 | The LLM selects one allowlisted action per step and receives backend facts until a terminal action or budget stop.                                                                                                    |
| Evaluator/optimizer   | Deterministic validator feedback only | The conceptual compiler or model tools return structural diagnostics to the same LLM. There is no independent semantic reviewer LLM.                                                                                  |
| Orchestrator/workers  | Durable code workflow only            | The worker persists plans and work items, but does not delegate semantic work to multiple LLM agents.                                                                                                                 |
| Parallel LLM voting   | Not used                              | Provider calls within a turn are sequential and budgeted. Two backend worker threads provide execution capacity, not parallel reasoning.                                                                              |

## Implemented topology

```text
durable worker
  -> structured LLM strategy router
     -> conceptual generator/compiler workflow
     -> inspect/contract autonomous action loop
     -> ANSWER intention through the ordinary AUTO action loop
```

The conceptual workflow is not an agent tool loop. It is a prompt chain with deterministic
compilation and bounded repair. The inspect/contract workflow is an agent because the model chooses
among planning, inspection, contract retrieval, mutation, answer, and question actions after seeing
tool results.

There is still one provider model and no specialist-agent delegation. `SourceDocumentWorkers` and
durable work items are infrastructure, not independent LLM workers.

## Agent-computer interfaces

### Adaptive strategy schema

```json
{ "strategy": "CONCEPTUAL_GENERATION|INSPECT_AGENT|ANSWER" }
```

This schema is closed and audited. The backend supplies structural facts and removes unsafe
strategies without inspecting request keywords.

`ANSWER` is not presently a separate read-only capability set. It maps to the same AUTO loop as
`INSPECT_AGENT`, so non-mutation is prompt-directed rather than backend-enforced. This is a current
implementation limitation.

### Inspect/contract actions

| Action                     | Backend capability                                                                           |
| -------------------------- | -------------------------------------------------------------------------------------------- |
| `plan_model_edit`          | Produce durable checkpoint slices and contract candidates.                                   |
| `inspect_model`            | Read exact workspace facts.                                                                  |
| `describe_types`           | Retrieve authoritative Ecore contracts.                                                      |
| `commit_model_batch`       | Apply checked creates, updates, connections, deletions, and evidence to a private workspace. |
| `answer_user` / `ask_user` | Finish without mutation or request necessary input.                                          |

### Conceptual intermediate representation

The conceptual path uses a JSON object keyed by instance ID. Each object has an exact EClass,
attribute triples, parent-to-child compositions, source-to-target references, and optional evidence.
The deterministic compiler is the computer interface: it resolves IDs and ordering and rejects any
contract mismatch without inventing semantic content.

## Safety properties

- Provider output never writes PostgreSQL directly.
- Ecore-derived contracts, not prompt claims, define legal model structure.
- Mutation occurs in a private `ModelWorkspace`.
- Only structural Ecore/EMF validation gates assistant apply and commit.
- Destructive batches require server-controlled confirmation and preconditions.
- Expected revisions prevent stale writes.
- Checkpoints, inverse patches, provenance, calls, tokens, failures, and events are durable.
- Source coverage prevents false completion when source units remain unaccounted.
- Users see factual workflow events, not private reasoning.

## Intentional exclusions

- No keyword router or hard-coded natural-language transformation.
- No canned/coded fallback model.
- No parallel model voting or specialist-agent delegation.
- No semantic EVL gate inside assistant generation, repair, apply, or commit.
- No silent conceptual-to-agent fallback after a failure; transitions require explicit durable
  workflow state.
- No client-facing internal strategy selector.

## Current evidence boundary

The inspect/contract agent has passed all four required Arvan fixtures. Conceptual generation has
passed bounded empty-model and source-backed cases but not persisted-model evolution. Unified mode
has three final passes out of four; library generation has a successful attempt but its final gated
rerun failed on truncated output. The pattern is architecturally sound, but repeated-run reliability
and human usefulness evaluation remain production work.

See [assistant-approach-comparison.md](assistant-approach-comparison.md) and
[current-llm-workflow.md](current-llm-workflow.md).
