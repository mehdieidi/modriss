# How the Varka AI Assistant Works

This guide teaches the concepts behind the Varka AI assistant and connects each concept to the
code that implements it. For setup and operator notes see [assistant.md](assistant.md). For the
public summary see [ai-assistant.md](../../public-docs/docs/guides/ai-assistant.md).

The most important idea:

> The large language model is an adviser and proposal writer. The Varka backend remains the
> authority that compiles, structurally validates, auto-applies, audits, and undoes model changes.

The assistant is deliberately **bounded**. It does not receive an entire model, metamodel, or EVL
repository. It receives compact summaries and retrieved snippets. Valid mutations **auto-apply**
after structural validation. There is no REST approve/reject step; **undo** is the post-apply
control.

## 1. The Big Picture

Varka combines two kinds of intelligence:

1. **Probabilistic AI** — an LLM explains, clarifies, or drafts structured model changes; RAG
   supplies metamodel and methodology snippets; providers are OpenAI-compatible or Gemini.
2. **Deterministic MDE** — Ecore defines legal structure; Java compiles `ModelDelta` into
   executable patch operations; structural validation gates apply; PostgreSQL stores models, memory,
   proposals, and audits.

```mermaid
flowchart LR
    user["User request"]
    backend["AssistantOrchestrator"]
    context["Compact model context"]
    rag["Retrieved metamodel and methodology snippets"]
    llm["ModelingAgent or responder"]
    delta["ModelDelta"]
    compiler["AssistantPatchCompiler"]
    validate["Ecore structural validation"]
    apply["Auto-apply via ModelService.patch"]
    db[("PostgreSQL")]

    user --> backend
    backend --> context
    backend --> rag
    context --> llm
    rag --> llm
    llm --> delta
    delta --> compiler
    compiler --> validate
    validate --> apply
    apply --> db
```

EVL semantic validation runs elsewhere in the platform (model workbench, MDE jobs) but is **not**
the assistant apply gate today. `ModelService.validateStructural()` is authoritative for whether a
mutation may be applied.

## 2. Technology Stack

| Layer          | Technologies                                                                                      |
| -------------- | ------------------------------------------------------------------------------------------------- |
| Application    | Java 17, Spring Boot 3.5, Spring MVC, WebSocket, JDBC, Actuator                                   |
| AI integration | Spring AI 1.1 — chat, structured output, tools, JDBC chat memory, OpenAI, Gemini, ONNX embeddings |
| Storage        | PostgreSQL 16, `jsonb`, `bytea`, pgvector, full-text search, Flyway                               |
| MDE runtime    | EMF/Ecore, Emfatic, XMI, Epsilon EVL/ETL/EGX                                                      |

Key configuration:

- `apps/backend/src/main/resources/application.yml` — `varka.ai.*` bindings
- `deploy/compose.yaml` — local stack including pgvector Postgres

## 3. Modeling Protocol

There is one assistant modeling mode. Model-changing turns use `ModelingAgent`, and the
provider-facing mutation protocol is `ModelDelta`.

| Concern        | Implementation                                                        |
| -------------- | --------------------------------------------------------------------- |
| Agent          | `ModelingAgent`                                                       |
| Output         | `ModelDelta` JSON                                                     |
| Schema/context | `ModelDeltaSchemaFactory`, metamodel contract snippets, model context |
| Compiler       | `DeltaCompiler`                                                       |
| Apply gate     | `ModelService.validateStructural()` and revision-guarded apply        |

The old selectable modeling-mode switch, subset planner, and direct provider patch planning have
been removed from production wiring. Internal semantic patch classes remain as a backend-owned
compiler IR and proposal/audit shape.

## 4. Concepts

### Prompt and context boundary

`AssistantPromptGuard` redacts secrets, tags injection-like text, and caps snippet/system sizes.
Prompts include project, level, model ID, revision, view, selection, compact model context,
retrieved catalog snippets, recent chat, and the user message — never whole models or raw EVL files.

### Embeddings and RAG

`LocalAssistantEmbeddingService` supports:

- **HASH** (default) — deterministic 384-dimensional vectors
- **ONNX** — Spring AI Transformers when the `onnx-embeddings` profile is available

`JdbcAssistantCatalog` indexes:

- `mde/**/*.emf` and `mde/**/*.ecore` → package, classifier, feature, enum scopes
- Methodology JSON/Markdown under `mde/` and `docs/public-docs/docs/guides/`

On every refresh it calls `removeEvlCatalogDocuments()` — **EVL constraint rows are not kept** in
the retrieval catalog. Search uses exact title/source match, then hybrid full-text + pgvector
cosine distance.

### Model context snapshot

`JdbcAssistantModelContextIndex` caches compact element/relationship summaries per
`(modelId, revision, modelHash)` in `assistant_model_contexts`. This is separate from RAG and is
rebuilt when the revision changes.

### Structured mutations

The LLM must express changes as `ModelDelta`:

- `elements` with `localId`, `eClass`, attributes, and explicit containment placement
- `references` for writable non-containment links
- `attributeUpdates` for saved/local element attribute edits
- `deletions` only when deletion intent is explicit

`DeltaCompiler` lowers `ModelDelta` into backend-owned patch operations, and
`AssistantPatchCompiler` compiles those into JSON Pointer patch operations plus an inverse patch
for undo.

### Tools

`AssistantToolService` exposes whitelisted inspection/preview tools, including:

- `searchCatalogs`, `previewModelDelta`, `summarizeValidation`, `requestUserChoice`
- `getElementContext`, `getTypeContract`, `validateSnapshot`, `inspectCurrentModelDelta`
- `getLanguageIndex`, `getMetamodelCoverage`, `findCreatableTypes`, `findContainmentOptions`
- `summarizeCurrentModel`, `listModelElements`

Tools are bound per turn via `AssistantToolBridge.bindSession()`. Counts are tracked for metrics,
and the public configuration exposes bounded step/tool limits for the unified agent path.

## 5. One Complete Turn

Example: user asks to add a PIM function connected to the selected API.

1. Frontend `POST /api/chatbot/sessions/{id}/messages` with `modelId`, `revision`, view,
   selection, optional `unsavedDraftPatch`, and `attachmentIds`.
2. `ChatbotController` → `AssistantOrchestrator.handleMessage()`.
3. Rate limit and circuit checks (`AssistantHardeningService`).
4. Persist user message to durable history and Spring AI JDBC chat memory.
5. Load authorized model; return **409** if `expectedRevision` is stale.
6. Build or load compact model context.
7. Retrieve catalog snippets (up to `VARKA_AI_MAX_CONTEXT_SNIPPETS`, default 24, with schema
   reservation via `AssistantSnippetBudget`).
8. Plan: answer, structured clarification (`WAITING_FOR_CHOICE`), or `ModelDelta` mutation.
9. For CIM with attachments, source text is treated as untrusted evidence and passed through
   source analysis/fallback chunking; it no longer bypasses the modeling agent.
10. Compile the delta through backend-owned patch IR; run the bounded repair loop on structural
    validation failures.
11. Publish realtime `assistant.progress` and optional `assistant.model.preview` events.
12. **Auto-apply** valid patch through `ModelService.patch()`; persist proposal as `APPLIED`.
13. Return `MessageResponse` with `workflowState: APPLIED` and proposal card data; publish
    `chat.assistant` and `model.updated`.

Failed validation yields `workflowState: FAILED` with grounded feedback. No proposal is persisted.

Undo: `POST .../proposals/{id}/undo` applies the inverse patch and sets status `UNDONE`.

## 6. Proposal Lifecycle and Risk

Database statuses: `APPLIED`, `UNDONE`, `FAILED` (orchestrator primarily writes `APPLIED` and
`UNDONE`). There is no `PROPOSED` state and no approve/reject API.

Risk levels (`LOW` / `MEDIUM` / `HIGH`) are computed for display and audit context. They do **not**
block auto-apply.

Current heuristics:

- **HIGH** — contains `DELETE_ELEMENT`
- **MEDIUM** — multiple operations or advisory validation noise
- **LOW** — single non-destructive operation

## 7. Realtime Delivery

`AssistantRealtimeHub` fans out to SSE (`GET /api/chatbot/sessions/{id}/events`) and WebSocket
(`/ws/chatbot/sessions/{id}`). Event envelope: `{ "type", "payload" }`.

| Type                      | Purpose                                                 |
| ------------------------- | ------------------------------------------------------- |
| `assistant.ready`         | Stream connected                                        |
| `assistant.progress`      | Stage updates (`PLANNING`, `VALIDATING`, `APPLYING`, …) |
| `assistant.model.preview` | Incremental preview during compile/validate             |
| `chat.assistant`          | Completed turn payload                                  |
| `model.updated`           | Model revision changed after apply or undo              |

SSE and WebSocket endpoints do not require `X-Auth-Token`; treat session IDs as capability tokens.

## 8. Resilience

| Control           | Implementation                                                                     |
| ----------------- | ---------------------------------------------------------------------------------- |
| Rate limit        | In-memory per user (`VARKA_AI_RATE_LIMIT_REQUESTS` / `VARKA_AI_RATE_LIMIT_WINDOW`) |
| Circuit breaker   | Per provider after consecutive failures                                            |
| Retries           | `VARKA_AI_PROVIDER_RETRY_ATTEMPTS` with backoff                                    |
| Fallback provider | **HTTP 429 only** via `VARKA_AI_FALLBACK_PROVIDER`                                 |
| AI proxy          | Optional HTTP/SOCKS for provider traffic only (`VARKA_AI_PROXY_*`)                 |

The `assistant_rate_limits` table exists in schema but is not written by current Java code.

## 9. Storage Layout

| Table                           | Role                                                                      |
| ------------------------------- | ------------------------------------------------------------------------- |
| `assistant_threads`             | Durable thread per user/project/level                                     |
| `assistant_messages`            | Audit history                                                             |
| `SPRING_AI_CHAT_MEMORY`         | Recent LLM window                                                         |
| `assistant_thread_summaries`    | Rolling summary (deterministic truncation today; summarizer model unused) |
| `assistant_proposals`           | Applied patches, inverse, validation, citations                           |
| `assistant_action_audits`       | Apply, undo, choice events                                                |
| `assistant_retrieval_documents` | RAG documents + `vector(384)` embeddings                                  |
| `assistant_model_contexts`      | Compact snapshots by model revision                                       |

Flyway assistant migrations live under `classpath:db/assistant-migration` in `platform-assistant`.

## 10. Code Map

| Concern         | Primary classes                                                                 |
| --------------- | ------------------------------------------------------------------------------- |
| Orchestration   | `packages/java/platform-assistant/.../AssistantOrchestrator.java`               |
| HTTP API        | `apps/backend/.../ChatbotController.java`                                       |
| Realtime        | `apps/backend/.../AssistantRealtimeHub.java`, `ChatbotWebSocketHandler.java`    |
| ModelDelta      | `.../agent/ModelingAgent.java`, `.../delta/*`                                   |
| Source evidence | `.../source/SourceUnderstandingService.java`, `.../source/SourceChunker.java`   |
| Compile / undo  | `.../delta/DeltaCompiler.java`, `.../patch/AssistantPatchCompiler.java`         |
| RAG             | `.../persistence/jdbc/JdbcAssistantCatalog.java`                                |
| Embeddings      | `.../persistence/embedding/LocalEmbeddingService.java`                          |
| Model context   | `.../persistence/jdbc/JdbcAssistantModelContextIndex.java`                      |
| Tools           | `.../tools/AssistantToolService.java`                                           |
| Hardening       | `.../application/AssistantHardeningService.java`                                |
| Providers       | `.../provider/ConfiguredAssistantModelProvider.java`, OpenAI/Gemini adapters    |
| Config          | `.../config/AiProperties.java`, `apps/backend/.../AssistantServicesConfig.java` |
| Frontend        | `apps/frontend/js/chat.js`                                                      |

## 11. EVL vs Assistant Validation

EVL remains central to the **platform** modeling pipeline:

- Workbench validation endpoints and MDE jobs execute full EVL profiles under `mde/validation/`.
- The assistant uses EVL issue text from the latest model context when available for prompting and
  repair feedback.

The assistant **apply gate** calls `ModelService.validateStructural()` (Ecore constraints). Do not
assume EVL mandatory rules block assistant auto-apply unless that behavior is added explicitly.

`VARKA_AI_SEMANTIC_VALIDATION_ENABLED` is a historical setting and is **not present in `.env.example` or read** by the runtime
code today.

## 12. Attachments and Session Resume

- Upload: `POST /api/chatbot/sessions/{id}/attachments` (`.md`, `.txt`, `.json`).
- Messages reference `attachmentIds`; recent session uploads may be reused automatically.
- Session create accepts `resumeSessionId` and `forceNew`; conversations list supports lookback.

## 13. Limitations (Deliberate Boundaries)

- No LLM fine-tuning on repository assets.
- No wholesale model/metamodel/EVL export to providers.
- Catalog indexing is compact extraction, with metamodel/retrieval services layered above it for
  assistant context.
- Hash embeddings are weaker than ONNX sentence transformers.
- Summarizer model role is configured but not invoked for thread summaries today.
- ModelDelta compilation covers supported element patterns, not every theoretical metamodel edit.
- Source understanding has a conservative chunk/fallback path; richer provider-native extraction
  can continue behind the same boundary.

## 14. Suggested Learning Path

1. `ModelDelta.java` and `ModelingAgent.java` — provider-facing mutation protocol.
2. `AssistantOrchestrator.handleMessage()` — full turn pipeline.
3. `DeltaCompiler` and `AssistantPatchCompiler` — delta lowering, compile, and inverse patches.
4. `JdbcAssistantCatalog.refresh()` — what enters RAG (and what does not).
5. `JdbcAssistantModelContextIndex` — compact model snapshots.
6. `SourceUnderstandingService` — source evidence fallback/chunking boundary.
7. `ModelService.validateStructural()` — apply gate.
8. `apps/frontend/js/chat.js` — UI: choices, applied proposal cards, undo.
9. Diagrams: `docs/diagrams/14-ai-assistant-architecture.md`,
   `15-ai-assistant-turn-and-proposal.md`, `16-ai-assistant-rag-and-memory.md`.

Related references:

- [assistant.md](assistant.md)
- [sample-prompts.md](sample-prompts.md)
- [postgres-storage.md](../operations/postgres-storage.md)
- [realtime-api.md](../../public-docs/docs/reference/realtime-api.md)
