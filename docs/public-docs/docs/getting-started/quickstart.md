# Quickstart

The fastest way to run the complete development stack is Docker Compose.

## Prerequisites

- Docker with Compose support
- At least several gigabytes of available memory and disk space
- A modern browser

Java, Maven, Python, Node.js, AWS CLI, SAM CLI, and Go are only required for source development or
generated-project verification.

## Start the Stack

From the repository root:

```bash
docker compose up --build
```

Wait for PostgreSQL and the backend to become healthy, then open:

- Modeling frontend: `http://127.0.0.1:8082`
- Backend health: `http://127.0.0.1:8080/api/health`
- API browser: `http://127.0.0.1:8080/swagger-ui.html`
- Landing site: `http://127.0.0.1:8083`
- Container logs: `http://127.0.0.1:9999`

## First Project

1. Register a user in the frontend.
2. Create a project.
3. Open the CIM workspace.
4. Create a model or import `mde/samples/cim.xmi`.
5. Save and validate the CIM.
6. Select **Generate PIM**, review the result, then select **Generate PSM**.
7. From PSM, select **Generate Artifacts**.
8. Open the artifact explorer and download the generated ZIP.

The included climate-relief sample exercises a broad portion of the CIM language and is the best
starting point for exploring the full pipeline.

## Enable the AI Assistant

The assistant is configurable and should remain disabled unless provider credentials, operating
cost, and organizational policy are intentional.

```bash
VARKA_AI_ENABLED=true
VARKA_AI_PROVIDER=openai
OPENAI_COMPATIBLE_BASE_URL=your_arvan_openai_compatible_base_url
OPENAI_COMPATIBLE_API_KEY=your_api_key
VARKA_AI_MODEL=Gemma-4-31B-IT
VARKA_AI_MODE=unified
VARKA_AI_OPENAI_PROTOCOL=json_schema
VARKA_AI_NATIVE_TOOLS_PREFERRED=false
VARKA_AI_FORCED_TOOL_CHOICE_RELIABLE=false
VARKA_AI_REQUEST_TIMEOUT=240s
VARKA_AI_TURN_TIMEOUT=12m
VARKA_AI_SOURCE_TURN_TIMEOUT=25m
VARKA_AI_MAX_SYSTEM_CHARS=32000
VARKA_AI_MAX_COMPLETION_TOKENS=16000
VARKA_AI_MAX_PROVIDER_CALLS_PER_TURN=64
VARKA_AI_MAX_PROVIDER_CALLS_SOURCE_TURN=96
VARKA_AI_PROVIDER_RETRY_ATTEMPTS=0
VARKA_AI_PREFER_LLM_SOURCE_EXTRACTION=true
VARKA_AI_LLM_REVIEW_ENABLED=true
docker compose up --build
```

The validated deployment uses Arvan and `Gemma-4-31B-IT`. `unified` automatically chooses the
internal modeling workflow; users and API clients do not select it. These limits are the tested
Gemma profile: a standard turn reserves up to two calls for adaptive routing and exposes the
remaining bounded budget to obligation planning, type selection, blueprinting, private slices,
corrections and independent review. The conceptual schema caps blueprints at 96 objects/types and
requirement ledgers at 64 obligations;
required Ecore closure and call reserves can lower the effective capacity.

## Stop or Reset

Stop containers while preserving data:

```bash
docker compose down
```

Remove local PostgreSQL and emulator volumes (only when you intentionally want a clean state):

```bash
docker compose down -v
```

The second command permanently removes local runtime data.
