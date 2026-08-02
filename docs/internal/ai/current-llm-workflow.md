# Current LLM Modeling Workflow

Updated: 2026-07-29

This document describes the current implementation state for the chatbot-based AI modeling
assistant. It is intentionally descriptive, not aspirational.

## Runtime Shape

Varka uses a deterministic durable-turn workflow around one bounded LLM agent loop.

```text
REST message -> assistant_turn -> DurableAssistantTurnWorker
  -> AgenticTurnService -> AgentTurnLoop
  -> ConfiguredAssistantModelProvider
  -> AgentAction -> AgentModelTools -> ModelWorkspace
  -> structural validation -> revision-checked model commit
```

The outer workflow is code-owned: authentication, idempotency, queueing, deadlines, cancellation,
checkpointing, undo, source coverage, provider-call audit, and SSE event replay are deterministic.
Inside that workflow, `AgentTurnLoop` is an agent because the model chooses one allowed action after
seeing current prompt context and previous tool results.

The design follows Anthropic's "Building effective agents" guidance: keep the agent-computer
interface small, make tools hard to misuse, and prefer deterministic validation feedback before
adding more agent roles.

## Provider Protocol

The assistant supports Gemini and OpenAI-compatible providers. For OpenAI-compatible providers,
`VARKA_AI_OPENAI_PROTOCOL=tools` uses native Chat Completions tool calls. The native provider path
also accepts strict JSON action content when a provider returns a JSON object in `message.content`
instead of `tool_calls`.

Runtime actions are normalized to the internal action names:

| Internal action      | Purpose                                                           |
| -------------------- | ----------------------------------------------------------------- |
| `plan_model_edit`    | Create a durable CIM/PIM modeling plan before one checkpoint.     |
| `inspect_model`      | Read a focused element or model inventory.                        |
| `describe_types`     | Retrieve exact Ecore-derived type contracts.                      |
| `commit_model_batch` | Submit creates, updates, connections, deletions, and evidence.    |
| `answer_user`        | Finish an informational turn without mutation.                    |
| `ask_user`           | Ask for required input when safe progress is impossible.          |

The OpenAI-compatible native tool schema exposes `apply_draft_patch` for the mutation tool and maps
it back to `commit_model_batch` internally. Documentation and prompts should use
`commit_model_batch` when describing the backend action model.

## Source-Backed CIM Modeling

When a user uploads a `.md`, `.txt`, or `.json` file and asks the chatbot to create a CIM model:

1. The upload is stored as an assistant attachment.
2. The message request references the attachment ID and returns `202 Accepted` with a durable
   `turnId`.
3. The worker extracts text, splits it into bounded source units, and persists
   `assistant_source_units`.
4. Source documents are modeled from supplied source units through `plan_model_edit`; the same
   bounded agent turn retrieves exact contracts and commits the first structurally valid slice.
5. The agent must ground model changes in source evidence. Evidence IDs accept current source unit
   IDs and mapped source-section aliases.
6. Source-backed turns reject premature `answer_user` and `ask_user` actions while source evidence
   is still in scope and modelable.
7. `commit_model_batch` creates model elements using provider-facing `clientRef` values, but the
   backend replaces them with UUID element IDs before persistence.
8. The backend checks types, containment features, references, enum literals, required references,
   and preconditions against live Ecore-derived contracts.
9. A valid workspace mutation becomes a model revision, checkpoint, provenance rows, provider-call
   audit rows, and replayable turn events.
10. Source coverage is computed from accounted source units. Complete coverage with no remaining
    work should end as `SUCCEEDED`; partial coverage or explicit remaining work ends as `PARTIAL`.

## Backend Hardening

Current hardening includes:

- strict action envelope parsing;
- native tool-call and strict JSON-content fallback for OpenAI-compatible providers;
- backend-generated UUID element IDs;
- same-batch reference resolution;
- invalid optional connection filtering;
- containment-reference normalization when a unique compatible containment exists;
- required enum defaulting;
- synthesis of required non-containment reference targets where Ecore demands them;
- source evidence validation before model mutation;
- source-section alias resolution for resumed source turns;
- source splitter handling for plain section labels followed by lists;
- rejection of source-backed final answers/questions when source units remain modelable;
- checkpoint, inverse patch, rollback, undo, rebase, cancellation, and feedback endpoints.

These guards are structural and contract-oriented. They must not become a hard-coded natural
language user-story generator.

## Live Status

Latest single-story live test through the real multipart/chatbot path:

| Fixture              | State       | Coverage | Checkpoints | Saved elements | Provider calls | Validation |
| -------------------- | ----------- | -------- | ----------- | -------------- | -------------- | ---------- |
| `story-v1-single.md` | `SUCCEEDED` | `100%`   | `1`         | `16`           | `4`            | structural |

The operational workflow now accepts the attachment, creates a checkpoint, persists model content,
and reports complete source coverage. Assistant output is accepted only against structural
Ecore/EMF conformance. EVL semantic validation is intentionally outside the chatbot apply gate and
is available through explicit model validation endpoints for human-driven review workflows.

The focused unit/regression set currently passes:

```text
SourceUnitSplitterTest
OpenAiCompatibleAssistantModelProviderTest
AgentTurnLoopTest
AgentModelToolsTest

26 tests, 0 failures
```

## Known Issues

- Larger source fixtures need to be rerun after the latest source-splitting and structural
  validation changes.
- Freemodel has returned intermittent `503` responses in local live testing. FreeLLM currently
  works better for the local workflow, but provider reliability remains an operational concern.
- The live eval script is useful for diagnosis but is not yet a CI-grade gate.
- EVL validation responses can still be useful after assistant checkpoints, but they must remain
  user-initiated review feedback rather than assistant apply criteria.

## Next Engineering Targets

1. Rerun progressive source fixtures: one, two, three, five, ten, fifteen, and twenty stories.
2. Add integration coverage for the live source-backed terminal-state path and structural
   validation-only assistant apply boundary.
3. Keep provider/model changes configuration-only; avoid provider-specific business logic in the
   modeling workflow.
