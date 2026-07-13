# Agentic Modeling Chatbot Redesign

## Summary

Replace the current assistant with a durable, correct-by-construction modeling agent built on Spring Boot 4.1.0, Spring AI 2.0.0, Jackson 3, Java 17, PostgreSQL, and the existing EMF/Ecore runtime.

The design follows the principle that a simple augmented agent with carefully engineered tools and direct environmental feedback is preferable to a costly multi-agent swarm. This matches [Anthropic’s effective-agent guidance](https://www.anthropic.com/engineering/building-effective-agents). Spring AI 2.0 is appropriate because it exposes the tool loop for explicit control, budgeting, observation, and cancellation rather than hiding it inside providers. [Spring AI 2.0 release notes](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/), [tool-calling documentation](https://docs.spring.io/spring-ai/reference/api/tools.html).

The default runtime will therefore be one autonomous modeling agent with deterministic Ecore-derived tools. Multi-agent/source workers will not be part of the default path because they increase calls, latency, aggregation errors, and token usage. Provider text-token streaming will be removed; durable progress and committed model checkpoints remain realtime.

## Current Problems to Eliminate

- Attachments are appended to the user prompt and then truncated to 4,000 characters. Both checked-in document-to-CIM samples exceed that limit, so the agent never sees their complete contents.
- Large-document workers make parallel preliminary LLM calls, then their combined result can be truncated again. These calls run outside the active `ThreadLocal` provider budget.
- Spring AI 1.x owns a hidden provider tool loop. The application counts one outer Java call even when several actual LLM API requests occur.
- Structural failure restarts the provider with the original prompt and diagnostics, repeatedly paying for the source document and metamodel context.
- CIM receives a large generated guide that can be cut by the system-prompt limit; PIM and PSM receive only type names unless the model successfully discovers and calls `describe_types`.
- Conversation history and Spring AI chat memory are persisted but not supplied to the active agent.
- `activeView`, selection, unsaved draft, idempotency key, configured rate limit, source-call limit, tool-per-step limit, and several other settings are ignored by the active turn path.
- New assistant models bypass the existing mandatory-valid `starterTemplate` models.
- Workspace mutations are previews until one final commit. Cancellation, timeout, conflict, or provider failure discards all progress.
- The controller always returns `APPLIED`, including explanations and no-op turns; proposal data is saved but omitted from the response.
- Several frontend events are handled but never published, while the supposed text stream is one completed response emitted as a single delta.
- The current compiler contains PIM-specific service scaffolding and hand-authored aliases rather than deriving all placement and feature behavior from Ecore and explicit modeling-policy data.
- Durable turn execution, crash recovery, source coverage, exact usage accounting, persisted cancellation, and authenticated realtime replay were removed.
- The old live-evaluation implementation was deleted while scripts and reports still refer to it. The remaining focused tests pass, but the agent-loop test only verifies a read operation against a mocked validator and does not test real modeling.
- Source turns default to twelve minutes, contradicting the required five-minute user-visible deadline.

## Target Architecture and Core Behavior

### 1. Platform and Spring AI migration

- Upgrade the whole reactor to Spring Boot 4.1.0 and Spring AI 2.0.0 while retaining Java 17.
- Migrate all Jackson usage to Jackson 3 `tools.jackson.*` types and inject the Boot-managed `JsonMapper`. Do not retain the deprecated Jackson 2 compatibility module.
- Update Spring AI dependencies and provider construction for the 2.0 artifact/API layout, retaining OpenAI-compatible and Google Gemini support plus existing proxy configuration.
- Update Flyway, test, actuator, WebSocket/SSE, YAML, and REST test dependencies required by Boot 4. The migration must follow the official [Spring Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).
- Complete this migration as an isolated green-build milestone before implementing the new assistant. Run all Maven tests and backend startup/health smoke tests at this gate.
- Temporarily use the Boot properties migrator to identify renamed properties, then remove it before the migration is considered complete.

### 2. Durable turn runtime

Replace the synchronous `AgentTurnLoop` with a PostgreSQL-backed state machine:

`QUEUED → RUNNING → SUCCEEDED | PARTIAL | NEEDS_INPUT | NEEDS_CONFIRMATION | CONFLICTED | CANCELLED | TIMED_OUT | FAILED`

- `POST /messages` persists a turn and returns `202` immediately. A database-backed worker claims turns with a lease and heartbeat using `FOR UPDATE SKIP LOCKED`.
- Persist request context, deadline, current model revision, plan summary, remaining work, cancellation flag, provider usage, checkpoints, and terminal outcome.
- Permit one active turn per session/model. Duplicate idempotency keys return the existing turn.
- Expired worker leases resume from the latest committed checkpoint after process restart.
- Set the absolute deadline to five minutes from request acceptance by default. Every provider call receives `min(requestTimeout, remainingTurnTime)`.
- Cancellation is persisted. Check it before and after every provider request, tool execution, validation, and commit. A late provider response must never apply after cancellation or timeout.
- Existing checkpoints remain saved when a turn becomes partial, cancelled, timed out, conflicted, or provider-failed.
- Replace the current assistant tables with a clean Flyway migration containing:
  - `assistant_threads` and `assistant_messages`;
  - `assistant_turns` and `assistant_turn_events`;
  - `assistant_checkpoints`;
  - `assistant_source_units` and `assistant_element_provenance`;
  - `assistant_provider_calls` for tokens, latency, finish reason, and errors;
  - `assistant_action_audits`.
- The migration may discard existing assistant conversations/proposals as selected, but must not alter projects or models. Document and test the destructive assistant-state migration.

### 3. Explicit, economical agent loop

Use Spring AI 2 with automatic tool-loop execution disabled. Drive each model request, tool execution, and subsequent iteration explicitly.

Expose at most five tools:

- `commit_model_batch`: create, update, connect, or propose deletion using stable IDs/client references.
- `inspect_model`: paginated focused inspection by ID, type, selection, neighborhood, or name.
- `describe_types`: exact Ecore contracts and creation obligations for requested types.
- `answer_user`: terminal explanation without mutation.
- `ask_user`: terminal structured clarification only when safe progress is impossible.

Rules:

- Every provider response must select a tool; do not add a separate LLM intent-classification call.
- `commit_model_batch`, `answer_user`, and `ask_user` are terminal. Their results return directly to the backend without another LLM call. The backend generates the final factual summary from the committed result.
- Inspection and contract tools may cause another provider call. The context builder should make them unnecessary for common turns.
- Default budgets are two actual provider requests for ordinary turns and three for source-document turns, with a target median of one.
- Count requests at the provider boundary, not around the outer ChatClient call. Include failed/fallback requests in the budget.
- Disable automatic provider retries by default. Allow one fallback-provider call for rate limiting or pre-response unavailability if budget and deadline remain. Never retry after receiving content/tool arguments.
- Use non-streaming provider calls. Do not emit private chain-of-thought. Emit only plan summaries, tool names, checkpoint facts, validation results, and remaining work.
- Support providers without reliable tool-choice enforcement through a provider capability profile and a strict native structured `AgentAction` fallback. Both paths execute the same tools and compiler.
- Remove separate planner, source-analyst, repair, and summarizer model roles. Use one configured agent model and deterministic thread/task summaries.
- Do not use general multi-agent workers in this release. Sequential source batches preserve context and minimize calls; multi-agent execution may be evaluated later behind an experimental latency-optimized flag.

### 4. Ecore-native modeling engine

Replace the current patch compiler with a generic `MetamodelContractGraph` and `ModelCommandCompiler`.

The contract graph must be generated from each combined Ecore resource and include:

- root EClass and root containment paths;
- all concrete and abstract EClasses and transitive supertypes;
- inherited attributes/references;
- exact serialized enum literals and Ecore defaults;
- lower/upper bounds, containment, opposites, derived/transient/changeable flags;
- assignability and every valid containment route;
- required attribute, containment, and non-containment obligations.

Use SHA-256 of the actual combined Ecore bytes as the cache/drift key. UI/CVS metadata may supply human labels, categories, examples, and visual placement, but never override structural contracts.

`commit_model_batch` accepts a provider-friendly model command format:

- creates: `clientRef`, `eClass`, attributes, optional owner/reference, provenance;
- updates: stable element ID, attribute changes, precondition hash;
- connections: source ID/clientRef, exact EReference, target ID/clientRef;
- deletions: stable ID, handled as confirmation-required;
- evidence: source-unit IDs or `INFERRED` with a concise assumption;
- `planSummary` and `turnComplete`.

Compiler behavior:

1. Canonicalize only exact or uniquely resolvable Ecore names; return ambiguity instead of guessing.
2. Resolve containment from the contract graph. Remove Java switches, type-specific serverless scaffolding, regex intent handling, and attribute alias tables.
3. Auto-fill required values only from Ecore defaults or an explicit versioned modeling-policy data file. If a required enum has no declared default, require the agent to provide it.
4. Recursively synthesize required containment children only when the target is concrete or has one unambiguous viable concrete subtype.
5. Resolve required non-containment references from explicit batch/current-model targets. Defer the component if no unique valid target exists.
6. Build a dependency graph between operations. Required containment/reference dependencies may never be split.
7. Compile and validate the whole batch first. If it fails, partition it into independent dependency components and commit every structurally valid component; retain exact diagnostics for rejected components.
8. Every component commit must call a new model-layer operation such as `patchStructurallyValid(...)`, which applies, normalizes, EMF-validates, and persists under the same model/revision lock.
9. Gate only EMF/Ecore structure and JSON/XMI bridge integrity. Do not execute EVL on the AI apply path.
10. Publish `model.checkpoint` only after persistence succeeds.

For concurrent user edits, refresh and deterministically recompile one time when touched-element precondition hashes are unchanged. Otherwise stop as `CONFLICTED` while preserving prior checkpoints and the user’s edit.

For a new-model request, immediately create and publish `ModelingConfigService.starterModel(level, name)` before contacting the provider. This guarantees a visible structurally valid model even if the provider subsequently fails. An existing invalid base model may receive deterministic missing-root repairs; deeper unresolved corruption must return `PARTIAL` or `FAILED` without overwriting user content.

### 5. Context, retrieval, source documents, and memory

- Build an in-memory retrieval index at startup from:
  - exact Ecore contracts;
  - CVS labels/categories;
  - modeling methodology documents;
  - approved sample models/guides.
- Use local `intfloat/multilingual-e5-small` ONNX embeddings with exact model revision/SHA verification, plus lexical ranking and reciprocal-rank fusion. The deployment image should cache the model; if unavailable, use lexical retrieval without making a remote embedding call. Spring AI documents local [ONNX embedding support](https://docs.spring.io/spring-ai/reference/api/embeddings/onnx.html).
- Retrieval only chooses context. The contract graph and compiler remain authoritative.
- Always include root contracts, selected-element contracts, relevant required closure, and a compact full type index. Include focused model counts, selection, one-hop neighbors, and element IDs rather than the complete model.
- Cache model summaries by `(modelId, revision, metamodelSha)`.
- Store full chat history for UX, but send only the deterministic task state plus the last four complete conversational turns within the token budget. Do not spend LLM calls summarizing history.
- Remove the global 4,000-character user/source truncation. User text and attachments receive separate token budgets and explicit untrusted-data delimiters.

For CIM source documents:

1. Normalize text and split it locally by headings, paragraphs, list items, and token limits. Assign stable source-unit IDs and spans; do not call an LLM merely to extract/chunk text.
2. If the document fits the configured context window, submit all source units in one agent request. The checked-in clinic and marketplace samples must each use this path.
3. For larger documents, submit sequential source-unit groups. After each group, commit valid model components and update a symbol table of created elements to prevent duplicates.
4. Require every created/inferred element to carry external provenance. Store provenance outside the Ecore model so exports remain structurally clean.
5. Distinguish `SOURCE_GROUNDED` and `INFERRED`. Conservative inferred goals, actors, assumptions, or relationships are allowed as selected, but must be labeled in storage and the UI and must never claim a source span.
6. Track every source unit as modeled, inferred-from, irrelevant-with-reason, or deferred. A successful “complete draft” turn has no relevant deferred units.
7. If the call/deadline limit is reached, return `PARTIAL` with saved checkpoints, coverage percentage, and a Continue action that starts a new idempotent turn from the ledger.

### 6. Public API and frontend UX

Replace the current assistant response protocol:

- `POST /api/chatbot/sessions/{sessionId}/messages`
  - Request: required `idempotencyKey`, `message`, optional `modelId`, `expectedRevision`, `selectedElementIds`, and `attachmentIds`.
  - Response: `202 {turnId, state, modelId, revision, acceptedAt, deadlineAt, eventCursor}`.
- `GET /api/chatbot/turns/{turnId}` returns durable status, latest revision, checkpoints, saved element count, coverage, remaining work, final message, and provider usage.
- `POST /api/chatbot/turns/{turnId}/cancel`.
- `POST /api/chatbot/turns/{turnId}/continue`.
- `POST /api/chatbot/turns/{turnId}/confirm` for destructive batches.
- `POST /api/chatbot/turns/{turnId}/undo` applies checkpoint inverses in reverse order with revision/precondition checks.
- Keep session creation, history, and attachment upload concepts, but replace proposal APIs with turn/checkpoint APIs.

Use a durable event envelope:

```json
{
  "eventId": 42,
  "turnId": "turn-id",
  "sequence": 7,
  "type": "model.checkpoint",
  "occurredAt": "timestamp",
  "payload": {}
}
```

Supported events are `turn.accepted`, `turn.stage`, `tool.started`, `tool.completed`, `model.checkpoint`, `turn.needs_input`, and `turn.completed`.

- Remove unauthenticated WebSocket/capability-token access.
- Use authenticated fetch-based SSE with `X-Auth-Token` and event-cursor replay; poll `GET /turns/{id}` if realtime disconnects.
- Auto-save a structurally valid frontend draft before starting a mutating AI turn. Do not send the current incorrect `unsavedDraftPatch`. Block mutation and show validation details if the local draft cannot be saved.
- Keep the composer responsive after the `202` response. Show current stage, elapsed time, exact saved checkpoint/element count, source coverage, token/call usage, and Stop.
- Reload the canvas after each persisted checkpoint, not from an uncommitted preview.
- On partial failure show: what was saved, why processing stopped, remaining work, Continue, and Undo turn.
- Show source-grounded and inferred badges with evidence/assumption details.
- Do not show raw reasoning or simulated token streaming.

## Verification, Evaluation, and Production Gates

### Automated tests

- Stack migration: full reactor compile/tests, backend startup, readiness, Flyway, OpenAPI, JSON/XMI import/export, transformations, and Testcontainers PostgreSQL.
- Contract tests across all current 90 CIM, 144 PIM, and 242 PSM EClasses:
  - inherited feature accuracy;
  - containment/assignability;
  - required closure;
  - enum serialization/defaults;
  - metamodel SHA drift.
- Property-style compiler tests over every creatable EClass: either produce an EMF-valid checkpoint or return explicit unsatisfied obligations; never persist an invalid model.
- Agent-loop fixtures for one-call terminal mutation, explanation, inspection then mutation, malformed tool arguments, unknown types, invalid enums, missing owners, duplicate IDs, partial component success, and exhausted budgets.
- Resilience tests for cancellation during provider calls, timeout, restart/lease recovery, idempotent replay, rate limit, fallback, provider failure after checkpoints, concurrent edits, deterministic rebase, conflict, undo, and deletion confirmation.
- Source tests for no truncation, stable chunk IDs, cross-batch deduplication, provenance, prompt injection, mixed-language text, inference labeling, and complete coverage.
- Frontend Node tests for async turn state, replayed/duplicate events, checkpoint canvas refresh, reconnect/poll fallback, partial/continue/undo, and inferred-content display.
- Do not run linters or formatters unless separately requested; use focused tests and build/type checks during implementation.

### Live evaluation harness

Rebuild an opt-in live harness using `.env` credentials, with a preflight report showing the hard maximum calls and tokens before execution.

Evaluate:

- both checked-in document-to-CIM samples;
- prompt-only full CIM creation;
- add/edit/delete/undo on existing CIM, PIM, and PSM models;
- explanations and selected-element edits;
- ambiguous prompts and clarification;
- free/small-model capability;
- provider failure after the first checkpoint.

Mandatory gates:

- 100% of persisted checkpoints pass structural EMF/Ecore validation.
- Zero EVL invocations on assistant commits.
- New-model turns publish a valid starter checkpoint before the first provider call.
- Relevant source-unit accountability is 100%; successful source turns have zero deferred relevant units.
- Median provider calls: at most 1 for ordinary turns and at most 2 for the checked-in source documents.
- Hard caps: 2 ordinary and 3 source provider requests.
- Average structural repair calls below 0.2 per mutation turn.
- No prompt/source truncation within configured limits.
- Backend p95 overhead excluding provider time below 500 ms per agent iteration.
- Every turn reaches a durable terminal state within the five-minute deadline.
- A provider failure, cancellation, timeout, or restart never removes a previously committed checkpoint.
- The low-cost/free-model matrix meets the structural gate and minimum task-completion thresholds.

### Observability and rollout

- Record calls, prompt/completion tokens, latency, finish reason, tool choice, checkpoints, compiler rejections, validation time, source coverage, fallback, cancellation, and final state through Micrometer.
- Never log prompt text, attachments, tool arguments, source contents, credentials, or chain-of-thought.
- Add Grafana panels and alerts for structural failures, zero-progress modeling turns, deadline breaches, call-budget breaches, provider errors, partial-turn rate, and token regressions.
- Write ADRs for progressive valid checkpoints, Ecore authority, explicit Spring AI tool-loop control, single-agent-over-multi-agent choice, inference provenance, and no-EVL apply gating.
- Produce a thesis evaluation report comparing the current and redesigned implementations on task success, structural validity, calls, tokens, repair rate, latency, resilience, and source coverage, including ablations for full metamodel prompts versus retrieval and single-agent versus source workers.
- Deploy with the assistant disabled, apply the clean assistant-schema migration, run deterministic and live gates, then enable for a small user cohort. Rollback consists of disabling AI and restoring the pre-migration assistant-table backup; model checkpoints remain ordinary structurally valid model revisions.

## Assumptions

- Existing project/model data must remain intact; existing assistant history may be discarded.
- OpenAI-compatible and Gemini providers remain supported.
- Java remains at 17 despite the Boot 4 migration.
- Inferred source-document content is permitted only when visibly labeled and separately traceable.
- EVL remains available elsewhere in the platform but is never an assistant apply gate.
- Provider token streaming is intentionally omitted.
- The first production implementation is a single autonomous agent with deterministic tools, not a multi-agent swarm.
