# AI Assistant Setup and Current Design

This project has a bounded backend assistant for model help, source-backed modeling, and validated
model mutation. It works from the active project, selected model level, current model ID/revision,
selected elements, optional text attachments, deterministic metamodel contracts, and bounded recent
conversation context.

For the current source-to-CIM workflow status and live-eval notes, see
[current-llm-workflow.md](current-llm-workflow.md).

## What the assistant uses

- OpenAI-compatible or Gemini providers via Spring AI for assistant turns.
- Spring AI JDBC chat memory for recent conversation window.
- PostgreSQL for durable threads, turns, events, checkpoints, provenance, provider-call audits, and
  chat memory.
- Backend metamodel contracts and validation before any model mutation is committed.

## How to enable it

Set these environment variables before starting the backend:

```bash
VARKA_AI_ENABLED=true
VARKA_AI_PROVIDER=openai
OPENAI_COMPATIBLE_API_KEY=your_api_key
```

Useful optional knobs:

```bash
VARKA_AI_PROVIDER=openai
OPENAI_COMPATIBLE_BASE_URL=https://api.openai.com
VARKA_AI_RESPONDER_MODEL=gpt-4o-mini
VARKA_AI_MAX_TOOL_CALLS=24
VARKA_AI_MAX_AGENT_STEPS=8
VARKA_AI_MAX_PROVIDER_CALLS_PER_TURN=8
VARKA_AI_MAX_PROVIDER_CALLS_SOURCE_TURN=6
VARKA_AI_TOKEN_BUDGET=16000
VARKA_AI_REQUEST_TIMEOUT=5m
VARKA_AI_TURN_TIMEOUT=5m
VARKA_AI_SOURCE_TURN_TIMEOUT=5m
VARKA_AI_OPENAI_PROTOCOL=tools
```

`VARKA_AI_REQUEST_TIMEOUT` and `VARKA_AI_TURN_TIMEOUT` default to 5 minutes. Connection
establishment still fails after at most 10 seconds. A response timeout is not retried because the
provider may still be processing the original request.

For another OpenAI-compatible provider, keep `VARKA_AI_PROVIDER=openai` or use the accepted aliases
`openai-compatible` / `openai_compatible`, then set `OPENAI_COMPATIBLE_BASE_URL`,
`OPENAI_COMPATIBLE_API_KEY`, and role model variables to provider-specific values. Current role
keys are `VARKA_AI_DIRECTOR_MODEL`, `VARKA_AI_MODELER_MODEL`, `VARKA_AI_CRITIC_MODEL`,
`VARKA_AI_SUMMARIZER_MODEL`, and `VARKA_AI_RESPONDER_MODEL`; the older planner/responder/summarizer
triple is still accepted for compatibility.

`VARKA_AI_OPENAI_PROTOCOL=tools` selects the native OpenAI-compatible tool-call path. The provider
adapter also accepts strict JSON action content when a compatible endpoint returns a JSON object in
`message.content` instead of native `tool_calls`.

For Gemini:

```bash
VARKA_AI_PROVIDER=gemini
GEMINI_API_KEY=your_gemini_api_key
VARKA_AI_RESPONDER_MODEL=gemini-2.0-flash
```

If you do not want provider calls or assistant model changes, set:

```bash
VARKA_AI_ENABLED=false
```

The assistant runs as one autonomous modeling agent. The LLM can answer, ask for needed input, or
drive validated model tools. The backend validates model-changing work before committing it and
stores checkpoints so committed changes can be undone through the API and UI.

Do not put API keys directly in `application.yml`. Use environment variables or a local `.env` file
that is not committed.

## Modeling protocol

There is one assistant modeling mode. Model-changing turns run through `AgentTurnLoop` with
tool-based access to an in-memory `ModelWorkspace`. Tool calls are checked against live Ecore
contracts before they mutate the workspace. The final workspace is structurally validated and then
committed atomically against the expected model revision.

The LLM returns one action per step:

- `plan_source_model` for a large source-document blueprint.
- `inspect_model` for current model facts.
- `describe_types` for exact Ecore contracts.
- `commit_model_batch` for creates, updates, connections, deletions, and source evidence.
- `answer_user` for non-mutating answers.
- `ask_user` when required input is missing.

The OpenAI-compatible native tool schema exposes the mutation action as `apply_draft_patch` and maps
it back to `commit_model_batch` internally.

### Source-backed CIM flow

For source attachments, the worker extracts text and persists bounded `assistant_source_units`.
The prompt includes either concrete source units or a source-document map. Small and medium files are
modeled directly; larger files may be planned into slices with `plan_source_model`. Source-backed
turns reject premature answer/question actions while modelable source evidence remains in scope.

Committed element provenance is labelled source-grounded or inferred. The backend replaces
provider-facing `clientRef` values with UUID element IDs, validates source evidence IDs, normalizes
safe containment/reference issues, and records checkpoint/source coverage details on the durable
turn.

## Docker Compose

`docker compose up` starts PostgreSQL, backend, frontend, landing, LocalStack,
and Dozzle. The backend ships with assistant code available, but AI calls are disabled by default:

```bash
VARKA_AI_ENABLED=false
```

To enable AI for a Compose run, set environment variables before starting Compose:

```bash
VARKA_AI_ENABLED=true
VARKA_AI_PROVIDER=openai
OPENAI_COMPATIBLE_API_KEY=your_api_key
docker compose up --build
```

Compose passes `.env` into the backend container. The AI proxy is disabled unless
`VARKA_AI_PROXY_ENABLED=true`; when the backend runs in a container, set
`VARKA_AI_PROXY_HOST=host.docker.internal` if the proxy is running on the host.

To turn AI off again:

```bash
VARKA_AI_ENABLED=false
docker compose up --build
```

## How to set the proxy

The backend uses a dedicated proxy only for AI requests.

Default local proxy settings:

```bash
VARKA_AI_PROXY_ENABLED=true
VARKA_AI_PROXY_TYPE=HTTP
VARKA_AI_PROXY_HOST=127.0.0.1
VARKA_AI_PROXY_PORT=2081
```

For SOCKS:

```bash
VARKA_AI_PROXY_TYPE=SOCKS
VARKA_AI_PROXY_PORT=2082
```

If the backend runs in Docker Compose, use:

```bash
VARKA_AI_PROXY_HOST=host.docker.internal
```

Compose already defaults to that host for the backend container.

Proxy health is checked separately from the rest of the app. If the proxy is unreachable, AI
requests fail clearly without affecting normal app traffic.

## How to give the assistant a model

The chatbot needs an existing project plus a model in the relevant level:

- `CIM`
- `PIM`
- `PSM`

The assistant session is scoped by project and level. It uses the selected model ID and revision
from the frontend, so create or open a real model first.

### Easiest path in the frontend

1. Create a project.
2. Open the CIM, PIM, or PSM workspace.
3. Create or import a model in that workspace.
4. Open the chat panel for the same project and level.
5. Send a message.

The chat request includes:

- `modelId`
- `revision` or `expectedRevision`
- `activeView`
- selected element IDs
- optional uploaded attachment IDs or inline attachment content
- an `idempotencyKey`

That is the compact context the backend needs.

### API path

You can also create a model directly:

```http
POST /api/cim
X-Auth-Token: <token>
Content-Type: application/json

{
  "projectId": "project-1",
  "name": "My CIM model",
  "model": {
    "eClass": "CIMModel",
    "diagram": {
      "elements": [],
      "relationships": []
    }
  }
}
```

Use `/api/pim` or `/api/psm` for the other levels.

The exact model shape can come from the existing editor or an import. The assistant only needs a
saved model ID and revision to work against.

## Where the AI gets context

The assistant gets its working context from three backend-owned sources:

1. **Metamodel and methodology catalogs**

   - The backend extracts deterministic contracts from the current Ecore metamodels.
   - Contracts include classifier names, attributes, references, containments, multiplicities,
     enum values, and level ownership.
   - Raw `.evl` files are not sent to the provider.

2. **Current model context**

   - The backend loads the saved model by `modelId` and revision, then presents it through
     validated workspace tools.
   - Selected element IDs help focus reads and edits, but the backend still enforces model access
     and revision checks.

3. **Recent conversation and source attachments**
   - Spring AI JDBC chat memory keeps the recent message window.
   - Durable assistant messages and turns preserve history and audit details.
   - Uploaded `.md`, `.txt`, and `.json` attachments can be split into bounded source units with
     per-element provenance.

So the assistant learns formal structure from Ecore-derived contracts and current state from
workspace reads. It does not get raw EVL files or unchecked direct database access.

## What is stored in PostgreSQL

The AI feature adds these tables:

- `SPRING_AI_CHAT_MEMORY`: recent chat memory used by Spring AI.
- `assistant_threads`: one assistant thread per user/project/modeling level.
- `assistant_messages`: durable user/assistant/system/tool message audit history.
- `assistant_thread_summaries`: rolling summaries of long conversations.
- `assistant_turns`: durable accepted/running/terminal assistant turns with idempotency keys,
  deadlines, model/revision state, counters, final messages, and cancellation flags.
- `assistant_turn_events`: replayable turn events with cursor IDs and per-turn sequences.
- `assistant_checkpoints`: committed model checkpoints and inverse patches used for undo.
- `assistant_source_units`: bounded source-document chunks used by source-backed turns.
- `assistant_element_provenance`: source-grounded or inferred labels for committed elements.
- `assistant_provider_calls`: provider/model/timing/token/error audit records.
- `assistant_action_audits`: cancellation, confirmation, undo, and other turn action records.
- `assistant_rate_limits`: schema reserved for persisted rate limiting; runtime limiting is
  in-memory today.

How they get filled:

- Flyway creates the tables when the backend starts against PostgreSQL.
- chat memory and durable messages are filled when users send messages in the chatbot.
- turns, events, checkpoints, source units, provenance, provider calls, and audits are filled as
  durable assistant work is accepted and processed.
- rate limiting is enforced in memory; the `assistant_rate_limits` table is not written today.

## If metamodels or methodology files change

What you should do after changing metamodel or methodology files:

1. Rebuild/restart the backend so packaged `mde/` resources and Ecore-derived contracts are fresh.
2. If model validation rules changed, revalidate affected models in the UI or via the validation
   endpoints before testing assistant turns.

If you are running with Docker Compose:

```bash
docker compose up --build
```

If you changed only files mounted into an already-running container, restart the backend container:

```bash
docker compose restart backend
```

Assistant turns use model ID and expected revision. When a model changes and gets a new revision,
new turns must use the updated revision or they will conflict.

## Frontend test flow

1. Start PostgreSQL.
2. Start the backend with the AI vars above.
3. Start the frontend.
4. Log in, create or open a project.
5. Create a model in CIM, PIM, or PSM.
6. Open the chat panel and ask for an explanation or a bounded change.

The assistant may explain, ask for needed input, apply a validated change, finish partially, or ask
for explicit confirmation before destructive work. The backend never commits an invalid mutation.
Applied changes can be undone when a checkpoint inverse is available.

## Current live status

Latest local live test through the real multipart upload path:

- `story-v1-single.md`: `SUCCEEDED`, 100% source coverage, one checkpoint, 16 saved elements, four
  provider calls.
- Semantic validation still reports `CIMModelHasSemanticCore` for the live persisted result. The
  unit-tested semantic-core synthesis guard exists, but the live JSON/XMI validation discrepancy
  remains the next issue to fix before treating source-to-CIM as fully validation-green.

Focused regression tests for source splitting, provider action parsing, turn-loop behavior, and
model tools currently pass: 26 tests, 0 failures.

## Does the frontend offer turn controls or undo?

Yes.

- The UI follows durable turn status and replayable SSE events.
- A completed/partial turn can be continued with `POST /api/chatbot/turns/{turnId}/continue`.
- A turn that needs destructive confirmation can be confirmed with
  `POST /api/chatbot/turns/{turnId}/confirm`.
- A committed checkpoint can be undone with `POST /api/chatbot/turns/{turnId}/undo`.

There is no Approve/Reject step in the current UI or API.

## Notes

- Chat memory is cleared when you clear a conversation.
- The assistant does not receive raw EVL files.
- Provider changes are configuration only, not code changes.
