# Assistant Storage ER Diagram

This is the complete assistant schema from Flyway migration V2.

```mermaid
erDiagram
    USERS {
        text id PK
    }
    PROJECTS {
        text id PK
    }
    ASSISTANT_THREADS {
        text id PK
        text user_id FK
        text project_id FK
        text level
        text active_model_id
        bigint active_revision
        text title
        timestamptz created_at
        timestamptz updated_at
    }
    ASSISTANT_MESSAGES {
        text id PK
        text thread_id FK
        text role
        text content
        jsonb metadata
        timestamptz created_at
    }
    SPRING_AI_CHAT_MEMORY {
        text conversation_id
        text content
        varchar type
        timestamp timestamp
    }
    ASSISTANT_THREAD_SUMMARIES {
        text thread_id PK,FK
        text summary
        text message_id FK
        timestamptz updated_at
    }
    ASSISTANT_PROPOSALS {
        text id PK
        text thread_id FK
        text project_id FK
        text model_id
        bigint model_revision
        text risk_level
        jsonb semantic_patch
        jsonb inverse_patch
        jsonb validation_summary
        jsonb citations
        text status
        timestamptz created_at
        timestamptz decided_at
    }
    ASSISTANT_ACTION_AUDITS {
        text id PK
        text proposal_id FK
        text project_id FK
        text actor_id FK
        text action
        jsonb details
        timestamptz created_at
    }
    ASSISTANT_RETRIEVAL_DOCUMENTS {
        text id PK
        text scope
        text source
        text source_hash
        text title
        text content
        jsonb metadata
        vector_384 embedding
        timestamptz updated_at
    }
    ASSISTANT_MODEL_CONTEXTS {
        text model_id PK
        bigint revision PK
        text project_id FK
        text level
        jsonb context_json
        text model_hash
        jsonb latest_issues_json
        timestamptz updated_at
    }
    ASSISTANT_RATE_LIMITS {
        text key PK
        timestamptz window_start
        integer count
    }

    USERS ||--o{ ASSISTANT_THREADS : owns
    PROJECTS ||--o{ ASSISTANT_THREADS : scopes
    ASSISTANT_THREADS ||--o{ ASSISTANT_MESSAGES : records
    ASSISTANT_THREADS ||--o| ASSISTANT_THREAD_SUMMARIES : summarizes
    ASSISTANT_MESSAGES o|--o| ASSISTANT_THREAD_SUMMARIES : summary_through
    ASSISTANT_THREADS ||--o{ ASSISTANT_PROPOSALS : proposes
    PROJECTS ||--o{ ASSISTANT_PROPOSALS : scopes
    ASSISTANT_PROPOSALS o|--o{ ASSISTANT_ACTION_AUDITS : audits
    PROJECTS ||--o{ ASSISTANT_ACTION_AUDITS : scopes
    USERS ||--o{ ASSISTANT_ACTION_AUDITS : acts
    PROJECTS ||--o{ ASSISTANT_MODEL_CONTEXTS : caches
```

`SPRING_AI_CHAT_MEMORY`, `ASSISTANT_RETRIEVAL_DOCUMENTS`, and `ASSISTANT_RATE_LIMITS` have logical
keys but intentionally no foreign keys to the application tables.
