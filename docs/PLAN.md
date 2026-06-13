# Production AI Modeling Assistant Plan

## Summary

Build a bounded, backend-controlled modeling agent around the existing frontend chat panel,
`ModelService`, Ecore/XMI bridge, EVL validator, model locks, and PostgreSQL storage.

The LLM must never receive entire models, metamodels, or EVL files. It receives compact context
assembled from structured catalogs, retrieved documentation, relevant model fragments, current
validation results, recent conversation memory, and canvas selection context.

Every generated mutation must use a typed semantic patch, pass structural and mandatory EVL
validation, respect optimistic concurrency, and remain auditable and reversible.

## Phased Implementation

1. **Foundation and provider gateway.** Upgrade to the current stable Spring Boot 3.5.x and Spring
   AI 1.1.x line. Add provider-neutral `AiProperties` and `AssistantModelProvider` abstractions with
   OpenAI-compatible and Gemini adapters. Support role-specific planner, responder, and summarizer
   models.

2. **Proxy-aware outbound AI client.** Build dedicated proxy-configured Spring AI HTTP clients so
   only AI traffic uses Nekoray. Default to HTTP proxy `127.0.0.1:2081`, support SOCKS
   `127.0.0.1:2082`, and use `host.docker.internal` when running the backend through Compose. Add an
   AI/proxy health indicator and fail AI requests clearly when the proxy is unavailable.

3. **Knowledge retrieval and indexing.** Build authoritative structured catalogs from the combined
   Ecore metamodels and parsed EVL modules. Index by level, EClass, attributes, references,
   multiplicities, constraint context, mandatory/optional kind, and source location. Use exact
   symbol lookup first and RAG for fuzzy explanations and pattern discovery.

4. **PostgreSQL memory and RAG storage.** Enable PGvector through Flyway and a pgvector-enabled
   PostgreSQL image. Use a configurable local ONNX sentence-transformer for embeddings so retrieval
   does not require another remote API. Reindex static metamodel/EVL documents only when their
   hashes change and model context only when its revision changes.

5. **Conversation and audit persistence.** Add assistant thread, message, rolling-summary, proposal,
   approval, validation, and action-audit tables. Use Spring AI JDBC chat memory for the recent
   message window and separate durable history for full auditing. Scope sessions by project and
   modeling level, and record the active model ID and revision on every turn.

6. **Compact model context.** Add a model-context index containing stable element IDs, types, names,
   JSON paths, relationships, graph neighborhoods, and latest validation issues. Replace the
   frontend’s full `currentModel` chat payload with `modelId`, revision, active view, selected
   element IDs, and an optional compact unsaved-draft patch.

7. **Bounded agent orchestration.** Implement `AssistantOrchestrator` using Spring AI `ChatClient`,
   tool calling, schema-validated structured outputs, bounded tool-call loops, and explicit workflow
   states. Expose only whitelisted tools for reading model slices, searching catalogs, previewing
   changes, validating drafts, requesting user choices, and committing approved changes.

8. **Safe semantic patch protocol.** Make the LLM produce operations such as `ADD_ELEMENT`,
   `CONNECT_ELEMENTS`, `SET_ATTRIBUTE`, and `DELETE_ELEMENT` against stable IDs. The backend
   resolves them into existing model patch operations; the LLM never writes raw JSON pointers, XMI,
   database rows, or arbitrary code.

9. **Validation and approval gate.** Apply proposals to an in-memory snapshot, run structural
   validation, then EVL validation. Never commit mandatory violations. Surface optional critiques
   with explanations. Auto-apply only low-risk changes; require approval for destructive, ambiguous,
   bulk, or constraint-sensitive changes. Persist inverse patches for undo.

10. **Frontend and realtime protocol.** Extend the existing chat panel with proposal cards, patch
    previews, validation summaries, citations, choice buttons, apply/reject actions, and progress
    states. Implement the existing REST, WebSocket, and SSE routes, preserving `assistantMessage`,
    `model`, and `model.updated` compatibility while adding richer proposal and choice events.
    Clearing chat must also clear backend memory.

11. **Production hardening and rollout.** Add prompt redaction, prompt-injection defenses, per-user
    rate limits, token budgets, timeouts, retries, circuit breakers, tool limits, metrics, tracing,
    provider/model metadata, and retrieval-source citations. Route bulk operations through the
    existing job framework. Roll out behind feature flags in explain-only, proposal-only, then
    guarded-apply modes.

## Public APIs And Types

- Add `AiProperties`, `AssistantModelProvider`, `AssistantOrchestrator`, `MetamodelCatalogService`,
  `ConstraintCatalogService`, `ModelContextIndexService`, `SemanticModelPatch`, `AssistantProposal`,
  `AssistantChoice`, and `AssistantValidationSummary`.
- Keep `POST /api/chatbot/sessions`, `POST /api/chatbot/sessions/{id}/messages`,
  `GET /api/chatbot/sessions/{id}/events`, and `/ws/chatbot/sessions/{id}`.
- Add proposal approval/rejection, user-choice submission, conversation clearing, and
  proposal-detail endpoints.
- Every proposal response includes affected elements, semantic operations, validation preview, risk
  level, approval requirement, and supporting metamodel/EVL citations.

## Test Plan And Acceptance Criteria

- Unit-test catalog extraction, retrieval ranking, prompt redaction, structured output parsing,
  semantic patch compilation, risk classification, and proxy configuration.
- Integration-test PostgreSQL/PGvector migrations, persistent memory, project-level isolation,
  provider stubs, SSE/WebSocket delivery, optimistic revision conflicts, and audit records.
- Add CIM, PIM, and PSM end-to-end scenarios for explanation, element creation, relationship
  creation, attribute edits, deletion approval, ambiguous choices, validation repair, and undo.
- Add a large-model regression proving outbound prompts use compact retrieved context rather than
  full model, metamodel, or EVL payloads.
- Accept only when every committed assistant mutation passes structural validation and mandatory EVL
  constraints, all LLM calls use the configured proxy, sessions never leak across projects, and
  changing providers requires configuration rather than orchestration rewrites.

## Assumptions And Defaults

- Use Spring Boot 3.5.x with the stable Spring AI 1.1.x line; defer Spring AI 2.0 release
  candidates.
- Use configurable OpenAI-compatible or Gemini chat providers, local ONNX embeddings with PGvector
  for retrieval, and keep both behind provider-neutral interfaces.
- Use project-plus-level conversation scope and hybrid mutation guardrails.

Reference
documentation: [Spring AI OpenAI](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html), [Spring AI Google GenAI](https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html), [Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html), [Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html), [RAG](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html), [PGvector](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html), [ONNX Embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/onnx.html), [Spring Boot HTTP Clients](https://docs.spring.io/spring-boot/how-to/http-clients.html).
