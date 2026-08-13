# AI assistant setup and architecture

Updated: 2026-08-13

This is the implementation reference for Varka's modeling chatbot. The supported assistant scope is
CIM and PIM. PSM modeling remains available elsewhere in Varka, but `ChatbotController` rejects PSM
assistant sessions with HTTP 422.

## Production configuration

The only validated deployment provider/model pair is Arvan `DeepSeek-V4-Flash` through the
OpenAI-compatible JSON-content adapter:

```dotenv
VARKA_AI_ENABLED=true
VARKA_AI_MODE=unified
VARKA_AI_WORKFLOW_ENGINE_V2=true
VARKA_AI_PROVIDER=openai
OPENAI_COMPATIBLE_BASE_URL=<Arvan endpoint>
OPENAI_COMPATIBLE_API_KEY=<secret>
VARKA_AI_MODEL=DeepSeek-V4-Flash
VARKA_AI_OPENAI_PROTOCOL=json_schema
VARKA_AI_NATIVE_TOOLS_PREFERRED=false
VARKA_AI_FORCED_TOOL_CHOICE_RELIABLE=false
VARKA_AI_MAX_PROVIDER_CALLS_PER_TURN=20
VARKA_AI_MAX_PROVIDER_CALLS_SOURCE_TURN=20
VARKA_AI_PROVIDER_RETRY_ATTEMPTS=0
```

`agent-test` and `conceptual-test` are acceptance-test overrides. `unified` is the sole normal
mode. The code contains other provider-family adapters, but they are not validated alternatives for
this deployment.

## Runtime architecture

```text
POST /api/chatbot/sessions/{sessionId}/messages
  -> persist assistant_turn (QUEUED)
  -> DurableAssistantTurnWorker claims a lease
  -> AgenticAssistantFacade / AgenticTurnService
  -> AgentTurnLoop(ADAPTIVE)
       -> CONCEPTUAL_GENERATION
       -> INSPECT_AGENT
       -> ANSWER -> EXPLAIN_MODEL (read-only)
  -> private ModelWorkspace
  -> ModelService.validateStructural(...)
  -> revision-checked atomic checkpoint
  -> durable events/status for polling and SSE replay
```

The durable boundary owns authentication scope, idempotency, leases, deadlines, cancellation,
expected revisions, continuations, confirmations, work items, checkpoints, inverse patches,
provider-call audit, source provenance, and event replay.

## Strategy selection

The LLM returns exactly one of `CONCEPTUAL_GENERATION`, `INSPECT_AGENT`, or `ANSWER`. Deterministic
state restricts the legal set:

- Fresh empty CIM/PIM: conceptual generation or read-only answer.
- Non-empty model: inspect agent or read-only answer.
- Selected elements, resumptions, or confirmed destructive work: inspect/contract path.

No prompt keyword, alias table, regex, or deterministic business template selects model meaning.
`ANSWER` maps to read-only `EXPLAIN_MODEL`; mutation actions are unavailable on that route.

## Conceptual generation

Production durable conceptual turns run these stages:

1. **Obligation ledger.** The LLM decomposes the request into stable mandatory/optional obligations
   and maps each to one to four exact creatable candidate EClasses. The ledger is persisted before
   type selection.
2. **Type selection.** The LLM chooses exact live EClasses. Deterministic code checks that each
   mandatory obligation retains a compatible candidate and that the actual combined required Ecore
   closure fits capacity.
3. **Blueprint.** The LLM plans stable object IDs, exact types, legal containment, major references,
   obligation IDs, source-unit IDs, and slices. Every selected semantic type must appear.
4. **Private slices.** Object payloads are generated into durable private work items. Large plans
   start at two objects per slice and fall back to one after length truncation.
5. **Independent obligation review.** The LLM returns per-obligation states and exact object and
   relationship evidence. Candidate EClasses are alternatives, not a conjunctive checklist.
6. **Compilation.** Deterministic code resolves stable IDs, attributes, containment, references,
   required features, ownership, multiplicity, and evidence into `ModelCommandBatch`.
7. **Structural gate and commit.** The private candidate is structurally validated and committed as
   one expected-revision checkpoint.

The schema maximum is 16 blueprint objects/types. Effective capacity can be lower because the
workflow reserves calls for routing/review/repair and required Ecore closure adds supporting types.
Abstract required targets count toward capacity. The backend exposes exact creatable subtypes and
the LLM chooses one; deterministic code never selects a business subtype.

Every mandatory obligation must be allocated in the blueprint and independently marked
`SATISFIED` with actual staged object evidence. Cited relationship triples are checked against the
generated model. Missing/partial coverage prevents commit. There is no canned semantic fallback.

## Arvan-specific resilience

The adapter sends temperature zero, JSON response format, and
`thinking: {"type":"disabled"}`. Arvan can nevertheless consume most output on reasoning and end
with `finish_reason=length`.

Stage-specific completion ceilings remain bounded. Type selection has four attempts. After a
truncation, retries use only the obligation ledger's candidate EClasses, exact closure costs, the
request, and the latest diagnostic instead of repeating the complete metamodel index. Capacity
corrections provide exact combined closure and marginal savings, but the LLM remains responsible
for choosing the semantic subset.

## Inspect/contract agent

The current normal action vocabulary is:

- `plan_model_edit`
- `inspect_model`
- `describe_types`
- `commit_model_batch`
- `answer_user`
- `ask_user`

The backend executes only allowlisted actions. `commit_model_batch` is compiled against exact Ecore
contracts and applied privately. Destructive actions require confirmation and preconditions.
Legacy source actions are retained only for compatible resumption of older durable workflows.

## Source documents

Attachments are uploaded to a session, bounded by upload settings, and may be supplied by
`attachmentIds`. Text sources are split into `assistant_source_units`. Created evidence is stored
as `SOURCE_GROUNDED` with a source-unit ID or `INFERRED` with an explicit assumption.

Source coverage is accounting, not EVL validity. A source-backed turn cannot claim complete source
coverage while relevant units remain unaccounted.

## Validation and persistence boundary

Assistant generation, correction, review, apply, and commit may call only
`ModelService.validateStructural(...)`. They must not call:

- `ModelService.validate(...)`
- stored validation endpoints
- `validateGeneratedXmi(...)`
- `EpsilonEvlValidator`
- EVL CLIs or profiles

EVL is available only through explicit validation workflows outside the chatbot.

Conceptual objects remain private until the complete candidate passes the obligation and structural
gates. A failure leaves the saved model revision unchanged. A successful checkpoint records its
base revision, resulting revision, candidate hash, inverse patch, status, and structural validation
summary.

## Public API and client flow

1. Create/resume a CIM or PIM assistant session.
2. Optionally upload attachments.
3. Send a message with an idempotency key and current model/revision.
4. Receive HTTP 202 with a durable turn ID.
5. Poll `/api/chatbot/turns/{turnId}` or replay authenticated turn SSE.
6. Handle terminal states and use continue, confirm, rebase, undo, rollback, or feedback controls
   where appropriate.

The current controller also retains legacy proposal-detail/undo endpoints. New durable turns do not
use an approve/reject proposal stage; checkpoints and inverse patches are the active mutation model.

## Durable storage

Assistant migrations store threads, messages, summaries, turns, replayable events, checkpoints,
source units, provenance, provider calls/prompts, action audits, workflow plans, private work items,
source facts/blueprints, context cache, and structural validation attempts. Provider calls use a
per-turn `call_key` uniqueness constraint for idempotent audit persistence.

The obligation ledger, selected types, blueprint, slice size, generated work-item payloads,
truncation diagnostics, and successful obligation verdict are stored in the workflow plan/work
items so a worker restart can resume rather than reinterpret completed stages.

## Current acceptance status

The latest obligation-gated PIM profile has not produced a clean live semantic acceptance pass.
Recent Arvan runs have confirmed atomic failure, concrete abstract-target selection, obligation
persistence, independent semantic rejection, compact retry prompts, exact token/call accounting,
and unchanged models on failure. The latest fourth type-selection correction attempt is deployed
and focused-test green but still requires live confirmation.

Do not describe the assistant as perfectly reliable or production-ready. Required ten-run
campaigns, paraphrase campaigns, failure/restart matrices, existing-model preservation evidence,
and visual UI verification remain incomplete. Exact evidence is recorded in
`live-eval-gate-report.md`.
