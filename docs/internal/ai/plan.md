# Production AI Modeling Assistant Plan

> **Status:** Implemented (2026). This document records the original design intent. For current
> behavior see [assistant.md](assistant.md),
> [implementation-learning-guide.md](implementation-learning-guide.md), and
> [ai-assistant.md](../../public-docs/docs/guides/ai-assistant.md).

## Summary

Build a bounded, backend-controlled modeling agent around the existing frontend chat panel,
`ModelService`, Ecore/XMI bridge, and PostgreSQL storage.

The LLM must never receive entire models, metamodels, or EVL files. It receives compact context
assembled from structured catalogs, retrieved documentation, relevant model fragments, current
validation issues, recent conversation memory, and canvas selection context.

## Original goals (achieved)

1. **Bounded context** — compact model snapshots, snippet-budgeted RAG, guarded prompts.
2. **Typed mutations** — `SemanticModelPatch` and `model-subset` JSON compiled by Java.
3. **Backend authority** — compile, structural validate, persist, audit.
4. **Auto-apply** — valid mutations apply immediately; undo via inverse patch (no approve/reject API).
5. **Durable memory** — threads, messages, proposals, audits, Spring AI JDBC chat memory.
6. **RAG** — pgvector + full-text over metamodel and methodology catalogs (EVL files excluded from
   catalog indexing).
7. **Resilience** — rate limits, circuit breaker, retries, optional 429-only provider fallback.
8. **Realtime** — SSE and receive-only WebSocket event hub.

## Deviations from the original plan

| Original plan                                    | Current implementation                                            |
| ------------------------------------------------ | ----------------------------------------------------------------- |
| Explicit user approval before apply              | Auto-apply after structural validation                            |
| EVL indexed into RAG catalog                     | EVL rows removed on catalog refresh; metamodel + methodology only |
| EVL gate on assistant apply                      | Ecore structural validation only (`validateStructural`)           |
| Persisted rate limits in `assistant_rate_limits` | In-memory rate limiting                                           |
| Enforced agent step / tool-call env limits       | Variables bound in config but not enforced                        |
| `MODLESS_AI_SEMANTIC_VALIDATION_ENABLED`         | Documented in `.env.example` only; not wired                      |

## Technology choices (unchanged)

- Spring AI for provider abstraction
- PostgreSQL + pgvector for retrieval documents
- OpenAI-compatible and Gemini chat providers
- HASH embeddings by default; optional ONNX profile

## Related diagrams

- `docs/diagrams/14-ai-assistant-architecture.md`
- `docs/diagrams/15-ai-assistant-turn-and-proposal.md`
- `docs/diagrams/16-ai-assistant-rag-and-memory.md`
- `docs/diagrams/08-assistant-storage-er.md`
