# ADR 0004: PostgreSQL + pgvector for Assistant Memory

- **Status:** Accepted
- **Date:** 2026-03-19
- **Deciders:** Modless core team

## Context

The assistant needs conversational memory and semantic retrieval over project context. File-based
storage was replaced by PostgreSQL for platform persistence. Introducing a separate vector database
would add operational overhead for a thesis-scale deployment.

## Decision

Store assistant chat memory and semantic embeddings in **PostgreSQL 16 with pgvector**:

- Spring AI JDBC chat memory repository for session history
- pgvector column for embedding-backed retrieval when ONNX or hash embeddings are enabled
- Flyway migrations version the schema alongside platform tables
- Pending clarification questions and their original requests are stored for restart-safe resumption
- Integration tests use Testcontainers with the `pgvector/pgvector:pg16` image

## Consequences

### Positive

- Single database for platform state, auth, models, jobs, and assistant memory
- Backup/restore and migration processes stay unified
- pgvector is mature enough for assistant-scale retrieval

### Negative

- Requires pgvector extension in every environment (including CI)
- Vector index tuning may be needed at higher scale
- Embedding model assets (ONNX) add optional deployment weight

## Alternatives considered

- **Dedicated vector store (Pinecone, Weaviate)** — rejected for operational simplicity at current scale
- **In-memory-only assistant context** — rejected; poor multi-session and restart behavior
- **File-based JSON storage** — superseded by ADR motivating PostgreSQL platform storage
