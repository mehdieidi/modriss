# AI Assistant Setup

This project has a bounded backend assistant for model help, retrieval, and proposal drafting.
It does not receive whole models or raw EVL files. It works from compact context, the active
project, the selected model level, and the current model ID/revision.

## What the assistant uses

- OpenAI-compatible or Gemini providers via Spring AI for chat and structured proposal drafting.
- Spring AI JDBC chat memory for recent conversation window.
- PostgreSQL + pgvector for retrieval storage.
- Local ONNX embeddings when enabled, with a hash fallback for constrained local runs.
- Backend validation before any proposal can be applied.

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
VARKA_AI_PLANNER_MODEL=gpt-4o-mini
VARKA_AI_RESPONDER_MODEL=gpt-4o-mini
VARKA_AI_SUMMARIZER_MODEL=gpt-4o-mini
VARKA_AI_MAX_TOOL_CALLS=24
VARKA_AI_TOKEN_BUDGET=16000
VARKA_AI_REQUEST_TIMEOUT=5m
VARKA_AI_TURN_TIMEOUT=5m
```

`VARKA_AI_REQUEST_TIMEOUT` and `VARKA_AI_TURN_TIMEOUT` default to 5 minutes. Connection
establishment still fails after at most 10 seconds. A response timeout is not retried because the
provider may still be processing the original request.

For another OpenAI-compatible provider, keep `VARKA_AI_PROVIDER=openai` and set
`OPENAI_COMPATIBLE_BASE_URL`, `OPENAI_COMPATIBLE_API_KEY`, and the role-specific model names to the
provider values.

For Gemini:

```bash
VARKA_AI_PROVIDER=gemini
GEMINI_API_KEY=your_gemini_api_key
VARKA_AI_RESPONDER_MODEL=gemini-2.0-flash
```

If you do not want provider calls or model proposals, set:

```bash
VARKA_AI_ENABLED=false
```

The assistant runs as one autonomous modeling agent. The LLM answers, asks structured
clarification questions when needed, or drafts `ModelDelta` from retrieved context. The backend
compiles and validates model-changing work before applying it, then exposes undo through the API
and UI.

Do not put API keys directly in `application.yml`. Use environment variables or a local `.env` file
that is not committed.

## Modeling protocol

There is one assistant modeling mode. Model-changing turns go through `ModelingAgent`, whose
provider-facing output contract is `ModelDelta`. The backend lowers that delta into internal patch
operations, structurally validates the preview, and applies atomically against the expected model
revision. The old selectable modeling-mode switch is removed.

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
- `revision`
- `activeView`
- selected element IDs
- optional draft patch

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

The assistant gets its knowledge from three backend-owned sources:

1. **Metamodel and methodology catalogs**

   - The backend scans `mde/**/*.emf` and `mde/**/*.ecore` plus methodology JSON/Markdown under
     `mde/` and `docs/public-docs/docs/guides/`.
   - Catalog entries include level, classifier names, attributes, references, multiplicities, and
     source locations.
   - Raw `.evl` constraint files are **not** indexed into retrieval documents; EVL scope rows are
     removed on every refresh.
   - Exact title/source matches are tried first; hybrid full-text and vector retrieval is used
     second.

2. **Current model context**

   - When you open a session, the frontend sends the current `modelId`, `revision`, active view,
     selected element IDs, and optional draft patch.
   - The backend loads the model record and builds a compact index of stable IDs, names, types,
     JSON paths, relationships, and the latest validation issues.
   - That compact index is what the assistant sees, not the full model dump.

3. **Recent conversation memory**
   - Spring AI JDBC chat memory keeps the recent message window.
   - The backend also keeps durable history, summary, proposals, and audits in its own tables.

So the assistant learns formal structure from indexed metamodel and methodology catalogs, and the
current model state from the model-context snapshot built from the saved model record. It does not
get the whole model or whole EVL files.

## What is stored in PostgreSQL

The AI feature adds these tables:

- `SPRING_AI_CHAT_MEMORY`: recent chat memory used by Spring AI.
- `assistant_threads`: one assistant thread per user/project/modeling level.
- `assistant_messages`: durable user/assistant/system/tool message audit history.
- `assistant_thread_summaries`: rolling summaries of long conversations.
- `assistant_proposals`: applied ModelDelta operation previews, risk level, validation preview, citations, and
  inverse patch for undo.
- `assistant_action_audits`: apply, undo, and choice audit records.
- `assistant_retrieval_documents`: indexed metamodel and methodology snippets with embeddings for
  RAG.
- `assistant_model_contexts`: compact model snapshots by model ID and revision.
- `assistant_rate_limits`: schema reserved for future persisted rate limiting (runtime limiting is
  in-memory today).

How they get filled:

- Flyway creates the tables when the backend starts against PostgreSQL.
- `assistant_retrieval_documents` is filled on backend startup by scanning the local `mde/` folder.
- `assistant_model_contexts` is filled when the assistant handles a request for a saved model.
- chat memory and durable messages are filled when users send messages in the chatbot.
- proposals and audits are filled when the assistant auto-applies or undoes changes.
- rate limiting is enforced in memory; the `assistant_rate_limits` table is not written today.

## If metamodels or methodology files change

The catalog indexer runs at backend startup. It scans `.emf` and `.ecore` files under `mde/`, plus
methodology guides, computes a hash for each source file, and compares it to the stored
`source_hash`.

If a file changed:

- old catalog rows for that source are deleted
- new catalog rows are inserted
- embeddings are regenerated

If a file did not change, it is skipped.

What you should do after changing metamodel or methodology files:

1. Restart the backend.
2. Let startup reindex the changed files.
3. If model validation rules changed, revalidate affected models in the UI or via the validation
   endpoints before testing assistant proposals.

If you are running with Docker Compose:

```bash
docker compose up --build
```

If you changed only files mounted into an already-running container, restart the backend container:

```bash
docker compose restart backend
```

Model context snapshots are stored by model ID and revision. When a model changes and gets a new
revision, the assistant builds a new compact context for that revision.

## Frontend test flow

1. Start PostgreSQL.
2. Start the backend with the AI vars above.
3. Start the frontend.
4. Log in, create or open a project.
5. Create a model in CIM, PIM, or PSM.
6. Open the chat panel and ask for an explanation or a bounded change.

The assistant may explain, ask structured questions, or auto-apply a validated change. The backend
never applies an invalid mutation. Applied changes can be undone when an inverse patch is available.

## Does the frontend offer choices or undo?

Yes.

- When a change was auto-applied, the UI renders a proposal card with validation summary,
  citations, affected elements, and an **Undo changes** button.
- If the backend returns an explicit choice request, the UI renders the choice prompt and option
  buttons. Submit answers with `POST /api/chatbot/sessions/{sessionId}/choices`.

There is no Approve/Reject step in the current UI or API.

## Notes

- Chat memory is cleared when you clear a conversation.
- The assistant never receives the full model or full EVL files.
- Provider changes are configuration only, not code changes.
