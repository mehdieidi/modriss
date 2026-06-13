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
MODLESS_AI_ENABLED=true
MODLESS_AI_MODE=GUARDED_APPLY
MODLESS_AI_PROVIDER=openai
OPENAI_COMPATIBLE_API_KEY=your_api_key
```

Useful optional knobs:

```bash
MODLESS_AI_PROVIDER=openai
OPENAI_COMPATIBLE_BASE_URL=https://api.openai.com
MODLESS_AI_PLANNER_MODEL=gpt-4o-mini
MODLESS_AI_RESPONDER_MODEL=gpt-4o-mini
MODLESS_AI_SUMMARIZER_MODEL=gpt-4o-mini
MODLESS_AI_MAX_TOOL_CALLS=24
MODLESS_AI_TOKEN_BUDGET=6000
MODLESS_AI_REQUEST_TIMEOUT=120s
```

`MODLESS_AI_REQUEST_TIMEOUT` defaults to 120 seconds. OpenAI-compatible gateways and reasoning
models can have highly variable time-to-first-byte latency; lower values may reject otherwise
successful requests. A timeout is not retried because the provider may still be processing the
original request.

For another OpenAI-compatible provider, keep `MODLESS_AI_PROVIDER=openai` and set
`OPENAI_COMPATIBLE_BASE_URL`, `OPENAI_COMPATIBLE_API_KEY`, and the role-specific model names to the
provider values.

For Gemini:

```bash
MODLESS_AI_PROVIDER=gemini
GEMINI_API_KEY=your_gemini_api_key
MODLESS_AI_RESPONDER_MODEL=gemini-2.0-flash
```

If you do not want live model changes, set:

```bash
MODLESS_AI_MODE=EXPLAIN_ONLY
```

The modes are:

- `EXPLAIN_ONLY`: safest mode. The assistant explains and retrieves context but does not draft
  changes.
- `PROPOSAL_ONLY`: the assistant can draft backend-validated proposals, but changes require user
  approval.
- `GUARDED_APPLY`: low-risk validated proposals can be applied automatically; risky changes still
  require approval.

Do not put API keys directly in `application.yml`. Use environment variables or a local `.env` file
that is not committed.

## Docker Compose

`docker compose up` starts PostgreSQL, backend, frontend, and landing. It also starts the backend
with the AI code available, but AI calls are disabled by default:

```bash
MODLESS_AI_ENABLED=false
```

To enable AI for a Compose run, set environment variables before starting Compose:

```bash
MODLESS_AI_ENABLED=true
MODLESS_AI_MODE=EXPLAIN_ONLY
MODLESS_AI_PROVIDER=openai
OPENAI_COMPATIBLE_API_KEY=your_api_key
docker compose up --build
```

For proposal testing:

```bash
MODLESS_AI_ENABLED=true
MODLESS_AI_MODE=PROPOSAL_ONLY
MODLESS_AI_PROVIDER=openai
OPENAI_COMPATIBLE_API_KEY=your_api_key
docker compose up --build
```

For guarded apply testing:

```bash
MODLESS_AI_ENABLED=true
MODLESS_AI_MODE=GUARDED_APPLY
MODLESS_AI_PROVIDER=openai
OPENAI_COMPATIBLE_API_KEY=your_api_key
docker compose up --build
```

Compose passes the common AI settings into the backend container. The backend service defaults the
AI proxy host to `host.docker.internal`, because `127.0.0.1` inside the container would mean the
container itself, not your host machine.

To turn AI off again:

```bash
MODLESS_AI_ENABLED=false
docker compose up --build
```

## How to set the proxy

The backend uses a dedicated proxy only for AI requests.

Default local proxy settings:

```bash
MODLESS_AI_PROXY_ENABLED=true
MODLESS_AI_PROXY_TYPE=HTTP
MODLESS_AI_PROXY_HOST=127.0.0.1
MODLESS_AI_PROXY_PORT=2081
```

For SOCKS:

```bash
MODLESS_AI_PROXY_TYPE=SOCKS
MODLESS_AI_PROXY_PORT=2082
```

If the backend runs in Docker Compose, use:

```bash
MODLESS_AI_PROXY_HOST=host.docker.internal
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

## ONNX embeddings

ONNX is a portable format for running machine-learning models outside the training framework.
In plain language, it is a standard way to ship a model that can be loaded locally by different
runtime libraries. Here, that means the assistant can turn text into vectors on your machine
instead of calling another remote API just for embeddings.

The project defaults to ONNX mode for embeddings, but falls back to local hash vectors if the
native runtime is not present. The fallback keeps the app usable even when the heavy native
libraries are not installed.

To try the real ONNX embedding path, enable the extra Maven profile:

```bash
mvn -Ponnx-embeddings -pl apps/backend test
```

You can also point at custom model/tokenizer resources with:

```bash
MODLESS_AI_EMBEDDINGS_PROVIDER=ONNX
MODLESS_AI_EMBEDDINGS_MODEL_RESOURCE=...
MODLESS_AI_EMBEDDINGS_TOKENIZER_RESOURCE=...
MODLESS_AI_EMBEDDINGS_CACHE_DIRECTORY=...
```

What these mean:

- `MODLESS_AI_EMBEDDINGS_PROVIDER=ONNX`: use Spring AI's ONNX embedding path instead of the local
  hash fallback.
- `MODLESS_AI_EMBEDDINGS_MODEL_RESOURCE=...`: where the ONNX model file comes from. If you leave it
  blank, Spring AI uses its built-in default model resource.
- `MODLESS_AI_EMBEDDINGS_TOKENIZER_RESOURCE=...`: optional tokenizer file for the model. If you
  leave it blank, Spring AI uses its built-in default tokenizer resource.
- `MODLESS_AI_EMBEDDINGS_CACHE_DIRECTORY=...`: where downloaded or extracted model files may be
  cached locally.

There are two more related switches:

```bash
MODLESS_AI_EMBEDDINGS_DISABLE_CACHING=false
MODLESS_AI_EMBEDDINGS_GPU_DEVICE_ID=-1
```

- `disable-caching=false` keeps local caching on.
- `gpu-device-id=-1` means CPU/default runtime. Set `0` or another number only if you have a GPU
  runtime configured.

If the ONNX runtime cannot start, the service automatically uses the local hash embedding fallback
unless you disable that fallback in configuration.

### Common ONNX settings

Most local development does not require you to provide anything.

Use the default safe setup:

```bash
MODLESS_AI_EMBEDDINGS_PROVIDER=ONNX
MODLESS_AI_EMBEDDINGS_FALLBACK_TO_HASH=true
```

This means: try ONNX, and if the native ONNX runtime is not available, keep working with local hash
embeddings.

Use hash embeddings only:

```bash
MODLESS_AI_EMBEDDINGS_PROVIDER=HASH
```

This is useful when you only want the app to run and do not care about high-quality semantic
retrieval.

Use a custom ONNX model:

```bash
MODLESS_AI_EMBEDDINGS_PROVIDER=ONNX
MODLESS_AI_EMBEDDINGS_MODEL_RESOURCE=file:/absolute/path/to/model.onnx
MODLESS_AI_EMBEDDINGS_TOKENIZER_RESOURCE=file:/absolute/path/to/tokenizer.json
MODLESS_AI_EMBEDDINGS_CACHE_DIRECTORY=/absolute/path/to/cache
```

Only use the custom model settings if you already know which ONNX embedding model and tokenizer you
want. Otherwise leave them blank.

## Where the AI gets context

The assistant gets its knowledge from three backend-owned sources:

1. **Metamodel and EVL catalogs**

   - The backend scans the local `mde/` tree.
   - It indexes `.ecore`, `.emf`, and `.evl` files into PostgreSQL.
   - The catalog entries include level, class name, attributes, references, multiplicities,
     constraint kind, and source location.
   - Exact title/source matches are tried first; fuzzy retrieval is used second.

2. **Current model context**

   - When you open a session, the frontend sends the current `modelId`, `revision`, active view,
     selected element IDs, and optional draft patch.
   - The backend loads the model record and builds a compact index of stable IDs, names, types,
     JSON paths, relationships, and the latest validation issues.
   - That compact index is what the assistant sees, not the full model dump.

3. **Recent conversation memory**
   - Spring AI JDBC chat memory keeps the recent message window.
   - The backend also keeps durable history, summary, proposals, and audits in its own tables.

So the assistant learns the needed metamodel and EVL information from the indexed local catalog,
and the current model state from the model-context snapshot built from the saved model record.
It does not get the whole model or whole EVL file.

## What is stored in PostgreSQL

The AI feature adds these tables:

- `SPRING_AI_CHAT_MEMORY`: recent chat memory used by Spring AI.
- `assistant_threads`: one assistant thread per user/project/modeling level.
- `assistant_messages`: durable user/assistant/system/tool message audit history.
- `assistant_thread_summaries`: rolling summaries of long conversations.
- `assistant_proposals`: semantic patch proposals, risk level, approval requirement, validation
  preview, citations, and inverse patch for undo.
- `assistant_action_audits`: approve/reject/apply/undo/choice audit records.
- `assistant_retrieval_documents`: indexed metamodel and EVL snippets with embeddings for RAG.
- `assistant_model_contexts`: compact model snapshots by model ID and revision.
- `assistant_rate_limits`: persisted request windows for rate limiting.

How they get filled:

- Flyway creates the tables when the backend starts against PostgreSQL.
- `assistant_retrieval_documents` is filled on backend startup by scanning the local `mde/` folder.
- `assistant_model_contexts` is filled when the assistant handles a request for a saved model.
- chat memory and durable messages are filled when users send messages in the chatbot.
- proposals and audits are filled when the assistant drafts, applies, rejects, or undoes changes.
- rate-limit rows are filled as users call the assistant.

## If metamodels or EVL files change

The catalog indexer runs at backend startup. It scans `.ecore`, `.emf`, and `.evl` files under
`mde/`, computes a hash for each source file, and compares it to the stored `source_hash`.

If a file changed:

- old catalog rows for that source are deleted
- new catalog rows are inserted
- embeddings are regenerated

If a file did not change, it is skipped.

What you should do after changing metamodel or EVL files:

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

If the assistant is in `EXPLAIN_ONLY`, it will explain and cite context but not propose changes.
If it is in `PROPOSAL_ONLY` or `GUARDED_APPLY`, it can draft proposals, and the backend will still
block anything that fails validation.

## Does the frontend offer choices?

Yes.

- If the assistant returns a proposal, the UI renders proposal cards with context, validation,
  citations, and action buttons.
- If approval is required, the buttons are `Approve` and `Reject`.
- If the proposal was already applied, the UI shows `Undo`.
- If the backend returns an explicit choice request, the UI renders the choice prompt and the option
  buttons for the user to pick from.

So the chatbot can ask the user to choose when it needs a bounded decision, and the frontend will
show those options directly.

## Notes

- Chat memory is cleared when you clear a conversation.
- The assistant never receives the full model or full EVL files.
- Provider changes are configuration only, not code changes.
