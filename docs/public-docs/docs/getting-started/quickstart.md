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
VARKA_AI_MODEL=DeepSeek-V4-Flash
VARKA_AI_MODE=unified
VARKA_AI_OPENAI_PROTOCOL=json_schema
VARKA_AI_NATIVE_TOOLS_PREFERRED=false
VARKA_AI_FORCED_TOOL_CHOICE_RELIABLE=false
docker compose up --build
```

The validated deployment uses Arvan and `DeepSeek-V4-Flash`. `unified` automatically chooses the
internal modeling workflow; users and API clients do not select it.

## Stop or Reset

Stop containers while preserving data:

```bash
docker compose down
```

Remove local PostgreSQL and LocalStack volumes:

```bash
docker compose down -v
```

The second command permanently removes local runtime data.
