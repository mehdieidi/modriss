# AI assistant setup and architecture

Updated: 2026-08-12

Varka exposes one durable LLM-based modeling chatbot for CIM, PIM, and PSM. It can answer questions,
generate a bounded model from an empty root, transform source text into model content, and evolve an
existing model. Users do not choose between agent and conceptual modes.

For exact runtime detail, see [current-llm-workflow.md](current-llm-workflow.md). For the paper
mapping, see [conceptual-instance-paper-traceability.md](conceptual-instance-paper-traceability.md).
The complete environment-variable reference, including accepted values, examples, inheritance,
and limit semantics, is [environment-variables.md](../environment-variables.md#ai-assistant).

## Production configuration

The available deployment provider is Arvan's OpenAI-compatible endpoint with
`DeepSeek-V4-Flash`:

```dotenv
VARKA_AI_ENABLED=true
VARKA_AI_PROVIDER=openai
OPENAI_COMPATIBLE_BASE_URL=<Arvan OpenAI-compatible base URL>
OPENAI_COMPATIBLE_API_KEY=<secret>
VARKA_AI_MODEL=DeepSeek-V4-Flash
VARKA_AI_MODE=unified
VARKA_AI_OPENAI_PROTOCOL=json_schema
VARKA_AI_NATIVE_TOOLS_PREFERRED=false
VARKA_AI_FORCED_TOOL_CHOICE_RELIABLE=false
VARKA_AI_MAX_TOOL_CALLS=0
VARKA_AI_MAX_AGENT_STEPS=8
VARKA_AI_MAX_PROVIDER_CALLS_PER_TURN=20
VARKA_AI_MAX_PROVIDER_CALLS_SOURCE_TURN=20
VARKA_AI_TOKEN_BUDGET=16000
VARKA_AI_MAX_COMPLETION_TOKENS=16000
VARKA_AI_REQUEST_TIMEOUT=180s
VARKA_AI_TURN_TIMEOUT=12m
VARKA_AI_SOURCE_TURN_TIMEOUT=15m
VARKA_AI_PROVIDER_RETRY_ATTEMPTS=0
```

`VARKA_AI_MAX_TOOL_CALLS=0` is the example's compatibility value; `AiProperties` normalizes it to
the runtime default of 24. Provider-call budgets remain the tighter control for the current action
protocol.

Do not commit API keys or put them in `application.yml`. Compose passes a local ignored `.env` to
the backend container. Set `VARKA_AI_ENABLED=false` to disable provider calls and assistant model
changes.

For Arvan structured requests, the adapter sends JSON-object output, temperature zero, and
`"thinking":{"type":"disabled"}`. Native tool calling and forced tool choice remain disabled
because they were not reliable through this endpoint. `VARKA_AI_TEST_MODEL` is only an evaluation
override; production uses `VARKA_AI_MODEL`.

`VARKA_AI_MODE` accepts exactly:

- `unified` — the only normal production value;
- `agent-test` — force the inspect/contract path for acceptance comparison;
- `conceptual-test` — force the conceptual path for non-destructive acceptance comparison.

Unknown values fail backend startup and never silently select a path.

## Runtime architecture

```text
ChatbotController (REST + authenticated SSE)
  -> AssistantTurnStore (QUEUED durable turn)
  -> DurableAssistantTurnWorker
  -> AgenticAssistantFacade / AgenticTurnService
  -> AgentTurnLoop
       -> strict adaptive strategy decision
          -> ConceptualInstanceModelWorkflow
          -> inspect/contract agent actions
          -> ANSWER intention through the ordinary AUTO action loop
  -> ModelWorkspace
  -> structural validation
  -> revision-checked commit + checkpoint + inverse patch
```

The durable layer owns authentication, project/model access, idempotency, queue leases, timeouts,
cancellation, expected revisions, destructive confirmation, checkpoints, undo, continuation,
source accounting, provider-call audit, and SSE replay. Strategy selection does not change these
guarantees.

## Automatic strategy selection

The adaptive LLM returns only one strict enum value: `CONCEPTUAL_GENERATION`, `INSPECT_AGENT`, or
`ANSWER`. The backend does not route by keywords or prompt matching. It restricts strategies using
model emptiness, selected-element context, source presence/size, durable workflow state, and
destructive confirmation.

Current production policy is deliberately conservative:

- Fresh empty models may use conceptual generation.
- Existing models use the inspect/contract agent for mutation.
- Selected-element, resumed, and destructive work stays on the agent path. Ambiguity is handled by
  agent inspection/clarification when that path is active; there is no deterministic ambiguity
  classifier in the router.
- Questions are expected to finish through `answer_user` without a checkpoint.

This reflects live evidence, not an assumption that either method is universally superior.

The adaptive `ANSWER` value maps to the enforced read-only explanation workflow, so mutation actions
are unavailable for informational turns.

## Conceptual generation path

The conceptual workflow implements a bounded stable-ID blueprint followed by conceptual slices and
deterministic Ecore compilation:

```text
request + source + current model + authoritative Ecore
  -> small LLM blueprint (maximum 8 objects and 12 EClasses)
  -> exact contract and required construction closure
  -> one complete object per DeepSeek/Arvan slice (at most two on other providers)
  -> stable-ID merge and cross-slice reference resolution
  -> structured LLM quality review/correction
  -> deterministic IDs/order/containment/reference compilation
  -> private workspace mutation
  -> structural validation
  -> diagnostic + bounded corrected objects when needed
  -> atomic durable commit
```

New objects use temporary instance IDs. Existing objects must use exact persisted IDs. Parent
objects own composition entries; source objects own non-containment reference entries. Attributes,
compositions, and references are validated and compiled independently. The compiler may derive root
placement only when one exact Ecore containment is mechanically unambiguous.

Conceptual omission never deletes existing data. This path cannot emit deletions or moves. A
truncated slice is discarded and split; it is never retried at the same size. All slices are merged
and compiled in the private workspace, so no partial conceptual result is committed. The blueprint
is stored in the durable workflow plan and every planned object has an idempotent work item holding
its blueprint entry and, once accepted, its complete conceptual payload. Lease recovery skips
completed objects and preserves any truncation-reduced slice size. Provider calls and token totals
are written incrementally with keyed audit rows, while intermediate payloads remain private and
never become model revisions.

The 20-call ceiling covers up to two adaptive-strategy attempts plus a conceptual budget of 18:
type selection, blueprint, as many as eight one-object DeepSeek slices, review, and bounded
selection, slice, review, and compiler corrections. This replaces the earlier eight-call/two-object,
14-call, and 18-call profiles after live runs exhausted review or correction capacity without
committing a checkpoint.

## Inspect/contract agent path

The action loop exposes six provider actions:

- `plan_model_edit`
- `inspect_model`
- `describe_types`
- `commit_model_batch`
- `answer_user`
- `ask_user`

Compatibility actions `analyze_source_units`, `plan_cim_blueprint`, and `plan_source_model` remain
available only when resuming the corresponding older durable source workflow. New source turns use
`plan_model_edit`.

The LLM chooses semantic content; deterministic code supplies model facts and exact Ecore contracts,
resolves UUID/client references, checks containment and references, applies preconditions, accounts
for source units, validates the candidate, and persists workflow state. Deletions require the
existing `NEEDS_CONFIRMATION` and server-authored confirmation continuation.

## Source documents

The chatbot accepts uploaded `.md`, `.txt`, and `.json` attachments, or inline text attachment
content. The backend stores text, splits it into bounded `assistant_source_units`, and gives the LLM
exact unit IDs. Committed evidence is persisted as:

- `SOURCE_GROUNDED` with a source-unit ID; or
- `INFERRED` with a written assumption.

`coveragePercent=100` means all tracked source units were accounted for. It does not mean the model
passed EVL or a human usefulness review. Relevant uncovered units prevent a successful completion.
Both paths persist plans and completed work across worker recovery. Conceptual generation still
produces one atomic model commit even when its private slices span multiple worker executions.

## Validation and persistence boundary

Assistant-generated actions, conceptual models, patches, repairs, and checkpoints are accepted only
after `ModelService.validateStructural(...)` succeeds in the private workspace.

Assistant apply/repair/commit does not call `ModelService.validate(...)`, stored semantic-validation
endpoints, `validateGeneratedXmi(...)`, `EpsilonEvlValidator`, or EVL CLIs/profiles. EVL remains an
explicit user-initiated validation workflow outside the chatbot.

A rejected candidate is never partially committed. Successful commits are revision checked and
produce a checkpoint plus inverse patch when applicable.

## Required client flow

1. Create or open a project and saved CIM/PIM/PSM model.
2. Create/resume a chatbot session.
3. Submit a message with `idempotencyKey`, model ID, current expected revision, optional selected
   element IDs, and optional attachment IDs.
4. Receive `202 Accepted` with a durable `turnId`.
5. Follow `GET /api/chatbot/turns/{turnId}/events` or poll the turn status endpoint.
6. Reload the model after a checkpoint event.
7. Use `/continue`, `/confirm`, `/rebase`, `/undo`, or checkpoint rollback as required by state.

There is no client-visible strategy selector, provider token streaming, WebSocket protocol, private
reasoning stream, or approve/reject proposal step.

## Durable storage

PostgreSQL stores:

- `SPRING_AI_CHAT_MEMORY`;
- `assistant_threads`, `assistant_messages`, and `assistant_thread_summaries`;
- `assistant_turns`, `assistant_turn_events`, and `assistant_checkpoints`;
- `assistant_source_units` and `assistant_element_provenance`;
- `assistant_provider_calls` and `assistant_action_audits`;
- durable workflow/work-item state used by plans and continuations;
- `assistant_rate_limits` as a reserved schema table while runtime limiting remains in memory.

## Operational guidance

- Rebuild/restart the backend after changing Ecore/metamodel resources.
- Every new turn must use the latest model revision or it can become `CONFLICTED`.
- Inspect persisted provider calls and turn events before changing prompts after a failure.
- Treat `finish_reason=length` as a size failure, not a connectivity success.
- Keep failed live reports; do not overwrite or reinterpret them as passes.
- Run explicit model validation separately when humans require EVL feedback.

## Current acceptance status

Agent-only acceptance passed all four required fixtures. Conceptual generation passed bounded empty
library, source-backed pantry, and empty serverless PIM runs, but failed reliable persisted-model
feature evolution. Unified mode passed feature evolution, pantry, and serverless PIM; library has a
successful run but the final gated rerun failed on length-limited provider output.

The staged conceptual protocol subsequently passed a live `create-cim-library` gate against
Arvan/`DeepSeek-V4-Flash`: `SUCCEEDED`, one structurally valid checkpoint, eight audited provider
calls, 21,720 prompt tokens, 23,286 completion tokens, and 304 seconds total latency. This is one
successful live gate, not yet the required repeated-run production campaign. See
[assistant-approach-comparison.md](assistant-approach-comparison.md) for the earlier comparison and
remaining limitations.
