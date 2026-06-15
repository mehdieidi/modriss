# AI Assistant

The Modless assistant is a bounded modeling assistant. It can explain formal concepts, retrieve
metamodel and constraint context, ask bounded questions, and draft validated semantic model-change
proposals.

## Operating Modes

| Mode            | Behavior                                                                             |
| --------------- | ------------------------------------------------------------------------------------ |
| `EXPLAIN_ONLY`  | Explains and retrieves context; does not draft model changes                         |
| `PROPOSAL_ONLY` | Drafts backend-validated proposals that require user approval                        |
| `GUARDED_APPLY` | May automatically apply low-risk validated proposals; risky changes require approval |

`EXPLAIN_ONLY` is the safest default for public or exploratory deployments.

## Context Boundary

The assistant does not receive an unrestricted dump of the entire model or raw EVL files. It works
from:

- Project, modeling level, selected model ID, and revision
- Active view, selected element IDs, and optional compact draft patch
- A compact indexed model context
- Retrieved metamodel and EVL catalog entries
- Recent conversation memory and durable summaries

Metamodel and constraint catalogs are indexed from `.emf`, `.ecore`, and `.evl` files at backend
startup. Changed sources are detected by hash and reindexed.

## Proposal Lifecycle

```mermaid
flowchart LR
    U["User request"] --> R["Retrieve context"]
    R --> D["Draft semantic patch"]
    D --> V["Compile and validate"]
    V --> P["Proposal"]
    P --> A["Approve / reject"]
    A --> AP["Apply"]
    AP --> UN["Optional undo"]
```

Proposals include risk, approval requirements, validation summary, citations, status, and an inverse
patch when undo is available. Proposal and action records are persisted for auditability.

## Providers and Embeddings

Chat providers:

- OpenAI-compatible endpoints through Spring AI
- Gemini through Spring AI

Retrieval embeddings default to local ONNX with a hash-vector fallback. The hash fallback keeps
development environments functional without native ONNX dependencies, but provides lower-quality
semantic retrieval.

## Enable Safely

```bash
MODLESS_AI_ENABLED=true
MODLESS_AI_MODE=EXPLAIN_ONLY
MODLESS_AI_PROVIDER=openai
OPENAI_COMPATIBLE_API_KEY=your_api_key
```

Do not commit provider credentials. Use environment variables or an untracked local `.env`.

## Realtime Behavior

Message submission uses REST. Progress and proposal events can be received through SSE or the
receive-only assistant WebSocket. See [Realtime Assistant API](../reference/realtime-api.md).
