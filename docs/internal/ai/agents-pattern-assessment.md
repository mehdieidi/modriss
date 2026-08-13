# AI modeling assistant pattern assessment

Updated: 2026-08-13

## Implemented patterns

| Pattern             | Current use                                                                                                                                                                         |
| ------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Routing             | One LLM closed strategy over deterministic legal choices: conceptual generation, inspect agent, or read-only answer.                                                                |
| Prompt chaining     | Obligation ledger -> type selection -> blueprint -> private slices -> independent obligation review -> deterministic compilation.                                                   |
| Tool use            | Existing-model work uses allowlisted inspection, exact-contract, planning, commit, answer, and clarification actions.                                                               |
| Evaluator/optimizer | An independent LLM obligation verdict evaluates staged evidence; deterministic code verifies every cited ID/relationship. Structural compiler diagnostics drive bounded correction. |
| Durable workflow    | Turns, events, workflow plans, private work items, provider calls, checkpoints, and continuations are persisted.                                                                    |
| Human in the loop   | Clarification, destructive confirmation, conflict rebase, cancellation, undo, rollback, and feedback are explicit controls.                                                         |

## Topology

```text
Durable worker
  -> LLM strategy
     -> conceptual prompt chain for fresh empty models
     -> inspect/contract action loop for existing/resumed/destructive work
     -> read-only explanation
  -> private workspace
  -> structural-only validation
  -> atomic checkpoint
```

This is a single-agent system with workflow-specialized stages, not a society of independent
business agents. The obligation reviewer is an independent LLM pass, but it cannot mutate the
model or bypass structural checks.

## Authority split

The LLM owns semantic interpretation, obligations, EClass choice, objects, relationships, subtype
choice, and satisfaction judgment. Deterministic code owns exact Ecore extraction, schemas,
capacity, IDs, containment/reference enforcement, evidence verification, private staging,
idempotency, validation, and persistence.

No keyword router, canned model, hardcoded alias map, or silent semantic fallback is allowed.

## Evidence boundary

The architecture now detects shallow structural false positives through mandatory-obligation
review and a stronger live gate. It has not yet demonstrated ten consecutive successes per fixture,
the full restart/transport matrix, existing-model preservation campaigns, or visual UI acceptance.
It must not be described as perfectly reliable or production-ready.
